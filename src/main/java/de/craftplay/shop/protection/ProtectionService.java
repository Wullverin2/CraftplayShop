package de.craftplay.shop.protection;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.protection.hooks.BentoBoxHook;
import de.craftplay.shop.protection.hooks.LandsHook;
import de.craftplay.shop.protection.hooks.PlotSquaredHook;
import de.craftplay.shop.protection.hooks.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ProtectionService {
    private final CraftplayShopPlugin plugin;
    private final List<ProtectionHook> hooks = new ArrayList<>();
    private PlotSquaredHook plotSquaredHook;

    public ProtectionService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadHooks() {
        hooks.clear();
        if (plugin.getConfig().getBoolean("protection.plotsquared", true)
                && plugin.getServer().getPluginManager().getPlugin("PlotSquared") != null) {
            if (plotSquaredHook == null) {
                plotSquaredHook = new PlotSquaredHook(plugin);
                plotSquaredHook.register();
            }
            hooks.add(plotSquaredHook);
        }
        if (plugin.getConfig().getBoolean("protection.worldguard", true)
                && plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            hooks.add(new WorldGuardHook());
        }
        if (plugin.getConfig().getBoolean("protection.lands", true)
                && plugin.getServer().getPluginManager().getPlugin("Lands") != null) {
            hooks.add(new LandsHook());
        }
        if (plugin.getConfig().getBoolean("protection.bentobox", true)
                && plugin.getServer().getPluginManager().getPlugin("BentoBox") != null) {
            hooks.add(new BentoBoxHook());
        }
        plugin.getPluginLogService().debug("protection", "Loaded " + hooks.size() + " protection hooks.");
    }

    public boolean canCreateShop(Player player, Location location) {
        return hooks.stream().allMatch(hook -> hook.canCreateShop(player, location));
    }

    public boolean canEditShop(Player player, Location location) {
        return hooks.stream().allMatch(hook -> hook.canEditShop(player, location));
    }

    public boolean canUseShop(Player player, Location location) {
        return hooks.stream().allMatch(hook -> hook.canUseShop(player, location));
    }

    public boolean canBreakShop(Player player, Location location) {
        return hooks.stream().allMatch(hook -> hook.canBreakShop(player, location));
    }
}
