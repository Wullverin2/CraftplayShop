package de.craftplay.shop.core.gui;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.PlaceholderUtil;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GuiItemBuilder {
    private final CraftplayShopPlugin plugin;

    public GuiItemBuilder(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack build(ConfigurationSection section, Player player, Map<String, String> placeholders) {
        Material material = material(section.getString("material", "STONE"));
        ConfigurationSection head = section.getConfigurationSection("head");
        if (head != null && "HEAD_DATABASE".equalsIgnoreCase(head.getString("type", "")) && !plugin.getHeadDatabaseHook().isAvailable()) {
            material = material(plugin.getConfig().getString("integrations.headDatabase.fallbackMaterial", "PLAYER_HEAD"));
        }
        ItemStack itemStack = new ItemStack(material, Math.max(1, Math.min(64, section.getInt("amount", 1))));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        if (section.contains("customModelData") && section.getInt("customModelData", 0) > 0) {
            meta.setCustomModelData(section.getInt("customModelData"));
        }
        String name = PlaceholderUtil.apply(section.getString("name", ""), placeholders);
        meta.setDisplayName(TextUtil.color(name));
        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(TextUtil.color(PlaceholderUtil.apply(line, placeholders)));
        }
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        for (String flagName : section.getStringList("itemFlags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (section.getBoolean("glow", false)) {
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (enchantment != null) {
                meta.addEnchant(enchantment, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        if (meta instanceof SkullMeta skullMeta && head != null) {
            applyHead(skullMeta, head, player, placeholders);
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private void applyHead(SkullMeta skullMeta, ConfigurationSection head, Player player, Map<String, String> placeholders) {
        String type = head.getString("type", "");
        String value = PlaceholderUtil.apply(head.getString("value", ""), placeholders);
        if ("PLAYER_NAME".equalsIgnoreCase(type) && !value.isBlank()) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(value));
            return;
        }
        if ("PLAYER_UUID".equalsIgnoreCase(type) && !value.isBlank()) {
            try {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(java.util.UUID.fromString(value)));
            } catch (IllegalArgumentException ignored) {
                skullMeta.setOwningPlayer(player);
            }
            return;
        }
        if (player != null) {
            skullMeta.setOwningPlayer(player);
        }
    }

    private Material material(String name) {
        Material material = Material.matchMaterial(name == null ? "STONE" : name);
        return material == null ? Material.STONE : material;
    }
}
