package de.craftplay.shop.rankshop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record RankProduct(
        String id,
        boolean enabled,
        String displayName,
        List<String> lore,
        Material material,
        int slot,
        double price,
        String group,
        String duration,
        List<String> commands,
        List<String> playerCommands,
        List<String> requiredPermissions,
        List<String> ownedPermissions,
        List<String> removeGroups,
        boolean oneTime
) {
    public ItemStack icon() {
        return new ItemStack(material == null ? Material.NAME_TAG : material);
    }
}
