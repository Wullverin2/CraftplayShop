package de.craftplay.shop.referral;

import java.util.UUID;

public record ReferralCodeEntry(
        UUID playerUuid,
        String playerName,
        String code,
        long createdAt,
        long updatedAt
) {
}
