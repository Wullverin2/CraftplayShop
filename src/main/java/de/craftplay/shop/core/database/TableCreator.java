package de.craftplay.shop.core.database;

import de.craftplay.shop.CraftplayShopPlugin;

import java.sql.SQLException;
import java.sql.Statement;

public class TableCreator {
    private final CraftplayShopPlugin plugin;

    public TableCreator(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void createTables() throws SQLException {
        DatabaseService database = plugin.getDatabaseService();
        synchronized (database.lock()) {
            try (Statement statement = database.connection().createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("transactions") + " (" +
                        idColumn() + ", " +
                        textColumn("type") + ", " +
                        uuidColumn("player_uuid") + ", " +
                        shortTextColumn("player_name") + ", " +
                        shortTextColumn("source") + ", " +
                        largeTextColumn("item_data") + ", " +
                        shortTextColumn("material") + ", " +
                        "amount INTEGER, " +
                        "price_each DOUBLE, " +
                        "total_price DOUBLE, " +
                        "created_at BIGINT)");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("player_settings") + " (" +
                        uuidColumn("player_uuid") + " PRIMARY KEY, " +
                        shortTextColumn("player_name") + ", " +
                        shortTextColumn("language") + ", " +
                        "direct_trade_enabled BOOLEAN, " +
                        "updated_at BIGINT)");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("server_shop_stock") + " (" +
                        shortTextColumn("category_id") + ", " +
                        shortTextColumn("item_id") + ", " +
                        "stock INTEGER, " +
                        "updated_at BIGINT, " +
                        "PRIMARY KEY (category_id, item_id))");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("server_shop_favorites") + " (" +
                        uuidColumn("player_uuid") + ", " +
                        shortTextColumn("category_id") + ", " +
                        shortTextColumn("item_id") + ", " +
                        "created_at BIGINT, " +
                        "PRIMARY KEY (player_uuid, category_id, item_id))");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("player_shops") + " (" +
                        idColumn() + ", " +
                        uuidColumn("owner_uuid") + ", " +
                        shortTextColumn("owner_name") + ", " +
                        shortTextColumn("type") + ", " +
                        shortTextColumn("world") + ", " +
                        "container_x INTEGER, " +
                        "container_y INTEGER, " +
                        "container_z INTEGER, " +
                        "sign_x INTEGER, " +
                        "sign_y INTEGER, " +
                        "sign_z INTEGER, " +
                        largeTextColumn("item_data") + ", " +
                        shortTextColumn("material") + ", " +
                        "amount INTEGER, " +
                        "price DOUBLE, " +
                        largeTextColumn("trade_item_data") + ", " +
                        shortTextColumn("trade_material") + ", " +
                        "trade_amount INTEGER, " +
                        shortTextColumn("display_type") + ", " +
                        "active BOOLEAN, " +
                        "created_at BIGINT, " +
                        "updated_at BIGINT)");
                addColumnIfMissing(statement, database.table("player_shops"), "display_type", "TEXT");
                addColumnIfMissing(statement, database.table("player_shops"), "trade_item_data", "TEXT");
                addColumnIfMissing(statement, database.table("player_shops"), "trade_material", "TEXT");
                addColumnIfMissing(statement, database.table("player_shops"), "trade_amount", "INTEGER");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("autosell_chests") + " (" +
                        idColumn() + ", " +
                        uuidColumn("owner_uuid") + ", " +
                        shortTextColumn("owner_name") + ", " +
                        shortTextColumn("world") + ", " +
                        "x INTEGER, " +
                        "y INTEGER, " +
                        "z INTEGER, " +
                        textColumn("name") + ", " +
                        "active BOOLEAN, " +
                        "notify_owner BOOLEAN, " +
                        "interval_level INTEGER, " +
                        "multiplier_level INTEGER, " +
                        "multiplier DOUBLE, " +
                        "total_items_sold INTEGER, " +
                        "total_money_earned DOUBLE, " +
                        "last_sold_at BIGINT, " +
                        "created_at BIGINT, " +
                        "updated_at BIGINT)");
                addColumnIfMissing(statement, database.table("autosell_chests"), "interval_level", "INTEGER");
                addColumnIfMissing(statement, database.table("autosell_chests"), "multiplier_level", "INTEGER");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("autosell_logs") + " (" +
                        idColumn() + ", " +
                        "chest_id INTEGER, " +
                        uuidColumn("owner_uuid") + ", " +
                        shortTextColumn("owner_name") + ", " +
                        shortTextColumn("world") + ", " +
                        "x INTEGER, " +
                        "y INTEGER, " +
                        "z INTEGER, " +
                        shortTextColumn("material") + ", " +
                        "amount INTEGER, " +
                        "price_each DOUBLE, " +
                        "total_price DOUBLE, " +
                        "created_at BIGINT)");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("autosell_trust") + " (" +
                        "chest_id INTEGER, " +
                        uuidColumn("player_uuid") + ", " +
                        shortTextColumn("player_name") + ", " +
                        "open_allowed BOOLEAN, " +
                        "manage_allowed BOOLEAN, " +
                        "upgrade_allowed BOOLEAN, " +
                        "delete_allowed BOOLEAN, " +
                        "created_at BIGINT, " +
                        "updated_at BIGINT, " +
                        "PRIMARY KEY (chest_id, player_uuid))");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("imports") + " (" +
                        idColumn() + ", " +
                        shortTextColumn("importer_name") + ", " +
                        shortTextColumn("source_plugin") + ", " +
                        textColumn("source_path") + ", " +
                        shortTextColumn("mode") + ", " +
                        shortTextColumn("status") + ", " +
                        uuidColumn("created_by_uuid") + ", " +
                        shortTextColumn("created_by_name") + ", " +
                        "created_at BIGINT, " +
                        "finished_at BIGINT, " +
                        "imported_count INTEGER, " +
                        "warning_count INTEGER, " +
                        "error_count INTEGER, " +
                        textColumn("backup_path") + ", " +
                        textColumn("report_path") + ")");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("import_mappings") + " (" +
                        idColumn() + ", " +
                        "import_id INTEGER, " +
                        shortTextColumn("source_plugin") + ", " +
                        textColumn("source_identifier") + ", " +
                        shortTextColumn("target_type") + ", " +
                        shortTextColumn("target_id") + ", " +
                        textColumn("notes") + ")");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("auction_house_listings") + " (" +
                        idColumn() + ", " +
                        uuidColumn("seller_uuid") + ", " +
                        shortTextColumn("seller_name") + ", " +
                        shortTextColumn("world") + ", " +
                        largeTextColumn("item_data") + ", " +
                        shortTextColumn("material") + ", " +
                        "amount INTEGER, " +
                        "price DOUBLE, " +
                        "fee DOUBLE, " +
                        shortTextColumn("status") + ", " +
                        uuidColumn("buyer_uuid") + ", " +
                        shortTextColumn("buyer_name") + ", " +
                        "created_at BIGINT, " +
                        "expires_at BIGINT, " +
                        "sold_at BIGINT, " +
                        "claimed_at BIGINT)");
            }
        }
    }

    private void addColumnIfMissing(Statement statement, String table, String column, String type) throws SQLException {
        try {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (SQLException exception) {
            String message = exception.getMessage();
            if (message == null || !message.toLowerCase(java.util.Locale.ROOT).contains("duplicate column")) {
                throw exception;
            }
        }
    }

    private String idColumn() {
        return plugin.getDatabaseService().isMySql()
                ? "id BIGINT PRIMARY KEY AUTO_INCREMENT"
                : "id INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    private String uuidColumn(String name) {
        return namedColumn(name, plugin.getDatabaseService().isMySql() ? "VARCHAR(36)" : "TEXT");
    }

    private String shortTextColumn(String name) {
        return namedColumn(name, plugin.getDatabaseService().isMySql() ? "VARCHAR(128)" : "TEXT");
    }

    private String textColumn(String name) {
        return namedColumn(name, plugin.getDatabaseService().isMySql() ? "VARCHAR(255)" : "TEXT");
    }

    private String largeTextColumn(String name) {
        return namedColumn(name, plugin.getDatabaseService().isMySql() ? "LONGTEXT" : "TEXT");
    }

    private String namedColumn(String name, String type) {
        return name + " " + type;
    }
}
