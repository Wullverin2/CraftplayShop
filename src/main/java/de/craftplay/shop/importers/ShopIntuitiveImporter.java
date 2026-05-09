package de.craftplay.shop.importers;

import de.craftplay.shop.CraftplayShopPlugin;
import de.craftplay.shop.playershop.PlayerShopDisplayType;
import de.craftplay.shop.playershop.PlayerShopType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ShopIntuitiveImporter {
    private final CraftplayShopPlugin plugin;
    private final ImporterService importerService;

    public ShopIntuitiveImporter(CraftplayShopPlugin plugin, ImporterService importerService) {
        this.plugin = plugin;
        this.importerService = importerService;
    }

    public ImporterService.ImportExecution preview(File source) {
        ParseResult result = parse(source, false);
        return ImporterService.ImportExecution.of(result.report(), "", result.report().successful());
    }

    public ImporterService.ImportExecution apply(File source, ImportMode mode) {
        ParseResult result = parse(source, true);
        if (!result.report().successful() && result.shops().isEmpty()) {
            return ImporterService.ImportExecution.of(result.report(), "", false);
        }
        File backup = importerService.createBackupFile("playershops", ".yml");
        try {
            writeBackup(backup);
        } catch (IOException | SQLException exception) {
            plugin.getPluginLogService().error("Could not create player shop import backup.", exception);
            return ImporterService.ImportExecution.of(new ImportReport(
                    0,
                    result.report().warningCount(),
                    result.report().errorCount() + 1,
                    List.of("PlayerShop backup could not be created."),
                    result.report().warnings(),
                    List.of(exception.getMessage() == null ? "Unknown backup error" : exception.getMessage())
            ), "", false);
        }

        try {
            if (mode == ImportMode.REPLACE) {
                clearPlayerShops();
            }
            List<ImporterService.ImportMapping> mappings = new ArrayList<>();
            int imported = insertShops(result.shops(), mode == ImportMode.MERGE, result.warnings(), mappings);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getPlayerShopService().load());
            ImportReport report = new ImportReport(
                    imported,
                    result.warnings().size(),
                    result.errors().size(),
                    List.of("Imported player shops: " + imported),
                    result.warnings(),
                    result.errors()
            );
            return new ImporterService.ImportExecution(report, backup.getAbsolutePath(), report.successful(), mappings);
        } catch (SQLException exception) {
            plugin.getPluginLogService().error("Could not import intuitive shops.", exception);
            return ImporterService.ImportExecution.of(new ImportReport(
                    0,
                    result.warnings().size(),
                    result.errors().size() + 1,
                    List.of("PlayerShop import failed."),
                    result.warnings(),
                    append(result.errors(), exception.getMessage() == null ? "Unknown SQL error" : exception.getMessage())
            ), backup.getAbsolutePath(), false);
        }
    }

    public boolean rollback(File backupFile) {
        if (backupFile == null || !backupFile.isFile()) {
            return false;
        }
        try {
            YamlConfiguration backup = YamlConfiguration.loadConfiguration(backupFile);
            clearPlayerShops();
            ConfigurationSection section = backup.getConfigurationSection("shops");
            if (section != null) {
                List<ImportedPlayerShop> shops = new ArrayList<>();
                for (String key : section.getKeys(false)) {
                    ConfigurationSection shopSection = section.getConfigurationSection(key);
                    if (shopSection == null) {
                        continue;
                    }
                    ItemStack itemStack = plugin.getItemSerializer().deserialize(shopSection.getString("item_data", ""));
                    ItemStack tradeItem = plugin.getItemSerializer().deserialize(shopSection.getString("trade_item_data", ""));
                    shops.add(new ImportedPlayerShop(
                            UUID.fromString(shopSection.getString("owner_uuid")),
                            shopSection.getString("owner_name", "Unknown"),
                            PlayerShopType.valueOf(shopSection.getString("type", "SELL")),
                            shopSection.getString("world"),
                            shopSection.getInt("container_x"),
                            shopSection.getInt("container_y"),
                            shopSection.getInt("container_z"),
                            shopSection.getInt("sign_x"),
                            shopSection.getInt("sign_y"),
                            shopSection.getInt("sign_z"),
                            itemStack,
                            shopSection.getInt("amount"),
                            shopSection.getDouble("price"),
                            tradeItem,
                            shopSection.getInt("trade_amount"),
                            displayType(shopSection.getString("display_type")),
                            "backup:" + key
                    ));
                }
                insertShops(shops, false, new ArrayList<>(), new ArrayList<>());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getPlayerShopService().load());
            return true;
        } catch (Exception exception) {
            plugin.getPluginLogService().error("Could not rollback player shop import.", exception);
            return false;
        }
    }

    private ParseResult parse(File source, boolean validatePhysical) {
        File dataFolder = resolveDataFolder(source);
        List<String> summary = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ImportedPlayerShop> shops = new ArrayList<>();
        if (dataFolder == null || !dataFolder.isDirectory()) {
            errors.add("Shop intuitive data folder not found.");
            return new ParseResult(shops, warnings, errors, new ImportReport(0, warnings.size(), errors.size(), summary, warnings, errors));
        }
        File[] files = dataFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml")
                && !"gambledisplayitem.yml".equalsIgnoreCase(name)
                && !"itemcurrency.yml".equalsIgnoreCase(name));
        if (files == null || files.length == 0) {
            errors.add("No shop data files found in " + dataFolder.getAbsolutePath());
            return new ParseResult(shops, warnings, errors, new ImportReport(0, warnings.size(), errors.size(), summary, warnings, errors));
        }
        java.util.Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection shopsSection = yaml.getConfigurationSection("shops");
            if (shopsSection == null) {
                continue;
            }
            for (String ownerId : shopsSection.getKeys(false)) {
                ConfigurationSection ownerSection = shopsSection.getConfigurationSection(ownerId);
                if (ownerSection == null) {
                    continue;
                }
                OfflinePlayer owner = Bukkit.getOfflinePlayer(UUID.fromString(ownerId));
                for (String shopId : ownerSection.getKeys(false)) {
                    ConfigurationSection shopSection = ownerSection.getConfigurationSection(shopId);
                    if (shopSection == null) {
                        continue;
                    }
                    ImportedPlayerShop imported = parseShop(file.getName(), ownerId, owner == null ? "Unknown" : owner.getName(), shopId, shopSection, validatePhysical, warnings);
                    if (imported != null) {
                        shops.add(imported);
                    }
                }
            }
        }
        summary.add("Shop intuitive player shops: " + shops.size());
        return new ParseResult(shops, warnings, errors, new ImportReport(shops.size(), warnings.size(), errors.size(), summary, warnings, errors));
    }

    private ImportedPlayerShop parseShop(String sourceFileName, String ownerId, String ownerName, String shopId,
                                         ConfigurationSection shopSection, boolean validatePhysical, List<String> warnings) {
        String typeValue = shopSection.getString("type", "sell").toLowerCase(Locale.ROOT);
        if ("gamble".equals(typeValue)) {
            warnings.add(sourceFileName + "#" + shopId + " was skipped because gamble shops are not imported.");
            return null;
        }
        PlayerShopType type = switch (typeValue) {
            case "sell" -> PlayerShopType.SELL;
            case "buy" -> PlayerShopType.BUY;
            case "combo" -> PlayerShopType.BUY_SELL;
            case "barter" -> PlayerShopType.TRADE_ITEM;
            default -> null;
        };
        if (type == null) {
            warnings.add(sourceFileName + "#" + shopId + " was skipped because type " + typeValue + " is unsupported.");
            return null;
        }

        Location signLocation = location(shopSection.getString("location", ""));
        if (signLocation == null) {
            warnings.add(sourceFileName + "#" + shopId + " was skipped because location is invalid.");
            return null;
        }
        Block signBlock = signLocation.getBlock();
        if (validatePhysical && !(signBlock.getState() instanceof Sign)) {
            warnings.add(sourceFileName + "#" + shopId + " was skipped because the sign no longer exists.");
            return null;
        }
        Block containerBlock = resolveContainer(signBlock);
        if (validatePhysical && (containerBlock == null || !(containerBlock.getState() instanceof Container))) {
            warnings.add(sourceFileName + "#" + shopId + " was skipped because the attached container no longer exists.");
            return null;
        }
        if (containerBlock == null) {
            return null;
        }

        ItemStack itemStack = shopSection.getItemStack("item");
        if (itemStack == null || itemStack.getType().isAir()) {
            warnings.add(sourceFileName + "#" + shopId + " was skipped because the sold item is missing.");
            return null;
        }
        itemStack = itemStack.clone();
        itemStack.setAmount(1);

        ItemStack barterItem = null;
        int barterAmount = 0;
        if (type == PlayerShopType.TRADE_ITEM) {
            barterItem = shopSection.getItemStack("barterItem");
            barterAmount = shopSection.getInt("barterAmount", 0);
            if (barterItem == null || barterItem.getType().isAir() || barterAmount <= 0) {
                warnings.add(sourceFileName + "#" + shopId + " was skipped because the barter item is incomplete.");
                return null;
            }
            barterItem = barterItem.clone();
            barterItem.setAmount(1);
        }
        return new ImportedPlayerShop(
                UUID.fromString(ownerId),
                ownerName == null ? "Unknown" : ownerName,
                type,
                signLocation.getWorld().getName(),
                containerBlock.getX(),
                containerBlock.getY(),
                containerBlock.getZ(),
                signLocation.getBlockX(),
                signLocation.getBlockY(),
                signLocation.getBlockZ(),
                itemStack,
                Math.max(1, shopSection.getInt("amount", 1)),
                Math.max(0.0D, shopSection.getDouble("price", 0.0D)),
                barterItem,
                barterAmount,
                displayType(shopSection.getString("displayType", plugin.getConfig().getString("playerShops.creation.defaultDisplayType", "ITEM"))),
                sourceFileName + "#" + shopId
        );
    }

    private File resolveDataFolder(File source) {
        if (source == null) {
            return null;
        }
        if (source.isDirectory()) {
            File nested = new File(source, "Data");
            if (nested.isDirectory()) {
                return nested;
            }
            return source;
        }
        return null;
    }

    private Location location(String serialized) {
        String[] parts = serialized.split(",");
        if (parts.length != 4) {
            return null;
        }
        if (Bukkit.getWorld(parts[0]) == null) {
            return null;
        }
        try {
            return new Location(Bukkit.getWorld(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Block resolveContainer(Block signBlock) {
        if (signBlock.getState() instanceof Sign sign) {
            if (sign.getBlockData() instanceof WallSign wallSign) {
                return signBlock.getRelative(wallSign.getFacing().getOppositeFace());
            }
        }
        for (org.bukkit.block.BlockFace face : List.of(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN)) {
            Block relative = signBlock.getRelative(face);
            if (relative.getState() instanceof Container) {
                return relative;
            }
        }
        return null;
    }

    private void clearPlayerShops() throws SQLException {
        String table = plugin.getDatabaseService().table("player_shops");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement("DELETE FROM " + table)) {
                statement.executeUpdate();
            }
        }
    }

    private int insertShops(List<ImportedPlayerShop> shops, boolean skipExisting, List<String> warnings, List<ImporterService.ImportMapping> mappings) throws SQLException {
        if (shops.isEmpty()) {
            return 0;
        }
        String table = plugin.getDatabaseService().table("player_shops");
        int imported = 0;
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "INSERT INTO " + table + " (owner_uuid, owner_name, type, world, container_x, container_y, container_z, sign_x, sign_y, sign_z, item_data, material, amount, price, trade_item_data, trade_material, trade_amount, display_type, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (ImportedPlayerShop shop : shops) {
                    if (skipExisting && exists(shop)) {
                        warnings.add(shop.sourceIdentifier() + " was skipped because a CraftplayShop already uses that sign or container.");
                        continue;
                    }
                    long now = System.currentTimeMillis();
                    statement.setString(1, shop.ownerUuid().toString());
                    statement.setString(2, shop.ownerName());
                    statement.setString(3, shop.type().name());
                    statement.setString(4, shop.world());
                    statement.setInt(5, shop.containerX());
                    statement.setInt(6, shop.containerY());
                    statement.setInt(7, shop.containerZ());
                    statement.setInt(8, shop.signX());
                    statement.setInt(9, shop.signY());
                    statement.setInt(10, shop.signZ());
                    statement.setString(11, plugin.getItemSerializer().serialize(shop.itemStack()));
                    statement.setString(12, shop.itemStack().getType().name());
                    statement.setInt(13, shop.amount());
                    statement.setDouble(14, shop.price());
                    statement.setString(15, plugin.getItemSerializer().serialize(shop.tradeItemStack()));
                    statement.setString(16, shop.tradeItemStack() == null ? "" : shop.tradeItemStack().getType().name());
                    statement.setInt(17, shop.tradeAmount());
                    statement.setString(18, shop.displayType().name());
                    statement.setBoolean(19, true);
                    statement.setLong(20, now);
                    statement.setLong(21, now);
                    statement.addBatch();
                    mappings.add(new ImporterService.ImportMapping(
                            "Shop",
                            shop.sourceIdentifier(),
                            "PLAYER_SHOP",
                            shop.world() + ":" + shop.signX() + ":" + shop.signY() + ":" + shop.signZ(),
                            shop.type().name()
                    ));
                    imported++;
                }
                statement.executeBatch();
            }
        }
        return imported;
    }

    private boolean exists(ImportedPlayerShop shop) throws SQLException {
        String table = plugin.getDatabaseService().table("player_shops");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement(
                    "SELECT id FROM " + table + " WHERE world = ? AND ((container_x = ? AND container_y = ? AND container_z = ?) OR (sign_x = ? AND sign_y = ? AND sign_z = ?)) LIMIT 1")) {
                statement.setString(1, shop.world());
                statement.setInt(2, shop.containerX());
                statement.setInt(3, shop.containerY());
                statement.setInt(4, shop.containerZ());
                statement.setInt(5, shop.signX());
                statement.setInt(6, shop.signY());
                statement.setInt(7, shop.signZ());
                return statement.executeQuery().next();
            }
        }
    }

    private void writeBackup(File file) throws IOException, SQLException {
        YamlConfiguration yaml = new YamlConfiguration();
        String table = plugin.getDatabaseService().table("player_shops");
        synchronized (plugin.getDatabaseService().lock()) {
            try (PreparedStatement statement = plugin.getDatabaseService().connection().prepareStatement("SELECT * FROM " + table);
                 java.sql.ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String path = "shops." + result.getLong("id");
                    yaml.set(path + ".owner_uuid", result.getString("owner_uuid"));
                    yaml.set(path + ".owner_name", result.getString("owner_name"));
                    yaml.set(path + ".type", result.getString("type"));
                    yaml.set(path + ".world", result.getString("world"));
                    yaml.set(path + ".container_x", result.getInt("container_x"));
                    yaml.set(path + ".container_y", result.getInt("container_y"));
                    yaml.set(path + ".container_z", result.getInt("container_z"));
                    yaml.set(path + ".sign_x", result.getInt("sign_x"));
                    yaml.set(path + ".sign_y", result.getInt("sign_y"));
                    yaml.set(path + ".sign_z", result.getInt("sign_z"));
                    yaml.set(path + ".item_data", result.getString("item_data"));
                    yaml.set(path + ".amount", result.getInt("amount"));
                    yaml.set(path + ".price", result.getDouble("price"));
                    yaml.set(path + ".trade_item_data", result.getString("trade_item_data"));
                    yaml.set(path + ".trade_amount", result.getInt("trade_amount"));
                    yaml.set(path + ".display_type", result.getString("display_type"));
                }
            }
        }
        yaml.save(file);
    }

    private PlayerShopDisplayType displayType(String value) {
        try {
            return PlayerShopDisplayType.valueOf(value == null ? "ITEM" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PlayerShopDisplayType.ITEM;
        }
    }

    private List<String> append(List<String> values, String extra) {
        List<String> merged = new ArrayList<>(values);
        merged.add(extra);
        return merged;
    }

    private record ParseResult(List<ImportedPlayerShop> shops, List<String> warnings, List<String> errors, ImportReport report) {
    }

    private record ImportedPlayerShop(UUID ownerUuid,
                                      String ownerName,
                                      PlayerShopType type,
                                      String world,
                                      int containerX,
                                      int containerY,
                                      int containerZ,
                                      int signX,
                                      int signY,
                                      int signZ,
                                      ItemStack itemStack,
                                      int amount,
                                      double price,
                                      ItemStack tradeItemStack,
                                      int tradeAmount,
                                      PlayerShopDisplayType displayType,
                                      String sourceIdentifier) {
    }
}
