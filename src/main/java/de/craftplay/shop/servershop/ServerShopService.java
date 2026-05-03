package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ServerShopService {
    private final CraftplayShopPlugin plugin;

    public ServerShopService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("serverShop.enabled", true);
    }

    public boolean allowBuy() {
        return enabled() && plugin.getConfig().getBoolean("serverShop.allowBuy", true);
    }

    public boolean allowSell() {
        return enabled() && plugin.getConfig().getBoolean("serverShop.allowSell", true);
    }

    public boolean hasInventorySpace(Player player, ItemStack itemStack, int amount) {
        int remaining = amount;
        int maxStack = itemStack.getMaxStackSize();
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                remaining -= maxStack;
            } else if (content.isSimilar(itemStack)) {
                remaining -= Math.max(0, maxStack - content.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    public int countMatchingItems(Player player, ServerShopItem shopItem) {
        int amount = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content != null && plugin.getItemMatcher().matches(content, shopItem.createStack(1), plugin.getConfigService().itemMatchMode())) {
                amount += content.getAmount();
            }
        }
        return amount;
    }

    public void removeMatchingItems(Player player, ServerShopItem shopItem, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack content = contents[index];
            if (content == null || !plugin.getItemMatcher().matches(content, shopItem.createStack(1), plugin.getConfigService().itemMatchMode())) {
                continue;
            }
            int remove = Math.min(content.getAmount(), remaining);
            content.setAmount(content.getAmount() - remove);
            remaining -= remove;
            if (content.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
    }
}
