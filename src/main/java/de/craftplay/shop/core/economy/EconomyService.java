package de.craftplay.shop.core.economy;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public interface EconomyService {
    boolean setup();

    boolean has(Player player, double amount);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(Player player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(Player player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    String format(double amount);
}
