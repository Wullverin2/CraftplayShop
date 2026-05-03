package de.craftplay.shop.servershop.admin;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import de.craftplay.shop.servershop.ServerShopCategory;
import de.craftplay.shop.servershop.ServerShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ServerShopAdminEditor {
    private final CraftplayShopPlugin plugin;

    public ServerShopAdminEditor(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void openCategories(Player player) {
        YamlConfiguration gui = gui(player);
        Map<Integer, String> keys = new HashMap<>();
        ServerShopAdminHolder holder = new ServerShopAdminHolder(ServerShopAdminView.CATEGORIES, "", "", keys);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(gui, "categories", Map.of()));
        holder.setInventory(inventory);
        fill(inventory);

        for (ServerShopCategory category : plugin.getServerShopRegistry().categories()) {
            int slot = clampSlot(category.slot(), inventory.getSize());
            inventory.setItem(slot, item(category.icon(), category.displayName(), lore(gui, "items.category.lore", Map.of(
                    "category_id", category.id(),
                    "item_count", Integer.toString(category.items().size())
            ))));
            keys.put(slot, category.id());
        }
        inventory.setItem(49, configuredItem(gui, "backAdmin", Map.of()));
        keys.put(49, "back");
        player.openInventory(inventory);
    }

    public void openItems(Player player, String categoryId) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        if (category == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return;
        }
        YamlConfiguration gui = gui(player);
        Map<Integer, String> keys = new HashMap<>();
        ServerShopAdminHolder holder = new ServerShopAdminHolder(ServerShopAdminView.ITEMS, categoryId, "", keys);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(gui, "items", Map.of("category", TextUtil.stripColor(category.displayName()))));
        holder.setInventory(inventory);
        fill(inventory);

        for (ServerShopItem shopItem : category.items()) {
            int slot = clampSlot(shopItem.slot(), inventory.getSize());
            inventory.setItem(slot, editorItem(gui, shopItem));
            keys.put(slot, shopItem.id());
        }
        inventory.setItem(49, configuredItem(gui, "backCategories", Map.of()));
        keys.put(49, "back");
        player.openInventory(inventory);
    }

    public void openItemEditor(Player player, String categoryId, String itemId) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        if (category == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return;
        }
        ServerShopItem shopItem = category.item(itemId);
        if (shopItem == null) {
            plugin.getLanguageService().send(player, "gui.missingItem", Map.of("item", itemId));
            return;
        }

        YamlConfiguration gui = gui(player);
        Map<Integer, String> keys = new HashMap<>();
        ServerShopAdminHolder holder = new ServerShopAdminHolder(ServerShopAdminView.ITEM_EDITOR, categoryId, itemId, keys);
        Inventory inventory = Bukkit.createInventory(holder, 54, title(gui, "itemEditor", Map.of()));
        holder.setInventory(inventory);
        fill(inventory);

        inventory.setItem(4, editorItem(gui, shopItem));
        inventory.setItem(10, toggleItem(gui, "toggleBuy", shopItem.buyEnabled()));
        keys.put(10, "toggle_buy");
        priceButton(inventory, keys, gui, 12, "buyMinus10", "buy_-10", shopItem.buyPrice());
        priceButton(inventory, keys, gui, 13, "buyMinus1", "buy_-1", shopItem.buyPrice());
        priceButton(inventory, keys, gui, 14, "buyPlus1", "buy_1", shopItem.buyPrice());
        priceButton(inventory, keys, gui, 15, "buyPlus10", "buy_10", shopItem.buyPrice());

        inventory.setItem(28, toggleItem(gui, "toggleSell", shopItem.sellEnabled()));
        keys.put(28, "toggle_sell");
        priceButton(inventory, keys, gui, 30, "sellMinus10", "sell_-10", shopItem.sellPrice());
        priceButton(inventory, keys, gui, 31, "sellMinus1", "sell_-1", shopItem.sellPrice());
        priceButton(inventory, keys, gui, 32, "sellPlus1", "sell_1", shopItem.sellPrice());
        priceButton(inventory, keys, gui, 33, "sellPlus10", "sell_10", shopItem.sellPrice());

        inventory.setItem(40, configuredItem(gui, "setFromHand", Map.of()));
        keys.put(40, "set_from_hand");
        inventory.setItem(45, configuredItem(gui, "backItems", Map.of()));
        keys.put(45, "back");
        player.openInventory(inventory);
    }

    public void handleClick(Player player, ServerShopAdminHolder holder, InventoryClickEvent event) {
        String key = holder.keyAt(event.getRawSlot());
        if (key == null) {
            return;
        }
        switch (holder.view()) {
            case CATEGORIES -> handleCategoryClick(player, key);
            case ITEMS -> handleItemListClick(player, holder.categoryId(), key);
            case ITEM_EDITOR -> handleItemEditorClick(player, holder.categoryId(), holder.itemId(), key);
        }
    }

    private void handleCategoryClick(Player player, String key) {
        if ("back".equals(key)) {
            plugin.getGuiService().open(player, "admin");
            return;
        }
        openItems(player, key);
    }

    private void handleItemListClick(Player player, String categoryId, String key) {
        if ("back".equals(key)) {
            openCategories(player);
            return;
        }
        openItemEditor(player, categoryId, key);
    }

    private void handleItemEditorClick(Player player, String categoryId, String itemId, String key) {
        if ("back".equals(key)) {
            openItems(player, categoryId);
            return;
        }
        if ("toggle_buy".equals(key)) {
            toggle(categoryId, itemId, "buyEnabled");
            saved(player, categoryId, itemId);
            return;
        }
        if ("toggle_sell".equals(key)) {
            toggle(categoryId, itemId, "sellEnabled");
            saved(player, categoryId, itemId);
            return;
        }
        if ("set_from_hand".equals(key)) {
            setFromHand(player, categoryId, itemId);
            openItemEditor(player, categoryId, itemId);
            return;
        }
        if (key.startsWith("buy_")) {
            adjustPrice(categoryId, itemId, "buyPrice", Double.parseDouble(key.substring(4)));
            saved(player, categoryId, itemId);
            return;
        }
        if (key.startsWith("sell_")) {
            adjustPrice(categoryId, itemId, "sellPrice", Double.parseDouble(key.substring(5)));
            saved(player, categoryId, itemId);
        }
    }

    private void saved(Player player, String categoryId, String itemId) {
        plugin.getLanguageService().send(player, "adminShop.saved");
        openItemEditor(player, categoryId, itemId);
    }

    private void toggle(String categoryId, String itemId, String key) {
        YamlConfiguration configuration = loadShopFile();
        String path = itemPath(categoryId, itemId) + "." + key;
        configuration.set(path, !configuration.getBoolean(path, false));
        save(configuration);
    }

    private void adjustPrice(String categoryId, String itemId, String key, double delta) {
        YamlConfiguration configuration = loadShopFile();
        String path = itemPath(categoryId, itemId) + "." + key;
        double current = configuration.getDouble(path, 0.0D);
        configuration.set(path, Math.max(0.0D, current + delta));
        save(configuration);
    }

    private void setFromHand(Player player, String categoryId, String itemId) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            plugin.getLanguageService().send(player, "adminShop.noHandItem");
            return;
        }
        YamlConfiguration configuration = loadShopFile();
        String path = itemPath(categoryId, itemId);
        configuration.set(path + ".material", hand.getType().name());
        if (hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
            configuration.set(path + ".displayName", hand.getItemMeta().getDisplayName().replace(ChatColor.COLOR_CHAR, '&'));
        } else {
            configuration.set(path + ".displayName", "&f" + formatMaterialName(hand.getType()));
        }
        save(configuration);
        plugin.getLanguageService().send(player, "adminShop.saved");
    }

    private YamlConfiguration loadShopFile() {
        return YamlConfiguration.loadConfiguration(shopFile());
    }

    private void save(YamlConfiguration configuration) {
        try {
            configuration.save(shopFile());
            plugin.getServerShopRegistry().load();
        } catch (IOException exception) {
            plugin.getPluginLogService().error("Could not save server_shop.yml.", exception);
        }
    }

    private File shopFile() {
        return new File(plugin.getDataFolder(), "server_shop.yml");
    }

    private String itemPath(String categoryId, String itemId) {
        return "categories." + categoryId + ".items." + itemId;
    }

    private ItemStack editorItem(YamlConfiguration gui, ServerShopItem shopItem) {
        return item(shopItem.material(), shopItem.displayName(), lore(gui, "items.shopItem.lore", Map.of(
                "item_id", shopItem.id(),
                "material", shopItem.material().name(),
                "buy_price", money(shopItem.buyPrice()),
                "sell_price", money(shopItem.sellPrice()),
                "buy_status", status(gui, shopItem.buyEnabled()),
                "sell_status", status(gui, shopItem.sellEnabled())
        )));
    }

    private void priceButton(Inventory inventory, Map<Integer, String> keys, YamlConfiguration gui, int slot, String itemKey, String actionKey, double price) {
        inventory.setItem(slot, configuredItem(gui, itemKey, Map.of("price", money(price))));
        keys.put(slot, actionKey);
    }

    private ItemStack toggleItem(YamlConfiguration gui, String key, boolean enabled) {
        ConfigurationSection section = gui.getConfigurationSection("items." + key);
        Material material = material(section == null ? null : section.getString("material"), Material.STONE);
        String name = section == null ? key : section.getString(enabled ? "enabledName" : "disabledName", key);
        List<String> lore = section == null ? List.of() : section.getStringList("lore");
        return item(material, name, lore);
    }

    private ItemStack configuredItem(YamlConfiguration gui, String key, Map<String, String> placeholders) {
        ConfigurationSection section = gui.getConfigurationSection("items." + key);
        if (section == null) {
            return item(Material.STONE, key, List.of());
        }
        return item(
                material(section.getString("material"), Material.STONE),
                PlaceholderUtil.apply(section.getString("name", key), placeholders),
                lore(section, "lore", placeholders)
        );
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(name));
            meta.setLore(lore.stream().map(TextUtil::color).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private YamlConfiguration gui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/admin_servershop.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/admin_servershop.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private String title(YamlConfiguration gui, String key, Map<String, String> placeholders) {
        return TextUtil.color(PlaceholderUtil.apply(gui.getString("titles." + key, key), placeholders));
    }

    private List<String> lore(YamlConfiguration gui, String path, Map<String, String> placeholders) {
        return gui.getStringList(path).stream()
                .map(line -> PlaceholderUtil.apply(line, placeholders))
                .toList();
    }

    private List<String> lore(ConfigurationSection section, String path, Map<String, String> placeholders) {
        return section.getStringList(path).stream()
                .map(line -> PlaceholderUtil.apply(line, placeholders))
                .toList();
    }

    private String status(YamlConfiguration gui, boolean enabled) {
        return gui.getString(enabled ? "status.enabled" : "status.disabled", enabled ? "enabled" : "disabled");
    }

    private Material material(String value, Material fallback) {
        Material material = Material.matchMaterial(value == null ? "" : value);
        return material == null ? fallback : material;
    }

    private int clampSlot(int slot, int size) {
        if (slot < 0 || slot >= size) {
            return 0;
        }
        return slot;
    }

    private String money(double value) {
        return plugin.getEconomyService().format(value);
    }

    private String formatMaterialName(Material material) {
        String lower = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
