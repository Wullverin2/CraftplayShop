package de.craftplay.shop.autosell;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AutoSellChestService implements Listener, CommandExecutor, TabCompleter {
    private final CraftplayShopPlugin plugin;
    private final NamespacedKey itemKey;
    private final AutoSellChestRegistry registry;
    private final AutoSellChestLogService logService;
    private final AutoSellChestUpgradeService upgradeService;
    private final AutoSellChestTrustService trustService;
    private final AutoSellChestProcessor processor;
    private final AutoSellChestDisplayService displayService;
    private final AutoSellChestGui gui;
    private final Map<UUID, PendingTextInput> textInputs = new HashMap<>();
    private BukkitTask cleanupTask;

    public AutoSellChestService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "autosell_chest_item");
        this.registry = new AutoSellChestRegistry(plugin);
        this.logService = new AutoSellChestLogService(plugin);
        this.upgradeService = new AutoSellChestUpgradeService(plugin);
        this.trustService = new AutoSellChestTrustService(plugin);
        this.processor = new AutoSellChestProcessor(plugin, registry, logService, upgradeService);
        this.displayService = new AutoSellChestDisplayService(plugin, registry, upgradeService, processor);
        this.gui = new AutoSellChestGui(plugin, registry, upgradeService, trustService, displayService, processor, logService, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void load() {
        upgradeService.load();
        registry.load();
        trustService.load();
        processor.start();
        displayService.start();
        startCleanupTask();
    }

    public void shutdown() {
        processor.stop();
        displayService.stop();
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        cleanupTask = null;
    }

    public AutoSellChestRegistry registry() {
        return registry;
    }

    public AutoSellChestGui gui() {
        return gui;
    }

    public AutoSellChestUpgradeService upgradeService() {
        return upgradeService;
    }

    public AutoSellChestTrustService trustService() {
        return trustService;
    }

    public AutoSellChestDisplayService displayService() {
        return displayService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            open(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("give".equals(sub)) {
            give(sender, args);
            return true;
        }
        if ("list".equals(sub) || "liste".equals(sub)) {
            open(sender);
            return true;
        }
        if ("admin".equals(sub)) {
            admin(sender, args);
            return true;
        }
        if ("create".equals(sub) || "erstellen".equals(sub)) {
            createLookedAt(sender);
            return true;
        }
        if ("toggle".equals(sub)) {
            toggleLookedAt(sender);
            return true;
        }
        if ("remove".equals(sub) || "delete".equals(sub) || "loeschen".equals(sub) || "löschen".equals(sub)) {
            removeLookedAt(sender);
            return true;
        }
        if ("reload".equals(sub)) {
            if (!sender.hasPermission(PermissionNodes.RELOAD)) {
                plugin.getLanguageService().send(sender, "general.noPermission");
                return true;
            }
            plugin.reloadAll();
            plugin.getLanguageService().send(sender, "general.reloadDone");
            return true;
        }
        plugin.getLanguageService().send(sender, "autoSellChest.usage");
        return true;
    }

    private void open(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.AUTOSELL_CHEST_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        if (!plugin.getConfigService().autoSellChestEnabled()) {
            plugin.getLanguageService().send(player, "autoSellChest.disabledFeature");
            return;
        }
        gui.openList(player);
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionNodes.AUTOSELL_CHEST_ADMIN)) {
            plugin.getLanguageService().send(sender, "general.noPermission");
            return;
        }
        if (args.length < 2) {
            plugin.getLanguageService().send(sender, "autoSellChest.giveUsage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getLanguageService().send(sender, "autoSellChest.playerNotFound");
            return;
        }
        int amount = args.length > 2 ? parseInt(args[2], 1) : 1;
        target.getInventory().addItem(createChestItem(Math.max(1, amount))).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        plugin.getLanguageService().send(sender, "autoSellChest.given", Map.of("player", target.getName(), "amount", Integer.toString(Math.max(1, amount))));
        plugin.getLanguageService().send(target, "autoSellChest.received", Map.of("amount", Integer.toString(Math.max(1, amount))));
    }

    private void createLookedAt(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!plugin.getConfigService().autoSellChestEnabled()) {
            plugin.getLanguageService().send(player, "autoSellChest.disabledFeature");
            return;
        }
        if (!player.hasPermission(PermissionNodes.AUTOSELL_CHEST_CREATE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        Block block = player.getTargetBlockExact(5);
        if (block == null || !(block.getState() instanceof Container)) {
            plugin.getLanguageService().send(player, "autoSellChest.lookAtContainer");
            return;
        }
        if (registry.find(block.getLocation()) != null) {
            plugin.getLanguageService().send(player, "autoSellChest.alreadyExists");
            return;
        }
        if (!plugin.getProtectionService().canCreateShop(player, block.getLocation())) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        int limit = maxChests(player);
        if (limit >= 0 && registry.countOwned(player.getUniqueId()) >= limit) {
            plugin.getLanguageService().send(player, "autoSellChest.limitReached", Map.of("limit", Integer.toString(limit)));
            return;
        }
        AutoSellChest chest = registry.create(player, block);
        if (chest == null) {
            plugin.getLanguageService().send(player, "general.databaseError");
            return;
        }
        playPlaceEffect(block.getLocation());
        plugin.getLanguageService().send(player, "autoSellChest.created", Map.of("id", Long.toString(chest.id())));
    }

    private void toggleLookedAt(CommandSender sender) {
        AutoSellChest chest = lookedAtChest(sender);
        if (chest == null) {
            return;
        }
        if (!trustService.canDelete((Player) sender, chest)) {
            plugin.getLanguageService().send(sender, "general.noPermission");
            return;
        }
        AutoSellChest updated = chest.withActive(!chest.active());
        registry.update(updated);
        plugin.getLanguageService().send(sender, updated.active() ? "autoSellChest.enabled" : "autoSellChest.disabled", Map.of("id", Long.toString(chest.id())));
    }

    private void removeLookedAt(CommandSender sender) {
        AutoSellChest chest = lookedAtChest(sender);
        if (chest == null) {
            return;
        }
        if (!canManage((Player) sender, chest)) {
            plugin.getLanguageService().send(sender, "general.noPermission");
            return;
        }
        registry.delete(chest);
        trustService.removeAll(chest.id());
        displayService.remove(chest);
        plugin.getLanguageService().send(sender, "autoSellChest.deleted", Map.of("id", Long.toString(chest.id())));
    }

    private void admin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.AUTOSELL_CHEST_ADMIN)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        String query = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
        gui.openAdmin(player, query, 0);
    }

    public void startTextInput(Player player, AutoSellChest chest, TextInputType type) {
        if (!canManage(player, chest)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        if (type == TextInputType.TRUST_ADD && !player.hasPermission(PermissionNodes.AUTOSELL_CHEST_TRUST)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        synchronized (textInputs) {
            textInputs.put(player.getUniqueId(), new PendingTextInput(chest.id(), type));
        }
        player.closeInventory();
        String key = switch (type) {
            case RENAME -> "autoSellChest.renamePrompt";
            case OWNER -> "autoSellChest.ownerPrompt";
            case TRUST_ADD -> "autoSellChest.trustAddPrompt";
        };
        plugin.getLanguageService().send(player, key, Map.of("id", Long.toString(chest.id())));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PendingTextInput input;
        synchronized (textInputs) {
            input = textInputs.remove(event.getPlayer().getUniqueId());
        }
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        plugin.getTaskService().runSync(() -> handleTextInput(event.getPlayer(), input, event.getMessage()));
    }

    private void handleTextInput(Player player, PendingTextInput input, String message) {
        AutoSellChest chest = registry.find(input.chestId());
        if (chest == null) {
            plugin.getLanguageService().send(player, "autoSellChest.missingPhysicalChest");
            return;
        }
        if ("cancel".equalsIgnoreCase(message) || "abbrechen".equalsIgnoreCase(message)) {
            plugin.getLanguageService().send(player, "autoSellChest.inputCancelled");
            gui.openInfo(player, chest);
            return;
        }
        switch (input.type()) {
            case RENAME -> rename(player, chest, message);
            case OWNER -> changeOwner(player, chest, message);
            case TRUST_ADD -> addTrust(player, chest, message);
        }
    }

    private void rename(Player player, AutoSellChest chest, String name) {
        String updatedName = name.trim();
        if (updatedName.isBlank() || updatedName.length() > 48) {
            plugin.getLanguageService().send(player, "autoSellChest.invalidInput");
            gui.openInfo(player, chest);
            return;
        }
        AutoSellChest updated = chest.withName(updatedName);
        registry.update(updated);
        plugin.getLanguageService().send(player, "autoSellChest.renamed", Map.of("id", Long.toString(chest.id()), "name", updatedName));
        gui.openInfo(player, updated);
    }

    private void changeOwner(Player player, AutoSellChest chest, String targetName) {
        if (!player.hasPermission(PermissionNodes.AUTOSELL_CHEST_ADMIN)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName.trim());
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.getLanguageService().send(player, "autoSellChest.playerNotFound");
            gui.openInfo(player, chest);
            return;
        }
        String name = target.getName() == null ? targetName.trim() : target.getName();
        AutoSellChest updated = chest.withOwner(target.getUniqueId(), name);
        registry.update(updated);
        plugin.getLanguageService().send(player, "autoSellChest.ownerChanged", Map.of("id", Long.toString(chest.id()), "owner", name));
        gui.openInfo(player, updated);
    }

    private void addTrust(Player player, AutoSellChest chest, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName.trim());
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.getLanguageService().send(player, "autoSellChest.playerNotFound");
            gui.openTrust(player, chest);
            return;
        }
        String name = target.getName() == null ? targetName.trim() : target.getName();
        if (target.getUniqueId().equals(chest.ownerUuid())) {
            plugin.getLanguageService().send(player, "autoSellChest.trustOwnerBlocked");
            gui.openTrust(player, chest);
            return;
        }
        AutoSellChestTrustEntry entry = trustService.add(chest.id(), target.getUniqueId(), name);
        if (entry == null) {
            plugin.getLanguageService().send(player, "autoSellChest.disabledFeature");
            gui.openInfo(player, chest);
            return;
        }
        plugin.getLanguageService().send(player, "autoSellChest.trustAdded", Map.of("player", name, "id", Long.toString(chest.id())));
        gui.openTrust(player, chest);
    }

    private AutoSellChest lookedAtChest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return null;
        }
        Block block = player.getTargetBlockExact(5);
        if (block == null) {
            plugin.getLanguageService().send(player, "autoSellChest.lookAtChest");
            return null;
        }
        AutoSellChest chest = registry.find(block.getLocation());
        if (chest == null) {
            plugin.getLanguageService().send(player, "autoSellChest.notAutoSellChest");
        }
        return chest;
    }

    public ItemStack createChestItem(int amount) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("autoSellChest.item.material", "CHEST"));
        if (material == null || !material.isBlock()) {
            material = Material.CHEST;
        }
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(plugin.getConfig().getString("autoSellChest.item.name", "&cAutoSellChest")));
            List<String> lore = plugin.getConfig().getStringList("autoSellChest.item.lore").stream().map(TextUtil::color).toList();
            meta.setLore(lore);
            int customModelData = plugin.getConfig().getInt("autoSellChest.item.customModelData", 0);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    public boolean isAutoSellChestItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isAutoSellChestItem(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getConfigService().autoSellChestEnabled()) {
            event.setCancelled(true);
            plugin.getLanguageService().send(player, "autoSellChest.disabledFeature");
            return;
        }
        if (!player.hasPermission(PermissionNodes.AUTOSELL_CHEST_CREATE)) {
            event.setCancelled(true);
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        if (!(event.getBlockPlaced().getState() instanceof Container)) {
            event.setCancelled(true);
            plugin.getLanguageService().send(player, "autoSellChest.mustBeContainer");
            return;
        }
        if (!plugin.getProtectionService().canCreateShop(player, event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        int limit = maxChests(player);
        if (limit >= 0 && registry.countOwned(player.getUniqueId()) >= limit) {
            event.setCancelled(true);
            plugin.getLanguageService().send(player, "autoSellChest.limitReached", Map.of("limit", Integer.toString(limit)));
            return;
        }
        AutoSellChest chest = registry.create(player, event.getBlockPlaced());
        if (chest == null) {
            event.setCancelled(true);
            plugin.getLanguageService().send(player, "general.databaseError");
            return;
        }
        playPlaceEffect(event.getBlockPlaced().getLocation());
        plugin.getLanguageService().send(player, "autoSellChest.created", Map.of("id", Long.toString(chest.id())));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        AutoSellChest chest = registry.find(event.getBlock().getLocation());
        if (chest == null) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.getConfig().getBoolean("autoSellChest.protection.blockBreakingByOthers", true)
                && !trustService.canDelete(player, chest)) {
            event.setCancelled(true);
            plugin.getLanguageService().send(player, "autoSellChest.breakProtected");
            return;
        }
        registry.delete(chest);
        trustService.removeAll(chest.id());
        displayService.remove(chest);
        if (plugin.getConfig().getBoolean("autoSellChest.item.dropItemOnBreak", true)) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5D, 0.5D, 0.5D), createChestItem(1));
        }
        plugin.getLanguageService().send(player, "autoSellChest.deleted", Map.of("id", Long.toString(chest.id())));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        AutoSellChest chest = registry.find(event.getClickedBlock().getLocation());
        if (chest == null) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            gui.openInfo(event.getPlayer(), chest);
            return;
        }
        if (!trustService.canOpen(event.getPlayer(), chest) && plugin.getConfig().getBoolean("autoSellChest.protection.protectContainer", true)) {
            event.setCancelled(true);
            plugin.getLanguageService().send(event.getPlayer(), "autoSellChest.containerProtected");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        AutoSellChest source = findByInventory(event.getSource());
        AutoSellChest destination = findByInventory(event.getDestination());
        if (source != null && plugin.getConfig().getBoolean("autoSellChest.protection.blockHopperExtraction", true)) {
            event.setCancelled(true);
            return;
        }
        if (destination != null) {
            if (!plugin.getConfig().getBoolean("autoSellChest.protection.allowHopperInsertion", true)) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> registry.markDirty(destination));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        AutoSellChest chest = findByInventory(event.getInventory());
        if (chest != null) {
            registry.markDirty(chest);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AutoSellChestGui.AutoSellChestHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == event.getView().getTopInventory()) {
                gui.handleClick(player, event.getView().getTopInventory(), event.getRawSlot(), event.getClick());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AutoSellChestGui.AutoSellChestHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("autoSellChest.protection.blockExplosionDamage", true)) {
            event.blockList().removeIf(block -> {
                AutoSellChest chest = registry.find(block.getLocation());
                if (chest != null) {
                    registry.delete(chest);
                    trustService.removeAll(chest.id());
                    displayService.remove(chest);
                }
                return false;
            });
            return;
        }
        event.blockList().removeIf(block -> registry.find(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("autoSellChest.protection.blockExplosionDamage", true)) {
            event.blockList().removeIf(block -> {
                AutoSellChest chest = registry.find(block.getLocation());
                if (chest != null) {
                    registry.delete(chest);
                    trustService.removeAll(chest.id());
                    displayService.remove(chest);
                }
                return false;
            });
            return;
        }
        event.blockList().removeIf(block -> registry.find(block.getLocation()) != null);
    }

    private AutoSellChest findByInventory(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Container container) {
            return registry.find(container.getBlock().getLocation());
        }
        if (holder instanceof DoubleChest doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide();
            InventoryHolder right = doubleChest.getRightSide();
            if (left instanceof Container leftContainer) {
                AutoSellChest chest = registry.find(leftContainer.getBlock().getLocation());
                if (chest != null) {
                    return chest;
                }
            }
            if (right instanceof Container rightContainer) {
                return registry.find(rightContainer.getBlock().getLocation());
            }
        }
        return null;
    }

    private boolean canManage(Player player, AutoSellChest chest) {
        return trustService.canManage(player, chest);
    }

    public int maxChests(Player player) {
        if (player.hasPermission(PermissionNodes.AUTOSELL_CHEST_ADMIN)
                && plugin.getConfig().getBoolean("autoSellChest.maxChests.adminBypass", true)) {
            return -1;
        }
        int value = plugin.getConfig().getInt("autoSellChest.maxChests.default", 5);
        for (String key : plugin.getConfig().getConfigurationSection("autoSellChest.maxChests.permissionOverrides") == null
                ? List.<String>of()
                : plugin.getConfig().getConfigurationSection("autoSellChest.maxChests.permissionOverrides").getKeys(false)) {
            if (player.hasPermission("craftplayshop.autosellchest.limit." + key)) {
                int override = plugin.getConfig().getInt("autoSellChest.maxChests.permissionOverrides." + key, value);
                if (override < 0) {
                    return -1;
                }
                value = Math.max(value, override);
            }
        }
        return value;
    }

    private void playPlaceEffect(Location location) {
        if (plugin.getConfig().getBoolean("autoSellChest.effects.placeParticles", true)) {
            location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0.5D, 1.0D, 0.5D), 12, 0.35D, 0.35D, 0.35D, 0.02D);
        }
        if (plugin.getConfig().getBoolean("autoSellChest.effects.placeSound", true)) {
            location.getWorld().playSound(location, Sound.BLOCK_CHEST_LOCKED, 0.8F, 1.2F);
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("list", "admin", "create", "give", "toggle", "remove", "reload"), args[0]);
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(lower)).toList();
    }

    private void startCleanupTask() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        if (!plugin.getConfigService().autoSellChestEnabled()
                || !plugin.getConfig().getBoolean("autoSellChest.cleanup.enabled", true)) {
            cleanupTask = null;
            return;
        }
        long interval = Math.max(10L, plugin.getConfig().getLong("autoSellChest.cleanup.intervalSeconds", 60L));
        cleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int removed = registry.cleanupMissingPhysicalChests();
            if (removed > 0 && plugin.getConfigService().debug()) {
                plugin.getPluginLogService().info("Cleaned up " + removed + " missing AutoSellChest(s).");
            }
        }, interval * 20L, interval * 20L);
    }

    public record PendingTextInput(long chestId, TextInputType type) {
    }

    public enum TextInputType {
        RENAME,
        OWNER,
        TRUST_ADD
    }
}
