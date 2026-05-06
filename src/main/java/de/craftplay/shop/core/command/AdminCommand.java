package de.craftplay.shop.core.command;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class AdminCommand implements CommandExecutor {
    private final CraftplayShopPlugin plugin;

    public AdminCommand(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission(PermissionNodes.RELOAD)) {
                plugin.getLanguageService().send(sender, "general.noPermission");
                return true;
            }
            plugin.getLanguageService().send(sender, "general.reloadStart");
            plugin.reloadAll();
            plugin.getLanguageService().send(sender, "general.reloadSummary", Map.of(
                    "languages", Integer.toString(plugin.getLanguageService().count()),
                    "guis", Integer.toString(plugin.getGuiService().count()),
                    "categories", Integer.toString(plugin.getServerShopRegistry().countCategories())
            ));
            plugin.getLanguageService().send(sender, "general.reloadDone");
            return true;
        }
        if (args.length > 0 && ("servershop".equalsIgnoreCase(args[0]) || "adminshop".equalsIgnoreCase(args[0]))) {
            if (!(sender instanceof Player player)) {
                plugin.getLanguageService().send(sender, "general.playerOnly");
                return true;
            }
            if (!player.hasPermission(PermissionNodes.ADMIN)) {
                plugin.getLanguageService().send(player, "general.noPermission");
                return true;
            }
            plugin.getServerShopAdminEditor().openCategories(player);
            return true;
        }
        if (args.length > 0 && "backup".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                plugin.getLanguageService().send(sender, "general.playerOnly");
                return true;
            }
            if (!player.hasPermission(PermissionNodes.ADMIN)) {
                plugin.getLanguageService().send(player, "general.noPermission");
                return true;
            }
            if (args.length > 1 && "list".equalsIgnoreCase(args[1])) {
                plugin.getServerShopAdminEditor().listBackups(player);
                return true;
            }
            if (args.length > 1 && "restore".equalsIgnoreCase(args[1])) {
                if (args.length < 3) {
                    plugin.getLanguageService().send(player, "adminShop.backupRestoreUsage");
                    return true;
                }
                plugin.getServerShopAdminEditor().requestBackupRestore(player, args[2]);
                return true;
            }
            if (args.length > 1 && "confirm".equalsIgnoreCase(args[1])) {
                plugin.getServerShopAdminEditor().confirmBackupRestore(player);
                return true;
            }
            if (args.length > 1 && "cancel".equalsIgnoreCase(args[1])) {
                plugin.getServerShopAdminEditor().cancelBackupRestore(player);
                return true;
            }
            plugin.getServerShopAdminEditor().createManualBackup(player);
            return true;
        }
        if (args.length > 0 && "backups".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                plugin.getLanguageService().send(sender, "general.playerOnly");
                return true;
            }
            if (!player.hasPermission(PermissionNodes.ADMIN)) {
                plugin.getLanguageService().send(player, "general.noPermission");
                return true;
            }
            plugin.getServerShopAdminEditor().listBackups(player);
            return true;
        }
        if (sender instanceof Player player && player.hasPermission(PermissionNodes.ADMIN)) {
            plugin.getGuiService().open(player, "admin");
            return true;
        }
        plugin.getLanguageService().send(sender, "general.noPermission");
        return true;
    }
}
