package de.craftplay.shop.auctionhouse;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record AuctionHouseListing(
        long id,
        UUID sellerUuid,
        String sellerName,
        String world,
        ItemStack itemStack,
        String material,
        int amount,
        double price,
        double fee,
        AuctionHouseStatus status,
        UUID buyerUuid,
        String buyerName,
        long createdAt,
        long expiresAt,
        long soldAt,
        long claimedAt
) {
    public boolean isClaimable() {
        return status == AuctionHouseStatus.CANCELLED || status == AuctionHouseStatus.EXPIRED;
    }
}
