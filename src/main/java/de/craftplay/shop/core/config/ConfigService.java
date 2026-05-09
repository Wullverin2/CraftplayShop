package de.craftplay.shop.core.config;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.database.DatabaseType;
import de.craftplay.shop.core.item.ItemMatchMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        new File(plugin.getDataFolder(), "debuglogs").mkdirs();
        new File(plugin.getDataFolder(), "gui/de_DE").mkdirs();
        new File(plugin.getDataFolder(), "gui/en_US").mkdirs();
        new File(plugin.getDataFolder(), "language").mkdirs();
    }

    private void saveMissingDefaults() {
        saveMissingResource("config.yml");
        saveMissingResource("server_shop.yml");
        for (String language : ConfigDefaults.LANGUAGES) {
            String languagePath = "language/" + language + ".yml";
            saveMissingResource(languagePath);
            for (String guiFile : ConfigDefaults.GUI_FILES) {
                String guiPath = "gui/" + language + "/" + guiFile;
                saveMissingResource(guiPath);
            }
        }
        if (autoUpdateExistingFiles()) {
            mergeMissingResourceKeys("config.yml");
            for (String language : ConfigDefaults.LANGUAGES) {
                mergeMissingResourceKeys("language/" + language + ".yml");
                for (String guiFile : ConfigDefaults.GUI_FILES) {
                    mergeMissingResourceKeys("gui/" + language + "/" + guiFile);
                }
            }
        }
    }

    private void saveMissingResource(String resourcePath) {
        if (!new File(plugin.getDataFolder(), resourcePath).exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private boolean autoUpdateExistingFiles() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return true;
        }
        return YamlConfiguration.loadConfiguration(configFile).getBoolean("files.autoUpdateExisting", true);
    }

    private void mergeMissingResourceKeys(String resourcePath) {
        File targetFile = new File(plugin.getDataFolder(), resourcePath);
        if (!targetFile.exists()) {
            return;
        }
        YamlConfiguration target = YamlConfiguration.loadConfiguration(targetFile);
        YamlConfiguration defaults = loadResourceConfiguration(resourcePath);
        if (defaults == null || !mergeMissing(target, defaults)) {
            return;
        }
        try {
            target.save(targetFile);
            plugin.getPluginLogService().info("Updated missing keys in " + resourcePath + ".");
        } catch (IOException exception) {
            plugin.getPluginLogService().error("Could not update missing keys in " + resourcePath + ".", exception);
        }
    }

    private YamlConfiguration loadResourceConfiguration(String resourcePath) {
        InputStream stream = plugin.getResource(resourcePath);
        if (stream == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            plugin.getPluginLogService().error("Could not read default resource " + resourcePath + ".", exception);
            return null;
        }
    }

    private boolean mergeMissing(ConfigurationSection target, ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
            if (defaultSection != null) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    targetSection = target.createSection(key);
                    changed = true;
                }
                changed |= mergeMissing(targetSection, defaultSection);
                continue;
            }
            if (!target.contains(key)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
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

    public boolean debugFileLoggingEnabled() {
        return config().getBoolean("debug.fileLogging.enabled", false);
    }

    public void setDebugFileLoggingEnabled(boolean enabled) {
        config().set("debug.fileLogging.enabled", enabled);
        plugin.saveConfig();
    }

    public String debugFileNamePattern() {
        return config().getString("debug.fileLogging.fileNamePattern", "debug-%date%.txt");
    }

    public String pluginCommand() {
        String command = config().getString("commands.pluginCommand", "cshop");
        if (command == null || command.isBlank()) {
            return "cshop";
        }
        return command.toLowerCase(Locale.ROOT).replace("/", "");
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

    public boolean playerShopsEnabled() {
        return config().getBoolean("playerShops.enabled", true);
    }

    public boolean autoSellChestEnabled() {
        return config().getBoolean("autoSellChest.enabled", false);
    }

    public ItemMatchMode playerShopItemMatchMode() {
        try {
            return ItemMatchMode.valueOf(config().getString("playerShops.itemMatchMode", "EXACT").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ItemMatchMode.EXACT;
        }
    }

    public String panelServerId() {
        return config().getString("panel.serverId", "change-this-server-id");
    }

    public String panelToken() {
        return config().getString("panel.token", "");
    }
}
