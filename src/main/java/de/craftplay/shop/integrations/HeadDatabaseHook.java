package de.craftplay.shop.integrations;

import de.craftplay.shop.CraftplayShopPlugin;

public class HeadDatabaseHook {
    private final CraftplayShopPlugin plugin;
    private boolean available;

    public HeadDatabaseHook(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        available = plugin.getServer().getPluginManager().getPlugin("HeadDatabase") != null;
        if (!available && plugin.getConfig().getBoolean("integrations.headDatabase.enabled", true)
                && plugin.getConfig().getBoolean("integrations.headDatabase.warnIfMissing", true)) {
            plugin.getPluginLogService().warn("HeadDatabase is not installed. Player head fallback material will be used.");
        }
    }

    public boolean isAvailable() {
        return available;
    }
}
