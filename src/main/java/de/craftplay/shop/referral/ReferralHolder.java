package de.craftplay.shop.referral;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class ReferralHolder implements InventoryHolder {
    private final Map<Integer, String> packageBySlot;
    private Inventory inventory;

    public ReferralHolder(Map<Integer, String> packageBySlot) {
        this.packageBySlot = packageBySlot;
    }

    public String packageIdAt(int slot) {
        return packageBySlot.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
