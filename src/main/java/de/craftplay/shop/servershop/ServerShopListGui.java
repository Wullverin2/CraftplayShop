package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerShopListGui {
    private final CraftplayShopPlugin plugin;
    private final Map<UUID, Boolean> searchInputs = new ConcurrentHashMap<>();

    public ServerShopListGui(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void requestSearch(Player player) {
        searchInputs.put(player.getUniqueId(), true);
        player.closeInventory();
        plugin.getLanguageService().send(player, "serverShop.searchInput");
    }

    public boolean hasSearchInput(Player player) {
        return searchInputs.containsKey(player.getUniqueId());
    }

    public void handleSearchInput(Player player, String message) {
        searchInputs.remove(player.getUniqueId());
        if ("cancel".equalsIgnoreCase(message)) {
            plugin.getLanguageService().send(player, "serverShop.searchCancelled");
            plugin.getServerShopGui().open(player);
            return;
        }
        openSearch(player, message);
    }

    public void openSearch(Player player, String query) {
        List<ServerShopItem> results = search(query);
        openList(player, ServerShopListView.SEARCH, query, results);
        if (results.isEmpty()) {
            plugin.getLanguageService().send(player, "serverShop.searchNoResults");
        }
    }

    public void openFavorites(Player player) {
        List<ServerShopItem> results = favorites(player);
        openList(player, ServerShopListView.FAVORITES, "", results);
        if (results.isEmpty()) {
            plugin.getLanguageService().send(player, "serverShop.favoritesEmpty");
        }
    }

    public void handleClick(Player player, ServerShopListHolder holder, InventoryClickEvent event) {
        String itemKey = holder.itemAt(event.getRawSlot());
        if (itemKey == null) {
            YamlConfiguration gui = loadGui(player);
            handleConfiguredButton(player, gui, holder.view(), event.getRawSlot(), event.isRightClick());
            return;
        }
        ServerShopItem item = itemByKey(itemKey);
        if (item == null) {
            plugin.getLanguageService().send(player, "gui.missingItem", Map.of("item", itemKey));
            reopen(player, holder);
            return;
        }
        plugin.getServerShopCategoryGui().executeClassicTransaction(player, item, event.isRightClick(), event.isShiftClick());
        reopen(player, holder);
    }

    public void toggleFavorite(Player player, ServerShopItem item) {
        boolean added = plugin.getServerShopFavoriteService().toggle(player, item);
        plugin.getLanguageService().send(player, added ? "serverShop.favoriteAdded" : "serverShop.favoriteRemoved", Map.of(
                "item", TextUtil.color(item.displayName())
        ));
    }

    private void reopen(Player player, ServerShopListHolder holder) {
        if (holder.view() == ServerShopListView.FAVORITES) {
            openFavorites(player);
            return;
        }
        openSearch(player, holder.query());
    }

    private void openList(Player player, ServerShopListView view, String query, List<ServerShopItem> results) {
        YamlConfiguration gui = loadGui(player);
        String section = view == ServerShopListView.SEARCH ? "search" : "favorites";
        Map<Integer, String> itemsBySlot = new HashMap<>();
        ServerShopListHolder holder = new ServerShopListHolder(view, query, itemsBySlot);
        Map<String, String> basePlaceholders = new HashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
        basePlaceholders.put("query", query);
        basePlaceholders.put("result_count", Integer.toString(results.size()));
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt(section + ".size", 54)),
                TextUtil.color(parse(player, gui.getString(section + ".title", "&8ServerShop"), basePlaceholders)));
        holder.setInventory(inventory);
        fill(inventory, gui);
        addConfiguredButtons(player, inventory, gui, view, basePlaceholders);

        List<Integer> slots = gui.getIntegerList(section + ".itemSlots");
        if (slots.isEmpty()) {
            for (int slot = 9; slot < 45; slot++) {
                slots.add(slot);
            }
        }
        for (int index = 0; index < results.size() && index < slots.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            ServerShopItem item = results.get(index);
            inventory.setItem(slot, itemStack(player, gui, item));
            itemsBySlot.put(slot, ServerShopFavoriteService.key(item.categoryId(), item.id()));
        }
        player.openInventory(inventory);
    }

    private List<ServerShopItem> search(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<ServerShopItem> results = new ArrayList<>();
        if (normalized.isBlank()) {
            return results;
        }
        for (ServerShopCategory category : plugin.getServerShopRegistry().categories()) {
            if (!category.enabled()) {
                continue;
            }
            for (ServerShopItem item : category.items()) {
                if (matchesSearch(category, item, normalized)) {
                    results.add(item);
                }
            }
        }
        return results;
    }

    private List<ServerShopItem> favorites(Player player) {
        List<ServerShopItem> results = new ArrayList<>();
        for (String key : plugin.getServerShopFavoriteService().favoriteKeys(player)) {
            ServerShopItem item = itemByKey(key);
            if (item != null) {
                results.add(item);
            }
        }
        return results;
    }

    private boolean matchesSearch(ServerShopCategory category, ServerShopItem item, String query) {
        return item.id().toLowerCase(Locale.ROOT).contains(query)
                || item.material().name().toLowerCase(Locale.ROOT).contains(query)
                || TextUtil.stripColor(item.displayName()).toLowerCase(Locale.ROOT).contains(query)
                || category.id().toLowerCase(Locale.ROOT).contains(query)
                || TextUtil.stripColor(category.displayName()).toLowerCase(Locale.ROOT).contains(query);
    }

    private ServerShopItem itemByKey(String key) {
        String categoryId = ServerShopFavoriteService.categoryId(key);
        String itemId = ServerShopFavoriteService.itemId(key);
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        return category == null || !category.enabled() ? null : category.item(itemId);
    }

    private ItemStack itemStack(Player player, YamlConfiguration gui, ServerShopItem item) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = itemPlaceholders(player, item);
            meta.setDisplayName(TextUtil.color(parse(player, item.displayName(), placeholders)));
            meta.setLore(gui.getStringList("listItemLore").stream()
                    .map(line -> TextUtil.color(parse(player, line, placeholders)))
                    .toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Map<String, String> itemPlaceholders(Player player, ServerShopItem item) {
        Map<String, String> placeholders = new LinkedHashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
        ServerShopCategory category = plugin.getServerShopRegistry().category(item.categoryId());
        placeholders.put("item", TextUtil.color(item.displayName()));
        placeholders.put("item_id", item.id());
        placeholders.put("category", category == null ? item.categoryId() : TextUtil.color(category.displayName()));
        placeholders.put("category_id", item.categoryId());
        placeholders.put("material", item.material().name());
        placeholders.put("buy_price", plugin.getEconomyService().format(item.buyPrice()));
        placeholders.put("sell_price", plugin.getEconomyService().format(item.sellPrice()));
        placeholders.put("favorite_status", plugin.getLanguageService().get(player,
                plugin.getServerShopFavoriteService().isFavorite(player, item) ? "serverShop.favoriteYes" : "serverShop.favoriteNo"));
        placeholders.put("stock", item.stockEnabled() ? Integer.toString(plugin.getServerShopRegistry().availableStock(item)) : plugin.getLanguageService().get(player, "serverShop.limitUnlimited"));
        placeholders.put("max_stock", item.maxStock() <= 0 ? plugin.getLanguageService().get(player, "serverShop.limitUnlimited") : Integer.toString(item.maxStock()));
        return placeholders;
    }

    private void addConfiguredButtons(Player player, Inventory inventory, YamlConfiguration gui, ServerShopListView view, Map<String, String> placeholders) {
        String sectionName = view == ServerShopListView.SEARCH ? "search.buttons" : "favorites.buttons";
        ConfigurationSection section = gui.getConfigurationSection(sectionName);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null || !item.getBoolean("enabled", true)) {
                continue;
            }
            int slot = item.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, configuredItem(player, item, placeholders));
        }
    }

    private ItemStack configuredItem(Player player, ConfigurationSection section, Map<String, String> placeholders) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(parse(player, section.getString("name", ""), placeholders)));
            meta.setLore(section.getStringList("lore").stream()
                    .map(line -> TextUtil.color(parse(player, line, placeholders)))
                    .toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void handleConfiguredButton(Player player, YamlConfiguration gui, ServerShopListView view, int slot, boolean rightClick) {
        String sectionName = view == ServerShopListView.SEARCH ? "search.buttons" : "favorites.buttons";
        ConfigurationSection section = gui.getConfigurationSection(sectionName);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null || item.getInt("slot", -1) != slot) {
                continue;
            }
            List<String> actions = rightClick ? item.getStringList("rightClickActions") : item.getStringList("leftClickActions");
            if (actions.isEmpty() && rightClick) {
                actions = item.getStringList("leftClickActions");
            }
            for (String action : actions) {
                plugin.getGuiActionExecutor().execute(player, action);
            }
        }
    }

    private void fill(Inventory inventory, YamlConfiguration gui) {
        ConfigurationSection filler = gui.getConfigurationSection("filler");
        if (filler == null || !filler.getBoolean("enabled", true)) {
            return;
        }
        Material material = Material.matchMaterial(filler.getString("material", "BLACK_STAINED_GLASS_PANE"));
        ItemStack stack = new ItemStack(material == null ? Material.BLACK_STAINED_GLASS_PANE : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(filler.getString("name", " ")));
            stack.setItemMeta(meta);
        }
        for (int slot : filler.getIntegerList("slots")) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, stack);
            }
        }
    }

    private String parse(Player player, String value, Map<String, String> placeholders) {
        return plugin.getPlaceholderApiHook().apply(player, PlaceholderUtil.apply(value, placeholders));
    }

    private YamlConfiguration loadGui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/servershop.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/servershop.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private int sanitizeSize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return ((size + 8) / 9) * 9;
    }
}
