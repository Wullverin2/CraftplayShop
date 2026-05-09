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

public class AutoSellChestGui {
    private final CraftplayShopPlugin plugin;
    private final AutoSellChestRegistry registry;
    private final AutoSellChestUpgradeService upgradeService;
    private final AutoSellChestProcessor processor;
    private final AutoSellChestLogService logService;

    public AutoSellChestGui(CraftplayShopPlugin plugin,
                            AutoSellChestRegistry registry,
                            AutoSellChestUpgradeService upgradeService,
                            AutoSellChestProcessor processor,
                            AutoSellChestLogService logService) {
        this.plugin = plugin;
        this.registry = registry;
        this.upgradeService = upgradeService;
        this.processor = processor;
        this.logService = logService;
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
        item(gui, inventory, "info.items.upgrades", placeholders(player, chest));
        item(gui, inventory, "info.items.stats", placeholders(player, chest));
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
        item(gui, inventory, "upgrades.items.interval", placeholders(player, chest));
        item(gui, inventory, "upgrades.items.multiplier", placeholders(player, chest));
        button(gui, inventory, "upgrades.buttons.back");
        button(gui, inventory, "upgrades.buttons.close");
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
            case ADMIN_LIST -> "admin";
            case LIST -> "list";
        };
        String action = actionForSlot(gui(player), section, rawSlot);
        if ("toggle".equals(action)) {
            toggle(player, chest);
        } else if ("upgrades".equals(action)) {
            openUpgrades(player, chest);
        } else if ("stats".equals(action)) {
            openStats(player, chest);
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
            if (holder.view == AutoSellChestView.UPGRADES || holder.view == AutoSellChestView.DELETE_CONFIRM || holder.view == AutoSellChestView.STATS) {
                openInfo(player, chest);
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

    private void buyIntervalUpgrade(Player player, AutoSellChest chest) {
        if (!canManage(player, chest)) {
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
        if (!canManage(player, chest)) {
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
        if (!canManage(player, chest)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        registry.delete(chest);
        plugin.getLanguageService().send(player, "autoSellChest.deleted", Map.of("id", Long.toString(chest.id())));
        openList(player);
    }

    private boolean canManage(Player player, AutoSellChest chest) {
        return chest.ownerUuid().equals(player.getUniqueId()) || player.hasPermission("craftplayshop.autosellchest.admin");
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
                "items.status", "items.toggle", "items.upgrades", "items.stats", "items.teleport", "items.delete",
                "items.interval", "items.multiplier", "items.info", "items.confirm", "items.cancel", "items.summary", "items.loading",
                "buttons.previous", "buttons.next", "buttons.search", "buttons.back", "buttons.close")) {
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
        placeholders.put("owner", chest.ownerName());
        placeholders.put("world", chest.world());
        placeholders.put("x", Integer.toString(chest.x()));
        placeholders.put("y", Integer.toString(chest.y()));
        placeholders.put("z", Integer.toString(chest.z()));
        placeholders.put("status", chest.active() ? plugin.getLanguageService().get(player, "autoSellChest.statusActive", Map.of()) : plugin.getLanguageService().get(player, "autoSellChest.statusInactive", Map.of()));
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
        placeholders.put("debug", processor.lastDebugReason(chest));
        placeholders.put("today_items", "-");
        placeholders.put("today_money", "-");
        placeholders.put("recent_count", "-");
        placeholders.put("material_count", "-");
        placeholders.put("next_multiplier_name", nextMultiplier == null ? plugin.getLanguageService().get(player, "autoSellChest.upgradeNone", Map.of()) : nextMultiplier.name());
        placeholders.put("next_multiplier_price", nextMultiplier == null ? "-" : plugin.getEconomyService().format(nextMultiplier.price()));
        placeholders.put("next_multiplier", nextMultiplier == null ? "-" : Double.toString(nextMultiplier.multiplier()));
        return placeholders;
    }

    private Map<String, String> statsPlaceholders(Player player, AutoSellChest chest, StatsSnapshot snapshot) {
        Map<String, String> placeholders = placeholders(player, chest);
        placeholders.put("today_items", Long.toString(snapshot.today.amount()));
        placeholders.put("today_money", plugin.getEconomyService().format(snapshot.today.totalPrice()));
        placeholders.put("recent_count", Integer.toString(snapshot.recent.size()));
        placeholders.put("material_count", Integer.toString(snapshot.materials.size()));
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
        String lower = query.toLowerCase(Locale.ROOT);
        return Long.toString(chest.id()).equals(lower)
                || chest.ownerName().toLowerCase(Locale.ROOT).contains(lower)
                || chest.ownerUuid().toString().toLowerCase(Locale.ROOT).contains(lower)
                || chest.world().toLowerCase(Locale.ROOT).contains(lower);
    }

    private String nextSell(AutoSellChest chest) {
        if (!chest.active()) {
            return "-";
        }
        long remaining = Math.max(0L, (chest.lastSoldAt() + (upgradeService.intervalSeconds(chest) * 1000L)) - System.currentTimeMillis());
        long seconds = (remaining + 999L) / 1000L;
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

    private Material material(String value) {
        Material material = Material.matchMaterial(value == null ? "STONE" : value);
        return material == null ? Material.STONE : material;
    }

    public static final class AutoSellChestHolder implements InventoryHolder {
        private final AutoSellChestView view;
        private final long chestId;
        private final Map<Integer, Long> chests = new HashMap<>();
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
        ADMIN_LIST
    }

    private record StatsSnapshot(List<AutoSellChestLogService.LogEntry> recent,
                                 List<AutoSellChestLogService.MaterialStats> materials,
                                 AutoSellChestLogService.Stats today) {
    }
}
