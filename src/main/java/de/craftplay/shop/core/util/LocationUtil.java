package de.craftplay.shop.core.util;

import org.bukkit.Location;

public final class LocationUtil {
    private LocationUtil() {
    }

    public static String compact(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
