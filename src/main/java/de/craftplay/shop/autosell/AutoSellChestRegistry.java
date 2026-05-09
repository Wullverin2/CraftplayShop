package de.craftplay.shop.autosell;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AutoSellChestRegistry {
    private final CraftplayShopPlugin plugin;
    private final Map<String, AutoSellChest> byLocation = new HashMap<>();
    private final Map<Long, AutoSellChest> byId = new HashMap<>();
    private final Set<String> dirty = new HashSet<>();
    private final Map<String, Long> dirtyTimestamps = new HashMap<>();
    private int cursor;

    public AutoSellChestRegistry(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        synchronized (byLocation) {
            byLocation.clear();
            byId.clear();
            dirty.clear();
            dirtyTimestamps.clear();
            cursor = 0;
            for (AutoSellChest chest : loadFromDatabase()) {
                cache(chest);
            }
        }
    }

    public AutoSellChest create(Player owner, Block block) {
        long now = System.currentTimeMillis();
        String name = plugin.getConfig().getString("autoSellChest.defaultName", "&cAutoSellChest #%id%");
        AutoSellChest draft = new AutoSellChest(0L, owner.getUniqueId(), owner.getName(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), name, true,
                plugin.getConfig().getBoolean("autoSellChest.notifyOwnerDefault", true), 1.0D,
                0L, 0.0D, 0L, now, now);
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + plugin.getDatabaseService().table("autosell_chests") + " " +
                            "(owner_uuid, owner_name, world, x, y, z, name, active, notify_owner, multiplier, total_items_sold, total_money_earned, last_sold_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                fillStatement(statement, draft, false);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        AutoSellChest created = new AutoSellChest(keys.getLong(1), draft.ownerUuid(), draft.ownerName(), draft.world(),
                                draft.x(), draft.y(), draft.z(), name.replace("%id%", Long.toString(keys.getLong(1))), draft.active(),
                                draft.notifyOwner(), draft.multiplier(), draft.totalItemsSold(), draft.totalMoneyEarned(),
                                draft.lastSoldAt(), draft.createdAt(), draft.updatedAt());
                        cache(created);
                        update(created);
                        markDirty(created);
                        return created;
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not create AutoSellChest.", exception);
            }
        }
        return null;
    }

    public void update(AutoSellChest chest) {
        synchronized (byLocation) {
            cache(chest);
        }
        plugin.getTaskService().runAsync(() -> {
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                        "UPDATE " + plugin.getDatabaseService().table("autosell_chests") + " SET " +
                                "owner_uuid = ?, owner_name = ?, world = ?, x = ?, y = ?, z = ?, name = ?, active = ?, notify_owner = ?, multiplier = ?, " +
                                "total_items_sold = ?, total_money_earned = ?, last_sold_at = ?, created_at = ?, updated_at = ? WHERE id = ?")) {
                    fillStatement(statement, chest, true);
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not update AutoSellChest.", exception);
                }
            }
        });
    }

    public void delete(AutoSellChest chest) {
        synchronized (byLocation) {
            byLocation.remove(chest.locationKey());
            byId.remove(chest.id());
            dirty.remove(chest.locationKey());
            dirtyTimestamps.remove(chest.locationKey());
        }
        plugin.getTaskService().runAsync(() -> {
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                        "DELETE FROM " + plugin.getDatabaseService().table("autosell_chests") + " WHERE id = ?")) {
                    statement.setLong(1, chest.id());
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not delete AutoSellChest.", exception);
                }
            }
        });
    }

    public AutoSellChest find(Location location) {
        synchronized (byLocation) {
            return byLocation.get(LocationUtil.compact(location));
        }
    }

    public AutoSellChest find(long id) {
        synchronized (byLocation) {
            return byId.get(id);
        }
    }

    public List<AutoSellChest> ownedBy(UUID owner) {
        synchronized (byLocation) {
            return byId.values().stream()
                    .filter(chest -> chest.ownerUuid().equals(owner))
                    .sorted(java.util.Comparator.comparingLong(AutoSellChest::id))
                    .toList();
        }
    }

    public int countOwned(UUID owner) {
        return ownedBy(owner).size();
    }

    public Collection<AutoSellChest> all() {
        synchronized (byLocation) {
            return new ArrayList<>(byId.values());
        }
    }

    public void markDirty(AutoSellChest chest) {
        markDirty(chest.locationKey());
    }

    public void markDirty(Location location) {
        markDirty(LocationUtil.compact(location));
    }

    public void markDirty(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        synchronized (byLocation) {
            dirty.add(key);
            dirtyTimestamps.put(key, System.currentTimeMillis());
        }
    }

    public List<AutoSellChest> nextProcessBatch(int maxChests, long intervalMillis, boolean dirtyOnly, long dirtyCooldownMillis) {
        List<AutoSellChest> chests;
        synchronized (byLocation) {
            chests = new ArrayList<>(byId.values());
        }
        if (chests.isEmpty()) {
            return List.of();
        }
        List<AutoSellChest> batch = new ArrayList<>();
        long now = System.currentTimeMillis();
        int checked = 0;
        while (checked < chests.size() && batch.size() < Math.max(1, maxChests)) {
            if (cursor >= chests.size()) {
                cursor = 0;
            }
            AutoSellChest chest = chests.get(cursor++);
            checked++;
            boolean due = chest.lastSoldAt() <= 0L || now - chest.lastSoldAt() >= intervalMillis;
            boolean dirtyReady;
            synchronized (byLocation) {
                Long dirtyAt = dirtyTimestamps.get(chest.locationKey());
                dirtyReady = dirty.contains(chest.locationKey()) && (dirtyAt == null || now - dirtyAt >= dirtyCooldownMillis);
                if (dirtyReady) {
                    dirty.remove(chest.locationKey());
                    dirtyTimestamps.remove(chest.locationKey());
                }
            }
            if (!chest.active()) {
                continue;
            }
            if ((dirtyOnly && dirtyReady) || (!dirtyOnly && (due || dirtyReady))) {
                batch.add(chest);
            }
        }
        return batch;
    }

    private List<AutoSellChest> loadFromDatabase() {
        List<AutoSellChest> chests = new ArrayList<>();
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT * FROM " + plugin.getDatabaseService().table("autosell_chests"));
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    chests.add(new AutoSellChest(
                            result.getLong("id"),
                            UUID.fromString(result.getString("owner_uuid")),
                            result.getString("owner_name"),
                            result.getString("world"),
                            result.getInt("x"),
                            result.getInt("y"),
                            result.getInt("z"),
                            result.getString("name"),
                            result.getBoolean("active"),
                            result.getBoolean("notify_owner"),
                            result.getDouble("multiplier"),
                            result.getLong("total_items_sold"),
                            result.getDouble("total_money_earned"),
                            result.getLong("last_sold_at"),
                            result.getLong("created_at"),
                            result.getLong("updated_at")
                    ));
                }
            } catch (SQLException | IllegalArgumentException exception) {
                plugin.getPluginLogService().error("Could not load AutoSellChests.", exception);
            }
        }
        return chests;
    }

    private void cache(AutoSellChest chest) {
        byLocation.put(chest.locationKey(), chest);
        byId.put(chest.id(), chest);
    }

    private void fillStatement(PreparedStatement statement, AutoSellChest chest, boolean includeId) throws SQLException {
        statement.setString(1, chest.ownerUuid().toString());
        statement.setString(2, chest.ownerName());
        statement.setString(3, chest.world());
        statement.setInt(4, chest.x());
        statement.setInt(5, chest.y());
        statement.setInt(6, chest.z());
        statement.setString(7, chest.name());
        statement.setBoolean(8, chest.active());
        statement.setBoolean(9, chest.notifyOwner());
        statement.setDouble(10, chest.multiplier());
        statement.setLong(11, chest.totalItemsSold());
        statement.setDouble(12, chest.totalMoneyEarned());
        statement.setLong(13, chest.lastSoldAt());
        statement.setLong(14, chest.createdAt());
        statement.setLong(15, chest.updatedAt());
        if (includeId) {
            statement.setLong(16, chest.id());
        }
    }
}
