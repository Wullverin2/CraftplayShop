package de.craftplay.shop.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class GuiHolder implements InventoryHolder {
    private final String guiId;
    private final Map<Integer, GuiItemDefinition> items;
    private Inventory inventory;

    public GuiHolder(String guiId, Map<Integer, GuiItemDefinition> items) {
        this.guiId = guiId;
        this.items = items;
    }

    public String guiId() {
        return guiId;
    }

    public Map<Integer, GuiItemDefinition> items() {
        return items;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
