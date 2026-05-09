package de.craftplay.shop.core.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseService {
    void connect() throws SQLException;

    void close();

    Connection connection();

    String table(String tableName);

    Object lock();

    DatabaseType type();

    default boolean isMySql() {
        return type() == DatabaseType.MYSQL;
    }
}
