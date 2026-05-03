package de.craftplay.shop.core.player;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.TimeUtil;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSettingsService {
    private final CraftplayShopPlugin plugin;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    public PlayerSettingsService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerSettings getSettings(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), uuid -> defaultSettings(player));
    }

    public void loadAsync(Player player) {
        plugin.getTaskService().runAsync(() -> {
            PlayerSettings settings = load(player.getUniqueId(), player.getName());
            cache.put(player.getUniqueId(), settings);
        });
    }

    public void saveAllSync() {
        for (PlayerSettings settings : cache.values()) {
            save(settings);
        }
    }

    public void clear() {
        cache.clear();
    }

    public void setLanguage(Player player, String language) {
        PlayerSettings settings = getSettings(player).withLanguage(language, player.getName(), TimeUtil.now());
        cache.put(player.getUniqueId(), settings);
        saveAsync(settings);
    }

    public boolean toggleDirectTrade(Player player) {
        boolean enabled = !getSettings(player).directTradeEnabled();
        setDirectTrade(player, enabled);
        return enabled;
    }

    public void setDirectTrade(Player player, boolean enabled) {
        PlayerSettings settings = getSettings(player).withDirectTradeEnabled(enabled, player.getName(), TimeUtil.now());
        cache.put(player.getUniqueId(), settings);
        saveAsync(settings);
    }

    private PlayerSettings defaultSettings(Player player) {
        return new PlayerSettings(
                player.getUniqueId(),
                player.getName(),
                plugin.getConfigService().defaultLanguage(),
                plugin.getConfig().getBoolean("directTrade.defaultEnabledForPlayers", true),
                TimeUtil.now()
        );
    }

    private PlayerSettings load(UUID uuid, String playerName) {
        String table = plugin.getDatabaseService().table("player_settings");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT language, direct_trade_enabled, updated_at FROM " + table + " WHERE player_uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String language = resultSet.getString("language");
                        boolean directTrade = resultSet.getBoolean("direct_trade_enabled");
                        long updatedAt = resultSet.getLong("updated_at");
                        return new PlayerSettings(uuid, playerName, language, directTrade, updatedAt);
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not load player settings.", exception);
            }
        }
        PlayerSettings settings = new PlayerSettings(
                uuid,
                playerName,
                plugin.getConfigService().defaultLanguage(),
                plugin.getConfig().getBoolean("directTrade.defaultEnabledForPlayers", true),
                TimeUtil.now()
        );
        save(settings);
        return settings;
    }

    private void saveAsync(PlayerSettings settings) {
        plugin.getTaskService().runAsync(() -> save(settings));
    }

    private void save(PlayerSettings settings) {
        String table = plugin.getDatabaseService().table("player_settings");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (player_uuid, player_name, language, direct_trade_enabled, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name, language = excluded.language, " +
                            "direct_trade_enabled = excluded.direct_trade_enabled, updated_at = excluded.updated_at")) {
                statement.setString(1, settings.playerUuid().toString());
                statement.setString(2, settings.playerName());
                statement.setString(3, settings.language());
                statement.setBoolean(4, settings.directTradeEnabled());
                statement.setLong(5, settings.updatedAt());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not save player settings.", exception);
            }
        }
    }
}
