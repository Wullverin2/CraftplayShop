package de.craftplay.shop.core.config;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.database.DatabaseType;
import de.craftplay.shop.core.item.ItemMatchMode;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Locale;

public class ConfigService {
    private final CraftplayShopPlugin plugin;

    public ConfigService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        createDirectories();
        saveMissingDefaults();
        plugin.reloadConfig();
    }

    public void reload() {
        saveMissingDefaults();
        plugin.reloadConfig();
    }

    private void createDirectories() {
        new File(plugin.getDataFolder(), "data").mkdirs();
        new File(plugin.getDataFolder(), "gui/de_DE").mkdirs();
        new File(plugin.getDataFolder(), "gui/en_US").mkdirs();
        new File(plugin.getDataFolder(), "language").mkdirs();
    }

    private void saveMissingDefaults() {
        if (!new File(plugin.getDataFolder(), "config.yml").exists()) {
            plugin.saveResource("config.yml", false);
        }
        if (!new File(plugin.getDataFolder(), "server_shop.yml").exists()) {
            plugin.saveResource("server_shop.yml", false);
        }
        for (String language : ConfigDefaults.LANGUAGES) {
            String languagePath = "language/" + language + ".yml";
            if (!new File(plugin.getDataFolder(), languagePath).exists()) {
                plugin.saveResource(languagePath, false);
            }
            for (String guiFile : ConfigDefaults.GUI_FILES) {
                String guiPath = "gui/" + language + "/" + guiFile;
                if (!new File(plugin.getDataFolder(), guiPath).exists()) {
                    plugin.saveResource(guiPath, false);
                }
            }
        }
    }

    public FileConfiguration config() {
        return plugin.getConfig();
    }

    public String defaultLanguage() {
        return config().getString("settings.defaultLanguage", "de_DE");
    }

    public String fallbackLanguage() {
        return config().getString("settings.fallbackLanguage", "en_US");
    }

    public boolean debug() {
        return config().getBoolean("settings.debug", false);
    }

    public boolean requireVault() {
        return config().getBoolean("economy.requireVault", true);
    }

    public String currencySymbol() {
        return config().getString("economy.currencySymbol", "Coins");
    }

    public String moneyFormat() {
        return config().getString("economy.format", "%amount% Coins");
    }

    public DatabaseType databaseType() {
        try {
            return DatabaseType.valueOf(config().getString("database.type", "SQLITE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DatabaseType.SQLITE;
        }
    }

    public String tablePrefix() {
        return config().getString("database.tablePrefix", "craftplay_shop_");
    }

    public ItemMatchMode itemMatchMode() {
        try {
            return ItemMatchMode.valueOf(config().getString("serverShop.itemMatchMode", "MATERIAL_ONLY").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ItemMatchMode.MATERIAL_ONLY;
        }
    }

    public String panelServerId() {
        return config().getString("panel.serverId", "Testserver-1");
    }

    public String panelToken() {
        return config().getString("panel.token", "");
    }
}
