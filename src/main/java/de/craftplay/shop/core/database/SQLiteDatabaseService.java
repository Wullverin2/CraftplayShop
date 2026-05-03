package de.craftplay.shop.core.database;

import de.craftplay.shop.CraftplayShopPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLiteDatabaseService implements DatabaseService {
    private final CraftplayShopPlugin plugin;
    private final Object lock = new Object();
    private Connection connection;

    public SQLiteDatabaseService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() throws SQLException {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        dataFolder.mkdirs();
        File database = new File(dataFolder, "storage.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not close SQLite connection.", exception);
                }
            }
        }
    }

    @Override
    public Connection connection() {
        return connection;
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
