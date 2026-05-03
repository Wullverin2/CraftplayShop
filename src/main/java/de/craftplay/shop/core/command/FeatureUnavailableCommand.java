package de.craftplay.shop.core.command;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class FeatureUnavailableCommand implements CommandExecutor {
    private final CraftplayShopPlugin plugin;

    public FeatureUnavailableCommand(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.getLanguageService().send(sender, "general.featureNotAvailable");
        return true;
    }
}
