package de.craftplay.shop.trade;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TradeGui {
    public static final int[] LEFT_OFFER_SLOTS = {10, 11, 19, 20, 28, 29, 37, 38};
    public static final int[] RIGHT_OFFER_SLOTS = {14, 15, 23, 24, 32, 33, 41, 42};
    public static final int[] DECORATION_SLOTS = {3, 4, 5, 12, 13, 21, 22, 30, 31, 39, 40};

    private final CraftplayShopPlugin plugin;

    public TradeGui(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public YamlConfiguration gui(Player player) {
        String language = plugin.getPlayerLanguageService().getLanguage(player);
        File file = new File(plugin.getDataFolder(), "gui/" + language + "/directtrade.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "gui/" + plugin.getConfigService().fallbackLanguage() + "/directtrade.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public String title(Player player, TradeSession session) {
        YamlConfiguration gui = gui(player);
        String target = session.other(player.getUniqueId()).equals(session.firstPlayer())
                ? session.firstOnline() == null ? "?" : session.firstOnline().getName()
                : session.secondOnline() == null ? "?" : session.secondOnline().getName();
        return TextUtil.color(apply(gui.getString("title", "&8DirectTrade: %target%"), Map.of("target", target)));
    }

    public void fillDecorations(Player player, Inventory inventory) {
        YamlConfiguration gui = gui(player);
        ConfigurationSection section = gui.getConfigurationSection("filler");
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        ItemStack itemStack = configuredItem(section, Map.of());
        for (int slot : section.getIntegerList("slots")) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, itemStack);
            }
        }
        for (int slot : DECORATION_SLOTS) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, itemStack);
            }
        }
    }

    public ItemStack configuredItem(ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return new ItemStack(Material.BARRIER);
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        ItemStack itemStack = new ItemStack(material == null ? Material.STONE : material, Math.max(1, section.getInt("amount", 1)));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(apply(section.getString("name", ""), placeholders)));
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(TextUtil.color(apply(line, placeholders)));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private String apply(String value, Map<String, String> placeholders) {
        String parsed = value == null ? "" : value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            parsed = parsed.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return parsed;
    }
}
