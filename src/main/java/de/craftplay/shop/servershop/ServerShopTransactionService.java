package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.permission.PermissionNodes;
import de.craftplay.shop.core.transaction.TransactionResult;
import de.craftplay.shop.core.transaction.TransactionType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class ServerShopTransactionService {
    private final CraftplayShopPlugin plugin;

    public ServerShopTransactionService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public TransactionResult buy(Player player, ServerShopItem item, int amount) {
        TransactionResult accessResult = validateAccess(player, false);
        if (!accessResult.success()) {
            return accessResult;
        }
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_BUY) || !plugin.getServerShopService().allowBuy()) {
            return TransactionResult.failure("serverShop.itemNotBuyable");
        }
        if (!item.buyEnabled() || item.buyPrice() < 0.0D) {
            return TransactionResult.failure("serverShop.itemNotBuyable");
        }
        if (plugin.getServerShopRegistry().availableStock(item) < amount) {
            return TransactionResult.failure("serverShop.notEnoughStock", Map.of(
                    "stock", Integer.toString(plugin.getServerShopRegistry().availableStock(item))
            ));
        }
        double unitPrice = plugin.getServerShopPricingService().buyUnitPrice(item);
        double total = plugin.getServerShopPricingService().buyTotal(item, amount);
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
        if (!plugin.getServerShopRegistry().decreaseStock(item, amount)) {
            plugin.getEconomyService().deposit(player, total);
            return TransactionResult.failure("serverShop.notEnoughStock", Map.of(
                    "stock", Integer.toString(plugin.getServerShopRegistry().availableStock(item))
            ));
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        if (!leftovers.isEmpty()) {
            plugin.getEconomyService().deposit(player, total);
            plugin.getServerShopRegistry().increaseStock(item, amount);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            return TransactionResult.failure("serverShop.inventoryFull");
        }
        plugin.getServerShopPricingService().record(TransactionType.SERVER_BUY, item, amount, unitPrice, total);
        plugin.getTransactionService().logAsync(TransactionType.SERVER_BUY, player, "servershop", stack, amount, unitPrice, total);
        return TransactionResult.success("serverShop.bought", total);
    }

    public TransactionResult sell(Player player, ServerShopItem item, int amount, TransactionType type) {
        TransactionResult accessResult = validateAccess(player, true);
        if (!accessResult.success()) {
            return accessResult;
        }
        if (!canSellInCurrentGameMode(player)) {
            return TransactionResult.failure("serverShop.creativeSellBlocked");
        }
        if (!player.hasPermission(PermissionNodes.SERVER_SHOP_SELL) || !plugin.getServerShopService().allowSell()) {
            return TransactionResult.failure("serverShop.itemNotSellable");
        }
        if (!item.sellEnabled() || item.sellPrice() < 0.0D) {
            return TransactionResult.failure("serverShop.itemNotSellable");
        }
        if (plugin.getServerShopService().countMatchingItems(player, item) < amount) {
            return TransactionResult.failure("serverShop.notEnoughItems");
        }
        if (plugin.getServerShopRegistry().availableStockCapacity(item) < amount) {
            return TransactionResult.failure("serverShop.stockFull", Map.of(
                    "space", Integer.toString(plugin.getServerShopRegistry().availableStockCapacity(item))
            ));
        }
        double unitPrice = plugin.getServerShopPricingService().sellUnitPrice(item);
        double total = plugin.getServerShopPricingService().sellTotal(item, amount);
        plugin.getServerShopService().removeMatchingItems(player, item, amount);
        if (!plugin.getEconomyService().deposit(player, total)) {
            player.getInventory().addItem(item.createStack(amount));
            return TransactionResult.failure("general.databaseError");
        }
        if (!plugin.getServerShopRegistry().increaseStock(item, amount)) {
            plugin.getEconomyService().withdraw(player, total);
            player.getInventory().addItem(item.createStack(amount));
            return TransactionResult.failure("serverShop.stockFull", Map.of(
                    "space", Integer.toString(plugin.getServerShopRegistry().availableStockCapacity(item))
            ));
        }
        plugin.getServerShopPricingService().record(type, item, amount, unitPrice, total);
        plugin.getTransactionService().logAsync(type, player, "servershop", item.createStack(amount), amount, unitPrice, total);
        return TransactionResult.success("serverShop.sold", total);
    }

    public TransactionResult sellAll(Player player) {
        TransactionResult accessResult = validateAccess(player, true);
        if (!accessResult.success()) {
            return accessResult;
        }
        if (!canSellInCurrentGameMode(player)) {
            return TransactionResult.failure("serverShop.creativeSellBlocked");
        }
        Map<ServerShopItem, Integer> batches = new java.util.LinkedHashMap<>();
        for (ItemStack content : player.getInventory().getStorageContents()) {
            ServerShopItem sellable = plugin.getServerShopRegistry().findSellable(content);
            if (sellable != null) {
                int capacity = plugin.getServerShopRegistry().availableStockCapacity(sellable);
                if (capacity > 0) {
                    int current = batches.getOrDefault(sellable, 0);
                    int accepted = Math.min(content.getAmount(), Math.max(0, capacity - current));
                    if (accepted > 0) {
                        batches.merge(sellable, accepted, Integer::sum);
                    }
                }
            }
        }
        if (batches.isEmpty()) {
            return TransactionResult.failure("serverShop.sellAllNothing");
        }
        double total = batches.entrySet().stream().mapToDouble(entry -> plugin.getServerShopPricingService().sellTotal(entry.getKey(), entry.getValue())).sum();
        for (Map.Entry<ServerShopItem, Integer> batch : batches.entrySet()) {
            plugin.getServerShopService().removeMatchingItems(player, batch.getKey(), batch.getValue());
        }
        if (!plugin.getEconomyService().deposit(player, total)) {
            for (Map.Entry<ServerShopItem, Integer> batch : batches.entrySet()) {
                player.getInventory().addItem(batch.getKey().createStack(batch.getValue()));
            }
            return TransactionResult.failure("general.databaseError");
        }
        for (Map.Entry<ServerShopItem, Integer> batch : batches.entrySet()) {
            if (!plugin.getServerShopRegistry().increaseStock(batch.getKey(), batch.getValue())) {
                continue;
            }
            double unit = plugin.getServerShopPricingService().sellUnitPrice(batch.getKey());
            double lineTotal = plugin.getServerShopPricingService().sellTotal(batch.getKey(), batch.getValue());
            plugin.getServerShopPricingService().record(TransactionType.SERVER_SELL_ALL, batch.getKey(), batch.getValue(), unit, lineTotal);
            plugin.getTransactionService().logAsync(TransactionType.SERVER_SELL_ALL, player, "servershop", batch.getKey().createStack(batch.getValue()), batch.getValue(), unit, lineTotal);
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

    private TransactionResult validateAccess(Player player, boolean sell) {
        if (!plugin.getServerShopService().canUseInWorld(player)) {
            return TransactionResult.failure("serverShop.worldBlocked");
        }
        if (!plugin.getServerShopService().canUseInGameMode(player, sell)) {
            return TransactionResult.failure("serverShop.gameModeBlocked");
        }
        return TransactionResult.success("", 0.0D);
    }

}
