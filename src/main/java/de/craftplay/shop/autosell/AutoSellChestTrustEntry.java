package de.craftplay.shop.autosell;

import java.util.UUID;

public record AutoSellChestTrustEntry(long chestId,
                                      UUID playerUuid,
                                      String playerName,
                                      boolean openAllowed,
                                      boolean manageAllowed,
                                      boolean upgradeAllowed,
                                      boolean deleteAllowed,
                                      long createdAt,
                                      long updatedAt) {
    public AutoSellChestTrustEntry withOpenAllowed(boolean value) {
        return new AutoSellChestTrustEntry(chestId, playerUuid, playerName, value, manageAllowed, upgradeAllowed, deleteAllowed, createdAt, System.currentTimeMillis());
    }

    public AutoSellChestTrustEntry withManageAllowed(boolean value) {
        return new AutoSellChestTrustEntry(chestId, playerUuid, playerName, openAllowed, value, upgradeAllowed, deleteAllowed, createdAt, System.currentTimeMillis());
    }

    public AutoSellChestTrustEntry withUpgradeAllowed(boolean value) {
        return new AutoSellChestTrustEntry(chestId, playerUuid, playerName, openAllowed, manageAllowed, value, deleteAllowed, createdAt, System.currentTimeMillis());
    }

    public AutoSellChestTrustEntry withDeleteAllowed(boolean value) {
        return new AutoSellChestTrustEntry(chestId, playerUuid, playerName, openAllowed, manageAllowed, upgradeAllowed, value, createdAt, System.currentTimeMillis());
    }
}
