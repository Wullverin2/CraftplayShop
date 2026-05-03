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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    public void openSellGui(Player player) {
        YamlConfiguration gui = loadGui(player);
        Set<Integer> itemSlots = new HashSet<>(gui.getIntegerList("itemSlots"));
        if (itemSlots.isEmpty()) {
            for (int slot = 9; slot < 45; slot++) {
                itemSlots.add(slot);
            }
        }
        int confirmSlot = gui.getInt("buttons.confirm.slot", 49);
        int backSlot = gui.getInt("buttons.back.slot", 45);
        SellGuiHolder holder = new SellGuiHolder(itemSlots, confirmSlot, backSlot);
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt("size", 54)), TextUtil.color(gui.getString("title", "&8Sell GUI")));
        holder.setInventory(inventory);
        fill(inventory, gui, itemSlots);
        inventory.setItem(confirmSlot, configuredItem(gui.getConfigurationSection("buttons.confirm"), Map.of()));
        inventory.setItem(backSlot, configuredItem(gui.getConfigurationSection("buttons.back"), Map.of()));
        player.openInventory(inventory);
    }

    public void handleSellGuiClick(Player player, SellGuiHolder holder, org.bukkit.event.inventory.InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot == holder.confirmSlot()) {
            event.setCancelled(true);
            sellGuiContents(player, holder);
            return;
        }
        if (rawSlot == holder.backSlot()) {
            event.setCancelled(true);
            holder.setHandled(true);
            returnItems(player, holder);
            player.closeInventory();
            plugin.getGuiService().open(player, "main");
            return;
        }
        if (rawSlot >= 0 && rawSlot < event.getInventory().getSize()) {
            event.setCancelled(!holder.itemSlot(rawSlot));
        }
    }

    public void handleSellGuiDrag(SellGuiHolder holder, org.bukkit.event.inventory.InventoryDragEvent event) {
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < event.getInventory().getSize() && !holder.itemSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public void handleSellGuiClose(Player player, SellGuiHolder holder) {
        if (holder.handled()) {
            return;
        }
        returnItems(player, holder);
    }

    private void sellGuiContents(Player player, SellGuiHolder holder) {
        Map<ServerShopItem, Integer> amounts = new java.util.LinkedHashMap<>();
        Map<Integer, ItemStack> originals = new java.util.HashMap<>();
        for (int slot : holder.itemSlots()) {
            ItemStack stack = holder.getInventory().getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            ServerShopItem shopItem = plugin.getServerShopRegistry().findSellable(stack);
            if (shopItem == null) {
                continue;
            }
            originals.put(slot, stack.clone());
            amounts.merge(shopItem, stack.getAmount(), Integer::sum);
        }
        if (amounts.isEmpty()) {
            plugin.getLanguageService().send(player, "serverShop.sellGuiNothing");
            return;
        }
        double total = 0.0D;
        for (Map.Entry<ServerShopItem, Integer> entry : amounts.entrySet()) {
            total += entry.getKey().sellPrice() * entry.getValue();
        }
        for (int slot : originals.keySet()) {
            holder.getInventory().setItem(slot, null);
        }
        if (!plugin.getEconomyService().deposit(player, total)) {
            for (Map.Entry<Integer, ItemStack> entry : originals.entrySet()) {
                holder.getInventory().setItem(entry.getKey(), entry.getValue());
            }
            plugin.getLanguageService().send(player, "general.databaseError");
            return;
        }
        for (Map.Entry<ServerShopItem, Integer> entry : amounts.entrySet()) {
            plugin.getTransactionService().logAsync(TransactionType.SERVER_SELL, player, "SELL_GUI", entry.getKey().createStack(1),
                    entry.getValue(), entry.getKey().sellPrice(), entry.getKey().sellPrice() * entry.getValue());
        }
        holder.setHandled(true);
        plugin.getLanguageService().send(player, "serverShop.sellGuiDone", Map.of("price", plugin.getEconomyService().format(total)));
        player.closeInventory();
    }

    private void returnItems(Player player, SellGuiHolder holder) {
        for (int slot : holder.itemSlots()) {
            ItemStack stack = holder.getInventory().getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            holder.getInventory().setItem(slot, null);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
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

    private YamlConfiguration loadGui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/sell_gui.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/sell_gui.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void fill(Inventory inventory, YamlConfiguration gui, Set<Integer> itemSlots) {
        ConfigurationSection filler = gui.getConfigurationSection("filler");
        if (filler == null || !filler.getBoolean("enabled", true)) {
            return;
        }
        ItemStack stack = configuredItem(filler, Map.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!itemSlots.contains(slot)) {
                inventory.setItem(slot, stack);
            }
        }
    }

    private ItemStack configuredItem(ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return new ItemStack(Material.STONE);
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(PlaceholderUtil.apply(section.getString("name", ""), placeholders)));
            meta.setLore(section.getStringList("lore").stream()
                    .map(line -> TextUtil.color(PlaceholderUtil.apply(line, placeholders)))
                    .toList());
            stack.setItemMeta(meta);
        }
        return stack;
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
