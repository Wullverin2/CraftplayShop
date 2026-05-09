package de.craftplay.shop.referral;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record RewardDefinition(
        double money,
        List<String> commands,
        List<ItemStack> items
) {
}
