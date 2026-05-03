package de.craftplay.shop.core.util;

import org.bukkit.ChatColor;

public final class TextUtil {
    private TextUtil() {
    }

    public static String color(String value) {
        if (value == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public static String stripColor(String value) {
        return ChatColor.stripColor(color(value));
    }
}
