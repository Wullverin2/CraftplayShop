package de.craftplay.shop.autosell;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AutoSellChestGui {
    private final CraftplayShopPlugin plugin;
    private final AutoSellChestRegistry registry;
    private final AutoSellChestUpgradeService upgradeService;
    private final AutoSellChestTrustService trustService;
    private final AutoSellChestDisplayService displayService;
    private final AutoSellChestProcessor processor;
    private final AutoSellChestLogService logService;
    private final AutoSellChestService service;

    public AutoSellChestGui(CraftplayShopPlugin plugin,
                            AutoSellChestRegistry registry,
                            AutoSellChestUpgradeService upgradeService,
                            AutoSellChestTrustService trustService,
                            AutoSellChestDisplayService displayService,
                            AutoSellChestProcessor processor,
                            AutoSellChestLogService logService,
                            AutoSellChestService service) {
        this.plugin = plugin;
        this.registry = registry;
        this.upgradeService = upgradeService;
        this.trustService = trustService;
        this.displayService = displayService;
        this.processor = processor;
        this.logService = logService;
        this.service = service;
    }

    public void openList(Player player) {
        YamlConfiguration gui = gui(player);
        int size = size(gui, "list.size", 54);
        AutoSellChestHolder holder = new AutoSellChestHolder(AutoSellChestView.LIST, 0L);
        Inventory inventory = Bukkit.createInventory(holder, size, title(player, gui, "list.title", "&8AutoSellChest"));
        fill(gui, inventory, "list.filler");
        List<AutoSellChest> chests = registry.ownedBy(player.getUniqueId());
        List<Integer> slots = gui.getIntegerList("list.chestSlots");
        if (slots.isEmpty()) {
            slots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        }
        for (int index = 0; index < chests.size() && index < slots.size(); index++) {
            AutoSellChest chest = chests.get(index);
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            holder.chests.put(slot, chest.id());
            inventory.setItem(slot, chestItem(player, gui, chest));
        }
        button(gui, inventory, "list.buttons.close");
        player.openInventory(inventory);
    }

    public void openAdmin(Player player, String query, int page) {
        YamlConfiguration gui = gui(player);
        int size = size(gui, "admin.size", 54);
        AutoSellChestHolder holder = new AutoSellChestHolder(AutoSellChestView.ADMIN_LIST, 0L);
        holder.page = Math.max(0, page);
        holder.query = query == null ? "" : query.trim();
        Inventory inventory = Bukkit.createInventory(holder, size, title(player, gui, "admin.title", "&8AutoSellChest Admin"));
        fill(gui, inventory, "admin.filler");
        List<Integer> slots = gui.getIntegerList("admin.chestSlots");
        if (slots.isEmpty()) {
            slots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        }
        List<AutoSellChest> chests = registry.all().stream()
                .filter(chest -> matches(chest, holder.query))
                .sorted(Comparator.comparingLong(AutoSellChest::id))
                .toList();
        int maxPage = Math.max(0, (chests.size() - 1) / Math.max(1, slots.size()));
        holder.page = Math.min(holder.page, maxPage);
        int start = holder.page * slots.size();
        for (int index = 0; index < slots.size() && start + index < chests.size(); index++) {
            AutoSellChest chest = chests.get(start + index);
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            holder.chests.put(slot, chest.id());
            inventory.setItem(slot, chestItem(player, gui, chest, "admin.chestItem"));
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", Integer.toString(holder.page + 1));
        placeholders.put("pages", Integer.toString(maxPage + 1));
        placeholders.put("query", holder.query.isBlank() ? "-" : holder.query);
        item(gui, inventory, "admin.buttons.previous", placeholders);
        item(gui, inventory, "admin.buttons.next", placeholders);
        item(gui, inventory, "admin.buttons.search", placeholders);
        item(gui, inventory, "admin.buttons.close", placeholders);
        player.openInventory(inventory);
    }

    public void openInfo(Player player, AutoSellChest chest) {
        YamlConfiguration gui = gui(player);
        AutoSellChestHolder holder = new AutoSellChestHolder(AutoSellChestView.INFO, chest.id());
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "info.size", 27), title(player, gui, "info.title", "&8AutoSellChest #%id%", chest));
        fill(gui, inventory, "info.filler");
        item(gui, inventory, "info.items.status", placeholders(player, chest));
        item(gui, inventory, "info.items.toggle", placeholders(player, chest));
        item(gui, inventory, "info.items.notify", placeholders(player, chest));
        item(gui, inventory, "info.items.upgrades", placeholders(player, chest));
        item(gui, inventory, "info.items.stats", placeholders(player, chest));
        item(gui, inventory, "info.items.trust", placeholders(player, chest));
        if (player.hasPermission("craftplayshop.autosellchest.admin")) {
            item(gui, inventory, "info.items.admin", placeholders(player, chest));
        }
        item(gui, inventory, "info.items.teleport", placeholders(player, chest));
        item(gui, inventory, "info.items.delete", placeholders(player, chest));
        button(gui, inventory, "info.buttons.back");
        button(gui, inventory, "info.buttons.close");
        player.openInventory(inventory);
    }

    public void openStats(Player player, AutoSellChest chest) {
        YamlConfiguration gui = gui(player);
        AutoSellChestHolder holder = new AutoSellChestHolder(AutoSellChestView.STATS, chest.id());
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "stats.size", 54), title(player, gui, "stats.title", "&8AutoSellChest Stats", chest));
        fill(gui, inventory, "stats.filler");
        item(gui, inventory, "stats.items.loading", placeholders(player, chest));
        player.openInventory(inventory);
        plugin.getTaskService().runAsync(() -> {
            long todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            StatsSnapshot snapshot = new StatsSnapshot(
                    logService.recentLogs(chest.id(), plugin.getConfig().getInt("autoSellChest.statistics.recentLimit", 8)),
                    logService.materialStats(chest.id(), plugin.getConfig().getInt("autoSellChest.statistics.materialLimit", 8)),
                    logService.stats(chest.id(), todayStart)
            );
            plugin.getTaskService().runSync(() -> openStatsLoaded(player, chest, snapshot));
        });
    }

    private void openStatsLoaded(Player player, AutoSellChest chest, StatsSnapshot snapshot) {
        YamlConfiguration gui = gui(player);
        AutoSellChestHolder holder = new AutoSellChestHolder(AutoSellChestView.STATS, chest.id());
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "stats.size", 54), title(player, gui, "stats.title", "&8AutoSellChest Stats", chest));
        fill(gui, inventory, "stats.filler");
        item(gui, inventory, "stats.items.summary", statsPlaceholders(player, chest, snapshot));
        addRecentLogs(player, gui, inventory, chest, snapshot);
        addMaterialStats(player, gui, inventory, chest, snapshot);
        button(gui, inventory, "stats.buttons.back");
        button(gui, inventory, "stats.buttons.close");
        player.openInventory(inventory);
    }

    public void openUpgrades(Player player, AutoSellChest chest) {
        YamlConfiguration gui = gui(player);
        AutoSellChestHolder holder = new AutoSellChestHolder(AutoSellChestView.UPGRADES, chest.id());
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "upgrades.size", 27), title(player, gui, "upgrades.title", "&8AutoSellChest Upgrades", chest));
        fill(gui, inventory, "upgrades.filler");
        if (upgradeService.intervalUpgradesEnabled()) {
            item(gui, inventory, "upgrades.items.interval", placeholders(player, chest));
        }
        if (upgradeService.multiplierUpgradesEnabled()) {
            item(gui, inventory, "upgrades.items.multiplier", placeholders(player, chest));
        }
        button(gui, inventory, "upgrades.buttons.back");
        button(gui, inventory, "upgrades.buttons.close");
        player.openInventory(inventory);
    }

    public void openTrust(Player player, AutoSellChest chest) {
        if (!plugin.getConfig().getBoolean("autoSellChest.trust.enabled", true)
                || !player.hasPermission("craftplayshop.autosellchest.trust")
                || !canManage(player, chest)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        YamlConfiguration gui = gui(player);
        AutoSellChestHolder holder = new AutoSellChestHolder(AutoSellChestView.TRUST_LIST, chest.id());
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "trust.size", 54), title(player, gui, "trust.title", "&8AutoSellChest Trust", chest));
        fill(gui, inventory, "trust.filler");
        List<Integer> slots = gui.getIntegerList("trust.memberSlots");
        if (slots.isEmpty()) {
            slots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
        }
        List<AutoSellChestTrustEntry> entries = trustService.trusted(chest.id());
        item(gui, inventory, "trust.items.summary", placeholders(player, chest));
        for (int index = 0; index < slots.size() && index < entries.size(); index++) {
            int slot = slots.get(index);
            AutoSellChestTrustEntry entry = entries.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            holder.trusted.put(slot, entry.playerUuid());
            inventory.setItem(slot, trustItem(player, gui, chest, entry));
        }
        item(gui, inventory, "trust.buttons.add", placeholders(player, chest));
        button(gui, inventory, "trust.buttons.back");
        button(gui, inventory, "trust.buttons.close");
        player.openInventory(inventory);
    }

    public void openTrustEntry(Player player, AutoSellChest chest, AutoSellChestTrustEntry entry) {
        YamlConfiguration gui = gui(player);
        AutoSellChestHolder holder = new AutoSellChestHolder(AutoSellChestView.TRUST_ENTRY, chest.id());
        holder.trustedPlayer = entry.playerUuid();
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "trustEntry.size", 27), title(player, gui, "trustEntry.title", "&8Trust: %trusted_player%", chest, entry));
        fill(gui, inventory, "trustEntry.filler");
        Map<String, String> placeholders = placeholders(player, chest, entry);
        item(gui, inventory, "trustEntry.items.info", placeholders);
        item(gui, inventory, "trustEntry.items.open", placeholders);
        item(gui, inventory, "trustEntry.items.manage", placeholders);
        item(gui, inventory, "trustEntry.items.upgrade", placeholders);
        item(gui, inventory, "trustEntry.items.delete", placeholders);
        item(gui, inventory, "trustEntry.items.remove", placeholders);
        button(gui, inventory, "trustEntry.buttons.back");
        button(gui, inventory, "trustEntry.buttons.close");
        player.openInventory(inventory);
    }

    public void openAdminManage(Player player, AutoSellChest chest) {
        YamlConfiguration gui = gui(player);
        AutoSellChestHolder holder = new AutoSellChestHolder(AutoSellChestView.ADMIN_MANAGE, chest.id());
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "adminManage.size", 36), title(player, gui, "adminManage.title", "&8AutoSellChest Admin", chest));
        fill(gui, inventory, "adminManage.filler");
        item(gui, inventory, "adminManage.items.info", placeholders(player, chest));
        item(gui, inventory, "adminManage.items.rename", placeholders(player, chest));
        item(gui, inventory, "adminManage.items.owner", placeholders(player, chest));
        item(gui, inventory, "adminManage.items.toggle", placeholders(player, chest));
        item(gui, inventory, "adminManage.items.notify", placeholders(player, chest));
        item(gui, inventory, "adminManage.items.intervalDown", placeholders(player, chest));
        item(gui, inventory, "adminManage.items.intervalUp", placeholders(player, chest));
        item(gui, inventory, "adminManage.items.multiplierDown", placeholders(player, chest));
        item(gui, inventory, "adminManage.items.multiplierUp", placeholders(player, chest));
        item(gui, inventory, "adminManage.items.delete", placeholders(player, chest));
        button(gui, inventory, "adminManage.buttons.back");
        button(gui, inventory, "adminManage.buttons.close");
        player.openInventory(inventory);
    }

    public void openDeleteConfirm(Player player, AutoSellChest chest) {
        YamlConfiguration gui = gui(player);
        AutoSellChestHolder holder = new AutoSellChestHolder(AutoSellChestView.DELETE_CONFIRM, chest.id());
        Inventory inventory = Bukkit.createInventory(holder, size(gui, "deleteConfirm.size", 27), title(player, gui, "deleteConfirm.title", "&8Delete AutoSellChest", chest));
        fill(gui, inventory, "deleteConfirm.filler");
        item(gui, inventory, "deleteConfirm.items.info", placeholders(player, chest));
        item(gui, inventory, "deleteConfirm.items.confirm", placeholders(player, chest));
        item(gui, inventory, "deleteConfirm.items.cancel", placeholders(player, chest));
        player.openInventory(inventory);
    }

    public void handleClick(Player player, Inventory inventory, int rawSlot, ClickType clickType) {
        if (!(inventory.getHolder() instanceof AutoSellChestHolder holder)) {
            return;
        }
        if (holder.view == AutoSellChestView.LIST || holder.view == AutoSellChestView.ADMIN_LIST) {
            Long id = holder.chests.get(rawSlot);
            if (id == null && holder.view == AutoSellChestView.ADMIN_LIST) {
                String action = actionForSlot(gui(player), "admin", rawSlot);
                if ("next_page".equals(action)) {
                    openAdmin(player, holder.query, holder.page + 1);
                } else if ("previous_page".equals(action)) {
                    openAdmin(player, holder.query, Math.max(0, holder.page - 1));
                } else if ("close".equals(action)) {
                    player.closeInventory();
                }
                return;
            }
            if (id == null) {
                return;
            }
            AutoSellChest chest = registry.find(id);
            if (chest == null) {
                openList(player);
                return;
            }
            if (clickType.isRightClick()) {
                teleport(player, chest);
                return;
            }
            if (clickType.isShiftClick() && clickType.isLeftClick()) {
                openDeleteConfirm(player, chest);
                return;
            }
            openInfo(player, chest);
            return;
        }
        AutoSellChest chest = registry.find(holder.chestId);
        if (chest == null) {
            openList(player);
            return;
        }
        String section = switch (holder.view) {
            case INFO -> "info";
            case UPGRADES -> "upgrades";
            case DELETE_CONFIRM -> "deleteConfirm";
            case STATS -> "stats";
            case TRUST_LIST -> "trust";
            case TRUST_ENTRY -> "trustEntry";
            case ADMIN_MANAGE -> "adminManage";
            case ADMIN_LIST -> "admin";
            case LIST -> "list";
        };
        if (holder.view == AutoSellChestView.TRUST_LIST) {
            UUID trustedId = holder.trusted.get(rawSlot);
            if (trustedId != null) {
                if (clickType.isRightClick()) {
                    removeTrust(player, chest, trustedId);
                    return;
                }
                AutoSellChestTrustEntry entry = trustService.find(chest.id(), trustedId);
                if (entry != null) {
                    openTrustEntry(player, chest, entry);
                } else {
                    openTrust(player, chest);
                }
                return;
            }
        }
        String action = actionForSlot(gui(player), section, rawSlot);
        if ("toggle".equals(action)) {
            toggle(player, chest);
        } else if ("notify".equals(action)) {
            toggleNotify(player, chest);
        } else if ("upgrades".equals(action)) {
            openUpgrades(player, chest);
        } else if ("stats".equals(action)) {
            openStats(player, chest);
        } else if ("trust".equals(action)) {
            openTrust(player, chest);
        } else if ("admin_manage".equals(action)) {
            openAdminManage(player, chest);
        } else if ("rename".equals(action)) {
            service.startTextInput(player, chest, AutoSellChestService.TextInputType.RENAME);
        } else if ("owner".equals(action)) {
            service.startTextInput(player, chest, AutoSellChestService.TextInputType.OWNER);
        } else if ("trust_add".equals(action)) {
            service.startTextInput(player, chest, AutoSellChestService.TextInputType.TRUST_ADD);
        } else if ("trust_toggle_open".equals(action)) {
            toggleTrust(player, chest, holder.trustedPlayer, "open");
        } else if ("trust_toggle_manage".equals(action)) {
            toggleTrust(player, chest, holder.trustedPlayer, "manage");
        } else if ("trust_toggle_upgrade".equals(action)) {
            toggleTrust(player, chest, holder.trustedPlayer, "upgrade");
        } else if ("trust_toggle_delete".equals(action)) {
            toggleTrust(player, chest, holder.trustedPlayer, "delete");
        } else if ("trust_remove".equals(action)) {
            removeTrust(player, chest, holder.trustedPlayer);
        } else if ("admin_interval_down".equals(action)) {
            setIntervalLevel(player, chest, chest.intervalLevel() - 1);
        } else if ("admin_interval_up".equals(action)) {
            setIntervalLevel(player, chest, chest.intervalLevel() + 1);
        } else if ("admin_multiplier_down".equals(action)) {
            setMultiplierLevel(player, chest, chest.multiplierLevel() - 1);
        } else if ("admin_multiplier_up".equals(action)) {
            setMultiplierLevel(player, chest, chest.multiplierLevel() + 1);
        } else if ("upgrade_interval".equals(action)) {
            buyIntervalUpgrade(player, chest);
        } else if ("upgrade_multiplier".equals(action)) {
            buyMultiplierUpgrade(player, chest);
        } else if ("teleport".equals(action)) {
            teleport(player, chest);
        } else if ("delete".equals(action)) {
            openDeleteConfirm(player, chest);
        } else if ("delete_execute".equals(action)) {
            delete(player, chest);
        } else if ("back".equals(action) || "cancel".equals(action)) {
            if (holder.view == AutoSellChestView.UPGRADES || holder.view == AutoSellChestView.DELETE_CONFIRM || holder.view == AutoSellChestView.STATS || holder.view == AutoSellChestView.TRUST_LIST || holder.view == AutoSellChestView.ADMIN_MANAGE) {
                openInfo(player, chest);
            } else if (holder.view == AutoSellChestView.TRUST_ENTRY) {
                openTrust(player, chest);
            } else {
                openList(player);
            }
        } else if ("close".equals(action)) {
            player.closeInventory();
        }
    }

    private void toggle(Player player, AutoSellChest chest) {
        if (!canManage(player, chest)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        AutoSellChest updated = chest.withActive(!chest.active());
        registry.update(updated);
        plugin.getLanguageService().send(player, updated.active() ? "autoSellChest.enabled" : "autoSellChest.disabled",
                Map.of("id", Long.toString(chest.id())));
        openInfo(player, updated);
    }

    private void toggleNotify(Player player, AutoSellChest chest) {
        if (!canManage(player, chest)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        AutoSellChest updated = chest.withNotifyOwner(!chest.notifyOwner());
        registry.update(updated);
        plugin.getLanguageService().send(player,
                updated.notifyOwner() ? "autoSellChest.notifyEnabled" : "autoSellChest.notifyDisabled",
                Map.of("id", Long.toString(chest.id())));
        if (player.hasPermission("craftplayshop.autosellchest.admin")) {
            openAdminManage(player, updated);
            return;
        }
        openInfo(player, updated);
    }

    private void buyIntervalUpgrade(Player player, AutoSellChest chest) {
        if (!trustService.canUpgrade(player, chest)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        AutoSellChestUpgrade upgrade = upgradeService.nextIntervalUpgrade(chest);
        AutoSellChestUpgradeService.UpgradePurchaseResult result = upgradeService.buyIntervalUpgrade(player, chest);
        if (handleUpgradeResult(player, result, upgrade)) {
            AutoSellChest updated = chest.withIntervalLevel(upgrade.level());
            registry.update(updated);
            plugin.getLanguageService().send(player, "autoSellChest.upgradeBought", Map.of(
                    "upgrade", upgrade.name(),
                    "price", plugin.getEconomyService().format(upgrade.price()),
                    "id", Long.toString(chest.id())
            ));
            openUpgrades(player, updated);
        }
    }

    private void buyMultiplierUpgrade(Player player, AutoSellChest chest) {
        if (!trustService.canUpgrade(player, chest)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        AutoSellChestUpgrade upgrade = upgradeService.nextMultiplierUpgrade(chest);
        AutoSellChestUpgradeService.UpgradePurchaseResult result = upgradeService.buyMultiplierUpgrade(player, chest);
        if (handleUpgradeResult(player, result, upgrade)) {
            AutoSellChest updated = chest.withMultiplierLevel(upgrade.level(), upgrade.multiplier());
            registry.update(updated);
            plugin.getLanguageService().send(player, "autoSellChest.upgradeBought", Map.of(
                    "upgrade", upgrade.name(),
                    "price", plugin.getEconomyService().format(upgrade.price()),
                    "id", Long.toString(chest.id())
            ));
            openUpgrades(player, updated);
        }
    }

    private boolean handleUpgradeResult(Player player, AutoSellChestUpgradeService.UpgradePurchaseResult result, AutoSellChestUpgrade upgrade) {
        switch (result) {
            case SUCCESS -> {
                return true;
            }
            case MAX_LEVEL -> plugin.getLanguageService().send(player, "autoSellChest.upgradeMaxLevel");
            case NO_PERMISSION -> plugin.getLanguageService().send(player, "autoSellChest.upgradeNoPermission");
            case NOT_ENOUGH_MONEY -> plugin.getLanguageService().send(player, "autoSellChest.upgradeNotEnoughMoney",
                    Map.of("price", plugin.getEconomyService().format(upgrade == null ? 0.0D : upgrade.price())));
            case ECONOMY_FAILED -> plugin.getLanguageService().send(player, "autoSellChest.upgradeEconomyFailed");
        }
        return false;
    }

    private void toggleTrust(Player player, AutoSellChest chest, UUID trustedPlayer, String right) {
        if (trustedPlayer == null || !canManage(player, chest)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        AutoSellChestTrustEntry entry = trustService.find(chest.id(), trustedPlayer);
        if (entry == null) {
            openTrust(player, chest);
            return;
        }
        AutoSellChestTrustEntry updated = switch (right) {
            case "open" -> entry.withOpenAllowed(!entry.openAllowed());
            case "manage" -> entry.withManageAllowed(!entry.manageAllowed());
            case "upgrade" -> entry.withUpgradeAllowed(!entry.upgradeAllowed());
            case "delete" -> entry.withDeleteAllowed(!entry.deleteAllowed());
            default -> entry;
        };
        trustService.save(updated);
        plugin.getLanguageService().send(player, "autoSellChest.trustUpdated", Map.of("player", updated.playerName(), "id", Long.toString(chest.id())));
        openTrustEntry(player, chest, updated);
    }

    private void removeTrust(Player player, AutoSellChest chest, UUID trustedPlayer) {
        if (trustedPlayer == null || !canManage(player, chest)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        AutoSellChestTrustEntry entry = trustService.find(chest.id(), trustedPlayer);
        trustService.remove(chest.id(), trustedPlayer);
        plugin.getLanguageService().send(player, "autoSellChest.trustRemoved", Map.of("player", entry == null ? "-" : entry.playerName(), "id", Long.toString(chest.id())));
        openTrust(player, chest);
    }

    private void setIntervalLevel(Player player, AutoSellChest chest, int level) {
        if (!player.hasPermission("craftplayshop.autosellchest.admin")) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        int clamped = Math.max(0, Math.min(upgradeService.maxIntervalLevel(), level));
        AutoSellChest updated = chest.withIntervalLevel(clamped);
        registry.update(updated);
        plugin.getLanguageService().send(player, "autoSellChest.adminUpdated", Map.of("id", Long.toString(chest.id())));
        openAdminManage(player, updated);
    }

    private void setMultiplierLevel(Player player, AutoSellChest chest, int level) {
        if (!player.hasPermission("craftplayshop.autosellchest.admin")) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        int clamped = Math.max(0, Math.min(upgradeService.maxMultiplierLevel(), level));
        AutoSellChestUpgrade upgrade = upgradeService.multiplierUpgrade(clamped);
        AutoSellChest updated = chest.withMultiplierLevel(clamped, upgrade == null ? 1.0D : upgrade.multiplier());
        registry.update(updated);
        plugin.getLanguageService().send(player, "autoSellChest.adminUpdated", Map.of("id", Long.toString(chest.id())));
        openAdminManage(player, updated);
    }

    private void teleport(Player player, AutoSellChest chest) {
        Location location = chest.location();
        if (location == null) {
            plugin.getLanguageService().send(player, "autoSellChest.missingPhysicalChest");
            return;
        }
        player.closeInventory();
        player.teleport(location.clone().add(0.5D, 1.0D, 0.5D));
        plugin.getLanguageService().send(player, "autoSellChest.teleported", Map.of("id", Long.toString(chest.id())));
    }

    private void delete(Player player, AutoSellChest chest) {
        if (!trustService.canDelete(player, chest)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        registry.delete(chest);
        trustService.removeAll(chest.id());
        displayService.remove(chest);
        plugin.getLanguageService().send(player, "autoSellChest.deleted", Map.of("id", Long.toString(chest.id())));
        openList(player);
    }

    private boolean canManage(Player player, AutoSellChest chest) {
        return trustService.canManage(player, chest);
    }

    private ItemStack chestItem(Player player, YamlConfiguration gui, AutoSellChest chest) {
        return chestItem(player, gui, chest, "list.chestItem");
    }

    private ItemStack chestItem(Player player, YamlConfiguration gui, AutoSellChest chest, String path) {
        ConfigurationSection section = gui.getConfigurationSection(path);
        ItemStack itemStack = new ItemStack(material(section == null ? "CHEST" : section.getString("material", "CHEST")));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = placeholders(player, chest);
            meta.setDisplayName(TextUtil.color(apply(section == null ? "&aAutoSellChest #%id%" : section.getString("name", "&aAutoSellChest #%id%"), placeholders)));
            List<String> lore = section == null ? List.of() : section.getStringList("lore");
            meta.setLore(lore.stream().map(line -> TextUtil.color(apply(line, placeholders))).toList());
            meta.addItemFlags(ItemFlag.values());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private ItemStack trustItem(Player player, YamlConfiguration gui, AutoSellChest chest, AutoSellChestTrustEntry entry) {
        ConfigurationSection section = gui.getConfigurationSection("trust.memberItem");
        ItemStack itemStack = new ItemStack(material(section == null ? "PLAYER_HEAD" : section.getString("material", "PLAYER_HEAD")));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = placeholders(player, chest, entry);
            meta.setDisplayName(TextUtil.color(apply(section == null ? "&e%trusted_player%" : section.getString("name", "&e%trusted_player%"), placeholders)));
            List<String> lore = section == null ? List.of() : section.getStringList("lore");
            meta.setLore(lore.stream().map(line -> TextUtil.color(apply(line, placeholders))).toList());
            meta.addItemFlags(ItemFlag.values());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void fill(YamlConfiguration gui, Inventory inventory, String path) {
        if (!gui.getBoolean(path + ".enabled", false)) {
            return;
        }
        ItemStack itemStack = named(gui.getString(path + ".material", "BLACK_STAINED_GLASS_PANE"), gui.getString(path + ".name", " "), List.of(), Map.of());
        for (int slot : gui.getIntegerList(path + ".slots")) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, itemStack);
            }
        }
    }

    private void button(YamlConfiguration gui, Inventory inventory, String path) {
        item(gui, inventory, path, Map.of());
    }

    private void item(YamlConfiguration gui, Inventory inventory, String path, Map<String, String> placeholders) {
        if (!gui.getBoolean(path + ".enabled", true)) {
            return;
        }
        int slot = gui.getInt(path + ".slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, named(gui.getString(path + ".material", "STONE"), gui.getString(path + ".name", ""), gui.getStringList(path + ".lore"), placeholders));
    }

    private ItemStack named(String material, String name, List<String> lore, Map<String, String> placeholders) {
        ItemStack itemStack = new ItemStack(material(material));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(apply(name, placeholders)));
            meta.setLore(lore.stream().map(line -> TextUtil.color(apply(line, placeholders))).toList());
            meta.addItemFlags(ItemFlag.values());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private String actionForSlot(YamlConfiguration gui, String section, int slot) {
        for (String key : List.of(
                "items.status", "items.toggle", "items.notify", "items.upgrades", "items.stats", "items.trust", "items.admin", "items.teleport", "items.delete",
                "items.interval", "items.multiplier", "items.info", "items.confirm", "items.cancel", "items.summary", "items.loading",
                "items.rename", "items.owner", "items.intervalDown", "items.intervalUp", "items.multiplierDown", "items.multiplierUp",
                "items.open", "items.manage", "items.upgrade", "items.remove",
                "buttons.previous", "buttons.next", "buttons.search", "buttons.add", "buttons.back", "buttons.close")) {
            String path = section + "." + key;
            if (gui.getInt(path + ".slot", -1) == slot) {
                return gui.getString(path + ".action", key.substring(key.indexOf('.') + 1));
            }
        }
        return "";
    }

    private Map<String, String> placeholders(Player player, AutoSellChest chest) {
        Map<String, String> placeholders = new HashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
        placeholders.put("id", Long.toString(chest.id()));
        placeholders.put("name", chest.name());
        placeholders.put("owner", chest.ownerName());
        placeholders.put("owner_uuid", chest.ownerUuid().toString());
        placeholders.put("world", chest.world());
        placeholders.put("x", Integer.toString(chest.x()));
        placeholders.put("y", Integer.toString(chest.y()));
        placeholders.put("z", Integer.toString(chest.z()));
        placeholders.put("location", chest.world() + " " + chest.x() + " " + chest.y() + " " + chest.z());
        placeholders.put("status", chest.active() ? plugin.getLanguageService().get(player, "autoSellChest.statusActive", Map.of()) : plugin.getLanguageService().get(player, "autoSellChest.statusInactive", Map.of()));
        placeholders.put("notify_status", chest.notifyOwner()
                ? plugin.getLanguageService().get(player, "autoSellChest.notifyStatusOn", Map.of())
                : plugin.getLanguageService().get(player, "autoSellChest.notifyStatusOff", Map.of()));
        placeholders.put("owner_online", Bukkit.getPlayer(chest.ownerUuid()) != null ? "&aonline" : "&coffline");
        placeholders.put("total_items", Long.toString(chest.totalItemsSold()));
        placeholders.put("total_money", plugin.getEconomyService().format(chest.totalMoneyEarned()));
        AutoSellChestUpgrade interval = upgradeService.intervalUpgrade(chest.intervalLevel());
        AutoSellChestUpgrade multiplier = upgradeService.multiplierUpgrade(chest.multiplierLevel());
        AutoSellChestUpgrade nextInterval = upgradeService.nextIntervalUpgrade(chest);
        AutoSellChestUpgrade nextMultiplier = upgradeService.nextMultiplierUpgrade(chest);
        placeholders.put("interval_level", Integer.toString(chest.intervalLevel()));
        placeholders.put("interval_name", interval == null ? "-" : interval.name());
        placeholders.put("interval_seconds", Long.toString(upgradeService.intervalSeconds(chest)));
        placeholders.put("next_interval_name", nextInterval == null ? plugin.getLanguageService().get(player, "autoSellChest.upgradeNone", Map.of()) : nextInterval.name());
        placeholders.put("next_interval_price", nextInterval == null ? "-" : plugin.getEconomyService().format(nextInterval.price()));
        placeholders.put("next_interval_seconds", nextInterval == null ? "-" : Long.toString(nextInterval.intervalSeconds()));
        placeholders.put("multiplier_level", Integer.toString(chest.multiplierLevel()));
        placeholders.put("multiplier_name", multiplier == null ? "-" : multiplier.name());
        placeholders.put("multiplier", Double.toString(upgradeService.multiplier(chest)));
        placeholders.put("next_sell", nextSell(chest));
        placeholders.put("next_sell_seconds", Long.toString(Math.max(0L, processor.secondsUntilNextSale(chest))));
        placeholders.put("debug", processor.lastDebugReason(chest));
        placeholders.put("today_items", "-");
        placeholders.put("today_money", "-");
        placeholders.put("recent_count", "-");
        placeholders.put("material_count", "-");
        placeholders.put("next_multiplier_name", nextMultiplier == null ? plugin.getLanguageService().get(player, "autoSellChest.upgradeNone", Map.of()) : nextMultiplier.name());
        placeholders.put("next_multiplier_price", nextMultiplier == null ? "-" : plugin.getEconomyService().format(nextMultiplier.price()));
        placeholders.put("next_multiplier", nextMultiplier == null ? "-" : Double.toString(nextMultiplier.multiplier()));
        int limit = service.maxChests(player);
        placeholders.put("limit", limit < 0 ? plugin.getLanguageService().get(player, "serverShop.limitUnlimited", Map.of()) : Integer.toString(limit));
        placeholders.put("owned_chests", Integer.toString(registry.countOwned(player.getUniqueId())));
        placeholders.put("trust_count", Integer.toString(trustService.trusted(chest.id()).size()));
        placeholders.put("trust_enabled", plugin.getConfig().getBoolean("autoSellChest.trust.enabled", true) ? "&aaktiv" : "&cinaktiv");
        placeholders.put("upgrades_enabled", upgradeService.upgradesEnabled() ? "&aaktiv" : "&cinaktiv");
        placeholders.put("interval_upgrades_enabled", upgradeService.intervalUpgradesEnabled() ? "&aaktiv" : "&cinaktiv");
        placeholders.put("multiplier_upgrades_enabled", upgradeService.multiplierUpgradesEnabled() ? "&aaktiv" : "&cinaktiv");
        placeholders.put("notify_mode", plugin.getConfig().getString("autoSellChest.messages.notifyMode", "SUMMARY"));
        placeholders.put("notify_processor_status", processor.ownerNotifyStatus(chest));
        return placeholders;
    }

    private Map<String, String> placeholders(Player player, AutoSellChest chest, AutoSellChestTrustEntry entry) {
        Map<String, String> placeholders = placeholders(player, chest);
        placeholders.put("trusted_player", entry.playerName());
        placeholders.put("trusted_uuid", entry.playerUuid().toString());
        placeholders.put("trust_open", entry.openAllowed() ? "&aaktiv" : "&cinaktiv");
        placeholders.put("trust_manage", entry.manageAllowed() ? "&aaktiv" : "&cinaktiv");
        placeholders.put("trust_upgrade", entry.upgradeAllowed() ? "&aaktiv" : "&cinaktiv");
        placeholders.put("trust_delete", entry.deleteAllowed() ? "&aaktiv" : "&cinaktiv");
        return placeholders;
    }

    private Map<String, String> statsPlaceholders(Player player, AutoSellChest chest, StatsSnapshot snapshot) {
        Map<String, String> placeholders = placeholders(player, chest);
        placeholders.put("today_items", Long.toString(snapshot.today.amount()));
        placeholders.put("today_money", plugin.getEconomyService().format(snapshot.today.totalPrice()));
        placeholders.put("recent_count", Integer.toString(snapshot.recent.size()));
        placeholders.put("material_count", Integer.toString(snapshot.materials.size()));
        long recentItems = snapshot.recent.stream().mapToLong(AutoSellChestLogService.LogEntry::amount).sum();
        double recentMoney = snapshot.recent.stream().mapToDouble(AutoSellChestLogService.LogEntry::totalPrice).sum();
        placeholders.put("recent_total_items", Long.toString(recentItems));
        placeholders.put("recent_total_money", plugin.getEconomyService().format(recentMoney));
        return placeholders;
    }

    private void addRecentLogs(Player player, YamlConfiguration gui, Inventory inventory, AutoSellChest chest, StatsSnapshot snapshot) {
        List<Integer> slots = gui.getIntegerList("stats.recentSlots");
        for (int index = 0; index < slots.size() && index < snapshot.recent.size(); index++) {
            AutoSellChestLogService.LogEntry entry = snapshot.recent.get(index);
            Map<String, String> placeholders = placeholders(player, chest);
            placeholders.put("material", entry.material());
            placeholders.put("amount", Integer.toString(entry.amount()));
            placeholders.put("price_each", plugin.getEconomyService().format(entry.priceEach()));
            placeholders.put("total_price", plugin.getEconomyService().format(entry.totalPrice()));
            placeholders.put("time", DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.createdAt())));
            int slot = slots.get(index);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, named(gui.getString("stats.recentItem.material", "PAPER"),
                        gui.getString("stats.recentItem.name", "&e%material%"),
                        gui.getStringList("stats.recentItem.lore"),
                        placeholders));
            }
        }
    }

    private void addMaterialStats(Player player, YamlConfiguration gui, Inventory inventory, AutoSellChest chest, StatsSnapshot snapshot) {
        List<Integer> slots = gui.getIntegerList("stats.materialSlots");
        for (int index = 0; index < slots.size() && index < snapshot.materials.size(); index++) {
            AutoSellChestLogService.MaterialStats entry = snapshot.materials.get(index);
            Map<String, String> placeholders = placeholders(player, chest);
            placeholders.put("material", entry.material());
            placeholders.put("amount", Long.toString(entry.amount()));
            placeholders.put("total_price", plugin.getEconomyService().format(entry.totalPrice()));
            int slot = slots.get(index);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, named(gui.getString("stats.materialItem.material", "CHEST"),
                        gui.getString("stats.materialItem.name", "&e%material%"),
                        gui.getStringList("stats.materialItem.lore"),
                        placeholders));
            }
        }
    }

    private boolean matches(AutoSellChest chest, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String[] tokens = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        String debug = processor.lastDebugReason(chest).toLowerCase(Locale.ROOT);
        int trustCount = trustService.trusted(chest.id()).size();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (token.contains(":")) {
                String[] parts = token.split(":", 2);
                String key = parts[0];
                String value = parts.length > 1 ? parts[1] : "";
                boolean match = switch (key) {
                    case "owner" -> chest.ownerName().toLowerCase(Locale.ROOT).contains(value);
                    case "world" -> chest.world().toLowerCase(Locale.ROOT).contains(value);
                    case "id" -> Long.toString(chest.id()).equals(value);
                    case "status" -> ("active".equals(value) || "aktiv".equals(value)) ? chest.active() : !chest.active();
                    case "notify" -> ("on".equals(value) || "an".equals(value) || "true".equals(value)) ? chest.notifyOwner() : !chest.notifyOwner();
                    case "trust" -> Integer.toString(trustCount).equals(value) || trustService.trusted(chest.id()).stream().anyMatch(entry -> entry.playerName().toLowerCase(Locale.ROOT).contains(value));
                    case "debug" -> debug.contains(value);
                    case "name" -> chest.name().toLowerCase(Locale.ROOT).contains(value);
                    case "online" -> ("true".equals(value) || "yes".equals(value) || "ja".equals(value)) == (Bukkit.getPlayer(chest.ownerUuid()) != null);
                    default -> true;
                };
                if (!match) {
                    return false;
                }
                continue;
            }
            if (!Long.toString(chest.id()).equals(token)
                    && !chest.ownerName().toLowerCase(Locale.ROOT).contains(token)
                    && !chest.ownerUuid().toString().toLowerCase(Locale.ROOT).contains(token)
                    && !chest.world().toLowerCase(Locale.ROOT).contains(token)
                    && !chest.name().toLowerCase(Locale.ROOT).contains(token)
                    && !debug.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private String nextSell(AutoSellChest chest) {
        long seconds = processor.secondsUntilNextSale(chest);
        if (seconds < 0L) {
            return "-";
        }
        if (seconds <= 0L) {
            return plugin.getLanguageService().get(plugin.getConfigService().defaultLanguage(), "autoSellChest.nextSellNow", Map.of());
        }
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        return minutes <= 0L ? seconds + "s" : minutes + "m " + rest + "s";
    }

    private String apply(String text, Map<String, String> placeholders) {
        String value = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return value;
    }

    private YamlConfiguration gui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/autosellchest.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/autosellchest.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private int size(YamlConfiguration gui, String path, int fallback) {
        int size = gui.getInt(path, fallback);
        return Math.max(9, Math.min(54, ((size + 8) / 9) * 9));
    }

    private String title(Player player, YamlConfiguration gui, String path, String fallback) {
        return title(player, gui, path, fallback, null);
    }

    private String title(Player player, YamlConfiguration gui, String path, String fallback, AutoSellChest chest) {
        Map<String, String> placeholders = chest == null ? plugin.getGuiPlaceholderService().placeholders(player) : placeholders(player, chest);
        return TextUtil.color(apply(gui.getString(path, fallback), placeholders));
    }

    private String title(Player player, YamlConfiguration gui, String path, String fallback, AutoSellChest chest, AutoSellChestTrustEntry entry) {
        Map<String, String> placeholders = entry == null ? placeholders(player, chest) : placeholders(player, chest, entry);
        return TextUtil.color(apply(gui.getString(path, fallback), placeholders));
    }

    private Material material(String value) {
        Material material = Material.matchMaterial(value == null ? "STONE" : value);
        return material == null ? Material.STONE : material;
    }

    public static final class AutoSellChestHolder implements InventoryHolder {
        private final AutoSellChestView view;
        private final long chestId;
        private final Map<Integer, Long> chests = new HashMap<>();
        private final Map<Integer, UUID> trusted = new HashMap<>();
        private UUID trustedPlayer;
        private int page;
        private String query = "";

        private AutoSellChestHolder(AutoSellChestView view, long chestId) {
            this.view = view;
            this.chestId = chestId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public enum AutoSellChestView {
        LIST,
        INFO,
        UPGRADES,
        DELETE_CONFIRM,
        STATS,
        ADMIN_LIST,
        ADMIN_MANAGE,
        TRUST_LIST,
        TRUST_ENTRY
    }

    private record StatsSnapshot(List<AutoSellChestLogService.LogEntry> recent,
                                 List<AutoSellChestLogService.MaterialStats> materials,
                                 AutoSellChestLogService.Stats today) {
    }
}
