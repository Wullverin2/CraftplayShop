package de.craftplay.shop.trade;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.entity.Player;

import java.util.Map;

public class DirectTradeService {
    private final CraftplayShopPlugin plugin;

    public DirectTradeService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void toggle(Player player) {
        if (!player.hasPermission("craftplayshop.trade.toggle")) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        boolean enabled = plugin.getPlayerSettingsService().toggleDirectTrade(player);
        sendStatus(player, enabled);
    }

    public void setEnabled(Player player, boolean enabled) {
        if (!player.hasPermission("craftplayshop.trade.toggle")) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        plugin.getPlayerSettingsService().setDirectTrade(player, enabled);
        sendStatus(player, enabled);
    }

    private void sendStatus(Player player, boolean enabled) {
        plugin.getLanguageService().send(player, enabled ? "trade.enabledSelf" : "trade.disabledSelf");
        plugin.getLanguageService().send(player, "trade.toggleStatus", Map.of(
                "status", plugin.getLanguageService().get(player, enabled ? "trade.statusEnabled" : "trade.statusDisabled")
        ));
    }
}
