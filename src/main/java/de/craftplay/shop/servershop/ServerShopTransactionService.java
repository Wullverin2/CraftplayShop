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
                batches.merge(sellable, content.getAmount(), Integer::sum);
            }
        }
        if (batches.isEmpty()) {
            return TransactionResult.failure("serverShop.sellAllNothing");
        }
        double total = batches.entrySet().stream().mapToDouble(entry -> entry.getKey().sellPrice() * entry.getValue()).sum();
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
            plugin.getTransactionService().logAsync(TransactionType.SERVER_SELL_ALL, player, "servershop", batch.getKey().createStack(batch.getValue()), batch.getValue(), batch.getKey().sellPrice(), batch.getKey().sellPrice() * batch.getValue());
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
