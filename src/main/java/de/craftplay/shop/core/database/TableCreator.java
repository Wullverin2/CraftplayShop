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
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "type TEXT, " +
                        "player_uuid TEXT, " +
                        "player_name TEXT, " +
                        "source TEXT, " +
                        "item_data TEXT, " +
                        "material TEXT, " +
                        "amount INTEGER, " +
                        "price_each DOUBLE, " +
                        "total_price DOUBLE, " +
                        "created_at BIGINT)");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("player_settings") + " (" +
                        "player_uuid TEXT PRIMARY KEY, " +
                        "player_name TEXT, " +
                        "language TEXT, " +
                        "direct_trade_enabled BOOLEAN, " +
                        "updated_at BIGINT)");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("server_shop_stock") + " (" +
                        "category_id TEXT, " +
                        "item_id TEXT, " +
                        "stock INTEGER, " +
                        "updated_at BIGINT, " +
                        "PRIMARY KEY (category_id, item_id))");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("server_shop_favorites") + " (" +
                        "player_uuid TEXT, " +
                        "category_id TEXT, " +
                        "item_id TEXT, " +
                        "created_at BIGINT, " +
                        "PRIMARY KEY (player_uuid, category_id, item_id))");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("player_shops") + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "owner_uuid TEXT, " +
                        "owner_name TEXT, " +
                        "type TEXT, " +
                        "world TEXT, " +
                        "container_x INTEGER, " +
                        "container_y INTEGER, " +
                        "container_z INTEGER, " +
                        "sign_x INTEGER, " +
                        "sign_y INTEGER, " +
                        "sign_z INTEGER, " +
                        "item_data TEXT, " +
                        "material TEXT, " +
                        "amount INTEGER, " +
                        "price DOUBLE, " +
                        "active BOOLEAN, " +
                        "created_at BIGINT, " +
                        "updated_at BIGINT)");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("imports") + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "importer_name TEXT, " +
                        "source_plugin TEXT, " +
                        "source_path TEXT, " +
                        "mode TEXT, " +
                        "status TEXT, " +
                        "created_by_uuid TEXT, " +
                        "created_by_name TEXT, " +
                        "created_at BIGINT, " +
                        "finished_at BIGINT, " +
                        "imported_count INTEGER, " +
                        "warning_count INTEGER, " +
                        "error_count INTEGER, " +
                        "backup_path TEXT, " +
                        "report_path TEXT)");

                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + database.table("import_mappings") + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "import_id INTEGER, " +
                        "source_plugin TEXT, " +
                        "source_identifier TEXT, " +
                        "target_type TEXT, " +
                        "target_id TEXT, " +
                        "notes TEXT)");
            }
        }
    }
}
