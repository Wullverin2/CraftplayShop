package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerShopFavoriteService {
    private final CraftplayShopPlugin plugin;
    private final ConcurrentHashMap<UUID, Set<String>> favorites = new ConcurrentHashMap<>();

    public ServerShopFavoriteService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAsync(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getTaskService().runAsync(() -> favorites.put(uuid, loadFavorites(uuid)));
    }

    public void clear() {
        favorites.clear();
    }

    public boolean isFavorite(Player player, ServerShopItem item) {
        return favoriteKeys(player).contains(key(item.categoryId(), item.id()));
    }

    public Set<String> favoriteKeys(Player player) {
        return favorites.computeIfAbsent(player.getUniqueId(), this::loadFavorites);
    }

    public boolean toggle(Player player, ServerShopItem item) {
        Set<String> keys = favoriteKeys(player);
        String key = key(item.categoryId(), item.id());
        boolean added;
        synchronized (keys) {
            added = keys.add(key);
            if (!added) {
                keys.remove(key);
            }
        }
        boolean finalAdded = added;
        UUID uuid = player.getUniqueId();
        plugin.getTaskService().runAsync(() -> {
            if (finalAdded) {
                insertFavorite(uuid, item);
            } else {
                deleteFavorite(uuid, item);
            }
        });
        return added;
    }

    public static String key(String categoryId, String itemId) {
        return categoryId + "\u0000" + itemId;
    }

    public static String categoryId(String key) {
        int separator = key.indexOf('\u0000');
        return separator <= 0 ? "" : key.substring(0, separator);
    }

    public static String itemId(String key) {
        int separator = key.indexOf('\u0000');
        return separator < 0 || separator >= key.length() - 1 ? "" : key.substring(separator + 1);
    }

    private Set<String> loadFavorites(UUID uuid) {
        Set<String> keys = Collections.synchronizedSet(new LinkedHashSet<>());
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT category_id, item_id FROM " + plugin.getDatabaseService().table("server_shop_favorites") + " WHERE player_uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        keys.add(key(result.getString("category_id"), result.getString("item_id")));
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not load ServerShop favorites.", exception);
            }
        }
        return keys;
    }

    private void insertFavorite(UUID uuid, ServerShopItem item) {
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement exists = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT player_uuid FROM " + plugin.getDatabaseService().table("server_shop_favorites") + " WHERE player_uuid = ? AND category_id = ? AND item_id = ?")) {
                exists.setString(1, uuid.toString());
                exists.setString(2, item.categoryId());
                exists.setString(3, item.id());
                try (ResultSet result = exists.executeQuery()) {
                    if (result.next()) {
                        return;
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not check ServerShop favorite.", exception);
                return;
            }
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + plugin.getDatabaseService().table("server_shop_favorites") + " (player_uuid, category_id, item_id, created_at) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, item.categoryId());
                statement.setString(3, item.id());
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not save ServerShop favorite.", exception);
            }
        }
    }

    private void deleteFavorite(UUID uuid, ServerShopItem item) {
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "DELETE FROM " + plugin.getDatabaseService().table("server_shop_favorites") + " WHERE player_uuid = ? AND category_id = ? AND item_id = ?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, item.categoryId());
                statement.setString(3, item.id());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not delete ServerShop favorite.", exception);
            }
        }
    }
}
