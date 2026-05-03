package de.craftplay.shop.core.language;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.entity.Player;

public class PlayerLanguageService {
    private final CraftplayShopPlugin plugin;

    public PlayerLanguageService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public String getLanguage(Player player) {
        if (player == null || plugin.getPlayerSettingsService() == null) {
            return plugin.getConfigService().defaultLanguage();
        }
        String language = plugin.getPlayerSettingsService().getSettings(player).language();
        if (language == null || language.isBlank()) {
            return plugin.getConfigService().defaultLanguage();
        }
        return language;
    }

    public boolean setLanguage(Player player, String language) {
        if (!plugin.getLanguageService().isAvailable(language)) {
            return false;
        }
        plugin.getPlayerSettingsService().setLanguage(player, language);
        return true;
    }
}
