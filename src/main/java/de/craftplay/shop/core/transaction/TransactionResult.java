package de.craftplay.shop.core.transaction;

public record TransactionResult(boolean success, String messageKey, double totalPrice) {
    public static TransactionResult success(String messageKey, double totalPrice) {
        return new TransactionResult(true, messageKey, totalPrice);
    }

    public static TransactionResult failure(String messageKey) {
        return new TransactionResult(false, messageKey, 0.0D);
    }
}
