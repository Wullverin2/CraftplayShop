package de.craftplay.shop.playershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class PlayerShopTrustListHolder implements InventoryHolder {
    private final long shopId;
    private final Map<Integer, java.util.UUID> entries = new HashMap<>();

    public PlayerShopTrustListHolder(long shopId) {
        this.shopId = shopId;
    }

    public long shopId() {
        return shopId;
    }

    public Map<Integer, java.util.UUID> entries() {
        return entries;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
