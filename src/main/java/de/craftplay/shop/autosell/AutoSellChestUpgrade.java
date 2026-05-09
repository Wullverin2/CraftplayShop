package de.craftplay.shop.autosell;

public record AutoSellChestUpgrade(int level,
                                   String id,
                                   String name,
                                   double price,
                                   String permission,
                                   long intervalSeconds,
                                   double multiplier) {
    public boolean hasPermission() {
        return permission != null && !permission.isBlank();
    }
}
