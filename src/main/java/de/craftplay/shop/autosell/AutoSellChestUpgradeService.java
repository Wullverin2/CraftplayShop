package de.craftplay.shop.autosell;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class AutoSellChestUpgradeService {
    private final CraftplayShopPlugin plugin;
    private final Map<Integer, AutoSellChestUpgrade> intervalUpgrades = new LinkedHashMap<>();
    private final Map<Integer, AutoSellChestUpgrade> multiplierUpgrades = new LinkedHashMap<>();

    public AutoSellChestUpgradeService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        intervalUpgrades.clear();
        multiplierUpgrades.clear();
        loadIntervalUpgrades();
        loadMultiplierUpgrades();
    }

    public long intervalSeconds(AutoSellChest chest) {
        AutoSellChestUpgrade upgrade = intervalUpgrade(chest.intervalLevel());
        if (upgrade == null || upgrade.intervalSeconds() <= 0L) {
            return Math.max(1L, plugin.getConfig().getLong("autoSellChest.selling.intervalSeconds", 10L));
        }
        return upgrade.intervalSeconds();
    }

    public double multiplier(AutoSellChest chest) {
        AutoSellChestUpgrade upgrade = multiplierUpgrade(chest.multiplierLevel());
        if (upgrade == null || upgrade.multiplier() <= 0.0D) {
            return Math.max(0.0D, chest.multiplier());
        }
        return upgrade.multiplier();
    }

    public AutoSellChestUpgrade intervalUpgrade(int level) {
        return intervalUpgrades.get(level);
    }

    public AutoSellChestUpgrade multiplierUpgrade(int level) {
        return multiplierUpgrades.get(level);
    }

    public AutoSellChestUpgrade nextIntervalUpgrade(AutoSellChest chest) {
        if (!plugin.getConfig().getBoolean("autoSellChest.upgrades.enabled", true)
                || !plugin.getConfig().getBoolean("autoSellChest.upgrades.interval.enabled", true)) {
            return null;
        }
        return intervalUpgrades.values().stream()
                .filter(upgrade -> upgrade.level() > chest.intervalLevel())
                .min(Comparator.comparingInt(AutoSellChestUpgrade::level))
                .orElse(null);
    }

    public AutoSellChestUpgrade nextMultiplierUpgrade(AutoSellChest chest) {
        if (!plugin.getConfig().getBoolean("autoSellChest.upgrades.enabled", true)
                || !plugin.getConfig().getBoolean("autoSellChest.upgrades.multiplier.enabled", true)) {
            return null;
        }
        return multiplierUpgrades.values().stream()
                .filter(upgrade -> upgrade.level() > chest.multiplierLevel())
                .min(Comparator.comparingInt(AutoSellChestUpgrade::level))
                .orElse(null);
    }

    public UpgradePurchaseResult buyIntervalUpgrade(Player player, AutoSellChest chest) {
        AutoSellChestUpgrade upgrade = nextIntervalUpgrade(chest);
        if (upgrade == null) {
            return UpgradePurchaseResult.MAX_LEVEL;
        }
        return buyUpgrade(player, upgrade);
    }

    public UpgradePurchaseResult buyMultiplierUpgrade(Player player, AutoSellChest chest) {
        AutoSellChestUpgrade upgrade = nextMultiplierUpgrade(chest);
        if (upgrade == null) {
            return UpgradePurchaseResult.MAX_LEVEL;
        }
        return buyUpgrade(player, upgrade);
    }

    private UpgradePurchaseResult buyUpgrade(Player player, AutoSellChestUpgrade upgrade) {
        if (upgrade.hasPermission() && !player.hasPermission(upgrade.permission())) {
            return UpgradePurchaseResult.NO_PERMISSION;
        }
        if (upgrade.price() > 0.0D && !plugin.getEconomyService().has(player, upgrade.price())) {
            return UpgradePurchaseResult.NOT_ENOUGH_MONEY;
        }
        if (upgrade.price() > 0.0D && !plugin.getEconomyService().withdraw(player, upgrade.price())) {
            return UpgradePurchaseResult.ECONOMY_FAILED;
        }
        return UpgradePurchaseResult.SUCCESS;
    }

    private void loadIntervalUpgrades() {
        long defaultInterval = Math.max(1L, plugin.getConfig().getLong("autoSellChest.selling.intervalSeconds", 10L));
        intervalUpgrades.put(0, new AutoSellChestUpgrade(0, "default", plugin.getConfig().getString("autoSellChest.upgrades.interval.defaultName", "&7Standard"), 0.0D, "", defaultInterval, 1.0D));
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("autoSellChest.upgrades.interval.levels");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            int level = parseLevel(key);
            if (level <= 0) {
                continue;
            }
            String path = "autoSellChest.upgrades.interval.levels." + key + ".";
            intervalUpgrades.put(level, new AutoSellChestUpgrade(
                    level,
                    key.toLowerCase(Locale.ROOT),
                    plugin.getConfig().getString(path + "name", "&eLevel " + level),
                    plugin.getConfig().getDouble(path + "price", 0.0D),
                    plugin.getConfig().getString(path + "permission", ""),
                    Math.max(1L, plugin.getConfig().getLong(path + "intervalSeconds", defaultInterval)),
                    1.0D
            ));
        }
    }

    private void loadMultiplierUpgrades() {
        multiplierUpgrades.put(0, new AutoSellChestUpgrade(0, "default", plugin.getConfig().getString("autoSellChest.upgrades.multiplier.defaultName", "&7Standard"), 0.0D, "", 0L, 1.0D));
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("autoSellChest.upgrades.multiplier.levels");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            int level = parseLevel(key);
            if (level <= 0) {
                continue;
            }
            String path = "autoSellChest.upgrades.multiplier.levels." + key + ".";
            multiplierUpgrades.put(level, new AutoSellChestUpgrade(
                    level,
                    key.toLowerCase(Locale.ROOT),
                    plugin.getConfig().getString(path + "name", "&eLevel " + level),
                    plugin.getConfig().getDouble(path + "price", 0.0D),
                    plugin.getConfig().getString(path + "permission", ""),
                    0L,
                    Math.max(0.0D, plugin.getConfig().getDouble(path + "multiplier", 1.0D))
            ));
        }
    }

    private int parseLevel(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public enum UpgradePurchaseResult {
        SUCCESS,
        MAX_LEVEL,
        NO_PERMISSION,
        NOT_ENOUGH_MONEY,
        ECONOMY_FAILED
    }
}
