package de.craftplay.shop.core.transaction;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TransactionService {
    private final CraftplayShopPlugin plugin;

    public TransactionService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void logAsync(TransactionType type, Player player, String source, ItemStack itemStack, int amount, double priceEach, double totalPrice) {
        plugin.getTaskService().runAsync(() -> log(type, player, source, itemStack, amount, priceEach, totalPrice));
        if (plugin.getDiscordWebhookService() != null) {
            plugin.getDiscordWebhookService().sendTransaction(type, player, source, itemStack, amount, priceEach, totalPrice);
        }
    }

    private void log(TransactionType type, Player player, String source, ItemStack itemStack, int amount, double priceEach, double totalPrice) {
        String table = plugin.getDatabaseService().table("transactions");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (type, player_uuid, player_name, source, item_data, material, amount, price_each, total_price, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, type.name());
                statement.setString(2, player.getUniqueId().toString());
                statement.setString(3, player.getName());
                statement.setString(4, source);
                statement.setString(5, plugin.getItemSerializer().serialize(itemStack));
                statement.setString(6, itemStack == null ? "" : itemStack.getType().name());
                statement.setInt(7, amount);
                statement.setDouble(8, priceEach);
                statement.setDouble(9, totalPrice);
                statement.setLong(10, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not write transaction log.", exception);
            }
        }
    }
}
