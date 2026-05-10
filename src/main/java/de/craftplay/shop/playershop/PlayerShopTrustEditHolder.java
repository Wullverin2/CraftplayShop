package de.craftplay.shop.playershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class PlayerShopTrustEditHolder implements InventoryHolder {
    private final long shopId;
    private final UUID playerUuid;

    public PlayerShopTrustEditHolder(long shopId, UUID playerUuid) {
        this.shopId = shopId;
        this.playerUuid = playerUuid;
    }

    public long shopId() {
        return shopId;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
