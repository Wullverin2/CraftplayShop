package de.craftplay.shop.servershop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record ServerShopItem(String id, Material material, String displayName, List<String> lore, double buyPrice,
                             double sellPrice, boolean buyEnabled, boolean sellEnabled, int slot,
                             int minBuyAmount, int maxBuyAmount, int minSellAmount, int maxSellAmount) {
    public ItemStack createStack(int amount) {
        return new ItemStack(material, amount);
    }

    public boolean hasBuyMaximum() {
        return maxBuyAmount > 0;
    }

    public boolean hasSellMaximum() {
        return maxSellAmount > 0;
    }
}
