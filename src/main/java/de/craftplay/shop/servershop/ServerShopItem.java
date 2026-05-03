package de.craftplay.shop.servershop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record ServerShopItem(String id, Material material, String displayName, List<String> lore, double buyPrice,
                             double sellPrice, boolean buyEnabled, boolean sellEnabled, int slot) {
    public ItemStack createStack(int amount) {
        return new ItemStack(material, amount);
    }
}
