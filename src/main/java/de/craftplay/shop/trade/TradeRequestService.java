package de.craftplay.shop.trade;

import java.util.UUID;

public record TradeRequestService(UUID sender,
                                  String senderName,
                                  UUID target,
                                  long createdAt,
                                  long expiresAt) {
    public boolean expired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
