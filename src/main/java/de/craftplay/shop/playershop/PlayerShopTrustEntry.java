package de.craftplay.shop.playershop;

import java.util.UUID;

public record PlayerShopTrustEntry(
        long shopId,
        UUID playerUuid,
        String playerName,
        boolean openAllowed,
        boolean manageAllowed,
        boolean deleteAllowed,
        long createdAt,
        long updatedAt
) {
}
