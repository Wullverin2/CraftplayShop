package de.craftplay.shop.core.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemMatcher {
    public boolean matches(ItemStack offered, ItemStack expected, ItemMatchMode mode) {
        if (offered == null || expected == null || offered.getType().isAir() || expected.getType().isAir()) {
            return false;
        }
        if (offered.getType() != expected.getType()) {
            return false;
        }
        return switch (mode) {
            case MATERIAL_ONLY -> true;
            case EXACT -> offered.isSimilar(expected);
            case IGNORE_LORE -> matchesIgnoringLore(offered, expected);
            case IGNORE_DURABILITY -> matchesIgnoringDurability(offered, expected);
        };
    }

    private boolean matchesIgnoringLore(ItemStack offered, ItemStack expected) {
        ItemStack offeredClone = offered.clone();
        ItemStack expectedClone = expected.clone();
        ItemMeta offeredMeta = offeredClone.getItemMeta();
        ItemMeta expectedMeta = expectedClone.getItemMeta();
        if (offeredMeta != null) {
            offeredMeta.setLore(null);
            offeredClone.setItemMeta(offeredMeta);
        }
        if (expectedMeta != null) {
            expectedMeta.setLore(null);
            expectedClone.setItemMeta(expectedMeta);
        }
        return offeredClone.isSimilar(expectedClone);
    }

    private boolean matchesIgnoringDurability(ItemStack offered, ItemStack expected) {
        ItemStack offeredClone = offered.clone();
        ItemStack expectedClone = expected.clone();
        ItemMeta offeredMeta = offeredClone.getItemMeta();
        ItemMeta expectedMeta = expectedClone.getItemMeta();
        if (offeredMeta instanceof Damageable damageable) {
            damageable.setDamage(0);
            offeredClone.setItemMeta(offeredMeta);
        }
        if (expectedMeta instanceof Damageable damageable) {
            damageable.setDamage(0);
            expectedClone.setItemMeta(expectedMeta);
        }
        return offeredClone.isSimilar(expectedClone);
    }
}
