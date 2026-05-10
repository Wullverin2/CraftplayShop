package de.craftplay.shop.playershop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class PlayerShopTrustService {
    private final CraftplayShopPlugin plugin;
    private final Map<Long, Map<UUID, PlayerShopTrustEntry>> entries = new HashMap<>();

    public PlayerShopTrustService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        synchronized (entries) {
            entries.clear();
            for (PlayerShopTrustEntry entry : loadFromDatabase()) {
                cache(entry);
            }
        }
    }

    public List<PlayerShopTrustEntry> trusted(long shopId) {
        synchronized (entries) {
            return entries.getOrDefault(shopId, Map.of()).values().stream()
                    .sorted(Comparator.comparing(PlayerShopTrustEntry::playerName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    public PlayerShopTrustEntry find(long shopId, UUID playerUuid) {
        synchronized (entries) {
            return entries.getOrDefault(shopId, Map.of()).get(playerUuid);
        }
    }

    public PlayerShopTrustEntry add(long shopId, UUID playerUuid, String playerName) {
        if (!enabled()) {
            return null;
        }
        long now = System.currentTimeMillis();
        PlayerShopTrustEntry entry = new PlayerShopTrustEntry(
                shopId,
                playerUuid,
                playerName,
                plugin.getConfig().getBoolean("playerShops.trust.defaultRights.open", true),
                plugin.getConfig().getBoolean("playerShops.trust.defaultRights.manage", false),
                plugin.getConfig().getBoolean("playerShops.trust.defaultRights.delete", false),
                now,
                now
        );
        save(entry);
        return entry;
    }

    public void save(PlayerShopTrustEntry entry) {
        synchronized (entries) {
            cache(entry);
        }
        plugin.getTaskService().runAsync(() -> {
            String sql = plugin.getDatabaseService().isMySql()
                    ? "INSERT INTO " + plugin.getDatabaseService().table("player_shop_trust") + " " +
                    "(shop_id, player_uuid, player_name, open_allowed, manage_allowed, delete_allowed, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), open_allowed = VALUES(open_allowed), " +
                    "manage_allowed = VALUES(manage_allowed), delete_allowed = VALUES(delete_allowed), " +
                    "created_at = VALUES(created_at), updated_at = VALUES(updated_at)"
                    : "INSERT OR REPLACE INTO " + plugin.getDatabaseService().table("player_shop_trust") + " " +
                    "(shop_id, player_uuid, player_name, open_allowed, manage_allowed, delete_allowed, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(sql)) {
                    fillStatement(statement, entry);
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not save PlayerShop trust entry.", exception);
                }
            }
        });
    }

    public void remove(long shopId, UUID playerUuid) {
        synchronized (entries) {
            Map<UUID, PlayerShopTrustEntry> shopEntries = entries.get(shopId);
            if (shopEntries != null) {
                shopEntries.remove(playerUuid);
                if (shopEntries.isEmpty()) {
                    entries.remove(shopId);
                }
            }
        }
        plugin.getTaskService().runAsync(() -> {
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                        "DELETE FROM " + plugin.getDatabaseService().table("player_shop_trust") + " WHERE shop_id = ? AND player_uuid = ?")) {
                    statement.setLong(1, shopId);
                    statement.setString(2, playerUuid.toString());
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not remove PlayerShop trust entry.", exception);
                }
            }
        });
    }

    public void removeAll(long shopId) {
        synchronized (entries) {
            entries.remove(shopId);
        }
        plugin.getTaskService().runAsync(() -> {
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                        "DELETE FROM " + plugin.getDatabaseService().table("player_shop_trust") + " WHERE shop_id = ?")) {
                    statement.setLong(1, shopId);
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not remove PlayerShop trust entries.", exception);
                }
            }
        });
    }

    public void forget(long shopId) {
        synchronized (entries) {
            entries.remove(shopId);
        }
    }

    public boolean canOpen(Player player, PlayerShop shop) {
        return isOwnerOrAdmin(player, shop) || (enabled() && allowed(player, shop, PlayerShopTrustEntry::openAllowed));
    }

    public boolean canManage(Player player, PlayerShop shop) {
        return isOwnerOrAdmin(player, shop) || (enabled() && allowed(player, shop, PlayerShopTrustEntry::manageAllowed));
    }

    public boolean canDelete(Player player, PlayerShop shop) {
        return isOwnerOrAdmin(player, shop) || (enabled() && allowed(player, shop, PlayerShopTrustEntry::deleteAllowed));
    }

    public boolean isOwnerOrAdmin(Player player, PlayerShop shop) {
        return shop.ownerUuid().equals(player.getUniqueId()) || player.hasPermission(PermissionNodes.PLAYER_SHOP_ADMIN);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("playerShops.trust.enabled", true);
    }

    private boolean allowed(Player player, PlayerShop shop, Predicate<PlayerShopTrustEntry> predicate) {
        PlayerShopTrustEntry entry = find(shop.id(), player.getUniqueId());
        return entry != null && predicate.test(entry);
    }

    private List<PlayerShopTrustEntry> loadFromDatabase() {
        List<PlayerShopTrustEntry> result = new ArrayList<>();
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT * FROM " + plugin.getDatabaseService().table("player_shop_trust"));
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new PlayerShopTrustEntry(
                            resultSet.getLong("shop_id"),
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("player_name"),
                            resultSet.getBoolean("open_allowed"),
                            resultSet.getBoolean("manage_allowed"),
                            resultSet.getBoolean("delete_allowed"),
                            resultSet.getLong("created_at"),
                            resultSet.getLong("updated_at")
                    ));
                }
            } catch (SQLException | IllegalArgumentException exception) {
                plugin.getPluginLogService().error("Could not load PlayerShop trust entries.", exception);
            }
        }
        return result;
    }

    private void cache(PlayerShopTrustEntry entry) {
        entries.computeIfAbsent(entry.shopId(), ignored -> new HashMap<>()).put(entry.playerUuid(), entry);
    }

    private void fillStatement(PreparedStatement statement, PlayerShopTrustEntry entry) throws SQLException {
        statement.setLong(1, entry.shopId());
        statement.setString(2, entry.playerUuid().toString());
        statement.setString(3, entry.playerName());
        statement.setBoolean(4, entry.openAllowed());
        statement.setBoolean(5, entry.manageAllowed());
        statement.setBoolean(6, entry.deleteAllowed());
        statement.setLong(7, entry.createdAt());
        statement.setLong(8, entry.updatedAt());
    }
}
