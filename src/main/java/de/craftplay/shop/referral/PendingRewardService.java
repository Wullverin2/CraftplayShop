package de.craftplay.shop.referral;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PendingRewardService {
    private final CraftplayShopPlugin plugin;

    public PendingRewardService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void addItemReward(Player player, String sourceType, String sourceId, ItemStack itemStack) {
        String table = plugin.getDatabaseService().table("referral_pending_rewards");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (player_uuid, player_name, source_type, source_id, reward_kind, item_data, command, money, created_at, claimed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, player.getName());
                statement.setString(3, sourceType);
                statement.setString(4, sourceId);
                statement.setString(5, "ITEM");
                statement.setString(6, plugin.getItemSerializer().serialize(itemStack));
                statement.setString(7, "");
                statement.setDouble(8, 0.0D);
                statement.setLong(9, System.currentTimeMillis());
                statement.setLong(10, 0L);
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not store pending referral reward.", exception);
            }
        }
    }

    public List<PendingReward> loadPending(UUID playerUuid) {
        List<PendingReward> rewards = new ArrayList<>();
        String table = plugin.getDatabaseService().table("referral_pending_rewards");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT * FROM " + table + " WHERE player_uuid = ? AND claimed_at = 0 ORDER BY created_at ASC")) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        rewards.add(new PendingReward(
                                resultSet.getLong("id"),
                                UUID.fromString(resultSet.getString("player_uuid")),
                                resultSet.getString("player_name"),
                                resultSet.getString("source_type"),
                                resultSet.getString("source_id"),
                                resultSet.getString("reward_kind"),
                                resultSet.getString("item_data"),
                                resultSet.getString("command"),
                                resultSet.getDouble("money"),
                                resultSet.getLong("created_at"),
                                resultSet.getLong("claimed_at")
                        ));
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not load pending referral rewards.", exception);
            }
        }
        return rewards;
    }

    public void deliver(Player player) {
        deliver(player, loadPending(player.getUniqueId()));
    }

    public void deliver(Player player, List<PendingReward> rewards) {
        for (PendingReward reward : rewards) {
            ItemStack itemStack = plugin.getItemSerializer().deserialize(reward.itemData());
            if (itemStack == null || itemStack.getType().isAir()) {
                markClaimed(reward.id());
                continue;
            }
            if (!player.getInventory().addItem(itemStack).isEmpty()) {
                return;
            }
            markClaimed(reward.id());
            plugin.getLanguageService().send(player, "referral.pendingClaimed", java.util.Map.of(
                    "item", itemStack.getType().name(),
                    "amount", Integer.toString(itemStack.getAmount())
            ));
        }
    }

    private void markClaimed(long id) {
        String table = plugin.getDatabaseService().table("referral_pending_rewards");
        plugin.getTaskService().runAsync(() -> {
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                        "UPDATE " + table + " SET claimed_at = ? WHERE id = ?")) {
                    statement.setLong(1, System.currentTimeMillis());
                    statement.setLong(2, id);
                    statement.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not mark referral reward as claimed.", exception);
                }
            }
        });
    }
}
