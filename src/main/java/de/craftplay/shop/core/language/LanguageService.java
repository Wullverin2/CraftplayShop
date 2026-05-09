package de.craftplay.shop.core.language;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.config.ConfigDefaults;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class LanguageService {
    private final CraftplayShopPlugin plugin;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();

    public LanguageService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        languages.clear();
        File folder = new File(plugin.getDataFolder(), "language");
        folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String language = file.getName().substring(0, file.getName().length() - 4);
            languages.put(language, YamlConfiguration.loadConfiguration(file));
        }
        for (String language : ConfigDefaults.LANGUAGES) {
            File file = new File(folder, language + ".yml");
            if (file.exists()) {
                languages.putIfAbsent(language, YamlConfiguration.loadConfiguration(file));
            }
        }
    }

    public Set<String> availableLanguages() {
        return new LinkedHashSet<>(languages.keySet());
    }

    public int count() {
        return languages.size();
    }

    public boolean isAvailable(String language) {
        return languages.containsKey(language);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(get(sender, key, placeholders));
    }

    public String get(CommandSender sender, String key) {
        return get(sender, key, Map.of());
    }

    public String get(CommandSender sender, String key, Map<String, String> placeholders) {
        String language = plugin.getConfigService().defaultLanguage();
        Player player = null;
        if (sender instanceof Player senderPlayer && plugin.getPlayerLanguageService() != null) {
            player = senderPlayer;
            language = plugin.getPlayerLanguageService().getLanguage(senderPlayer);
        }
        return get(language, key, placeholders, player);
    }

    public String get(String language, String key, Map<String, String> placeholders) {
        return get(language, key, placeholders, null);
    }

    private String get(String language, String key, Map<String, String> placeholders, Player player) {
        String prefix = raw(language, "prefix");
        Map<String, String> merged = new HashMap<>();
        if (player != null) {
            merged.putAll(PlaceholderUtil.player(player));
            merged.put("language", language);
            merged.put("currency", plugin.getConfigService().currencySymbol());
        }
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        merged.putIfAbsent("prefix", prefix);
        String raw = raw(language, key);
        String parsed = PlaceholderUtil.apply(raw, merged);
        if (player != null) {
            parsed = plugin.getPlaceholderApiHook().apply(player, parsed);
        }
        return TextUtil.color(parsed);
    }

    private String raw(String language, String key) {
        String value = getValue(language, key);
        if (value != null) {
            return value;
        }
        String fallback = plugin.getConfigService().fallbackLanguage();
        value = getValue(fallback, key);
        if (value != null) {
            return value;
        }
        plugin.getPluginLogService().warn("Missing language key: " + key);
        return "&cMissing language key: " + key;
    }

    private String getValue(String language, String key) {
        YamlConfiguration configuration = languages.get(language);
        if (configuration == null) {
            return null;
        }
        return configuration.getString(key);
    }
}
