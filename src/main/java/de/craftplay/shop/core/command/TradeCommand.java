package de.craftplay.shop.core.command;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TradeCommand implements CommandExecutor {
    private final CraftplayShopPlugin plugin;

    public TradeCommand(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return true;
        }
        if (!player.hasPermission(PermissionNodes.TRADE_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return true;
        }
        if (args.length == 0 || "toggle".equalsIgnoreCase(args[0])) {
            plugin.getDirectTradeService().toggle(player);
            return true;
        }
        if ("on".equalsIgnoreCase(args[0])) {
            plugin.getDirectTradeService().setEnabled(player, true);
            return true;
        }
        if ("off".equalsIgnoreCase(args[0])) {
            plugin.getDirectTradeService().setEnabled(player, false);
            return true;
        }
        if ("accept".equalsIgnoreCase(args[0])) {
            plugin.getDirectTradeService().accept(player);
            return true;
        }
        if ("deny".equalsIgnoreCase(args[0])) {
            plugin.getDirectTradeService().deny(player);
            return true;
        }
        if ("cancel".equalsIgnoreCase(args[0])) {
            plugin.getDirectTradeService().cancel(player);
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[0]);
        plugin.getDirectTradeService().requestTrade(player, target);
        return true;
    }
}
