package de.craftplay.shop.core.economy;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.util.NumberUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultEconomyService implements EconomyService {
    private final CraftplayShopPlugin plugin;
    private Economy economy;

    public VaultEconomyService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return false;
        }
        economy = provider.getProvider();
        return economy != null;
    }

    @Override
    public boolean has(Player player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(Player player, double amount) {
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public String format(double amount) {
        String formatted = plugin.getConfigService().moneyFormat()
                .replace("%amount%", NumberUtil.money(amount))
                .replace("%currency%", plugin.getConfigService().currencySymbol());
        return formatted.replace("%symbol%", plugin.getConfigService().currencySymbol());
    }
}
