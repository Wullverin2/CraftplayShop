package de.craftplay.shop.playershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class PlayerShopMenuHolder implements InventoryHolder {
    private final PlayerShopMenuView view;
    private final Map<Integer, PlayerShopMenuAction> actions = new HashMap<>();
    private final Map<Integer, Long> shops = new HashMap<>();

    public PlayerShopMenuHolder(PlayerShopMenuView view) {
        this.view = view;
    }

    public PlayerShopMenuView view() {
        return view;
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
