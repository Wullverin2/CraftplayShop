package de.craftplay.shop.playershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PlayerShopDeleteHolder implements InventoryHolder {
    private final long shopId;
    private final PlayerShopMenuView returnView;
    private final String query;
    private final int page;

    public PlayerShopDeleteHolder(long shopId, PlayerShopMenuView returnView, String query, int page) {
        this.shopId = shopId;
        this.returnView = returnView;
        this.query = query == null ? "" : query;
        this.page = Math.max(0, page);
    }

    public long shopId() {
        return shopId;
    }

    public PlayerShopMenuView returnView() {
        return returnView;
    }

    public String query() {
        return query;
    }

    public int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
