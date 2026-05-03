package de.craftplay.shop.core.player;

import java.util.UUID;

public record PlayerSettings(UUID playerUuid, String playerName, String language, boolean directTradeEnabled, long updatedAt) {
    public PlayerSettings withLanguage(String newLanguage, String newName, long updatedAt) {
        return new PlayerSettings(playerUuid, newName, newLanguage, directTradeEnabled, updatedAt);
    }

    public PlayerSettings withDirectTradeEnabled(boolean enabled, String newName, long updatedAt) {
        return new PlayerSettings(playerUuid, newName, language, enabled, updatedAt);
    }
}
