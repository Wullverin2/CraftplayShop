package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.core.transaction.TransactionResult;
import de.craftplay.shop.core.transaction.TransactionType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ServerShopTransactionService {
    private final CraftplayShopPlugin plugin;

    public ServerShopTransactionService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public TransactionResult buy(Player player, ServerShopItem item, int amount) {
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_BUY) || !plugin.getServerShopService().allowBuy()) {
            return TransactionResult.failure("serverShop.itemNotBuyable");
        }
        if (!item.buyEnabled() || item.buyPrice() < 0.0D) {
            return TransactionResult.failure("serverShop.itemNotBuyable");
        }
        TransactionResult limitResult = validateBuyAmount(item, amount);
        if (!limitResult.success()) {
            return limitResult;
        }
        double total = item.buyPrice() * amount;
        ItemStack stack = item.createStack(amount);
        if (!plugin.getEconomyService().has(player, total)) {
            return TransactionResult.failure("serverShop.notEnoughMoney");
        }
        if (!plugin.getServerShopService().hasInventorySpace(player, stack, amount)) {
            return TransactionResult.failure("serverShop.inventoryFull");
        }
        if (!plugin.getEconomyService().withdraw(player, total)) {
            return TransactionResult.failure("serverShop.notEnoughMoney");
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        if (!leftovers.isEmpty()) {
            plugin.getEconomyService().deposit(player, total);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            return TransactionResult.failure("serverShop.inventoryFull");
        }
        plugin.getTransactionService().logAsync(TransactionType.SERVER_BUY, player, "servershop", stack, amount, item.buyPrice(), total);
        return TransactionResult.success("serverShop.bought", total);
    }

    public TransactionResult sell(Player player, ServerShopItem item, int amount, TransactionType type) {
        if (!canSellInCurrentGameMode(player)) {
            return TransactionResult.failure("serverShop.creativeSellBlocked");
        }
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_SELL) || !plugin.getServerShopService().allowSell()) {
            return TransactionResult.failure("serverShop.itemNotSellable");
        }
        if (!item.sellEnabled() || item.sellPrice() < 0.0D) {
            return TransactionResult.failure("serverShop.itemNotSellable");
        }
        TransactionResult limitResult = validateSellAmount(item, amount);
        if (!limitResult.success()) {
            return limitResult;
        }
        if (plugin.getServerShopService().countMatchingItems(player, item) < amount) {
            return TransactionResult.failure("serverShop.notEnoughItems");
        }
        double total = item.sellPrice() * amount;
        plugin.getServerShopService().removeMatchingItems(player, item, amount);
        if (!plugin.getEconomyService().deposit(player, total)) {
            player.getInventory().addItem(item.createStack(amount));
            return TransactionResult.failure("general.databaseError");
        }
        plugin.getTransactionService().logAsync(type, player, "servershop", item.createStack(amount), amount, item.sellPrice(), total);
        return TransactionResult.success("serverShop.sold", total);
    }

    public TransactionResult sellAll(Player player) {
        if (!canSellInCurrentGameMode(player)) {
            return TransactionResult.failure("serverShop.creativeSellBlocked");
        }
        Map<ServerShopItem, Integer> amounts = new java.util.LinkedHashMap<>();
        for (ItemStack content : player.getInventory().getStorageContents()) {
            ServerShopItem sellable = plugin.getServerShopRegistry().findSellable(content);
            if (sellable != null) {
                amounts.merge(sellable, content.getAmount(), Integer::sum);
            }
        }
        List<SellBatch> batches = new ArrayList<>();
        for (Map.Entry<ServerShopItem, Integer> entry : amounts.entrySet()) {
            int amount = limitSellAllAmount(entry.getKey(), entry.getValue());
            if (amount >= entry.getKey().minSellAmount()) {
                batches.add(new SellBatch(entry.getKey(), amount));
            }
        }
        if (batches.isEmpty()) {
            return TransactionResult.failure("serverShop.sellAllNothing");
        }
        double total = batches.stream().mapToDouble(batch -> batch.item().sellPrice() * batch.amount()).sum();
        for (SellBatch batch : batches) {
            plugin.getServerShopService().removeMatchingItems(player, batch.item(), batch.amount());
        }
        if (!plugin.getEconomyService().deposit(player, total)) {
            for (SellBatch batch : batches) {
                player.getInventory().addItem(batch.item().createStack(batch.amount()));
            }
            return TransactionResult.failure("general.databaseError");
        }
        for (SellBatch batch : batches) {
            plugin.getTransactionService().logAsync(TransactionType.SERVER_SELL_ALL, player, "servershop", batch.item().createStack(batch.amount()), batch.amount(), batch.item().sellPrice(), batch.item().sellPrice() * batch.amount());
        }
        return TransactionResult.success("serverShop.sellAllDone", total);
    }

    public boolean canSellInCurrentGameMode(Player player) {
        GameMode gameMode = player.getGameMode();
        if (gameMode == GameMode.CREATIVE && plugin.getConfig().getBoolean("serverShop.sellProtection.blockCreative", true)) {
            return false;
        }
        return gameMode != GameMode.SPECTATOR || !plugin.getConfig().getBoolean("serverShop.sellProtection.blockSpectator", true);
    }

    private TransactionResult validateBuyAmount(ServerShopItem item, int amount) {
        if (amount < item.minBuyAmount()) {
            return TransactionResult.failure("serverShop.buyAmountTooLow", Map.of("limit", Integer.toString(item.minBuyAmount())));
        }
        if (item.hasBuyMaximum() && amount > item.maxBuyAmount()) {
            return TransactionResult.failure("serverShop.buyAmountTooHigh", Map.of("limit", Integer.toString(item.maxBuyAmount())));
        }
        return TransactionResult.success("", 0.0D);
    }

    public TransactionResult validateSellAmount(ServerShopItem item, int amount) {
        if (amount < item.minSellAmount()) {
            return TransactionResult.failure("serverShop.sellAmountTooLow", Map.of("limit", Integer.toString(item.minSellAmount())));
        }
        if (item.hasSellMaximum() && amount > item.maxSellAmount()) {
            return TransactionResult.failure("serverShop.sellAmountTooHigh", Map.of("limit", Integer.toString(item.maxSellAmount())));
        }
        return TransactionResult.success("", 0.0D);
    }

    private int limitSellAllAmount(ServerShopItem item, int amount) {
        if (!item.hasSellMaximum()) {
            return amount;
        }
        return Math.min(amount, item.maxSellAmount());
    }

    private record SellBatch(ServerShopItem item, int amount) {
    }
}
