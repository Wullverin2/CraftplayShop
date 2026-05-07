package de.craftplay.shop.core.transaction;

import java.util.Map;

public record TransactionResult(boolean success, String messageKey, double totalPrice, Map<String, String> placeholders) {
    public static TransactionResult success(String messageKey, double totalPrice) {
        return new TransactionResult(true, messageKey, totalPrice, Map.of());
    }

    public static TransactionResult failure(String messageKey) {
        return new TransactionResult(false, messageKey, 0.0D, Map.of());
    }

    public static TransactionResult failure(String messageKey, Map<String, String> placeholders) {
        return new TransactionResult(false, messageKey, 0.0D, placeholders);
    }
}
