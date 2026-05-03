package de.craftplay.shop.integrations;

import de.craftplay.shop.CraftplayShopPlugin;

public class PlaceholderApiHook {
    private final CraftplayShopPlugin plugin;
    private boolean available;

    public PlaceholderApiHook(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        available = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public boolean isAvailable() {
        return available;
    }
}
