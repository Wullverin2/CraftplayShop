package de.craftplay.shop.autosell;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record AutoSellChest(long id,
                            UUID ownerUuid,
                            String ownerName,
                            String world,
                            int x,
                            int y,
                            int z,
                            String name,
                            boolean active,
                            boolean notifyOwner,
                            int intervalLevel,
                            int multiplierLevel,
                            double multiplier,
                            long totalItemsSold,
                            double totalMoneyEarned,
                            long lastSoldAt,
                            long createdAt,
                            long updatedAt) {
    public String locationKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public Location location() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
    }

    public AutoSellChest withActive(boolean value) {
        return new AutoSellChest(id, ownerUuid, ownerName, world, x, y, z, name, value, notifyOwner, intervalLevel, multiplierLevel, multiplier,
                totalItemsSold, totalMoneyEarned, lastSoldAt, createdAt, System.currentTimeMillis());
    }

    public AutoSellChest withOwner(UUID uuid, String playerName) {
        return new AutoSellChest(id, uuid, playerName, world, x, y, z, name, active, notifyOwner, intervalLevel, multiplierLevel, multiplier,
                totalItemsSold, totalMoneyEarned, lastSoldAt, createdAt, System.currentTimeMillis());
    }

    public AutoSellChest withName(String value) {
        return new AutoSellChest(id, ownerUuid, ownerName, world, x, y, z, value, active, notifyOwner, intervalLevel, multiplierLevel, multiplier,
                totalItemsSold, totalMoneyEarned, lastSoldAt, createdAt, System.currentTimeMillis());
    }

    public AutoSellChest withNotifyOwner(boolean value) {
        return new AutoSellChest(id, ownerUuid, ownerName, world, x, y, z, name, active, value, intervalLevel, multiplierLevel, multiplier,
                totalItemsSold, totalMoneyEarned, lastSoldAt, createdAt, System.currentTimeMillis());
    }

    public AutoSellChest withSale(long items, double money) {
        return new AutoSellChest(id, ownerUuid, ownerName, world, x, y, z, name, active, notifyOwner, intervalLevel, multiplierLevel, multiplier,
                totalItemsSold + Math.max(0L, items), totalMoneyEarned + Math.max(0.0D, money),
                System.currentTimeMillis(), createdAt, System.currentTimeMillis());
    }

    public AutoSellChest withIntervalLevel(int value) {
        return new AutoSellChest(id, ownerUuid, ownerName, world, x, y, z, name, active, notifyOwner, Math.max(0, value), multiplierLevel, multiplier,
                totalItemsSold, totalMoneyEarned, lastSoldAt, createdAt, System.currentTimeMillis());
    }

    public AutoSellChest withMultiplierLevel(int value, double multiplierValue) {
        return new AutoSellChest(id, ownerUuid, ownerName, world, x, y, z, name, active, notifyOwner, intervalLevel, Math.max(0, value), Math.max(0.0D, multiplierValue),
                totalItemsSold, totalMoneyEarned, lastSoldAt, createdAt, System.currentTimeMillis());
    }
}
