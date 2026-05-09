package de.craftplay.shop.autosell;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.transaction.TransactionType;
import de.craftplay.shop.servershop.ServerShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoSellChestProcessor {
    private final CraftplayShopPlugin plugin;
    private final AutoSellChestRegistry registry;
    private final AutoSellChestLogService logService;
    private final AutoSellChestUpgradeService upgradeService;
    private final Map<Long, String> lastDebugReasons = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastNotifyAt = new ConcurrentHashMap<>();
    private BukkitTask task;

    public AutoSellChestProcessor(CraftplayShopPlugin plugin, AutoSellChestRegistry registry, AutoSellChestLogService logService, AutoSellChestUpgradeService upgradeService) {
        this.plugin = plugin;
        this.registry = registry;
        this.logService = logService;
        this.upgradeService = upgradeService;
    }

    public void start() {
        stop();
        if (!plugin.getConfigService().autoSellChestEnabled()) {
            return;
        }
        long scanInterval = Math.max(1L, plugin.getConfig().getLong("autoSellChest.performance.scanIntervalSeconds", 10L));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processTick, 40L, scanInterval * 20L);
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = null;
    }

    public void processNow(AutoSellChest chest) {
        process(chest);
    }

    public String lastDebugReason(AutoSellChest chest) {
        return lastDebugReasons.getOrDefault(chest.id(), "Warte auf den naechsten Verkauf.");
    }

    public long secondsUntilNextSale(AutoSellChest chest) {
        if (!chest.active()) {
            return -1L;
        }
        long intervalMillis = Math.max(1L, upgradeService.intervalSeconds(chest)) * 1000L;
        long remaining = Math.max(0L, (chest.lastSoldAt() + intervalMillis) - System.currentTimeMillis());
        return (remaining + 999L) / 1000L;
    }

    public String ownerNotifyStatus(AutoSellChest chest) {
        if (!plugin.getConfig().getBoolean("autoSellChest.messages.notifyOwnerOnSell", true)) {
            return "global_off";
        }
        if (!chest.notifyOwner()) {
            return "chest_off";
        }
        if (!canNotifyByCooldown(chest)) {
            return "cooldown";
        }
        return "ready";
    }

    private void processTick() {
        long dirtyCooldownMillis = Math.max(0L, plugin.getConfig().getLong("autoSellChest.performance.dirtyCooldownSeconds", 5L)) * 1000L;
        int maxChests = Math.max(1, plugin.getConfig().getInt("autoSellChest.performance.maxChestsPerTick", 5));
        boolean dirtyOnly = plugin.getConfig().getBoolean("autoSellChest.performance.processOnlyDirtyChests", false);
        for (AutoSellChest chest : registry.nextProcessBatch(maxChests, 0L, dirtyOnly, dirtyCooldownMillis, upgradeService)) {
            process(chest);
        }
    }

    private void process(AutoSellChest chest) {
        if (!plugin.getServerShopService().allowSell()) {
            setReason(chest, "ServerShop-Ankauf ist deaktiviert.");
            return;
        }
        if (plugin.getConfig().getBoolean("autoSellChest.selling.sellOnlyWhenOwnerOnline", false)
                && Bukkit.getPlayer(chest.ownerUuid()) == null) {
            setReason(chest, "Besitzer ist offline.");
            return;
        }
        Location location = chest.location();
        if (location == null || location.getWorld() == null) {
            setReason(chest, "Welt ist nicht geladen.");
            return;
        }
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            setReason(chest, "Chunk ist nicht geladen.");
            return;
        }
        Block block = location.getBlock();
        BlockState state = block.getState();
        if (!(state instanceof Container container)) {
            setReason(chest, "Physische Kiste fehlt.");
            registry.delete(chest);
            return;
        }
        SellPlan plan = createPlan(container.getInventory(), chest);
        if (plan.totalAmount <= 0 || plan.totalPrice <= 0.0D) {
            setReason(chest, "Keine ankaufbaren Items gefunden.");
            registry.update(chest.withSale(0L, 0.0D));
            return;
        }
        applyRemoval(container.getInventory(), plan.removals);
        OfflinePlayer owner = Bukkit.getOfflinePlayer(chest.ownerUuid());
        if (!plugin.getEconomyService().deposit(owner, plan.totalPrice)) {
            restore(container.getInventory(), plan.originals);
            setReason(chest, "Vault-Auszahlung fehlgeschlagen.");
            return;
        }
        setReason(chest, "Letzter Verkauf: " + plan.totalAmount + " Items fuer " + plugin.getEconomyService().format(plan.totalPrice) + ".");
        AutoSellChest updated = chest.withSale(plan.totalAmount, plan.totalPrice);
        registry.update(updated);
        for (SaleLine line : plan.lines.values()) {
            logService.logAsync(updated, line.material, line.amount, line.priceEach, line.totalPrice);
        }
        Player onlineOwner = Bukkit.getPlayer(chest.ownerUuid());
        if (onlineOwner != null) {
            ItemStack representative = plan.lines.values().stream().findFirst().map(line -> line.itemStack).orElse(null);
            plugin.getTransactionService().logAsync(TransactionType.AUTOSELL_CHEST, onlineOwner,
                    "AutoSellChest #" + chest.id(), representative, plan.totalAmount, 0.0D, plan.totalPrice);
            notifyOwner(updated, onlineOwner, plan);
        }
    }

    private SellPlan createPlan(Inventory inventory, AutoSellChest chest) {
        int maxItems = Math.max(1, plugin.getConfig().getInt("autoSellChest.performance.maxItemsPerScan", 2304));
        SellPlan plan = new SellPlan();
        ItemStack[] contents = inventory.getContents();
        int scannedItems = 0;
        for (int slot = 0; slot < contents.length && scannedItems < maxItems; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            scannedItems += stack.getAmount();
            ServerShopItem shopItem = plugin.getServerShopRegistry().findSellable(stack);
            if (shopItem == null || !shopItem.sellEnabled() || shopItem.sellPrice() <= 0.0D) {
                continue;
            }
            int amount = Math.min(stack.getAmount(), Math.max(0, maxItems - (scannedItems - stack.getAmount())));
            if (amount <= 0) {
                continue;
            }
            double priceEach = shopItem.sellPrice() * Math.max(0.0D, upgradeService.multiplier(chest));
            double total = priceEach * amount;
            plan.originals.add(new SlotSnapshot(slot, stack.clone()));
            plan.removals.add(new SlotRemoval(slot, amount));
            plan.totalAmount += amount;
            plan.totalPrice += total;
            String key = shopItem.material().name() + "@" + priceEach;
            SaleLine line = plan.lines.computeIfAbsent(key, ignored -> new SaleLine(shopItem.material().name(), priceEach, shopItem.createStack(1)));
            line.amount += amount;
            line.totalPrice += total;
        }
        return plan;
    }

    private void applyRemoval(Inventory inventory, List<SlotRemoval> removals) {
        ItemStack[] contents = inventory.getContents();
        for (SlotRemoval removal : removals) {
            ItemStack stack = contents[removal.slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            int updated = stack.getAmount() - removal.amount;
            if (updated <= 0) {
                contents[removal.slot] = null;
            } else {
                stack.setAmount(updated);
            }
        }
        inventory.setContents(contents);
    }

    private void restore(Inventory inventory, List<SlotSnapshot> snapshots) {
        ItemStack[] contents = inventory.getContents();
        for (SlotSnapshot snapshot : snapshots) {
            contents[snapshot.slot] = snapshot.itemStack.clone();
        }
        inventory.setContents(contents);
    }

    private void notifyOwner(AutoSellChest chest, Player owner, SellPlan plan) {
        if (!plugin.getConfig().getBoolean("autoSellChest.messages.notifyOwnerOnSell", true)
                || !chest.notifyOwner()
                || plan.totalPrice < Math.max(0.0D, plugin.getConfig().getDouble("autoSellChest.messages.minimumTotalPrice", 0.0D))
                || !canNotifyByCooldown(chest)) {
            return;
        }
        String mode = plugin.getConfig().getString("autoSellChest.messages.notifyMode", "SUMMARY");
        if ("PER_ITEM".equalsIgnoreCase(mode)) {
            for (SaleLine line : plan.lines.values()) {
                plugin.getLanguageService().send(owner, "autoSellChest.soldLine", Map.of(
                        "amount", Integer.toString(line.amount),
                        "price", plugin.getEconomyService().format(line.totalPrice),
                        "price_each", plugin.getEconomyService().format(line.priceEach),
                        "item", line.material,
                        "material", line.material,
                        "id", Long.toString(chest.id())
                ));
            }
        } else {
            plugin.getLanguageService().send(owner, "autoSellChest.sold", Map.of(
                    "amount", Integer.toString(plan.totalAmount),
                    "price", plugin.getEconomyService().format(plan.totalPrice),
                    "line_count", Integer.toString(plan.lines.size()),
                    "id", Long.toString(chest.id())
            ));
        }
        lastNotifyAt.put(chest.id(), System.currentTimeMillis());
    }

    private boolean canNotifyByCooldown(AutoSellChest chest) {
        long cooldownMillis = Math.max(0L, plugin.getConfig().getLong("autoSellChest.messages.cooldownSeconds", 0L)) * 1000L;
        if (cooldownMillis <= 0L) {
            return true;
        }
        Long last = lastNotifyAt.get(chest.id());
        return last == null || System.currentTimeMillis() - last >= cooldownMillis;
    }

    private static final class SellPlan {
        private final List<SlotSnapshot> originals = new ArrayList<>();
        private final List<SlotRemoval> removals = new ArrayList<>();
        private final Map<String, SaleLine> lines = new LinkedHashMap<>();
        private int totalAmount;
        private double totalPrice;
    }

    private void setReason(AutoSellChest chest, String reason) {
        lastDebugReasons.put(chest.id(), reason);
    }

    private record SlotSnapshot(int slot, ItemStack itemStack) {
    }

    private record SlotRemoval(int slot, int amount) {
    }

    private static final class SaleLine {
        private final String material;
        private final double priceEach;
        private final ItemStack itemStack;
        private int amount;
        private double totalPrice;

        private SaleLine(String material, double priceEach, ItemStack itemStack) {
            this.material = material;
            this.priceEach = priceEach;
            this.itemStack = itemStack;
        }
    }
}
