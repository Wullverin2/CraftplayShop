package de.craftplay.shop.servershop.admin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class ServerShopAdminHolder implements InventoryHolder {
    private final ServerShopAdminView view;
    private final String categoryId;
    private final String itemId;
    private final Map<Integer, String> keysBySlot;
    private final int page;
    private Inventory inventory;

    public ServerShopAdminHolder(ServerShopAdminView view, String categoryId, String itemId, Map<Integer, String> keysBySlot) {
        this(view, categoryId, itemId, keysBySlot, 0);
    }

    public ServerShopAdminHolder(ServerShopAdminView view, String categoryId, String itemId, Map<Integer, String> keysBySlot, int page) {
        this.view = view;
        this.categoryId = categoryId;
        this.itemId = itemId;
        this.keysBySlot = keysBySlot;
        this.page = page;
    }

    public ServerShopAdminView view() {
        return view;
    }

    public String categoryId() {
        return categoryId;
    }

    public String itemId() {
        return itemId;
    }

    public String keyAt(int slot) {
        return keysBySlot.get(slot);
    }

    public int page() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
