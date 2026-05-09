package de.craftplay.shop.core.database;

import de.craftplay.shop.CraftplayShopPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class MySqlDatabaseService implements DatabaseService {
    private final CraftplayShopPlugin plugin;
    private final Object lock = new Object();
    private Connection connection;

    public MySqlDatabaseService(CraftplayShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() throws SQLException {
        String host = plugin.getConfig().getString("mysql.host", "localhost");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String database = plugin.getConfig().getString("mysql.database", "minecraft");
        String username = plugin.getConfig().getString("mysql.username", "root");
        String password = plugin.getConfig().getString("mysql.password", "");
        boolean useSsl = plugin.getConfig().getBoolean("mysql.useSSL", false);

        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("useSSL", Boolean.toString(useSsl));
        properties.setProperty("characterEncoding", "utf8");
        properties.setProperty("useUnicode", "true");
        properties.setProperty("serverTimezone", "UTC");
        properties.setProperty("connectTimeout", "10000");
        properties.setProperty("socketTimeout", "10000");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&allowPublicKeyRetrieval=true"
                + "&useUnicode=true"
                + "&characterEncoding=utf8"
                + "&serverTimezone=UTC";
        connection = DriverManager.getConnection(url, properties);
        connection.setAutoCommit(true);
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException exception) {
                    plugin.getPluginLogService().error("Could not close MySQL connection.", exception);
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

    @Override
    public DatabaseType type() {
        return DatabaseType.MYSQL;
    }
}
