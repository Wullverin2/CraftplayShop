package de.craftplay.shop.auctionhouse;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class AuctionHouseHolder implements InventoryHolder {
    private final AuctionHouseView view;
    private final int page;
    private final String query;
    private final Map<Integer, Long> listingsBySlot;
    private Inventory inventory;

    public AuctionHouseHolder(AuctionHouseView view, int page, String query, Map<Integer, Long> listingsBySlot) {
        this.view = view;
        this.page = page;
        this.query = query;
        this.listingsBySlot = listingsBySlot;
    }

    public AuctionHouseView view() {
        return view;
    }

    public int page() {
        return page;
    }

    public String query() {
        return query;
    }

    public Long listingAt(int slot) {
        return listingsBySlot.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
