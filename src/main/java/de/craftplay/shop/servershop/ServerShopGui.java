package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
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
import java.util.List;
import java.util.Map;

public class ServerShopGui {
    private final CraftplayShopPlugin plugin;

    public ServerShopGui(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!plugin.getServerShopService().enabled()) {
            plugin.getLanguageService().send(player, "general.featureNotAvailable");
            return;
        }
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        YamlConfiguration gui = loadGui(language, "servershop.yml");
        Map<Integer, String> categoriesBySlot = new HashMap<>();
        ServerShopHolder holder = new ServerShopHolder(categoriesBySlot);
        Inventory inventory = Bukkit.createInventory(holder, sanitizeSize(gui.getInt("size", 54)), TextUtil.color(gui.getString("title", "&8ServerShop")));
        holder.setInventory(inventory);
        fill(inventory, gui);
        for (ServerShopCategory category : plugin.getServerShopRegistry().categories()) {
            ItemStack itemStack = new ItemStack(category.icon());
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(TextUtil.color(category.displayName()));
                meta.setLore(gui.getStringList("categoryLore").stream().map(TextUtil::color).toList());
                itemStack.setItemMeta(meta);
            }
            inventory.setItem(category.slot(), itemStack);
            categoriesBySlot.put(category.slot(), category.id());
        }
        addConfiguredButtons(player, inventory, gui);
        player.openInventory(inventory);
        plugin.getLanguageService().send(player, "serverShop.opened");
    }

    public void handleClick(Player player, ServerShopHolder holder, InventoryClickEvent event) {
        String categoryId = holder.categoryAt(event.getRawSlot());
        if (categoryId != null) {
            plugin.getServerShopCategoryGui().open(player, categoryId);
            return;
        }
        YamlConfiguration gui = loadGui(plugin.getPlayerLanguageService().getLanguage(player), "servershop.yml");
        handleConfiguredButton(player, gui, event.getRawSlot(), event.isRightClick());
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
            if (slot < 0 || slot >= inventory.getSize() || inventory.getItem(slot) != null) {
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
            List<String> actions = rightClick ? item.getStringList("rightClickActions") : item.getStringList("leftClickActions");
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
