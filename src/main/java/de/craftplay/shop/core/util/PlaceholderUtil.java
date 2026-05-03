package de.craftplay.shop.core.util;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class PlaceholderUtil {
    private PlaceholderUtil() {
    }

    public static Map<String, String> player(Player player) {
        Map<String, String> placeholders = new HashMap<>();
        if (player == null) {
            return placeholders;
        }
        placeholders.put("player", player.getName());
        placeholders.put("player_uuid", player.getUniqueId().toString());
        placeholders.put("world", player.getWorld().getName());
        placeholders.put("x", Integer.toString(player.getLocation().getBlockX()));
        placeholders.put("y", Integer.toString(player.getLocation().getBlockY()));
        placeholders.put("z", Integer.toString(player.getLocation().getBlockZ()));
        return placeholders;
    }

    public static String apply(String value, Map<String, String> placeholders) {
        String result = value == null ? "" : value;
        if (placeholders == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
