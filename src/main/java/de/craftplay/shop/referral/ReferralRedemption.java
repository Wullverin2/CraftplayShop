package de.craftplay.shop.referral;

import java.util.UUID;

public record ReferralRedemption(
        long id,
        String code,
        UUID referrerUuid,
        String referrerName,
        UUID redeemerUuid,
        String redeemerName,
        String packageId,
        long createdAt
) {
}
