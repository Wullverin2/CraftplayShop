package de.craftplay.shop.permissionshop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record PermissionProduct(
        String id,
        boolean enabled,
        String displayName,
        List<String> lore,
        Material material,
        int slot,
        double price,
        List<String> permissions,
        List<String> commands,
        List<String> playerCommands,
        List<String> requiredPermissions,
        List<String> ownedPermissions,
        boolean oneTime,
        String duration
) {
    public ItemStack icon() {
        return new ItemStack(material == null ? Material.PAPER : material);
    }
}
