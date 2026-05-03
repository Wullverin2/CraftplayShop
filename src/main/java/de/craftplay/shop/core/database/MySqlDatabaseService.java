package de.craftplay.shop.core.database;

import de.craftplay.shop.CraftplayShopPlugin;

import java.sql.Connection;
import java.sql.SQLException;

public class MySqlDatabaseService implements DatabaseService {
    private final CraftplayShopPlugin plugin;
    private final Object lock = new Object();

    public MySqlDatabaseService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() throws SQLException {
        throw new SQLException("MySQL is prepared as a Phase 1 skeleton and not enabled yet.");
    }

    @Override
    public void close() {
    }

    @Override
    public Connection connection() {
        return null;
    }

    @Override
    public String table(String tableName) {
        return plugin.getConfigService().tablePrefix() + tableName;
    }

    @Override
    public Object lock() {
        return lock;
    }
}
