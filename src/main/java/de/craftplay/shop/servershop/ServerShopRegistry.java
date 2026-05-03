package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
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
                            itemId,
                            material,
                            itemSection.getString("displayName", material.name()),
                            itemSection.getStringList("lore"),
                            itemSection.getDouble("buyPrice", 0.0D),
                            itemSection.getDouble("sellPrice", 0.0D),
                            itemSection.getBoolean("buyEnabled", false),
                            itemSection.getBoolean("sellEnabled", false),
                            itemSection.getInt("slot", 0)
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
        for (ServerShopCategory category : categories.values()) {
            for (ServerShopItem item : category.items()) {
                if (item.sellEnabled() && plugin.getItemMatcher().matches(itemStack, item.createStack(1), plugin.getConfigService().itemMatchMode())) {
                    return item;
                }
            }
        }
        return null;
    }

    private Material material(String name) {
        Material material = Material.matchMaterial(name == null ? "STONE" : name);
        return material == null ? Material.STONE : material;
    }
}
