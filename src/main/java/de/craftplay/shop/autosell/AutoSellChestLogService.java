package de.craftplay.shop.autosell;

import de.craftplay.shop.CraftplayShopPlugin;

import java.sql.PreparedStatement;
import java.sql.SQLException;

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
}
