package de.craftplay.shop.protection.hooks;

import de.craftplay.shop.protection.ProtectionHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

public class LandsHook implements ProtectionHook {
    private final Plugin plugin = Bukkit.getPluginManager().getPlugin("Lands");

    @Override
    public boolean canCreateShop(Player player, Location location) {
        return canManage(player, location);
    }

    @Override
    public boolean canEditShop(Player player, Location location) {
        return canManage(player, location);
    }

    @Override
    public boolean canBreakShop(Player player, Location location) {
        return canManage(player, location);
    }

    @Override
    public boolean canUseShop(Player player, Location location) {
        return true;
    }

    private boolean canManage(Player player, Location location) {
        if (player == null || location == null || plugin == null) {
            return true;
        }
        try {
            Class<?> integrationClass = Class.forName("me.angeschossen.lands.api.integration.LandsIntegration");
            Object integration = integrationClass.getMethod("of", Plugin.class).invoke(null, plugin);
            Object area = integrationClass.getMethod("getArea", Location.class).invoke(integration, location);
            if (area == null) {
                return true;
            }
            UUID playerId = player.getUniqueId();
            if (invokeBoolean(area, "isTrusted", playerId) || invokeBoolean(area, "isMember", playerId)) {
                return true;
            }
            Object owner = invoke(area, "getOwnerUID");
            if (owner instanceof UUID uuid && uuid.equals(playerId)) {
                return true;
            }
            Object land = invoke(area, "getLand");
            if (land != null) {
                Object landOwner = invoke(land, "getOwnerUID");
                if (landOwner instanceof UUID uuid && uuid.equals(playerId)) {
                    return true;
                }
                if (invokeBoolean(land, "isTrusted", playerId) || invokeBoolean(land, "isMember", playerId)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
        return false;
    }

    private Object invoke(Object target, String method) {
        try {
            Method reflected = target.getClass().getMethod(method);
            return reflected.invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private boolean invokeBoolean(Object target, String method, UUID playerId) {
        try {
            Method reflected = target.getClass().getMethod(method, UUID.class);
            Object result = reflected.invoke(target, playerId);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }
}
