package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.transaction.TransactionResult;
import de.craftplay.shop.core.transaction.TransactionType;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerShopCategoryGui {
    private final CraftplayShopPlugin plugin;
    private final Map<UUID, PendingAmountInput> amountInputs = new ConcurrentHashMap<>();

    public ServerShopCategoryGui(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String categoryId) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        if (category == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return;
        }
        if (!category.enabled()) {
            plugin.getLanguageService().send(player, "general.featureNotAvailable");
            return;
        }
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        YamlConfiguration gui = loadGui(language, "servershop_category.yml");
        Map<Integer, String> itemsBySlot = new HashMap<>();
        ServerShopCategoryHolder holder = new ServerShopCategoryHolder(category.id(), itemsBySlot);
        String title = gui.getString("title", "&8%category%").replace("%category%", TextUtil.color(category.displayName()));
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt("size", 54)), TextUtil.color(title));
        holder.setInventory(inventory);
        fill(inventory, gui);
        addConfiguredButtons(player, inventory, gui);
        for (ServerShopItem item : category.items()) {
            inventory.setItem(item.slot(), buildShopItem(player, item, gui));
            itemsBySlot.put(item.slot(), item.id());
        }
        player.openInventory(inventory);
    }

    public void handleClick(Player player, ServerShopCategoryHolder holder, InventoryClickEvent event) {
        String itemId = holder.itemAt(event.getRawSlot());
        if (itemId == null) {
            YamlConfiguration gui = loadGui(plugin.getPlayerLanguageService().getLanguage(player), "servershop_category.yml");
            handleConfiguredButton(player, gui, event.getRawSlot(), event.isRightClick());
            return;
        }
        ServerShopCategory category = plugin.getServerShopRegistry().category(holder.categoryId());
        if (category == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return;
        }
        ServerShopItem item = category.item(itemId);
        if (item == null) {
            plugin.getLanguageService().send(player, "gui.missingItem", Map.of("item", itemId));
            return;
        }
        executeClassicTransaction(player, item, event.isRightClick(), event.isShiftClick());
        open(player, holder.categoryId());
    }

    public void handleAmountClick(Player player, ServerShopAmountHolder holder, InventoryClickEvent event) {
        String key = holder.keyAt(event.getRawSlot());
        if (key == null) {
            return;
        }
        if ("back".equals(key)) {
            open(player, holder.categoryId());
            return;
        }
        if ("custom_amount".equals(key)) {
            amountInputs.put(player.getUniqueId(), new PendingAmountInput(holder.categoryId(), holder.itemId(), holder.action()));
            player.closeInventory();
            plugin.getLanguageService().send(player, "serverShop.amountInput");
            return;
        }
        if ("confirm_buy".equals(key)) {
            executeSelectedAmount(player, holder.categoryId(), holder.itemId(), holder.action(), holder.amount(), false);
            return;
        }
        if ("cancel_buy".equals(key)) {
            openAmountSelection(player, holder.categoryId(), holder.itemId(), holder.action());
            return;
        }
        if (key.startsWith("amount:")) {
            int amount = parseAmount(key.substring("amount:".length()), 1);
            executeSelectedAmount(player, holder.categoryId(), holder.itemId(), holder.action(), amount, true);
        }
    }

    public boolean hasAmountInput(Player player) {
        return amountInputs.containsKey(player.getUniqueId());
    }

    public void handleAmountInput(Player player, String message) {
        PendingAmountInput pending = amountInputs.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if ("cancel".equalsIgnoreCase(message)) {
            plugin.getLanguageService().send(player, "serverShop.amountInputCancelled");
            openAmountSelection(player, pending.categoryId(), pending.itemId(), pending.action());
            return;
        }
        int amount = parseAmount(message, -1);
        int max = Math.max(1, plugin.getConfig().getInt("serverShop.amountSelection.maxCustomAmount", 64));
        if (amount <= 0) {
            plugin.getLanguageService().send(player, "serverShop.invalidAmount");
            openAmountSelection(player, pending.categoryId(), pending.itemId(), pending.action());
            return;
        }
        if (amount > max) {
            plugin.getLanguageService().send(player, "serverShop.amountTooHigh", Map.of("max", Integer.toString(max)));
            openAmountSelection(player, pending.categoryId(), pending.itemId(), pending.action());
            return;
        }
        executeSelectedAmount(player, pending.categoryId(), pending.itemId(), pending.action(), amount, true);
    }

    public void openAmountSelection(Player player, String categoryId, String itemId, ServerShopAction action) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        ServerShopItem item = category == null ? null : category.item(itemId);
        if (category == null || item == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return;
        }
        YamlConfiguration gui = loadGui(plugin.getPlayerLanguageService().getLanguage(player), "servershop_category.yml");
        Map<Integer, String> keysBySlot = new HashMap<>();
        ServerShopAmountHolder holder = new ServerShopAmountHolder(ServerShopAmountHolder.View.AMOUNT_SELECTION, categoryId, itemId, action, 0, keysBySlot);
        Map<String, String> placeholders = itemPlaceholders(player, item);
        placeholders.put("action", actionName(player, action));
        placeholders.put("price", plugin.getEconomyService().format(action == ServerShopAction.BUY
                ? plugin.getServerShopPricingService().buyUnitPrice(item)
                : plugin.getServerShopPricingService().sellUnitPrice(item)));
        String title = parse(player, gui.getString("amountSelection.title", "&8%item%"), placeholders);
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt("amountSelection.size", 54)), TextUtil.color(title));
        holder.setInventory(inventory);
        fill(inventory, gui);

        List<Integer> slots = gui.getIntegerList("amountSelection.slots");
        List<Integer> amounts = configuredAmounts(gui);
        for (int index = 0; index < amounts.size() && index < slots.size(); index++) {
            int slot = slots.get(index);
            int amount = amounts.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            keysBySlot.put(slot, "amount:" + amount);
            inventory.setItem(slot, amountButton(player, gui, item, action, amount));
        }

        if (plugin.getConfig().getBoolean("serverShop.amountSelection.allowCustomAmount", true)) {
            int customSlot = gui.getInt("slots.amountSelection.custom", 31);
            if (customSlot >= 0 && customSlot < inventory.getSize()) {
                keysBySlot.put(customSlot, "custom_amount");
                inventory.setItem(customSlot, configuredItem(player, gui.getConfigurationSection("items.customAmount"), placeholders));
            }
        }
        int backSlot = gui.getInt("slots.amountSelection.back", 49);
        if (backSlot >= 0 && backSlot < inventory.getSize()) {
            keysBySlot.put(backSlot, "back");
            inventory.setItem(backSlot, configuredItem(player, gui.getConfigurationSection("items.amountBack"), placeholders));
        }
        player.openInventory(inventory);
    }

    public void executeClassicTransaction(Player player, ServerShopItem item, boolean rightClick, boolean shiftClick) {
        int amount = shiftClick ? Math.max(1, item.material().getMaxStackSize()) : 1;
        TransactionResult result = rightClick
                ? plugin.getServerShopTransactionService().sell(player, item, amount, TransactionType.SERVER_SELL)
                : plugin.getServerShopTransactionService().buy(player, item, amount);
        sendTransactionMessage(player, item, amount, result);
    }

    private void openBuyConfirmation(Player player, String categoryId, ServerShopItem item, int amount) {
        YamlConfiguration gui = loadGui(plugin.getPlayerLanguageService().getLanguage(player), "servershop_category.yml");
        Map<Integer, String> keysBySlot = new HashMap<>();
        ServerShopAmountHolder holder = new ServerShopAmountHolder(ServerShopAmountHolder.View.BUY_CONFIRMATION, categoryId, item.id(), ServerShopAction.BUY, amount, keysBySlot);
        Map<String, String> placeholders = itemPlaceholders(player, item);
        placeholders.put("action", actionName(player, ServerShopAction.BUY));
        placeholders.put("amount", Integer.toString(amount));
        placeholders.put("price", plugin.getEconomyService().format(plugin.getServerShopPricingService().buyTotal(item, amount)));
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt("buyConfirmation.size", 54)), TextUtil.color(parse(player, gui.getString("buyConfirmation.title", "&8Confirm"), placeholders)));
        holder.setInventory(inventory);
        fill(inventory, gui);

        int previewSlot = gui.getInt("slots.buyConfirmation.preview", 13);
        int confirmSlot = gui.getInt("slots.buyConfirmation.confirm", 30);
        int cancelSlot = gui.getInt("slots.buyConfirmation.cancel", 32);
        if (previewSlot >= 0 && previewSlot < inventory.getSize()) {
            inventory.setItem(previewSlot, buildShopItem(player, item, gui));
        }
        if (confirmSlot >= 0 && confirmSlot < inventory.getSize()) {
            keysBySlot.put(confirmSlot, "confirm_buy");
            inventory.setItem(confirmSlot, configuredItem(player, gui.getConfigurationSection("items.confirmBuy"), placeholders));
        }
        if (cancelSlot >= 0 && cancelSlot < inventory.getSize()) {
            keysBySlot.put(cancelSlot, "cancel_buy");
            inventory.setItem(cancelSlot, configuredItem(player, gui.getConfigurationSection("items.cancelBuy"), placeholders));
        }
        player.openInventory(inventory);
    }

    private void executeSelectedAmount(Player player, String categoryId, String itemId, ServerShopAction action, int amount, boolean allowConfirmation) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        ServerShopItem item = category == null ? null : category.item(itemId);
        if (category == null || item == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
            return;
        }
        int safeAmount = Math.max(1, amount);
        if (allowConfirmation && action == ServerShopAction.BUY && requiresBuyConfirmation(item, safeAmount)) {
            openBuyConfirmation(player, categoryId, item, safeAmount);
            return;
        }
        TransactionResult result = action == ServerShopAction.SELL
                ? plugin.getServerShopTransactionService().sell(player, item, safeAmount, TransactionType.SERVER_SELL)
                : plugin.getServerShopTransactionService().buy(player, item, safeAmount);
        sendTransactionMessage(player, item, safeAmount, result);
        open(player, categoryId);
    }

    private ItemStack buildShopItem(Player player, ServerShopItem item, YamlConfiguration gui) {
        ItemStack itemStack = new ItemStack(item.material());
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = itemPlaceholders(player, item);
            meta.setDisplayName(TextUtil.color(parse(player, item.displayName(), placeholders)));
            meta.setLore(shopItemLore(player, gui, item, placeholders));
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private ItemStack amountButton(Player player, YamlConfiguration gui, ServerShopItem item, ServerShopAction action, int amount) {
        Map<String, String> placeholders = itemPlaceholders(player, item);
        placeholders.put("action", actionName(player, action));
        placeholders.put("amount", Integer.toString(amount));
        double total = action == ServerShopAction.BUY
                ? plugin.getServerShopPricingService().buyTotal(item, amount)
                : plugin.getServerShopPricingService().sellTotal(item, amount);
        placeholders.put("price", plugin.getEconomyService().format(total));
        return configuredItem(player, gui.getConfigurationSection("items.amountButton"), placeholders);
    }

    private ItemStack configuredItem(Player player, ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return new ItemStack(Material.STONE);
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material, Math.max(1, section.getInt("amount", 1)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(parse(player, section.getString("name", ""), placeholders)));
            meta.setLore(section.getStringList("lore").stream()
                    .map(line -> TextUtil.color(parse(player, line, placeholders)))
                    .toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void sendTransactionMessage(Player player, ServerShopItem item, int amount, TransactionResult result) {
        if (!result.success()) {
            plugin.getLanguageService().send(player, result.messageKey(), result.placeholders());
            return;
        }
        plugin.getLanguageService().send(player, result.messageKey(), Map.of(
                "amount", Integer.toString(amount),
                "item", TextUtil.color(item.displayName()),
                "price", plugin.getEconomyService().format(result.totalPrice())
        ));
    }

    private void addConfiguredButtons(Player player, Inventory inventory, YamlConfiguration gui) {
        var section = gui.getConfigurationSection("items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            var item = section.getConfigurationSection(key);
            if (item == null || !item.getBoolean("enabled", true)) {
                continue;
            }
            int slot = item.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            Material material = Material.matchMaterial(item.getString("material", "STONE"));
            ItemStack stack = new ItemStack(material == null ? Material.STONE : material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                Map<String, String> placeholders = plugin.getGuiPlaceholderService().placeholders(player);
                meta.setDisplayName(TextUtil.color(plugin.getPlaceholderApiHook().apply(player, PlaceholderUtil.apply(item.getString("name", ""), placeholders))));
                meta.setLore(item.getStringList("lore").stream()
                        .map(line -> TextUtil.color(plugin.getPlaceholderApiHook().apply(player, PlaceholderUtil.apply(line, placeholders))))
                        .toList());
                stack.setItemMeta(meta);
            }
            inventory.setItem(slot, stack);
        }
    }

    private void handleConfiguredButton(Player player, YamlConfiguration gui, int slot, boolean rightClick) {
        var section = gui.getConfigurationSection("items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            var item = section.getConfigurationSection(key);
            if (item == null || item.getInt("slot", -1) != slot) {
                continue;
            }
            var actions = rightClick ? item.getStringList("rightClickActions") : item.getStringList("leftClickActions");
            if (actions.isEmpty() && rightClick) {
                actions = item.getStringList("leftClickActions");
            }
            for (String action : actions) {
                plugin.getGuiActionExecutor().execute(player, action);
            }
        }
    }

    private void fill(Inventory inventory, YamlConfiguration gui) {
        var filler = gui.getConfigurationSection("filler");
        if (filler == null || !filler.getBoolean("enabled", true)) {
            return;
        }
        Material material = Material.matchMaterial(filler.getString("material", "BLACK_STAINED_GLASS_PANE"));
        ItemStack stack = new ItemStack(material == null ? Material.BLACK_STAINED_GLASS_PANE : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(filler.getString("name", " ")));
            stack.setItemMeta(meta);
        }
        for (int slot : filler.getIntegerList("slots")) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, stack);
            }
        }
    }

    private List<String> shopItemLore(Player player, YamlConfiguration gui, ServerShopItem item, Map<String, String> placeholders) {
        if (!item.lore().isEmpty()) {
            return item.lore().stream()
                    .map(line -> TextUtil.color(parse(player, line, placeholders)))
                    .toList();
        }
        return gui.getStringList("shopItemLore").stream()
                .filter(line -> !isLegacyStockOrLimitLore(line))
                .map(line -> TextUtil.color(parse(player, line, placeholders)))
                .toList();
    }

    private boolean isLegacyStockOrLimitLore(String line) {
        String normalized = line == null ? "" : line.toLowerCase(Locale.ROOT);
        return normalized.contains("kauflimit")
                || normalized.contains("verkaufslimit")
                || normalized.contains("buy limit")
                || normalized.contains("sell limit")
                || normalized.contains("bestand")
                || normalized.contains("stock")
                || normalized.contains("serverankauf")
                || normalized.contains("server purchases")
                || normalized.contains("%min_buy_amount%")
                || normalized.contains("%max_buy_amount%")
                || normalized.contains("%min_sell_amount%")
                || normalized.contains("%max_sell_amount%")
                || normalized.contains("%stock%")
                || normalized.contains("%max_stock%")
                || normalized.contains("%stock_status%");
    }

    private Map<String, String> itemPlaceholders(Player player, ServerShopItem item) {
        Map<String, String> placeholders = new HashMap<>(plugin.getGuiPlaceholderService().placeholders(player));
        placeholders.put("item", TextUtil.color(item.displayName()));
        placeholders.put("item_id", item.id());
        placeholders.put("material", item.material().name());
        placeholders.put("buy_price", plugin.getEconomyService().format(plugin.getServerShopPricingService().buyUnitPrice(item)));
        placeholders.put("sell_price", plugin.getEconomyService().format(plugin.getServerShopPricingService().sellUnitPrice(item)));
        placeholders.put("base_buy_price", plugin.getEconomyService().format(item.buyPrice()));
        placeholders.put("base_sell_price", plugin.getEconomyService().format(item.sellPrice()));
        placeholders.put("price_multiplier", String.format(Locale.US, "%.2f", plugin.getServerShopPricingService().multiplier(item)));
        String unlimited = plugin.getLanguageService().get(player, "serverShop.limitUnlimited");
        placeholders.put("min_buy_amount", "1");
        placeholders.put("max_buy_amount", unlimited);
        placeholders.put("min_sell_amount", "1");
        placeholders.put("max_sell_amount", unlimited);
        placeholders.put("stock_status", item.stockEnabled()
                ? plugin.getLanguageService().get(player, "serverShop.stockEnabled")
                : plugin.getLanguageService().get(player, "serverShop.stockDisabled"));
        placeholders.put("stock", item.stockEnabled() ? Integer.toString(plugin.getServerShopRegistry().availableStock(item)) : unlimited);
        placeholders.put("max_stock", item.stockEnabled() && item.hasStockMaximum() ? Integer.toString(item.maxStock()) : unlimited);
        placeholders.put("buy_status", Boolean.toString(item.buyEnabled()));
        placeholders.put("sell_status", Boolean.toString(item.sellEnabled()));
        return placeholders;
    }

    private List<Integer> configuredAmounts(YamlConfiguration gui) {
        List<Integer> amounts = new ArrayList<>(gui.getIntegerList("amountSelection.amounts"));
        if (amounts.isEmpty()) {
            amounts.addAll(plugin.getConfig().getIntegerList("serverShop.amountSelection.amounts"));
        }
        if (amounts.isEmpty()) {
            amounts.addAll(List.of(1, 8, 16, 32, 64));
        }
        return amounts.stream()
                .filter(amount -> amount > 0)
                .distinct()
                .toList();
    }

    private boolean requiresBuyConfirmation(ServerShopItem item, int amount) {
        if (!plugin.getConfig().getBoolean("serverShop.buyConfirmation.enabled", true)) {
            return false;
        }
        double threshold = plugin.getConfig().getDouble("serverShop.buyConfirmation.threshold", 10000.0D);
        return item.buyPrice() * amount >= threshold;
    }

    private String actionName(Player player, ServerShopAction action) {
        String key = action == ServerShopAction.BUY ? "serverShop.actionBuy" : "serverShop.actionSell";
        return plugin.getLanguageService().get(player, key);
    }

    private int parseAmount(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String parse(Player player, String value, Map<String, String> placeholders) {
        return plugin.getPlaceholderApiHook().apply(player, PlaceholderUtil.apply(value, placeholders));
    }

    private YamlConfiguration loadGui(String language, String fileName) {
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/" + fileName);
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/" + fileName);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private int sanitizeSize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return ((size + 8) / 9) * 9;
    }

    private record PendingAmountInput(String categoryId, String itemId, ServerShopAction action) {
    }
}
