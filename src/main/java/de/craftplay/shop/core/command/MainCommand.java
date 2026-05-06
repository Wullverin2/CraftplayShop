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
        String commandName = command.getName().toLowerCase();
        if ("sellhand".equals(commandName)) {
            sellHand(sender);
            return true;
        }
        if ("sellall".equals(commandName)) {
            sellAll(sender);
            return true;
        }
        if ("sellgui".equals(commandName)) {
            sellGui(sender);
            return true;
        }
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
        if ("admin".equals(sub)) {
            if (!matchesConfiguredCommand(label)) {
                plugin.getLanguageService().send(sender, "general.unknownCommand");
                return true;
            }
            admin(sender, args);
            return true;
        }
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
        if ("sellgui".equals(sub) || "sellinv".equals(sub) || "sellinventory".equals(sub)) {
            sellGui(sender);
            return true;
        }
        plugin.getLanguageService().send(sender, "general.unknownCommand");
        return true;
    }

    private void admin(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                plugin.getLanguageService().send(sender, "general.playerOnly");
                return;
            }
            if (!player.hasPermission(PermissionNodes.ADMIN)) {
                plugin.getLanguageService().send(player, "general.noPermission");
                return;
            }
            plugin.getGuiService().open(player, "admin");
            return;
        }
        String sub = args[1].toLowerCase();
        if ("reload".equals(sub)) {
            reload(sender);
            return;
        }
        if ("editor".equals(sub) || "servershop".equals(sub) || "adminshop".equals(sub)) {
            if (!(sender instanceof Player player)) {
                plugin.getLanguageService().send(sender, "general.playerOnly");
                return;
            }
            if (!player.hasPermission(PermissionNodes.ADMIN)) {
                plugin.getLanguageService().send(player, "general.noPermission");
                return;
            }
            plugin.getServerShopAdminEditor().openCategories(player);
            return;
        }
        if ("backup".equals(sub)) {
            if (!(sender instanceof Player player)) {
                plugin.getLanguageService().send(sender, "general.playerOnly");
                return;
            }
            if (!player.hasPermission(PermissionNodes.ADMIN)) {
                plugin.getLanguageService().send(player, "general.noPermission");
                return;
            }
            plugin.getServerShopAdminEditor().createManualBackup(player);
            return;
        }
        plugin.getLanguageService().send(sender, "general.unknownCommand");
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

    private void sellGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_SELL_GUI)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        plugin.getSellCommandService().openSellGui(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("admin", "reload", "language", "lang", "sellhand", "sellall", "sellgui"), args[0]);
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            return filter(List.of("editor", "reload", "servershop", "adminshop", "backup"), args[1]);
        }
        if (args.length == 2 && ("language".equalsIgnoreCase(args[0]) || "lang".equalsIgnoreCase(args[0]))) {
            return filter(new ArrayList<>(plugin.getLanguageService().availableLanguages()), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String token) {
        return values.stream().filter(value -> value.toLowerCase().startsWith(token.toLowerCase())).toList();
    }

    private boolean matchesConfiguredCommand(String label) {
        String configured = plugin.getConfigService().pluginCommand();
        String used = label.toLowerCase();
        if (configured.equals(used)) {
            return true;
        }
        return "shop".equals(configured) && "craftplayshop".equals(used);
    }
}
