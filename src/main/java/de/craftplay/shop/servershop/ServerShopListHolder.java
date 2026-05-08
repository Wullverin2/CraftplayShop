package de.craftplay.shop.servershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class ServerShopListHolder implements InventoryHolder {
    private final ServerShopListView view;
    private final String query;
    private final Map<Integer, String> itemsBySlot;
    private Inventory inventory;

    public ServerShopListHolder(ServerShopListView view, String query, Map<Integer, String> itemsBySlot) {
        this.view = view;
        this.query = query;
        this.itemsBySlot = itemsBySlot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public ServerShopListView view() {
        return view;
    }

    public String query() {
        return query;
    }

    public String itemAt(int slot) {
        return itemsBySlot.get(slot);
    }
}
