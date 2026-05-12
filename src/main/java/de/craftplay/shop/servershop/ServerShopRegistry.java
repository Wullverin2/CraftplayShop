package de.craftplay.shop.servershop;

import de.craftplay.shop.CraftplayShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerShopRegistry {
    private final CraftplayShopPlugin plugin;
    private final Map<String, ServerShopCategory> categories = new LinkedHashMap<>();
    private final Map<String, Integer> stockCache = new ConcurrentHashMap<>();
    private final Set<String> dirtyStock = ConcurrentHashMap.newKeySet();
    private BukkitTask stockFlushTask;

    public ServerShopRegistry(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        cancelStockFlushTask();
        categories.clear();
        stockCache.clear();
        dirtyStock.clear();
        File file = new File(plugin.getDataFolder(), "server_shop.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = configuration.getConfigurationSection("categories");
        if (section == null) {
            return;
        }
        for (String categoryId : section.getKeys(false)) {
            ConfigurationSection categorySection = section.getConfigurationSection(categoryId);
            if (categorySection == null) {
                continue;
            }
            Material icon = material(categorySection.getString("icon", "CHEST"));
            ServerShopCategory category = new ServerShopCategory(
                    categoryId,
                    categorySection.getString("displayName", categoryId),
                    categorySection.getStringList("lore"),
                    icon,
                    categorySection.getBoolean("enabled", true),
                    categorySection.getInt("slot", 0)
            );
            ConfigurationSection items = categorySection.getConfigurationSection("items");
            if (items != null) {
                for (String itemId : items.getKeys(false)) {
                    ConfigurationSection itemSection = items.getConfigurationSection(itemId);
                    if (itemSection == null) {
                        continue;
                    }
                    Material material = material(itemSection.getString("material", "STONE"));
                    category.addItem(new ServerShopItem(
                            categoryId,
                            itemId,
                            material,
                            itemSection.getString("displayName", material.name()),
                            itemSection.getStringList("lore"),
                            itemSection.getDouble("buyPrice", 0.0D),
                            itemSection.getDouble("sellPrice", 0.0D),
                            itemSection.getBoolean("buyEnabled", false),
                            itemSection.getBoolean("sellEnabled", false),
                            itemSection.getInt("slot", 0),
                            Math.max(1, itemSection.getInt("minBuyAmount", 1)),
                            Math.max(0, itemSection.getInt("maxBuyAmount", 0)),
                            Math.max(1, itemSection.getInt("minSellAmount", 1)),
                            Math.max(0, itemSection.getInt("maxSellAmount", 0)),
                            itemSection.getBoolean("stockEnabled", false),
                            Math.max(0, itemSection.getInt("stock", 0)),
                            Math.max(0, itemSection.getInt("maxStock", 0))
                    ));
                }
            }
            categories.put(category.id(), category);
        }
        initializeStockRows();
        startStockFlushTask();
    }

    public Collection<ServerShopCategory> categories() {
        return categories.values();
    }

    public int countCategories() {
        return categories.size();
    }

    public ServerShopCategory category(String id) {
        return categories.get(id);
    }

    public ServerShopItem findSellable(ItemStack itemStack) {
        return findSellable(itemStack, true);
    }

    public ServerShopItem findAutoSellable(ItemStack itemStack) {
        return findSellable(itemStack, false);
    }

    private ServerShopItem findSellable(ItemStack itemStack, boolean validateSellableStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        if (validateSellableStack && !plugin.getServerShopService().canSellItemStack(itemStack)) {
            return null;
        }
        for (ServerShopCategory category : categories.values()) {
            if (!category.enabled()) {
                continue;
            }
            for (ServerShopItem item : category.items()) {
                if (item.sellEnabled() && plugin.getItemMatcher().matches(itemStack, item.createStack(1), plugin.getConfigService().itemMatchMode())) {
                    return item;
                }
            }
        }
        return null;
    }

    public synchronized boolean decreaseStock(ServerShopItem item, int amount) {
        return adjustStock(item, -amount);
    }

    public synchronized boolean increaseStock(ServerShopItem item, int amount) {
        return adjustStock(item, amount);
    }

    public int availableStock(ServerShopItem item) {
        if (!item.stockEnabled()) {
            return Integer.MAX_VALUE;
        }
        return currentStock(item, Math.max(0, item.stock()));
    }

    public int availableStockCapacity(ServerShopItem item) {
        if (!item.stockEnabled() || !item.hasStockMaximum()) {
            return Integer.MAX_VALUE;
        }
        ServerShopItem current = currentItem(item);
        int stock = currentStock(item, current == null ? item.stock() : current.stock());
        int maxStock = current == null ? item.maxStock() : current.maxStock();
        return Math.max(0, maxStock - stock);
    }

    public synchronized boolean setStock(String categoryId, String itemId, int stock) {
        ServerShopCategory category = category(categoryId);
        ServerShopItem item = category == null ? null : category.item(itemId);
        if (item == null) {
            return false;
        }
        int updated = Math.max(0, stock);
        if (item.hasStockMaximum()) {
            updated = Math.min(updated, item.maxStock());
        }
        ensureStockRow(item);
        return updateStockCache(item, updated);
    }

    public synchronized boolean clampStockToMax(String categoryId, String itemId) {
        ServerShopCategory category = category(categoryId);
        ServerShopItem item = category == null ? null : category.item(itemId);
        if (item == null || !item.stockEnabled() || !item.hasStockMaximum()) {
            return true;
        }
        int current = currentStock(item, item.stock());
        if (current <= item.maxStock()) {
            return true;
        }
        return updateStockCache(item, item.maxStock());
    }

    public void startStockFlushTask() {
        cancelStockFlushTask();
        long intervalSeconds = Math.max(5L, plugin.getConfig().getLong("serverShop.stock.flushIntervalSeconds", 30L));
        stockFlushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flushStockSync, intervalSeconds * 20L, intervalSeconds * 20L);
    }

    public void cancelStockFlushTask() {
        if (stockFlushTask != null && !stockFlushTask.isCancelled()) {
            stockFlushTask.cancel();
        }
        stockFlushTask = null;
    }

    public synchronized void flushStockSync() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        for (String key : dirtyStock) {
            Integer stock = stockCache.get(key);
            if (stock != null) {
                snapshot.put(key, stock);
            }
        }
        if (snapshot.isEmpty()) {
            return;
        }
        dirtyStock.removeAll(snapshot.keySet());
        for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
            int separator = entry.getKey().indexOf('\u0000');
            if (separator <= 0 || separator >= entry.getKey().length() - 1
                    || !writeStockToDatabase(entry.getKey().substring(0, separator), entry.getKey().substring(separator + 1), entry.getValue())) {
                dirtyStock.add(entry.getKey());
            }
        }
    }

    private boolean adjustStock(ServerShopItem item, int delta) {
        if (!item.stockEnabled()) {
            return true;
        }
        ensureStockRow(item);
        int current = currentStock(item, item.stock());
        int updated = current + delta;
        if (updated < 0) {
            return false;
        }
        if (item.hasStockMaximum() && updated > item.maxStock()) {
            return false;
        }
        return updateStockCache(item, updated);
    }

    private void initializeStockRows() {
        for (ServerShopCategory category : categories.values()) {
            for (ServerShopItem item : category.items()) {
                if (item.stockEnabled()) {
                    ensureStockRow(item);
                    stockCache.put(stockKey(item), readStockFromDatabase(item, item.stock()));
                    clampStockToMax(item.categoryId(), item.id());
                }
            }
        }
    }

    private void ensureStockRow(ServerShopItem item) {
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement exists = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT stock FROM " + plugin.getDatabaseService().table("server_shop_stock") + " WHERE category_id = ? AND item_id = ?")) {
                exists.setString(1, item.categoryId());
                exists.setString(2, item.id());
                try (ResultSet result = exists.executeQuery()) {
                    if (result.next()) {
                        return;
                    }
                }
                try (PreparedStatement insert = plugin.getDatabaseService().connection().prepareStatement(
                        "INSERT INTO " + plugin.getDatabaseService().table("server_shop_stock") + " (category_id, item_id, stock, updated_at) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, item.categoryId());
                    insert.setString(2, item.id());
                    insert.setInt(3, Math.max(0, item.stock()));
                    insert.setLong(4, System.currentTimeMillis());
                    insert.executeUpdate();
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not initialize ServerShop stock.", exception);
            }
        }
    }

    private int currentStock(ServerShopItem item, int fallback) {
        Integer cached = stockCache.get(stockKey(item));
        if (cached != null) {
            return Math.max(0, cached);
        }
        int stock = readStockFromDatabase(item, fallback);
        stockCache.put(stockKey(item), stock);
        return stock;
    }

    private int readStockFromDatabase(ServerShopItem item, int fallback) {
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT stock FROM " + plugin.getDatabaseService().table("server_shop_stock") + " WHERE category_id = ? AND item_id = ?")) {
                statement.setString(1, item.categoryId());
                statement.setString(2, item.id());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return Math.max(0, result.getInt("stock"));
                    }
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not read ServerShop stock.", exception);
            }
        }
        return Math.max(0, fallback);
    }

    private boolean updateStockCache(ServerShopItem item, int stock) {
        stockCache.put(stockKey(item), Math.max(0, stock));
        dirtyStock.add(stockKey(item));
        return true;
    }

    private boolean writeStockToDatabase(String categoryId, String itemId, int stock) {
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "UPDATE " + plugin.getDatabaseService().table("server_shop_stock") + " SET stock = ?, updated_at = ? WHERE category_id = ? AND item_id = ?")) {
                statement.setInt(1, Math.max(0, stock));
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, categoryId);
                statement.setString(4, itemId);
                if (statement.executeUpdate() > 0) {
                    return true;
                }
                try (PreparedStatement insert = plugin.getDatabaseService().connection().prepareStatement(
                        "INSERT INTO " + plugin.getDatabaseService().table("server_shop_stock") + " (category_id, item_id, stock, updated_at) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, categoryId);
                    insert.setString(2, itemId);
                    insert.setInt(3, Math.max(0, stock));
                    insert.setLong(4, System.currentTimeMillis());
                    insert.executeUpdate();
                    return true;
                }
            } catch (SQLException exception) {
                plugin.getPluginLogService().error("Could not update ServerShop stock.", exception);
                return false;
            }
        }
    }

    private ServerShopItem currentItem(ServerShopItem item) {
        ServerShopCategory category = category(item.categoryId());
        return category == null ? null : category.item(item.id());
    }

    private String stockKey(ServerShopItem item) {
        return item.categoryId() + "\u0000" + item.id();
    }

    private Material material(String name) {
        Material material = Material.matchMaterial(name == null ? "STONE" : name);
        return material == null ? Material.STONE : material;
    }
}
