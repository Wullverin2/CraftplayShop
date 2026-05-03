package de.craftplay.shop.servershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class ServerShopCategoryHolder implements InventoryHolder {
    private final String categoryId;
    private final Map<Integer, String> itemsBySlot;
    private Inventory inventory;

    public ServerShopCategoryHolder(String categoryId, Map<Integer, String> itemsBySlot) {
        this.categoryId = categoryId;
        this.itemsBySlot = itemsBySlot;
    }

    public String categoryId() {
        return categoryId;
    }

    public String itemAt(int slot) {
        return itemsBySlot.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
