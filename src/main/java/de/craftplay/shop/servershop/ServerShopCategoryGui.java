package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.transaction.TransactionResult;
import de.craftplay.shop.core.transaction.TransactionType;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ServerShopCategoryGui {
    private final CraftplayShopPlugin plugin;

    public ServerShopCategoryGui(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String categoryId) {
        ServerShopCategory category = plugin.getServerShopRegistry().category(categoryId);
        if (category == null) {
            plugin.getLanguageService().send(player, "serverShop.categoryNotFound");
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
        addConfiguredButtons(inventory, gui);
        for (ServerShopItem item : category.items()) {
            inventory.setItem(item.slot(), buildShopItem(item, gui));
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
        int amount = event.isShiftClick() ? item.material().getMaxStackSize() : 1;
        TransactionResult result = event.isRightClick()
                ? plugin.getServerShopTransactionService().sell(player, item, amount, TransactionType.SERVER_SELL)
                : plugin.getServerShopTransactionService().buy(player, item, amount);
        sendTransactionMessage(player, item, amount, result);
        open(player, holder.categoryId());
    }

    private ItemStack buildShopItem(ServerShopItem item, YamlConfiguration gui) {
        ItemStack itemStack = new ItemStack(item.material());
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(item.displayName()));
            Map<String, String> placeholders = Map.of(
                    "buy_price", plugin.getEconomyService().format(item.buyPrice()),
                    "sell_price", plugin.getEconomyService().format(item.sellPrice()),
                    "item", TextUtil.color(item.displayName())
            );
            meta.setLore(gui.getStringList("shopItemLore").stream()
                    .map(line -> TextUtil.color(PlaceholderUtil.apply(line, placeholders)))
                    .toList());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void sendTransactionMessage(Player player, ServerShopItem item, int amount, TransactionResult result) {
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

    private void addConfiguredButtons(Inventory inventory, YamlConfiguration gui) {
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
                meta.setDisplayName(TextUtil.color(item.getString("name", "")));
                meta.setLore(item.getStringList("lore").stream().map(TextUtil::color).toList());
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
}
