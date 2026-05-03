package de.craftplay.shop.core.command;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainCommand implements CommandExecutor, TabCompleter {
    private final CraftplayShopPlugin plugin;

    public MainCommand(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.getLanguageService().send(sender, "general.playerOnly");
                return true;
            }
            if (!player.hasPermission(PermissionNodes.USE)) {
                plugin.getLanguageService().send(player, "general.noPermission");
                return true;
            }
            plugin.getGuiService().open(player, "main");
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            reload(sender);
            return true;
        }
        if ("language".equals(sub) || "lang".equals(sub)) {
            language(sender, args);
            return true;
        }
        if ("sellhand".equals(sub)) {
            sellHand(sender);
            return true;
        }
        if ("sellall".equals(sub)) {
            sellAll(sender);
            return true;
        }
        plugin.getLanguageService().send(sender, "general.unknownCommand");
        return true;
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(PermissionNodes.RELOAD)) {
            plugin.getLanguageService().send(sender, "general.noPermission");
            return;
        }
        plugin.getLanguageService().send(sender, "general.reloadStart");
        plugin.reloadAll();
        plugin.getLanguageService().send(sender, "general.reloadSummary", Map.of(
                "languages", Integer.toString(plugin.getLanguageService().count()),
                "guis", Integer.toString(plugin.getGuiService().count()),
                "categories", Integer.toString(plugin.getServerShopRegistry().countCategories())
        ));
        plugin.getLanguageService().send(sender, "general.reloadDone");
    }

    private void language(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.LANGUAGE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        if (args.length == 1) {
            plugin.getLanguageService().send(player, "language.available", Map.of("languages", String.join(", ", plugin.getLanguageService().availableLanguages())));
            return;
        }
        String language = args[1];
        if (!plugin.getPlayerLanguageService().setLanguage(player, language)) {
            plugin.getLanguageService().send(player, "language.invalid");
            return;
        }
        plugin.getLanguageService().send(player, "language.changed", Map.of("language", language));
    }

    private void sellHand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_SELL_HAND)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        plugin.getSellCommandService().sellHand(player);
    }

    private void sellAll(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_SELL_ALL)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        plugin.getSellCommandService().sellAll(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "language", "lang", "sellhand", "sellall"), args[0]);
        }
        if (args.length == 2 && ("language".equalsIgnoreCase(args[0]) || "lang".equalsIgnoreCase(args[0]))) {
            return filter(new ArrayList<>(plugin.getLanguageService().availableLanguages()), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String token) {
        return values.stream().filter(value -> value.toLowerCase().startsWith(token.toLowerCase())).toList();
    }
}
