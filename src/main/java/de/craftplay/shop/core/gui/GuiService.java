package de.craftplay.shop.core.gui;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.config.ConfigDefaults;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class GuiService {
    private final CraftplayShopPlugin plugin;
    private final Map<String, GuiDefinition> definitions = new HashMap<>();
    private final GuiItemBuilder itemBuilder;

    public GuiService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
        this.itemBuilder = new GuiItemBuilder(plugin);
    }

    public void load() {
        definitions.clear();
        for (String language : ConfigDefaults.LANGUAGES) {
            for (String fileName : ConfigDefaults.GUI_FILES) {
                loadGui(language, fileName);
            }
        }
    }

    public int count() {
        return definitions.size();
    }

    public void clearCache() {
        definitions.clear();
    }

    public void open(Player player, String guiId) {
        if ("servershop".equalsIgnoreCase(guiId)) {
            plugin.getServerShopGui().open(player);
            return;
        }
        if ("playershop".equalsIgnoreCase(guiId)) {
            plugin.getPlayerShopService().openHome(player);
            return;
        }
        if ("auctionhouse".equalsIgnoreCase(guiId)) {
            plugin.getAuctionHouseService().openHome(player);
            return;
        }
        if ("rankshop".equalsIgnoreCase(guiId)) {
            plugin.getRankShopService().open(player);
            return;
        }
        if ("permissionshop".equalsIgnoreCase(guiId)) {
            plugin.getPermissionProductService().open(player);
            return;
        }
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        GuiDefinition definition = definition(language, guiId);
        if (definition == null) {
            plugin.getLanguageService().send(player, "gui.missingFile", Map.of("file", guiId));
            return;
        }
        Map<String, String> placeholders = plugin.getGuiPlaceholderService().placeholders(player);
        GuiHolder holder = new GuiHolder(guiId, definition.items());
        Inventory inventory = Bukkit.createInventory(holder, definition.size(), TextUtil.color(PlaceholderUtil.apply(definition.title(), placeholders)));
        holder.setInventory(inventory);
        for (GuiItemDefinition item : definition.items().values()) {
            if (!item.permission().isBlank() && !player.hasPermission(item.permission())) {
                continue;
            }
            inventory.setItem(item.slot(), itemBuilder.build(item.section(), player, placeholders));
        }
        player.openInventory(inventory);
    }

    private GuiDefinition definition(String language, String guiId) {
        GuiDefinition definition = definitions.get(language + ":" + guiId);
        if (definition != null) {
            return definition;
        }
        if (plugin.getConfig().getBoolean("gui.useFallbackLanguage", true)) {
            return definitions.get(plugin.getConfigService().fallbackLanguage() + ":" + guiId);
        }
        return null;
    }

    private void loadGui(String language, String fileName) {
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/" + fileName);
        if (!file.exists()) {
            return;
        }
        String id = fileName.substring(0, fileName.length() - 4);
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        String title = configuration.getString("title", "&8CraftplayShop");
        int size = sanitizeSize(configuration.getInt("size", 54));
        Map<Integer, GuiItemDefinition> items = new LinkedHashMap<>();
        addFiller(configuration.getConfigurationSection("filler"), items);
        ConfigurationSection decorations = configuration.getConfigurationSection("decorations");
        if (decorations != null) {
            for (String key : decorations.getKeys(false)) {
                addDecoration(key, decorations.getConfigurationSection(key), items);
            }
        }
        ConfigurationSection itemSection = configuration.getConfigurationSection("items");
        if (itemSection != null) {
            for (String key : itemSection.getKeys(false)) {
                ConfigurationSection section = itemSection.getConfigurationSection(key);
                if (section == null || !section.getBoolean("enabled", true)) {
                    continue;
                }
                int slot = section.getInt("slot", -1);
                if (slot < 0 || slot >= size) {
                    continue;
                }
                items.put(slot, new GuiItemDefinition(
                        key,
                        slot,
                        section,
                        section.getStringList("leftClickActions"),
                        section.getStringList("rightClickActions"),
                        section.getBoolean("closeInventory", false),
                        section.getString("permission", "")
                ));
            }
        }
        definitions.put(language + ":" + id, new GuiDefinition(id, language, title, size, items));
    }

    private void addFiller(ConfigurationSection section, Map<Integer, GuiItemDefinition> items) {
        addDecoration("filler", section, items);
    }

    private void addDecoration(String id, ConfigurationSection section, Map<Integer, GuiItemDefinition> items) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        for (int slot : section.getIntegerList("slots")) {
            if (slot >= 0) {
                items.putIfAbsent(slot, new GuiItemDefinition(id + "_" + slot, slot, section, java.util.List.of(), java.util.List.of(), false, ""));
            }
        }
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
