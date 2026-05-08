package de.craftplay.shop.protection.hooks;

import com.google.common.eventbus.Subscribe;
import com.plotsquared.core.PlotAPI;
import com.plotsquared.core.events.post.PostPlotDeleteEvent;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.Plot;
import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.protection.ProtectionHook;

public class PlotSquaredHook implements ProtectionHook {
    private final CraftplayShopPlugin plugin;
    private boolean registered;

    public PlotSquaredHook(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (registered || !plugin.getConfig().getBoolean("playerShops.plotsquared.deleteShopsOnPlotDelete", true)) {
            return;
        }
        try {
            new PlotAPI().registerListener(this);
            registered = true;
            plugin.getPluginLogService().info("PlotSquared hook loaded for PlayerShop cleanup.");
        } catch (RuntimeException exception) {
            plugin.getPluginLogService().warn("PlotSquared hook could not be registered: " + exception.getMessage());
        }
    }

    @Subscribe
    public void onPostPlotDelete(PostPlotDeleteEvent event) {
        if (!plugin.getConfig().getBoolean("playerShops.plotsquared.deleteShopsOnPlotDelete", true)
                || plugin.getPlayerShopService() == null) {
            return;
        }
        Plot plot = event.getPlot();
        Location bottom = plot.getExtendedBottomAbs();
        Location top = plot.getExtendedTopAbs();
        int minX = Math.min(bottom.getX(), top.getX());
        int maxX = Math.max(bottom.getX(), top.getX());
        int minZ = Math.min(bottom.getZ(), top.getZ());
        int maxZ = Math.max(bottom.getZ(), top.getZ());
        int removed = plugin.getPlayerShopService().deleteShopsInRegion(bottom.getWorldName(), minX, maxX, minZ, maxZ);
        if (removed > 0) {
            plugin.getPluginLogService().info("Removed " + removed + " player shops after PlotSquared plot deletion at " + plot.getId() + ".");
        }
    }
}
