package de.craftplay.shop.core.economy;

import org.bukkit.entity.Player;

public interface EconomyService {
    boolean setup();

    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount);

    boolean deposit(Player player, double amount);

    String format(double amount);
}
