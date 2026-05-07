package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ServerShopRegistry {
    private final CraftplayShopPlugin plugin;
    private final Map<String, ServerShopCategory> categories = new LinkedHashMap<>();

    public ServerShopRegistry(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        categories.clear();
        File file = new File(plugin.getDataFolder(), "server_shop.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = configuration.getConfigurationSection("categories");
        if (section == null) {
            return;
        }
        for (String categoryId : section.getKeys(false)) {
            ConfigurationSection categorySection = section.getConfigurationSection(categoryId);
            if (categorySection == null) {
                continue;
            }
            Material icon = material(categorySection.getString("icon", "CHEST"));
            ServerShopCategory category = new ServerShopCategory(
                    categoryId,
                    categorySection.getString("displayName", categoryId),
                    categorySection.getStringList("lore"),
                    icon,
                    categorySection.getBoolean("enabled", true),
                    categorySection.getInt("slot", 0)
            );
            ConfigurationSection items = categorySection.getConfigurationSection("items");
            if (items != null) {
                for (String itemId : items.getKeys(false)) {
                    ConfigurationSection itemSection = items.getConfigurationSection(itemId);
                    if (itemSection == null) {
                        continue;
                    }
                    Material material = material(itemSection.getString("material", "STONE"));
                    category.addItem(new ServerShopItem(
                            categoryId,
                            itemId,
                            material,
                            itemSection.getString("displayName", material.name()),
                            itemSection.getStringList("lore"),
                            itemSection.getDouble("buyPrice", 0.0D),
                            itemSection.getDouble("sellPrice", 0.0D),
                            itemSection.getBoolean("buyEnabled", false),
                            itemSection.getBoolean("sellEnabled", false),
                            itemSection.getInt("slot", 0),
                            Math.max(1, itemSection.getInt("minBuyAmount", 1)),
                            itemSection.getInt("maxBuyAmount", 0),
                            Math.max(1, itemSection.getInt("minSellAmount", 1)),
                            itemSection.getInt("maxSellAmount", 0),
                            itemSection.getBoolean("stockEnabled", false),
                            Math.max(0, itemSection.getInt("stock", 0)),
                            Math.max(0, itemSection.getInt("maxStock", 0))
                    ));
                }
            }
            categories.put(category.id(), category);
        }
    }

    public Collection<ServerShopCategory> categories() {
        return categories.values();
    }

    public int countCategories() {
        return categories.size();
    }

    public ServerShopCategory category(String id) {
        return categories.get(id);
    }

    public ServerShopItem findSellable(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        if (!plugin.getServerShopService().canSellItemStack(itemStack)) {
            return null;
        }
        for (ServerShopCategory category : categories.values()) {
            if (!category.enabled()) {
                continue;
            }
            for (ServerShopItem item : category.items()) {
                if (item.sellEnabled() && plugin.getItemMatcher().matches(itemStack, item.createStack(1), plugin.getConfigService().itemMatchMode())) {
                    return item;
                }
            }
        }
        return null;
    }

    public synchronized boolean decreaseStock(ServerShopItem item, int amount) {
        return adjustStock(item, -amount);
    }

    public synchronized boolean increaseStock(ServerShopItem item, int amount) {
        return adjustStock(item, amount);
    }

    public int availableStock(ServerShopItem item) {
        if (!item.stockEnabled()) {
            return Integer.MAX_VALUE;
        }
        ServerShopItem current = currentItem(item);
        return current == null ? Math.max(0, item.stock()) : Math.max(0, current.stock());
    }

    public int availableStockCapacity(ServerShopItem item) {
        if (!item.stockEnabled() || !item.hasStockMaximum()) {
            return Integer.MAX_VALUE;
        }
        ServerShopItem current = currentItem(item);
        int stock = current == null ? item.stock() : current.stock();
        int maxStock = current == null ? item.maxStock() : current.maxStock();
        return Math.max(0, maxStock - stock);
    }

    private boolean adjustStock(ServerShopItem item, int delta) {
        if (!item.stockEnabled()) {
            return true;
        }
        File file = new File(plugin.getDataFolder(), "server_shop.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        String path = itemPath(item);
        if (!configuration.getBoolean(path + ".stockEnabled", item.stockEnabled())) {
            return true;
        }
        int current = Math.max(0, configuration.getInt(path + ".stock", item.stock()));
        int max = Math.max(0, configuration.getInt(path + ".maxStock", item.maxStock()));
        int updated = current + delta;
        if (updated < 0) {
            return false;
        }
        if (max > 0 && updated > max) {
            return false;
        }
        configuration.set(path + ".stock", updated);
        try {
            configuration.save(file);
            load();
            return true;
        } catch (IOException exception) {
            plugin.getPluginLogService().error("Could not update ServerShop stock.", exception);
            return false;
        }
    }

    private ServerShopItem currentItem(ServerShopItem item) {
        ServerShopCategory category = category(item.categoryId());
        return category == null ? null : category.item(item.id());
    }

    private String itemPath(ServerShopItem item) {
        return "categories." + item.categoryId() + ".items." + item.id();
    }

    private Material material(String name) {
        Material material = Material.matchMaterial(name == null ? "STONE" : name);
        return material == null ? Material.STONE : material;
    }
}
