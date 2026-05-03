package de.craftplay.shop.core.transaction;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class TransactionRollbackService {
    public void giveItemsOrDrop(Player player, ItemStack itemStack) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}
