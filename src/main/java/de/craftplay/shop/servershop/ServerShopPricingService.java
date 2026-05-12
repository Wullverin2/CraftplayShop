package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.core.transaction.TransactionType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ServerShopPricingService {
    private final CraftplayShopPlugin plugin;
    private final Map<String, PricingState> states = new HashMap<>();

    public ServerShopPricingService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        synchronized (states) {
            states.clear();
            synchronized (plugin.getDatabaseService().lock()) {
                try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                        "SELECT * FROM " + plugin.getDatabaseService().table("server_shop_pricing_state"));
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String key = key(result.getString("category_id"), result.getString("item_id"));
                        states.put(key, new PricingState(
                                result.getDouble("multiplier"),
                                result.getLong("buy_count"),
                                result.getLong("sell_count"),
                                result.getLong("updated_at")
                        ));
                    }
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not load ServerShop dynamic pricing state.", exception);
                }
            }
        }
    }

    public double buyUnitPrice(ServerShopItem item) {
        return roundMoney(Math.max(0.0D, item.buyPrice() * multiplier(item) * eventMultiplier(item, true)));
    }

    public double sellUnitPrice(ServerShopItem item) {
        return roundMoney(Math.max(0.0D, item.sellPrice() * multiplier(item) * eventMultiplier(item, false)));
    }

    public double buyTotal(ServerShopItem item, int amount) {
        double subtotal = buyUnitPrice(item) * Math.max(1, amount);
        return roundMoney(subtotal + fee(true, subtotal));
    }

    public double sellTotal(ServerShopItem item, int amount) {
        double subtotal = sellUnitPrice(item) * Math.max(1, amount);
        return roundMoney(Math.max(0.0D, subtotal - fee(false, subtotal)));
    }

    public double fee(boolean buy, double subtotal) {
        String path = buy ? "serverShop.fees.buy" : "serverShop.fees.sell";
        if (!plugin.getConfig().getBoolean(path + ".enabled", false)) {
            return 0.0D;
        }
        double flat = Math.max(0.0D, plugin.getConfig().getDouble(path + ".flat", 0.0D));
        double percent = Math.max(0.0D, plugin.getConfig().getDouble(path + ".percent", 0.0D));
        return roundMoney(flat + (subtotal * percent / 100.0D));
    }

    public double multiplier(ServerShopItem item) {
        if (!plugin.getConfig().getBoolean("serverShop.dynamicPricing.enabled", false)) {
            return 1.0D;
        }
        synchronized (states) {
            PricingState state = states.computeIfAbsent(key(item), ignored -> new PricingState(1.0D, 0L, 0L, System.currentTimeMillis()));
            return clamp(recoveredMultiplier(state));
        }
    }

    public void record(TransactionType type, ServerShopItem item, int amount, double unitPrice, double totalPrice) {
        if (item == null || amount <= 0) {
            return;
        }
        if (plugin.getConfig().getBoolean("serverShop.dynamicPricing.enabled", false)) {
            adjustState(type, item, amount);
        }
        if (plugin.getConfig().getBoolean("serverShop.priceHistory.enabled", true)) {
            plugin.getTaskService().runAsync(() -> writeHistory(type, item, amount, unitPrice, totalPrice));
        }
    }

    private void adjustState(TransactionType type, ServerShopItem item, int amount) {
        synchronized (states) {
            String key = key(item);
            PricingState current = states.computeIfAbsent(key, ignored -> new PricingState(1.0D, 0L, 0L, System.currentTimeMillis()));
            double multiplier = recoveredMultiplier(current);
            double step = plugin.getConfig().getDouble("serverShop.dynamicPricing.stepPerStack", 0.02D) * (amount / 64.0D);
            long buyCount = current.buyCount();
            long sellCount = current.sellCount();
            if (type == TransactionType.SERVER_BUY) {
                multiplier += step;
                buyCount += amount;
            } else if (type == TransactionType.SERVER_SELL || type == TransactionType.SERVER_SELL_ALL || type == TransactionType.AUTOSELL_CHEST) {
                multiplier -= step;
                sellCount += amount;
            }
            PricingState updated = new PricingState(clamp(multiplier), buyCount, sellCount, System.currentTimeMillis());
            states.put(key, updated);
            plugin.getTaskService().runAsync(() -> writeState(item, updated));
        }
    }

    private double recoveredMultiplier(PricingState state) {
        double recovery = Math.max(0.0D, plugin.getConfig().getDouble("serverShop.dynamicPricing.recoveryPerHour", 0.01D));
        if (recovery <= 0.0D) {
            return state.multiplier();
        }
        long elapsed = Math.max(0L, System.currentTimeMillis() - state.updatedAt());
        double delta = recovery * (elapsed / 3_600_000.0D);
        if (state.multiplier() > 1.0D) {
            return Math.max(1.0D, state.multiplier() - delta);
        }
        if (state.multiplier() < 1.0D) {
            return Math.min(1.0D, state.multiplier() + delta);
        }
        return 1.0D;
    }

    private double eventMultiplier(ServerShopItem item, boolean buy) {
        if (!plugin.getConfig().getBoolean("serverShop.eventShop.enabled", false)) {
            return 1.0D;
        }
        long now = System.currentTimeMillis();
        var deals = plugin.getConfig().getMapList("serverShop.eventShop.deals");
        for (Map<?, ?> deal : deals) {
            Object enabledValue = deal.containsKey("enabled") ? deal.get("enabled") : Boolean.TRUE;
            if (!Boolean.parseBoolean(String.valueOf(enabledValue))) {
                continue;
            }
            String category = String.valueOf(deal.containsKey("category") ? deal.get("category") : "*");
            String id = String.valueOf(deal.containsKey("item") ? deal.get("item") : "*");
            if (!"*".equals(category) && !category.equalsIgnoreCase(item.categoryId())) {
                continue;
            }
            if (!"*".equals(id) && !id.equalsIgnoreCase(item.id())) {
                continue;
            }
            long start = longValue(deal.get("startEpochMillis"), 0L);
            long end = longValue(deal.get("endEpochMillis"), 0L);
            if ((start > 0L && now < start) || (end > 0L && now > end)) {
                continue;
            }
            return Math.max(0.0D, doubleValue(deal.get(buy ? "buyMultiplier" : "sellMultiplier"), 1.0D));
        }
        return 1.0D;
    }

    private void writeHistory(TransactionType type, ServerShopItem item, int amount, double unitPrice, double totalPrice) {
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + plugin.getDatabaseService().table("server_shop_price_history") + " " +
                            "(category_id, item_id, type, amount, unit_price, total_price, multiplier, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, item.categoryId());
                statement.setString(2, item.id());
                statement.setString(3, type.name());
                statement.setInt(4, amount);
                statement.setDouble(5, unitPrice);
                statement.setDouble(6, totalPrice);
                statement.setDouble(7, multiplier(item));
                statement.setLong(8, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not write ServerShop price history.", exception);
            }
        }
    }

    private void writeState(ServerShopItem item, PricingState state) {
        String table = plugin.getDatabaseService().table("server_shop_pricing_state");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement update = plugin.getDatabaseService().connection().prepareStatement(
                    "UPDATE " + table + " SET multiplier = ?, buy_count = ?, sell_count = ?, updated_at = ? WHERE category_id = ? AND item_id = ?")) {
                update.setDouble(1, state.multiplier());
                update.setLong(2, state.buyCount());
                update.setLong(3, state.sellCount());
                update.setLong(4, state.updatedAt());
                update.setString(5, item.categoryId());
                update.setString(6, item.id());
                if (update.executeUpdate() > 0) {
                    return;
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not update ServerShop pricing state.", exception);
                return;
            }
            try (PreparedStatement insert = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (category_id, item_id, multiplier, buy_count, sell_count, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, item.categoryId());
                insert.setString(2, item.id());
                insert.setDouble(3, state.multiplier());
                insert.setLong(4, state.buyCount());
                insert.setLong(5, state.sellCount());
                insert.setLong(6, state.updatedAt());
                insert.executeUpdate();
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not insert ServerShop pricing state.", exception);
            }
        }
    }

    private double clamp(double value) {
        double min = plugin.getConfig().getDouble("serverShop.dynamicPricing.minMultiplier", 0.5D);
        double max = plugin.getConfig().getDouble("serverShop.dynamicPricing.maxMultiplier", 2.0D);
        return Math.max(min, Math.min(max, value));
    }

    private String key(ServerShopItem item) {
        return key(item.categoryId(), item.id());
    }

    private String key(String categoryId, String itemId) {
        return categoryId.toLowerCase(Locale.ROOT) + "\u0000" + itemId.toLowerCase(Locale.ROOT);
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record PricingState(double multiplier, long buyCount, long sellCount, long updatedAt) {
    }
}
