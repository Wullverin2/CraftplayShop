package de.craftplay.shop.core.gui;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.PlaceholderUtil;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class GuiPlaceholderService {
    private final CraftplayShopPlugin plugin;

    public GuiPlaceholderService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, String> placeholders(Player player) {
        Map<String, String> placeholders = new HashMap<>(PlaceholderUtil.player(player));
        placeholders.put("language", plugin.getPlayerLanguageService().getLanguage(player));
        placeholders.put("currency", plugin.getConfigService().currencySymbol());
        return placeholders;
    }
}
