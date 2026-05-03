package de.craftplay.shop.core.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record ShopItemData(String id, Material material, String displayName, double buyPrice, double sellPrice,
                           boolean buyEnabled, boolean sellEnabled, int slot, ItemStack itemStack) {
}
