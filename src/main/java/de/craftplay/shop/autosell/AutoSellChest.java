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
        return new AutoSellChest(id, ownerUuid, ownerName, world, x, y, z, name, value, notifyOwner, multiplier,
                totalItemsSold, totalMoneyEarned, lastSoldAt, createdAt, System.currentTimeMillis());
    }

    public AutoSellChest withNotifyOwner(boolean value) {
        return new AutoSellChest(id, ownerUuid, ownerName, world, x, y, z, name, active, value, multiplier,
                totalItemsSold, totalMoneyEarned, lastSoldAt, createdAt, System.currentTimeMillis());
    }

    public AutoSellChest withSale(long items, double money) {
        return new AutoSellChest(id, ownerUuid, ownerName, world, x, y, z, name, active, notifyOwner, multiplier,
                totalItemsSold + Math.max(0L, items), totalMoneyEarned + Math.max(0.0D, money),
                System.currentTimeMillis(), createdAt, System.currentTimeMillis());
    }
}
