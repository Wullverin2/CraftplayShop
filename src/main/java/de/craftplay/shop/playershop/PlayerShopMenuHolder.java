package de.craftplay.shop.playershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class PlayerShopMenuHolder implements InventoryHolder {
    private final PlayerShopMenuView view;
    private final String query;
    private final int page;
    private final int totalPages;
    private final Map<Integer, PlayerShopMenuAction> actions = new HashMap<>();
    private final Map<Integer, Long> shops = new HashMap<>();

    public PlayerShopMenuHolder(PlayerShopMenuView view) {
        this(view, "", 0, 1);
    }

    public PlayerShopMenuHolder(PlayerShopMenuView view, String query, int page, int totalPages) {
        this.view = view;
        this.query = query == null ? "" : query;
        this.page = Math.max(0, page);
        this.totalPages = Math.max(1, totalPages);
    }

    public PlayerShopMenuView view() {
        return view;
    }

    public String query() {
        return query;
    }

    public int page() {
        return page;
    }

    public int totalPages() {
        return totalPages;
    }

    public Map<Integer, PlayerShopMenuAction> actions() {
        return actions;
    }

    public Map<Integer, Long> shops() {
        return shops;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
