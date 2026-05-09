package de.craftplay.shop.referral;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record ReferralRewardPackage(
        String id,
        boolean enabled,
        String displayName,
        Material material,
        int slot,
        List<String> lore,
        RewardDefinition referrerReward,
        RewardDefinition redeemerReward
) {
    public ItemStack icon() {
        return new ItemStack(material == null ? Material.CHEST : material);
    }
}
