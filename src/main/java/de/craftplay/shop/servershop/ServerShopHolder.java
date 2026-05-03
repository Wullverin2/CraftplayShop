package de.craftplay.shop.servershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class ServerShopHolder implements InventoryHolder {
    private final Map<Integer, String> categoriesBySlot;
    private Inventory inventory;

    public ServerShopHolder(Map<Integer, String> categoriesBySlot) {
        this.categoriesBySlot = categoriesBySlot;
    }

    public String categoryAt(int slot) {
        return categoriesBySlot.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
