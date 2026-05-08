package de.craftplay.shop.playershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PlayerShopEditHolder implements InventoryHolder {
    private final long shopId;

    public PlayerShopEditHolder(long shopId) {
        this.shopId = shopId;
    }

    public long shopId() {
        return shopId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
