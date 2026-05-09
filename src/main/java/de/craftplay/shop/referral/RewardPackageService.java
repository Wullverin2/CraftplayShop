package de.craftplay.shop.referral;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RewardPackageService {
    private final CraftplayShopPlugin plugin;
    private final Map<String, ReferralRewardPackage> packages = new LinkedHashMap<>();

    public RewardPackageService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        packages.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("referral.packages");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection packageSection = section.getConfigurationSection(id);
            if (packageSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(packageSection.getString("material", "CHEST"));
            packages.put(id, new ReferralRewardPackage(
                    id,
                    packageSection.getBoolean("enabled", true),
                    packageSection.getString("displayName", id),
                    material == null ? Material.CHEST : material,
                    packageSection.getInt("slot", -1),
                    packageSection.getStringList("lore"),
                    loadReward(packageSection.getConfigurationSection("referrer")),
                    loadReward(packageSection.getConfigurationSection("redeemer"))
            ));
        }
    }

    public ReferralRewardPackage packageById(String id) {
        return packages.get(id);
    }

    public Collection<ReferralRewardPackage> packages() {
        return packages.values();
    }

    public String defaultPackageId() {
        String configured = plugin.getConfig().getString("referral.defaultPackage", "");
        if (configured != null && packages.containsKey(configured)) {
            return configured;
        }
        return packages.keySet().stream().findFirst().orElse("");
    }

    private RewardDefinition loadReward(ConfigurationSection section) {
        if (section == null) {
            return new RewardDefinition(0.0D, List.of(), List.of());
        }
        List<ItemStack> items = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("items")) {
            Object materialRaw = raw.get("material");
            Material material = Material.matchMaterial(materialRaw == null ? "STONE" : materialRaw.toString());
            if (material == null || material.isAir()) {
                continue;
            }
            int amount = 1;
            Object amountRaw = raw.get("amount");
            if (amountRaw instanceof Number number) {
                amount = Math.max(1, number.intValue());
            }
            ItemStack stack = new ItemStack(material, amount);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                Object nameRaw = raw.get("name");
                if (nameRaw != null) {
                    meta.setDisplayName(TextUtil.color(nameRaw.toString()));
                }
                Object loreRaw = raw.get("lore");
                if (loreRaw instanceof List<?> loreList) {
                    meta.setLore(loreList.stream().map(Object::toString).map(TextUtil::color).toList());
                }
                stack.setItemMeta(meta);
            }
            items.add(stack);
        }
        return new RewardDefinition(section.getDouble("money", 0.0D), section.getStringList("commands"), items);
    }
}
