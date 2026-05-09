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
        if ("ah".equals(commandName)) {
            auctionHouse(sender, args);
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
        if ("search".equals(sub) || "suche".equals(sub)) {
            search(sender, args);
            return true;
        }
        if ("favorites".equals(sub) || "favourites".equals(sub) || "favoriten".equals(sub)) {
            favorites(sender);
            return true;
        }
        if ("playershop".equals(sub) || "pshop".equals(sub) || "spielershop".equals(sub)) {
            playerShop(sender, args);
            return true;
        }
        if ("auctionhouse".equals(sub) || "ah".equals(sub) || "auktion".equals(sub)) {
            auctionHouse(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if ("rankshop".equals(sub) || "ranks".equals(sub)) {
            rankShop(sender);
            return true;
        }
        if ("permissionshop".equals(sub) || "permshop".equals(sub) || "permissions".equals(sub)) {
            permissionShop(sender);
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
            if (args.length > 2 && "list".equalsIgnoreCase(args[2])) {
                plugin.getServerShopAdminEditor().listBackups(player);
                return;
            }
            if (args.length > 2 && "restore".equalsIgnoreCase(args[2])) {
                if (args.length < 4) {
                    plugin.getLanguageService().send(player, "adminShop.backupRestoreUsage");
                    return;
                }
                plugin.getServerShopAdminEditor().requestBackupRestore(player, args[3]);
                return;
            }
            if (args.length > 2 && "confirm".equalsIgnoreCase(args[2])) {
                plugin.getServerShopAdminEditor().confirmBackupRestore(player);
                return;
            }
            if (args.length > 2 && "cancel".equalsIgnoreCase(args[2])) {
                plugin.getServerShopAdminEditor().cancelBackupRestore(player);
                return;
            }
            plugin.getServerShopAdminEditor().createManualBackup(player);
            return;
        }
        if ("backups".equals(sub)) {
            if (!(sender instanceof Player player)) {
                plugin.getLanguageService().send(sender, "general.playerOnly");
                return;
            }
            if (!player.hasPermission(PermissionNodes.ADMIN)) {
                plugin.getLanguageService().send(player, "general.noPermission");
                return;
            }
            plugin.getServerShopAdminEditor().listBackups(player);
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

    private void search(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        if (args.length < 2) {
            plugin.getServerShopListGui().requestSearch(player);
            return;
        }
        plugin.getServerShopListGui().openSearch(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
    }

    private void favorites(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        plugin.getServerShopListGui().openFavorites(player);
    }

    private void playerShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.PLAYER_SHOP_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        if (args.length > 1 && ("search".equalsIgnoreCase(args[1]) || "suche".equalsIgnoreCase(args[1]))) {
            plugin.getPlayerShopService().requestSearch(player);
            return;
        }
        if (args.length > 1 && ("mine".equalsIgnoreCase(args[1]) || "own".equalsIgnoreCase(args[1]) || "meine".equalsIgnoreCase(args[1]))) {
            plugin.getPlayerShopService().openOwn(player);
            return;
        }
        plugin.getPlayerShopService().openHome(player);
    }

    private void auctionHouse(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.AUCTION_HOUSE_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        if (args.length == 0) {
            plugin.getAuctionHouseService().openHome(player);
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "browse", "search", "suche" -> {
                if (args.length > 1) {
                    plugin.getAuctionHouseService().openBrowse(player, 0, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                } else {
                    plugin.getAuctionHouseService().requestSearch(player);
                }
            }
            case "sell", "list", "verkaufen" -> {
                if (args.length < 2) {
                    plugin.getLanguageService().send(player, "auctionHouse.sellUsage");
                    return;
                }
                double price;
                int amount = player.getInventory().getItemInMainHand().getAmount();
                try {
                    price = Double.parseDouble(args[1].replace(',', '.'));
                    if (args.length > 2) {
                        amount = Integer.parseInt(args[2]);
                    }
                } catch (NumberFormatException exception) {
                    plugin.getLanguageService().send(player, "auctionHouse.sellUsage");
                    return;
                }
                plugin.getAuctionHouseService().createListing(player, price, amount);
            }
            case "mine", "my", "meine" -> plugin.getAuctionHouseService().openMine(player, 0);
            case "claims", "claim", "abholen" -> plugin.getAuctionHouseService().openClaims(player, 0);
            default -> plugin.getAuctionHouseService().openHome(player);
        }
    }

    private void rankShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.RANK_SHOP_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        plugin.getRankShopService().open(player);
    }

    private void permissionShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getLanguageService().send(sender, "general.playerOnly");
            return;
        }
        if (!player.hasPermission(PermissionNodes.PERMISSION_SHOP_USE)) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        plugin.getPermissionProductService().open(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if ("ah".equalsIgnoreCase(command.getName())) {
                return filter(List.of("browse", "search", "sell", "mine", "claims"), args[0]);
            }
            return filter(List.of("admin", "reload", "language", "lang", "sellhand", "sellall", "sellgui", "search", "favorites", "playershop", "pshop", "auctionhouse", "ah", "rankshop", "permissionshop"), args[0]);
        }
        if (args.length == 2 && ("auctionhouse".equalsIgnoreCase(args[0]) || "ah".equalsIgnoreCase(args[0]))) {
            return filter(List.of("browse", "search", "sell", "mine", "claims"), args[1]);
        }
        if (args.length == 2 && "ah".equalsIgnoreCase(command.getName()) && "sell".equalsIgnoreCase(args[0])) {
            return List.of("<price>");
        }
        if (args.length == 3 && "ah".equalsIgnoreCase(command.getName()) && "sell".equalsIgnoreCase(args[0])) {
            return List.of("<amount>");
        }
        if (args.length == 2 && ("playershop".equalsIgnoreCase(args[0]) || "pshop".equalsIgnoreCase(args[0]))) {
            return filter(List.of("search", "mine", "own"), args[1]);
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            return filter(List.of("editor", "reload", "servershop", "adminshop", "backup", "backups"), args[1]);
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "backup".equalsIgnoreCase(args[1])) {
            return filter(List.of("list", "restore", "confirm", "cancel"), args[2]);
        }
        if (args.length == 4
                && "admin".equalsIgnoreCase(args[0])
                && "backup".equalsIgnoreCase(args[1])
                && "restore".equalsIgnoreCase(args[2])) {
            return filter(plugin.getServerShopAdminEditor().backupFileNames(), args[3]);
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
