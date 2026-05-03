package de.craftplay.shop.core.gui;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GuiActionExecutor {
    private final CraftplayShopPlugin plugin;

    public GuiActionExecutor(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, GuiItemDefinition item, boolean rightClick) {
        if (!item.permission().isBlank() && !player.hasPermission(item.permission())) {
            plugin.getLanguageService().send(player, "general.noPermission");
            return;
        }
        playSound(player, item);
        List<String> actions = rightClick ? item.rightClickActions() : item.leftClickActions();
        if (actions.isEmpty() && rightClick) {
            actions = item.leftClickActions();
        }
        for (String action : actions) {
            execute(player, action);
        }
        if (item.closeInventory()) {
            player.closeInventory();
        }
    }

    public void execute(Player player, String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        String[] split = action.split(":", 2);
        String type = split[0].toUpperCase(Locale.ROOT);
        String value = split.length > 1 ? split[1] : "";
        switch (type) {
            case "OPEN_GUI" -> plugin.getGuiService().open(player, value);
            case "CLOSE" -> player.closeInventory();
            case "SERVER_SHOP_CATEGORY" -> plugin.getServerShopCategoryGui().open(player, value);
            case "PLAYER_COMMAND" -> runPlayerCommand(player, value);
            case "CONSOLE_COMMAND" -> runConsoleCommand(player, value);
            case "MESSAGE" -> plugin.getLanguageService().send(player, value);
            case "SOUND" -> playSound(player, value, 1.0F, 1.0F);
            case "DIRECT_TRADE_TOGGLE" -> plugin.getDirectTradeService().toggle(player);
            case "LANGUAGE_SET" -> setLanguage(player, value);
            default -> plugin.getPluginLogService().debug("Unknown GUI action: " + action);
        }
    }

    private void setLanguage(Player player, String language) {
        if (!plugin.getPlayerLanguageService().setLanguage(player, language)) {
            plugin.getLanguageService().send(player, "language.invalid");
            return;
        }
        plugin.getLanguageService().send(player, "language.changed", Map.of("language", language));
    }

    private void runPlayerCommand(Player player, String command) {
        if (!plugin.getConfig().getBoolean("gui.allowPlayerCommands", true) || isBlocked(command)) {
            return;
        }
        player.performCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    private void runConsoleCommand(Player player, String command) {
        if (!plugin.getConfig().getBoolean("gui.allowConsoleCommands", true) || isBlocked(command)) {
            return;
        }
        String parsed = command.replace("%player%", player.getName()).replace("%player_uuid%", player.getUniqueId().toString());
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed.startsWith("/") ? parsed.substring(1) : parsed);
    }

    private boolean isBlocked(String command) {
        if (!plugin.getConfig().getBoolean("gui.blockDangerousCommands", true)) {
            return false;
        }
        String normalized = command.toLowerCase(Locale.ROOT).strip();
        for (String prefix : plugin.getConfig().getStringList("gui.blockedCommandPrefixes")) {
            if (normalized.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void playSound(Player player, GuiItemDefinition item) {
        if (!item.section().getBoolean("sound.enabled", false)) {
            return;
        }
        playSound(player, item.section().getString("sound.name", "UI_BUTTON_CLICK"),
                (float) item.section().getDouble("sound.volume", 1.0D),
                (float) item.section().getDouble("sound.pitch", 1.0D));
    }

    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
