package de.craftplay.shop.trade;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.core.transaction.TransactionType;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DirectTradeService implements Listener {
    private final CraftplayShopPlugin plugin;
    private final Map<UUID, TradeRequestService> incomingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> requestCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, TradeSession> sessionsByPlayer = new ConcurrentHashMap<>();
    private final Map<Long, TradeSession> sessionsById = new ConcurrentHashMap<>();
    private final Set<UUID> programmaticClose = ConcurrentHashMap.newKeySet();
    private final AtomicLong sessionIds = new AtomicLong(1L);
    private final TradeGui tradeGui;

    public DirectTradeService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
        this.tradeGui = new TradeGui(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void toggle(Player player) {
        if (!player.hasPermission(PermissionNodes.TRADE_TOGGLE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        boolean enabled = !plugin.getPlayerSettingsService().getSettings(player).directTradeEnabled();
        setEnabled(player, enabled);
    }

    public void setEnabled(Player player, boolean enabled) {
        if (!player.hasPermission(PermissionNodes.TRADE_TOGGLE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        plugin.getPlayerSettingsService().setDirectTrade(player, enabled);
        sendStatus(player, enabled);
    }

    public void requestTrade(Player sender, Player target) {
        if (!enabled()) {
            plugin.getLanguageService().send(sender, "general.featureNotAvailable");
            return;
        }
        if (!sender.hasPermission(PermissionNodes.TRADE_REQUEST)) {
            plugin.getLanguageService().send(sender, "general.noPermission");
            return;
        }
        if (target == null || !target.isOnline()) {
            plugin.getLanguageService().send(sender, "trade.playerNotFound");
            return;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            plugin.getLanguageService().send(sender, "trade.selfTarget");
            return;
        }
        if (session(sender) != null || session(target) != null) {
            plugin.getLanguageService().send(sender, "trade.busy");
            return;
        }
        if (plugin.getConfig().getBoolean("directTrade.blockSendingWhenDisabled", true)
                && !plugin.getPlayerSettingsService().getSettings(sender).directTradeEnabled()) {
            plugin.getLanguageService().send(sender, "trade.disabledSelf");
            return;
        }
        if (plugin.getConfig().getBoolean("directTrade.blockRequestsWhenDisabled", true)
                && !plugin.getPlayerSettingsService().getSettings(target).directTradeEnabled()) {
            plugin.getLanguageService().send(sender, "trade.targetDisabled");
            return;
        }
        if (plugin.getConfig().getBoolean("directTrade.request.requireSameWorld", true)
                && !sender.getWorld().equals(target.getWorld())) {
            plugin.getLanguageService().send(sender, "trade.sameWorldRequired");
            return;
        }
        double maxDistance = Math.max(0.0D, plugin.getConfig().getDouble("directTrade.request.maxDistance", 10.0D));
        if (maxDistance > 0.0D && sender.getLocation().distanceSquared(target.getLocation()) > maxDistance * maxDistance) {
            plugin.getLanguageService().send(sender, "trade.tooFar", Map.of("distance", trim(maxDistance)));
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0L, plugin.getConfig().getLong("directTrade.request.cooldownSeconds", 10L)) * 1000L;
        Long nextAllowed = requestCooldowns.get(sender.getUniqueId());
        if (nextAllowed != null && nextAllowed > now) {
            plugin.getLanguageService().send(sender, "trade.cooldown", Map.of("seconds", Long.toString(Math.max(1L, (nextAllowed - now + 999L) / 1000L))));
            return;
        }
        long timeoutMillis = Math.max(5L, plugin.getConfig().getLong("directTrade.request.timeoutSeconds", 30L)) * 1000L;
        incomingRequests.put(target.getUniqueId(), new TradeRequestService(sender.getUniqueId(), sender.getName(), target.getUniqueId(), now, now + timeoutMillis));
        requestCooldowns.put(sender.getUniqueId(), now + cooldownMillis);
        plugin.getLanguageService().send(sender, "trade.requestSent", Map.of("target", target.getName()));
        plugin.getLanguageService().send(target, "trade.requestReceived", Map.of("player", sender.getName()));
        plugin.getTaskService().runAsync(() -> {
            try {
                Thread.sleep(timeoutMillis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            plugin.getTaskService().runSync(() -> expireRequest(target.getUniqueId(), sender.getUniqueId()));
        });
    }

    public void accept(Player player) {
        if (!enabled()) {
            plugin.getLanguageService().send(player, "general.featureNotAvailable");
            return;
        }
        if (!player.hasPermission(PermissionNodes.TRADE_ACCEPT)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        TradeRequestService request = incomingRequests.get(player.getUniqueId());
        if (request == null || request.expired()) {
            incomingRequests.remove(player.getUniqueId());
            plugin.getLanguageService().send(player, "trade.noRequest");
            return;
        }
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null) {
            incomingRequests.remove(player.getUniqueId());
            plugin.getLanguageService().send(player, "trade.requestExpired");
            return;
        }
        if (session(player) != null || session(sender) != null) {
            incomingRequests.remove(player.getUniqueId());
            plugin.getLanguageService().send(player, "trade.busy");
            return;
        }
        incomingRequests.remove(player.getUniqueId());
        openSession(sender, player);
    }

    public void deny(Player player) {
        TradeRequestService request = incomingRequests.remove(player.getUniqueId());
        if (request == null || request.expired()) {
            plugin.getLanguageService().send(player, "trade.noRequest");
            return;
        }
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null) {
            plugin.getLanguageService().send(sender, "trade.requestDenied", Map.of("target", player.getName()));
        }
        plugin.getLanguageService().send(player, "trade.requestDeniedSelf", Map.of("player", request.senderName()));
    }

    public void cancel(Player player) {
        TradeSession session = session(player);
        if (session == null) {
            plugin.getLanguageService().send(player, "trade.noActiveTrade");
            return;
        }
        cancelSession(session, "trade.cancelled", Map.of("player", player.getName()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTradeClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeHolder holder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        TradeSession session = sessionsById.get(holder.sessionId());
        if (session == null || !session.isParticipant(player.getUniqueId()) || session.state() != TradeState.OPEN) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() >= event.getInventory().getSize()) {
            moveFromPlayerInventory(player, session, event.getCurrentItem());
            return;
        }
        handleTradeSlot(player, session, holder.viewer(), event.getRawSlot(), event.getClick());
    }

    @EventHandler
    public void onTradeClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeHolder holder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (programmaticClose.remove(player.getUniqueId())) {
            return;
        }
        TradeSession session = sessionsById.get(holder.sessionId());
        if (session != null && session.state() == TradeState.OPEN) {
            cancelSession(session, "trade.closed", Map.of("player", player.getName()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        incomingRequests.remove(event.getPlayer().getUniqueId());
        TradeSession session = session(event.getPlayer());
        if (session != null) {
            cancelSession(session, "trade.closed", Map.of("player", event.getPlayer().getName()));
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("directTrade.enabled", false);
    }

    private void openSession(Player first, Player second) {
        TradeSession session = new TradeSession(first.getUniqueId(), second.getUniqueId(), TradeState.OPEN, System.currentTimeMillis());
        long sessionId = sessionIds.getAndIncrement();
        Inventory firstInventory = Bukkit.createInventory(new TradeHolder(sessionId, first.getUniqueId()), 54, tradeGui.title(first, session));
        Inventory secondInventory = Bukkit.createInventory(new TradeHolder(sessionId, second.getUniqueId()), 54, tradeGui.title(second, session));
        session.setFirstInventory(firstInventory);
        session.setSecondInventory(secondInventory);
        sessionsById.put(sessionId, session);
        sessionsByPlayer.put(first.getUniqueId(), session);
        sessionsByPlayer.put(second.getUniqueId(), session);
        refreshInventories(session);
        first.openInventory(firstInventory);
        second.openInventory(secondInventory);
        plugin.getLanguageService().send(first, "trade.requestAccepted", Map.of("target", second.getName()));
        plugin.getLanguageService().send(second, "trade.opened", Map.of("target", first.getName()));
    }

    private void refreshInventories(TradeSession session) {
        Player first = session.firstOnline();
        Player second = session.secondOnline();
        if (first != null && session.firstInventory() != null) {
            renderInventory(first, session, session.firstInventory());
        }
        if (second != null && session.secondInventory() != null) {
            renderInventory(second, session, session.secondInventory());
        }
    }

    private void renderInventory(Player viewer, TradeSession session, Inventory inventory) {
        inventory.clear();
        tradeGui.fillDecorations(viewer, inventory);
        boolean firstPerspective = session.firstPlayer().equals(viewer.getUniqueId());
        ItemStack[] ownOffers = firstPerspective ? session.offers(session.firstPlayer()) : session.offers(session.secondPlayer());
        ItemStack[] otherOffers = firstPerspective ? session.offers(session.secondPlayer()) : session.offers(session.firstPlayer());
        for (int index = 0; index < TradeGui.LEFT_OFFER_SLOTS.length; index++) {
            if (ownOffers[index] != null) {
                inventory.setItem(TradeGui.LEFT_OFFER_SLOTS[index], ownOffers[index]);
            }
            if (otherOffers[index] != null) {
                inventory.setItem(TradeGui.RIGHT_OFFER_SLOTS[index], otherOffers[index]);
            }
        }
        YamlConfiguration gui = tradeGui.gui(viewer);
        Map<String, String> placeholders = placeholders(viewer, session);
        putConfigured(gui, inventory, "items.info", placeholders);
        putConfigured(gui, inventory, "items.ownMoney", placeholders);
        putConfigured(gui, inventory, "items.otherMoney", placeholders);
        putConfigured(gui, inventory, "items.ready", placeholders);
        putConfigured(gui, inventory, "items.cancel", placeholders);
    }

    private Map<String, String> placeholders(Player viewer, TradeSession session) {
        UUID viewerId = viewer.getUniqueId();
        UUID otherId = session.other(viewerId);
        Player other = Bukkit.getPlayer(otherId);
        boolean ready = session.ready(viewerId);
        boolean finalConfirmed = session.finalConfirmed(viewerId);
        boolean otherReady = session.ready(otherId);
        boolean otherFinal = session.finalConfirmed(otherId);
        Map<String, String> placeholders = new HashMap<>(plugin.getGuiPlaceholderService().placeholders(viewer));
        placeholders.put("target", other == null ? "?" : other.getName());
        placeholders.put("own_money", plugin.getEconomyService().format(session.money(viewerId)));
        placeholders.put("other_money", plugin.getEconomyService().format(session.money(otherId)));
        placeholders.put("ready", ready ? plugin.getLanguageService().get(viewer, "trade.statusEnabled") : plugin.getLanguageService().get(viewer, "trade.statusDisabled"));
        placeholders.put("other_ready", otherReady ? plugin.getLanguageService().get(viewer, "trade.statusEnabled") : plugin.getLanguageService().get(viewer, "trade.statusDisabled"));
        placeholders.put("final", finalConfirmed ? plugin.getLanguageService().get(viewer, "trade.statusEnabled") : plugin.getLanguageService().get(viewer, "trade.statusDisabled"));
        placeholders.put("other_final", otherFinal ? plugin.getLanguageService().get(viewer, "trade.statusEnabled") : plugin.getLanguageService().get(viewer, "trade.statusDisabled"));
        placeholders.put("phase", session.bothReady() && plugin.getConfig().getBoolean("directTrade.confirmation.requireFinalConfirm", true)
                ? plugin.getLanguageService().get(viewer, "trade.phaseFinal")
                : plugin.getLanguageService().get(viewer, "trade.phaseReady"));
        return placeholders;
    }

    private void putConfigured(YamlConfiguration gui, Inventory inventory, String path, Map<String, String> placeholders) {
        ConfigurationSection section = gui.getConfigurationSection(path);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, tradeGui.configuredItem(section, placeholders));
    }

    private void moveFromPlayerInventory(Player player, TradeSession session, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        ItemStack[] offers = session.offers(player.getUniqueId());
        int freeIndex = -1;
        for (int index = 0; index < offers.length; index++) {
            if (offers[index] == null || offers[index].getType().isAir()) {
                freeIndex = index;
                break;
            }
        }
        if (freeIndex < 0) {
            plugin.getLanguageService().send(player, "trade.offerFull");
            return;
        }
        ItemStack stack = clicked.clone();
        offers[freeIndex] = stack;
        player.getInventory().removeItem(stack);
        changed(session);
    }

    private void handleTradeSlot(Player player, TradeSession session, UUID viewerId, int slot, ClickType clickType) {
        int ownIndex = indexOf(TradeGui.LEFT_OFFER_SLOTS, slot);
        if (ownIndex >= 0) {
            ItemStack[] offers = session.offers(viewerId);
            ItemStack stack = offers[ownIndex];
            if (stack == null || stack.getType().isAir()) {
                return;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
            if (!leftovers.isEmpty()) {
                plugin.getLanguageService().send(player, "serverShop.inventoryFull");
                return;
            }
            offers[ownIndex] = null;
            changed(session);
            return;
        }
        YamlConfiguration gui = tradeGui.gui(player);
        if (slot == gui.getInt("items.ownMoney.slot", 46)) {
            adjustMoney(player, session, clickType);
            return;
        }
        if (slot == gui.getInt("items.ready.slot", 49)) {
            toggleReady(player, session);
            return;
        }
        if (slot == gui.getInt("items.cancel.slot", 53)) {
            cancelSession(session, "trade.cancelled", Map.of("player", player.getName()));
        }
    }

    private void adjustMoney(Player player, TradeSession session, ClickType clickType) {
        if (!plugin.getConfig().getBoolean("directTrade.money.enabled", true)) {
            return;
        }
        double delta = clickType.isShiftClick() ? 10.0D : 1.0D;
        double current = session.money(player.getUniqueId());
        double updated = clickType.isRightClick() ? current - delta : current + delta;
        double min = Math.max(0.0D, plugin.getConfig().getDouble("directTrade.money.minAmount", 1.0D));
        double max = Math.max(min, plugin.getConfig().getDouble("directTrade.money.maxAmount", 1000000.0D));
        if (updated > 0.0D && updated < min) {
            updated = 0.0D;
        }
        updated = Math.max(0.0D, Math.min(max, updated));
        session.setMoney(player.getUniqueId(), updated);
        changed(session);
    }

    private void toggleReady(Player player, TradeSession session) {
        UUID playerId = player.getUniqueId();
        boolean requireFinal = plugin.getConfig().getBoolean("directTrade.confirmation.requireFinalConfirm", true);
        if (requireFinal && session.bothReady()) {
            session.setFinalConfirmed(playerId, !session.finalConfirmed(playerId));
            refreshInventories(session);
            if (session.bothFinalConfirmed()) {
                executeTrade(session);
            }
            return;
        }
        session.setReady(playerId, !session.ready(playerId));
        refreshInventories(session);
        if (session.bothReady() && !requireFinal) {
            executeTrade(session);
        }
    }

    private void changed(TradeSession session) {
        if (plugin.getConfig().getBoolean("directTrade.confirmation.resetReadyOnChange", true)) {
            session.resetConfirmations();
        }
        refreshInventories(session);
    }

    private void executeTrade(TradeSession session) {
        Player first = session.firstOnline();
        Player second = session.secondOnline();
        if (first == null || second == null) {
            cancelSession(session, "trade.closed", Map.of("player", first == null ? "?" : first.getName()));
            return;
        }
        if (!hasSpaceFor(first, session.oppositeOffers(first.getUniqueId())) || !hasSpaceFor(second, session.oppositeOffers(second.getUniqueId()))) {
            plugin.getLanguageService().send(first, "trade.inventoryFullTrade");
            plugin.getLanguageService().send(second, "trade.inventoryFullTrade");
            return;
        }
        double firstMoney = session.money(first.getUniqueId());
        double secondMoney = session.money(second.getUniqueId());
        if (firstMoney > 0.0D && !plugin.getEconomyService().withdraw(first, firstMoney)) {
            plugin.getLanguageService().send(first, "trade.notEnoughMoney");
            return;
        }
        if (secondMoney > 0.0D && !plugin.getEconomyService().withdraw(second, secondMoney)) {
            if (firstMoney > 0.0D) {
                plugin.getEconomyService().deposit(first, firstMoney);
            }
            plugin.getLanguageService().send(second, "trade.notEnoughMoney");
            return;
        }
        giveOffers(first, session.oppositeOffers(first.getUniqueId()));
        giveOffers(second, session.oppositeOffers(second.getUniqueId()));
        if (firstMoney > 0.0D) {
            plugin.getEconomyService().deposit(second, firstMoney);
        }
        if (secondMoney > 0.0D) {
            plugin.getEconomyService().deposit(first, secondMoney);
        }
        logTrade(first, second, session.oppositeOffers(first.getUniqueId()), firstMoney, secondMoney);
        logTrade(second, first, session.oppositeOffers(second.getUniqueId()), secondMoney, firstMoney);
        closeSession(session, TradeState.COMPLETED);
        plugin.getLanguageService().send(first, "trade.completed", Map.of("target", second.getName()));
        plugin.getLanguageService().send(second, "trade.completed", Map.of("target", first.getName()));
    }

    private void logTrade(Player player, Player target, ItemStack[] received, double paid, double earned) {
        int amount = 0;
        ItemStack representative = null;
        for (ItemStack itemStack : received) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            amount += itemStack.getAmount();
            if (representative == null) {
                representative = itemStack;
            }
        }
        plugin.getTransactionService().logAsync(TransactionType.DIRECT_TRADE, player,
                "directtrade:" + target.getUniqueId(), representative, amount, paid, earned);
    }

    private boolean hasSpaceFor(Player player, ItemStack[] items) {
        HashMap<Integer, ItemStack> test = new HashMap<>();
        ItemStack[] contents = player.getInventory().getStorageContents().clone();
        Inventory inventory = Bukkit.createInventory(null, 54);
        inventory.setStorageContents(contents);
        for (ItemStack itemStack : items) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            test.putAll(inventory.addItem(itemStack.clone()));
            if (!test.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void giveOffers(Player player, ItemStack[] items) {
        for (ItemStack itemStack : items) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            player.getInventory().addItem(itemStack.clone());
        }
    }

    private void cancelSession(TradeSession session, String key, Map<String, String> placeholders) {
        Player first = session.firstOnline();
        Player second = session.secondOnline();
        if (first != null) {
            restoreOffers(first, session.offers(first.getUniqueId()));
        }
        if (second != null) {
            restoreOffers(second, session.offers(second.getUniqueId()));
        }
        closeSession(session, TradeState.CANCELLED);
        if (first != null) {
            plugin.getLanguageService().send(first, key, placeholders);
        }
        if (second != null) {
            plugin.getLanguageService().send(second, key, placeholders);
        }
    }

    private void restoreOffers(Player player, ItemStack[] offers) {
        for (ItemStack itemStack : offers) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            player.getInventory().addItem(itemStack.clone());
        }
    }

    private void closeSession(TradeSession session, TradeState state) {
        session.setState(state);
        sessionsByPlayer.remove(session.firstPlayer());
        sessionsByPlayer.remove(session.secondPlayer());
        Long sessionId = findSessionId(session);
        if (sessionId != null) {
            sessionsById.remove(sessionId);
        }
        Player first = session.firstOnline();
        Player second = session.secondOnline();
        if (first != null && first.getOpenInventory() != null && first.getOpenInventory().getTopInventory() == session.firstInventory()) {
            programmaticClose.add(first.getUniqueId());
            first.closeInventory();
        }
        if (second != null && second.getOpenInventory() != null && second.getOpenInventory().getTopInventory() == session.secondInventory()) {
            programmaticClose.add(second.getUniqueId());
            second.closeInventory();
        }
        session.clearOffers();
    }

    private Long findSessionId(TradeSession session) {
        for (Map.Entry<Long, TradeSession> entry : sessionsById.entrySet()) {
            if (entry.getValue() == session) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void expireRequest(UUID targetId, UUID senderId) {
        TradeRequestService request = incomingRequests.get(targetId);
        if (request == null || !request.sender().equals(senderId) || !request.expired()) {
            return;
        }
        incomingRequests.remove(targetId);
        Player sender = Bukkit.getPlayer(senderId);
        Player target = Bukkit.getPlayer(targetId);
        if (sender != null) {
            plugin.getLanguageService().send(sender, "trade.requestExpired");
        }
        if (target != null) {
            plugin.getLanguageService().send(target, "trade.requestExpired");
        }
    }

    private TradeSession session(Player player) {
        return session(player.getUniqueId());
    }

    private TradeSession session(UUID playerId) {
        return sessionsByPlayer.get(playerId);
    }

    private int indexOf(int[] values, int needle) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == needle) {
                return index;
            }
        }
        return -1;
    }

    private String trim(double value) {
        if (Math.floor(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private void sendStatus(Player player, boolean enabled) {
        plugin.getLanguageService().send(player, enabled ? "trade.enabledSelf" : "trade.disabledSelf");
        plugin.getLanguageService().send(player, "trade.toggleStatus", Map.of(
                "status", plugin.getLanguageService().get(player, enabled ? "trade.statusEnabled" : "trade.statusDisabled")
        ));
    }

    private static final class TradeHolder implements InventoryHolder {
        private final long sessionId;
        private final UUID viewer;

        private TradeHolder(long sessionId, UUID viewer) {
            this.sessionId = sessionId;
            this.viewer = viewer;
        }

        public long sessionId() {
            return sessionId;
        }

        public UUID viewer() {
            return viewer;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
