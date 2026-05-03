package de.craftplay.shop.integrations;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class PlaceholderApiHook {
    private final CraftplayShopPlugin plugin;
    private boolean available;
    private Method setPlaceholders;

    public PlaceholderApiHook(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        available = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        setPlaceholders = null;
        if (!available) {
            return;
        }
        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholders = placeholderApi.getMethod("setPlaceholders", Player.class, String.class);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            available = false;
            plugin.getPluginLogService().warn("PlaceholderAPI was found but its API could not be accessed.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String apply(Player player, String value) {
        if (!available || setPlaceholders == null || value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        try {
            return (String) setPlaceholders.invoke(null, player, value);
        } catch (ReflectiveOperationException exception) {
            return value;
        }
    }
}
