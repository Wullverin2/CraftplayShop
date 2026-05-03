package de.craftplay.shop.integrations;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class HeadDatabaseHook {
    private final CraftplayShopPlugin plugin;
    private boolean available;
    private Object api;
    private Method getItemHead;

    public HeadDatabaseHook(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        available = plugin.getServer().getPluginManager().getPlugin("HeadDatabase") != null;
        api = null;
        getItemHead = null;
        if (available) {
            try {
                Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
                api = apiClass.getConstructor().newInstance();
                getItemHead = apiClass.getMethod("getItemHead", String.class);
            } catch (ReflectiveOperationException exception) {
                available = false;
                plugin.getPluginLogService().warn("HeadDatabase is installed but its API could not be accessed.");
            }
        }
        if (!available && plugin.getConfig().getBoolean("integrations.headDatabase.enabled", true)
                && plugin.getConfig().getBoolean("integrations.headDatabase.warnIfMissing", true)) {
            plugin.getPluginLogService().warn("HeadDatabase is not installed. Player head fallback material will be used.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public ItemStack head(String id) {
        if (!available || api == null || getItemHead == null || id == null || id.isBlank()) {
            return null;
        }
        try {
            Object item = getItemHead.invoke(api, id);
            return item instanceof ItemStack itemStack ? itemStack.clone() : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
