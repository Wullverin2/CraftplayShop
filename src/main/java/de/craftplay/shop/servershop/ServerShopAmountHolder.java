package de.craftplay.shop.servershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class ServerShopAmountHolder implements InventoryHolder {
    private final View view;
    private final String categoryId;
    private final String itemId;
    private final ServerShopAction action;
    private final int amount;
    private final Map<Integer, String> keysBySlot;
    private Inventory inventory;

    public ServerShopAmountHolder(View view, String categoryId, String itemId, ServerShopAction action, int amount, Map<Integer, String> keysBySlot) {
        this.view = view;
        this.categoryId = categoryId;
        this.itemId = itemId;
        this.action = action;
        this.amount = amount;
        this.keysBySlot = keysBySlot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public View view() {
        return view;
    }

    public String categoryId() {
        return categoryId;
    }

    public String itemId() {
        return itemId;
    }

    public ServerShopAction action() {
        return action;
    }

    public int amount() {
        return amount;
    }

    public String keyAt(int slot) {
        return keysBySlot.get(slot);
    }

    public enum View {
        AMOUNT_SELECTION,
        BUY_CONFIRMATION
    }
}
