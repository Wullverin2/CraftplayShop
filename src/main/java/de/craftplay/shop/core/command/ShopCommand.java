package de.craftplay.shop.core.command;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {
    private final CraftplayShopPlugin plugin;

    public ShopCommand(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return true;
        }
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return true;
        }
        plugin.getServerShopGui().open(player);
        return true;
    }
}
