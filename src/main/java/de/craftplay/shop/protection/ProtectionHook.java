package de.craftplay.shop.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface ProtectionHook {
    default boolean canCreateShop(Player player, Location location) {
        return true;
    }

    default boolean canEditShop(Player player, Location location) {
        return true;
    }

    default boolean canUseShop(Player player, Location location) {
        return true;
    }

    default boolean canBreakShop(Player player, Location location) {
        return true;
    }
}
