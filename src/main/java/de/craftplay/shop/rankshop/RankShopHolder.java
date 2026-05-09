package de.craftplay.shop.rankshop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class RankShopHolder implements InventoryHolder {
    private final Map<Integer, String> productsBySlot;
    private Inventory inventory;

    public RankShopHolder(Map<Integer, String> productsBySlot) {
        this.productsBySlot = productsBySlot;
    }

    public String productIdAt(int slot) {
        return productsBySlot.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
