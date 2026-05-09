package de.craftplay.shop.referral;

import java.util.UUID;

public record PendingReward(
        long id,
        UUID playerUuid,
        String playerName,
        String sourceType,
        String sourceId,
        String rewardKind,
        String itemData,
        String command,
        double money,
        long createdAt,
        long claimedAt
) {
    public boolean claimed() {
        return claimedAt > 0L;
    }
}
