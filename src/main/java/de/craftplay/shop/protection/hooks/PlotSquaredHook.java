package de.craftplay.shop.protection.hooks;

import com.google.common.eventbus.Subscribe;
import com.plotsquared.core.PlotAPI;
import com.plotsquared.core.events.post.PostPlotDeleteEvent;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.Plot;
import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.protection.ProtectionHook;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public class PlotSquaredHook implements ProtectionHook {
    private final CraftplayShopPlugin plugin;
    private boolean registered;

    public PlotSquaredHook(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (registered
                || (!plugin.getConfig().getBoolean("playerShops.plotsquared.deleteShopsOnPlotDelete", true)
                && !plugin.getConfig().getBoolean("autoSellChest.plotsquared.deleteChestsOnPlotDelete", true))) {
            return;
        }
        try {
            new PlotAPI().registerListener(this);
            registered = true;
            plugin.getPluginLogService().info("PlotSquared hook loaded.");
        } catch (RuntimeException exception) {
            plugin.getPluginLogService().warn("PlotSquared hook could not be registered: " + exception.getMessage());
        }
    }

    @Subscribe
    public void onPostPlotDelete(PostPlotDeleteEvent event) {
        Plot plot = event.getPlot();
        Location bottom = plot.getExtendedBottomAbs();
        Location top = plot.getExtendedTopAbs();
        int minX = Math.min(bottom.getX(), top.getX());
        int maxX = Math.max(bottom.getX(), top.getX());
        int minZ = Math.min(bottom.getZ(), top.getZ());
        int maxZ = Math.max(bottom.getZ(), top.getZ());
        if (plugin.getConfig().getBoolean("playerShops.plotsquared.deleteShopsOnPlotDelete", true)
                && plugin.getPlayerShopService() != null) {
            int removed = plugin.getPlayerShopService().deleteShopsInRegion(bottom.getWorldName(), minX, maxX, minZ, maxZ);
            if (removed > 0) {
                plugin.getPluginLogService().info("Removed " + removed + " player shops after PlotSquared plot deletion at " + plot.getId() + ".");
            }
        }
        if (plugin.getConfig().getBoolean("autoSellChest.plotsquared.deleteChestsOnPlotDelete", true)
                && plugin.getAutoSellChestService() != null) {
            int removed = plugin.getAutoSellChestService().registry().deleteChestsInRegion(bottom.getWorldName(), minX, maxX, minZ, maxZ);
            if (removed > 0) {
                plugin.getPluginLogService().info("Removed " + removed + " AutoSellChests after PlotSquared plot deletion at " + plot.getId() + ".");
            }
        }
    }

    @Override
    public boolean canCreateShop(Player player, org.bukkit.Location location) {
        return canManageAt(player, location);
    }

    @Override
    public boolean canEditShop(Player player, org.bukkit.Location location) {
        return canManageAt(player, location);
    }

    @Override
    public boolean canBreakShop(Player player, org.bukkit.Location location) {
        return canManageAt(player, location);
    }

    @Override
    public boolean canUseShop(Player player, org.bukkit.Location location) {
        return true;
    }

    private boolean canManageAt(Player player, org.bukkit.Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return true;
        }
        if (player.hasPermission(PermissionNodes.ADMIN)) {
            return true;
        }
        try {
            Object plotLocation = Class.forName("com.plotsquared.core.location.Location")
                    .getMethod("at", String.class, int.class, int.class, int.class)
                    .invoke(null, location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
            Object plot = plotLocation.getClass().getMethod("getPlot").invoke(plotLocation);
            if (plot == null || !invokeBoolean(plot, "hasOwner")) {
                return true;
            }
            UUID playerId = player.getUniqueId();
            return invokeBoolean(plot, "isOwner", playerId) || invokeBoolean(plot, "isAdded", playerId);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getPluginLogService().debug("PlotSquared permission check failed: " + exception.getMessage());
            return true;
        }
    }

    private boolean invokeBoolean(Object target, String method) throws ReflectiveOperationException {
        Object result = target.getClass().getMethod(method).invoke(target);
        return result instanceof Boolean value && value;
    }

    private boolean invokeBoolean(Object target, String method, UUID playerId) throws ReflectiveOperationException {
        try {
            Object result = target.getClass().getMethod(method, UUID.class).invoke(target, playerId);
            return result instanceof Boolean value && value;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }
}
