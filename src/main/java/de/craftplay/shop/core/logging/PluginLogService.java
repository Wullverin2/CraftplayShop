package de.craftplay.shop.core.logging;

import de.craftplay.shop.CraftplayShopPlugin;

import java.util.logging.Level;

public class PluginLogService {
    private final CraftplayShopPlugin plugin;

    public PluginLogService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void info(String message) {
        plugin.getLogger().info(message);
    }

    public void warn(String message) {
        plugin.getLogger().warning(message);
    }

    public void error(String message, Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, message, throwable);
    }

    public void debug(String message) {
        if (plugin.getConfigService() != null && plugin.getConfigService().debug()) {
            plugin.getLogger().info("[Debug] " + message);
        }
    }
}
