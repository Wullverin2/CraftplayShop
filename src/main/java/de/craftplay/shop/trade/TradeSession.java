package de.craftplay.shop.trade;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.UUID;

public class TradeSession {
    private final UUID firstPlayer;
    private final UUID secondPlayer;
    private final long createdAt;
    private final ItemStack[] firstOffers = new ItemStack[8];
    private final ItemStack[] secondOffers = new ItemStack[8];
    private Inventory firstInventory;
    private Inventory secondInventory;
    private TradeState state;
    private double firstMoney;
    private double secondMoney;
    private boolean firstReady;
    private boolean secondReady;
    private boolean firstFinalConfirmed;
    private boolean secondFinalConfirmed;

    public TradeSession(UUID firstPlayer, UUID secondPlayer, TradeState state, long createdAt) {
        this.firstPlayer = firstPlayer;
        this.secondPlayer = secondPlayer;
        this.state = state;
        this.createdAt = createdAt;
    }

    public UUID firstPlayer() {
        return firstPlayer;
    }

    public UUID secondPlayer() {
        return secondPlayer;
    }

    public long createdAt() {
        return createdAt;
    }

    public TradeState state() {
        return state;
    }

    public void setState(TradeState state) {
        this.state = state;
    }

    public Inventory firstInventory() {
        return firstInventory;
    }

    public void setFirstInventory(Inventory firstInventory) {
        this.firstInventory = firstInventory;
    }

    public Inventory secondInventory() {
        return secondInventory;
    }

    public void setSecondInventory(Inventory secondInventory) {
        this.secondInventory = secondInventory;
    }

    public Player firstOnline() {
        return Bukkit.getPlayer(firstPlayer);
    }

    public Player secondOnline() {
        return Bukkit.getPlayer(secondPlayer);
    }

    public UUID other(UUID playerId) {
        if (firstPlayer.equals(playerId)) {
            return secondPlayer;
        }
        return firstPlayer;
    }

    public boolean isParticipant(UUID playerId) {
        return firstPlayer.equals(playerId) || secondPlayer.equals(playerId);
    }

    public ItemStack[] offers(UUID playerId) {
        return firstPlayer.equals(playerId) ? firstOffers : secondOffers;
    }

    public ItemStack[] oppositeOffers(UUID playerId) {
        return firstPlayer.equals(playerId) ? secondOffers : firstOffers;
    }

    public double money(UUID playerId) {
        return firstPlayer.equals(playerId) ? firstMoney : secondMoney;
    }

    public void setMoney(UUID playerId, double value) {
        if (firstPlayer.equals(playerId)) {
            firstMoney = value;
        } else {
            secondMoney = value;
        }
    }

    public boolean ready(UUID playerId) {
        return firstPlayer.equals(playerId) ? firstReady : secondReady;
    }

    public void setReady(UUID playerId, boolean value) {
        if (firstPlayer.equals(playerId)) {
            firstReady = value;
        } else {
            secondReady = value;
        }
    }

    public boolean finalConfirmed(UUID playerId) {
        return firstPlayer.equals(playerId) ? firstFinalConfirmed : secondFinalConfirmed;
    }

    public void setFinalConfirmed(UUID playerId, boolean value) {
        if (firstPlayer.equals(playerId)) {
            firstFinalConfirmed = value;
        } else {
            secondFinalConfirmed = value;
        }
    }

    public boolean bothReady() {
        return firstReady && secondReady;
    }

    public boolean bothFinalConfirmed() {
        return firstFinalConfirmed && secondFinalConfirmed;
    }

    public void resetConfirmations() {
        firstReady = false;
        secondReady = false;
        firstFinalConfirmed = false;
        secondFinalConfirmed = false;
    }

    public void clearOffers() {
        Arrays.fill(firstOffers, null);
        Arrays.fill(secondOffers, null);
        firstMoney = 0.0D;
        secondMoney = 0.0D;
        resetConfirmations();
    }
}
