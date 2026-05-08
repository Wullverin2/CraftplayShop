package de.craftplay.shop.playershop;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record PlayerShop(
        long id,
        UUID ownerUuid,
        String ownerName,
        PlayerShopType type,
        String world,
        int containerX,
        int containerY,
        int containerZ,
        int signX,
        int signY,
        int signZ,
        ItemStack itemStack,
        String material,
        int amount,
        double price,
        boolean active,
        long createdAt,
        long updatedAt
) {
    public boolean owns(Location location) {
        return isContainer(location) || isSign(location);
    }

    public boolean isContainer(Location location) {
        return matches(location, containerX, containerY, containerZ);
    }

    public boolean isSign(Location location) {
        return matches(location, signX, signY, signZ);
    }

    public Location containerLocation() {
        return new Location(org.bukkit.Bukkit.getWorld(world), containerX, containerY, containerZ);
    }

    private boolean matches(Location location, int x, int y, int z) {
        return location != null
                && location.getWorld() != null
                && location.getWorld().getName().equals(world)
                && location.getBlockX() == x
                && location.getBlockY() == y
                && location.getBlockZ() == z;
    }
}
