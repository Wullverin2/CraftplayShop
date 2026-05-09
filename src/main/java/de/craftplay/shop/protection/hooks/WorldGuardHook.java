package de.craftplay.shop.protection.hooks;

import de.craftplay.shop.protection.ProtectionHook;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardHook implements ProtectionHook {
    @Override
    public boolean canCreateShop(Player player, Location location) {
        return canBuild(player, location);
    }

    @Override
    public boolean canEditShop(Player player, Location location) {
        return canBuild(player, location);
    }

    @Override
    public boolean canBreakShop(Player player, Location location) {
        return canBuild(player, location);
    }

    @Override
    public boolean canUseShop(Player player, Location location) {
        return true;
    }

    private boolean canBuild(Player player, Location location) {
        if (player == null || location == null) {
            return true;
        }
        try {
            Class<?> pluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Object plugin = pluginClass.getMethod("inst").invoke(null);
            Object result = pluginClass.getMethod("canBuild", Player.class, Location.class).invoke(plugin, player, location);
            return !(result instanceof Boolean value) || value;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }
}
