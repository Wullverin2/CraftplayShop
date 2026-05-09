package de.craftplay.shop.autosell;

import de.craftplay.shop.CraftplayShopPlugin;
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

public class AutoSellChestTrustService {
    private final CraftplayShopPlugin plugin;
    private final Map<Long, Map<UUID, AutoSellChestTrustEntry>> entries = new HashMap<>();

    public AutoSellChestTrustService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        synchronized (entries) {
            entries.clear();
            for (AutoSellChestTrustEntry entry : loadFromDatabase()) {
                cache(entry);
            }
        }
    }

    public List<AutoSellChestTrustEntry> trusted(long chestId) {
        synchronized (entries) {
            return entries.getOrDefault(chestId, Map.of()).values().stream()
                    .sorted(Comparator.comparing(AutoSellChestTrustEntry::playerName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    public AutoSellChestTrustEntry find(long chestId, UUID playerUuid) {
        synchronized (entries) {
            return entries.getOrDefault(chestId, Map.of()).get(playerUuid);
        }
    }

    public AutoSellChestTrustEntry add(long chestId, UUID playerUuid, String playerName) {
        if (!enabled()) {
            return null;
        }
        long now = System.currentTimeMillis();
        AutoSellChestTrustEntry entry = new AutoSellChestTrustEntry(
                chestId,
                playerUuid,
                playerName,
                plugin.getConfig().getBoolean("autoSellChest.trust.defaultRights.open", true),
                plugin.getConfig().getBoolean("autoSellChest.trust.defaultRights.manage", false),
                plugin.getConfig().getBoolean("autoSellChest.trust.defaultRights.upgrade", false),
                plugin.getConfig().getBoolean("autoSellChest.trust.defaultRights.delete", false),
                now,
                now
        );
        save(entry);
        return entry;
    }

    public void save(AutoSellChestTrustEntry entry) {
        synchronized (entries) {
            cache(entry);
        }
        plugin.getTaskService().runAsync(() -> {
            String sql = plugin.getDatabaseService().isMySql()
                    ? "INSERT INTO " + plugin.getDatabaseService().table("autosell_trust") + " " +
                    "(chest_id, player_uuid, player_name, open_allowed, manage_allowed, upgrade_allowed, delete_allowed, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), open_allowed = VALUES(open_allowed), " +
                    "manage_allowed = VALUES(manage_allowed), upgrade_allowed = VALUES(upgrade_allowed), " +
                    "delete_allowed = VALUES(delete_allowed), created_at = VALUES(created_at), updated_at = VALUES(updated_at)"
                    : "INSERT OR REPLACE INTO " + plugin.getDatabaseService().table("autosell_trust") + " " +
                    "(chest_id, player_uuid, player_name, open_allowed, manage_allowed, upgrade_allowed, delete_allowed, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(sql)) {
                    fillStatement(statement, entry);
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not save AutoSellChest trust entry.", exception);
                }
            }
        });
    }

    public void remove(long chestId, UUID playerUuid) {
        synchronized (entries) {
            Map<UUID, AutoSellChestTrustEntry> chestEntries = entries.get(chestId);
            if (chestEntries != null) {
                chestEntries.remove(playerUuid);
                if (chestEntries.isEmpty()) {
                    entries.remove(chestId);
                }
            }
        }
        plugin.getTaskService().runAsync(() -> {
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                        "DELETE FROM " + plugin.getDatabaseService().table("autosell_trust") + " WHERE chest_id = ? AND player_uuid = ?")) {
                    statement.setLong(1, chestId);
                    statement.setString(2, playerUuid.toString());
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not remove AutoSellChest trust entry.", exception);
                }
            }
        });
    }

    public void removeAll(long chestId) {
        synchronized (entries) {
            entries.remove(chestId);
        }
        plugin.getTaskService().runAsync(() -> {
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                        "DELETE FROM " + plugin.getDatabaseService().table("autosell_trust") + " WHERE chest_id = ?")) {
                    statement.setLong(1, chestId);
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not remove AutoSellChest trust entries.", exception);
                }
            }
        });
    }

    public boolean canOpen(Player player, AutoSellChest chest) {
        return isOwnerOrAdmin(player, chest) || (enabled() && allowed(player, chest, AutoSellChestTrustEntry::openAllowed));
    }

    public boolean canManage(Player player, AutoSellChest chest) {
        return isOwnerOrAdmin(player, chest) || (enabled() && allowed(player, chest, AutoSellChestTrustEntry::manageAllowed));
    }

    public boolean canUpgrade(Player player, AutoSellChest chest) {
        return isOwnerOrAdmin(player, chest) || (enabled() && allowed(player, chest, AutoSellChestTrustEntry::upgradeAllowed));
    }

    public boolean canDelete(Player player, AutoSellChest chest) {
        return isOwnerOrAdmin(player, chest) || (enabled() && allowed(player, chest, AutoSellChestTrustEntry::deleteAllowed));
    }

    public boolean isOwnerOrAdmin(Player player, AutoSellChest chest) {
        return chest.ownerUuid().equals(player.getUniqueId()) || player.hasPermission("craftplayshop.autosellchest.admin");
    }

    private boolean allowed(Player player, AutoSellChest chest, java.util.function.Predicate<AutoSellChestTrustEntry> predicate) {
        AutoSellChestTrustEntry entry = find(chest.id(), player.getUniqueId());
        return entry != null && predicate.test(entry);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("autoSellChest.trust.enabled", true);
    }

    private List<AutoSellChestTrustEntry> loadFromDatabase() {
        List<AutoSellChestTrustEntry> result = new ArrayList<>();
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT * FROM " + plugin.getDatabaseService().table("autosell_trust"));
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new AutoSellChestTrustEntry(
                            resultSet.getLong("chest_id"),
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("player_name"),
                            resultSet.getBoolean("open_allowed"),
                            resultSet.getBoolean("manage_allowed"),
                            resultSet.getBoolean("upgrade_allowed"),
                            resultSet.getBoolean("delete_allowed"),
                            resultSet.getLong("created_at"),
                            resultSet.getLong("updated_at")
                    ));
                }
            } catch (SQLException | IllegalArgumentException exception) {
                plugin.getPluginLogService().error("Could not load AutoSellChest trust entries.", exception);
            }
        }
        return result;
    }

    private void cache(AutoSellChestTrustEntry entry) {
        entries.computeIfAbsent(entry.chestId(), ignored -> new HashMap<>()).put(entry.playerUuid(), entry);
    }

    private void fillStatement(PreparedStatement statement, AutoSellChestTrustEntry entry) throws SQLException {
        statement.setLong(1, entry.chestId());
        statement.setString(2, entry.playerUuid().toString());
        statement.setString(3, entry.playerName());
        statement.setBoolean(4, entry.openAllowed());
        statement.setBoolean(5, entry.manageAllowed());
        statement.setBoolean(6, entry.upgradeAllowed());
        statement.setBoolean(7, entry.deleteAllowed());
        statement.setLong(8, entry.createdAt());
        statement.setLong(9, entry.updatedAt());
    }
}
