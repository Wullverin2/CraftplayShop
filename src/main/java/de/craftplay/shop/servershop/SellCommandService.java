package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.transaction.TransactionResult;
import de.craftplay.shop.core.transaction.TransactionType;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class SellCommandService {
    private final CraftplayShopPlugin plugin;

    public SellCommandService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        ServerShopItem item = plugin.getServerShopRegistry().findSellable(hand);
        if (item == null) {
            plugin.getLanguageService().send(player, "serverShop.sellHandNoItem");
            return;
        }
        int amount = hand.getAmount();
        TransactionResult result = plugin.getServerShopTransactionService().sell(player, item, amount, TransactionType.SERVER_SELL);
        send(player, item, amount, result);
    }

    public void sellAll(Player player) {
        TransactionResult result = plugin.getServerShopTransactionService().sellAll(player);
        if (!result.success()) {
            plugin.getLanguageService().send(player, result.messageKey());
            return;
        }
        plugin.getLanguageService().send(player, result.messageKey(), Map.of("price", plugin.getEconomyService().format(result.totalPrice())));
    }

    private void send(Player player, ServerShopItem item, int amount, TransactionResult result) {
        if (!result.success()) {
            plugin.getLanguageService().send(player, result.messageKey());
            return;
        }
        plugin.getLanguageService().send(player, result.messageKey(), Map.of(
                "amount", Integer.toString(amount),
                "item", TextUtil.color(item.displayName()),
                "price", plugin.getEconomyService().format(result.totalPrice())
        ));
    }
}
