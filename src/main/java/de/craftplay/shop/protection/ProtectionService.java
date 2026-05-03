package de.craftplay.shop.protection;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ProtectionService {
    private final CraftplayShopPlugin plugin;
    private final List<ProtectionHook> hooks = new ArrayList<>();

    public ProtectionService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadHooks() {
        hooks.clear();
        plugin.getPluginLogService().debug("Protection hooks are prepared as Phase 1 skeletons.");
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
