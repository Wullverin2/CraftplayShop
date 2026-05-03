package de.craftplay.shop.servershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Set;

public class SellGuiHolder implements InventoryHolder {
    private final Set<Integer> itemSlots;
    private final int confirmSlot;
    private final int backSlot;
    private boolean handled;
    private Inventory inventory;

    public SellGuiHolder(Set<Integer> itemSlots, int confirmSlot, int backSlot) {
        this.itemSlots = itemSlots;
        this.confirmSlot = confirmSlot;
        this.backSlot = backSlot;
    }

    public boolean itemSlot(int slot) {
        return itemSlots.contains(slot);
    }

    public Set<Integer> itemSlots() {
        return itemSlots;
    }

    public int confirmSlot() {
        return confirmSlot;
    }

    public int backSlot() {
        return backSlot;
    }

    public boolean handled() {
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
