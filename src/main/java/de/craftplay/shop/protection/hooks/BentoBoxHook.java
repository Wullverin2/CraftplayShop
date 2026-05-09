package de.craftplay.shop.protection.hooks;

import de.craftplay.shop.protection.ProtectionHook;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

public class BentoBoxHook implements ProtectionHook {
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
        if (player == null || location == null || location.getWorld() == null) {
            return true;
        }
        try {
            Class<?> bentoBoxClass = Class.forName("world.bentobox.bentobox.BentoBox");
            Object addon = bentoBoxClass.getMethod("getInstance").invoke(null);
            Object islands = bentoBoxClass.getMethod("getIslands").invoke(addon);
            Object island = invokeWithLocation(islands, "getIslandAt", location);
            if (island == null) {
                return true;
            }
            UUID playerId = player.getUniqueId();
            Object owner = invoke(island, "getOwner");
            if (owner instanceof UUID uuid && uuid.equals(playerId)) {
                return true;
            }
            Object memberSet = invoke(island, "getMemberSet");
            if (memberSet instanceof Collection<?> collection) {
                for (Object entry : collection) {
                    if (entry instanceof UUID uuid && uuid.equals(playerId)) {
                        return true;
                    }
                }
            }
            if (invokeBoolean(island, "isMember", playerId) || invokeBoolean(island, "inTeam", playerId)) {
                return true;
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

    private Object invokeWithLocation(Object target, String method, Location location) {
        try {
            Method reflected = target.getClass().getMethod(method, Location.class);
            return reflected.invoke(target, location);
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
