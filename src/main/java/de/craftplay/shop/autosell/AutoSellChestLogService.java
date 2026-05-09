package de.craftplay.shop.autosell;

import de.craftplay.shop.CraftplayShopPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AutoSellChestLogService {
    private final CraftplayShopPlugin plugin;

    public AutoSellChestLogService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void logAsync(AutoSellChest chest, String material, int amount, double priceEach, double totalPrice) {
        if (!plugin.getConfig().getBoolean("autoSellChest.performance.asyncDatabaseLogging", true)) {
            log(chest, material, amount, priceEach, totalPrice);
            return;
        }
        plugin.getTaskService().runAsync(() -> log(chest, material, amount, priceEach, totalPrice));
    }

    private void log(AutoSellChest chest, String material, int amount, double priceEach, double totalPrice) {
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + plugin.getDatabaseService().table("autosell_logs") + " " +
                            "(chest_id, owner_uuid, owner_name, world, x, y, z, material, amount, price_each, total_price, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setLong(1, chest.id());
                statement.setString(2, chest.ownerUuid().toString());
                statement.setString(3, chest.ownerName());
                statement.setString(4, chest.world());
                statement.setInt(5, chest.x());
                statement.setInt(6, chest.y());
                statement.setInt(7, chest.z());
                statement.setString(8, material);
                statement.setInt(9, amount);
                statement.setDouble(10, priceEach);
                statement.setDouble(11, totalPrice);
                statement.setLong(12, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not write AutoSellChest log.", exception);
            }
        }
    }

    public List<LogEntry> recentLogs(long chestId, int limit) {
        List<LogEntry> entries = new ArrayList<>();
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT material, amount, price_each, total_price, created_at FROM " + plugin.getDatabaseService().table("autosell_logs") +
                            " WHERE chest_id = ? ORDER BY created_at DESC LIMIT ?")) {
                statement.setLong(1, chestId);
                statement.setInt(2, Math.max(1, limit));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        entries.add(new LogEntry(
                                result.getString("material"),
                                result.getInt("amount"),
                                result.getDouble("price_each"),
                                result.getDouble("total_price"),
                                result.getLong("created_at")
                        ));
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not read AutoSellChest logs.", exception);
            }
        }
        return entries;
    }

    public Stats stats(long chestId, long since) {
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) AS amount, COALESCE(SUM(total_price), 0) AS total FROM " + plugin.getDatabaseService().table("autosell_logs") +
                            " WHERE chest_id = ? AND created_at >= ?")) {
                statement.setLong(1, chestId);
                statement.setLong(2, Math.max(0L, since));
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return new Stats(result.getLong("amount"), result.getDouble("total"));
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not read AutoSellChest stats.", exception);
            }
        }
        return new Stats(0L, 0.0D);
    }

    public List<MaterialStats> materialStats(long chestId, int limit) {
        List<MaterialStats> entries = new ArrayList<>();
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT material, COALESCE(SUM(amount), 0) AS amount, COALESCE(SUM(total_price), 0) AS total FROM " + plugin.getDatabaseService().table("autosell_logs") +
                            " WHERE chest_id = ? GROUP BY material ORDER BY amount DESC LIMIT ?")) {
                statement.setLong(1, chestId);
                statement.setInt(2, Math.max(1, limit));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        entries.add(new MaterialStats(result.getString("material"), result.getLong("amount"), result.getDouble("total")));
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not read AutoSellChest material stats.", exception);
            }
        }
        return entries;
    }

    public record LogEntry(String material, int amount, double priceEach, double totalPrice, long createdAt) {
    }

    public record Stats(long amount, double totalPrice) {
    }

    public record MaterialStats(String material, long amount, double totalPrice) {
    }
}
