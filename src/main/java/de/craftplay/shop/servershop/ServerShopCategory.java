package de.craftplay.shop.servershop;

import org.bukkit.Material;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerShopCategory {
    private final String id;
    private final String displayName;
    private final List<String> lore;
    private final Material icon;
    private final int slot;
    private final Map<String, ServerShopItem> items = new LinkedHashMap<>();

    public ServerShopCategory(String id, String displayName, List<String> lore, Material icon, int slot) {
        this.id = id;
        this.displayName = displayName;
        this.lore = lore;
        this.icon = icon;
        this.slot = slot;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public Material icon() {
        return icon;
    }

    public int slot() {
        return slot;
    }

    public void addItem(ServerShopItem item) {
        items.put(item.id(), item);
    }

    public ServerShopItem item(String id) {
        return items.get(id);
    }

    public Collection<ServerShopItem> items() {
        return items.values();
    }
}
