package de.craftplay.shop.trade;

import java.util.UUID;

public record TradeSession(UUID firstPlayer, UUID secondPlayer, TradeState state, long createdAt) {
}
