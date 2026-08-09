package me.bounser.nascraft.database.sqlite;

import com.zaxxer.hikari.HikariConfig;
import me.bounser.nascraft.database.BaseDatabase;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteDatabase extends BaseDatabase {

    private final File dataFolder;
    private final File dbFile;

    public SqliteDatabase(File dataFolder) {
        this.dataFolder = dataFolder;
        File dataDir = new File(dataFolder, "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dbFile = new File(dataDir, "sqlite.db");
    }

    @Override
    protected void configureHikari(HikariConfig config) {
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath()
                + "?foreign_keys=on&journal_mode=WAL&synchronous=NORMAL";
        config.setJdbcUrl(url);
        config.setDriverClassName("org.sqlite.JDBC");

        // Nascraft portfolio/leaderboard reads may perform nested database reads
        // while the outer query still owns its connection. A single-connection
        // pool therefore deadlocks itself (active=1, idle=0, waiting=1) and can
        // stall the Paper server thread when another operation, such as a player
        // join, needs the database at the same time. WAL mode supports concurrent
        // readers, so keep a small bounded pool instead of a single connection.
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setPoolName("Nascraft-SQLite");
    }

    @Override
    protected void onConnectionInit(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
        }
    }

    @Override
    protected void runMigrations(Connection connection) throws SQLException {
        createAllTables(connection);
        addColumnIfMissing(connection, "items", "version", "BIGINT NOT NULL DEFAULT 0");
        addMissingIndexes(connection);
    }

    @Override
    public void createTables() {
        withConnection((SqlConsumer) this::createAllTables);
    }

    private void createAllTables(Connection connection) throws SQLException {
        safeExec(connection, "CREATE TABLE IF NOT EXISTS items (" +
                "identifier TEXT PRIMARY KEY, " +
                "lastprice DOUBLE, " +
                "lowest DOUBLE, " +
                "highest DOUBLE, " +
                "stock DOUBLE DEFAULT 0, " +
                "taxes DOUBLE, " +
                "version BIGINT NOT NULL DEFAULT 0)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS prices_day (" +
                "identifier TEXT NOT NULL, " +
                "bucket_start TEXT NOT NULL, " +
                "open REAL NOT NULL, " +
                "high REAL NOT NULL, " +
                "low REAL NOT NULL, " +
                "close REAL NOT NULL, " +
                "volume REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (identifier, bucket_start))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS prices_month (" +
                "identifier TEXT NOT NULL, " +
                "bucket_start TEXT NOT NULL, " +
                "open REAL NOT NULL, " +
                "high REAL NOT NULL, " +
                "low REAL NOT NULL, " +
                "close REAL NOT NULL, " +
                "volume REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (identifier, bucket_start))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS prices_history (" +
                "identifier TEXT NOT NULL, " +
                "bucket_start TEXT NOT NULL, " +
                "open REAL NOT NULL, " +
                "high REAL NOT NULL, " +
                "low REAL NOT NULL, " +
                "close REAL NOT NULL, " +
                "volume REAL NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (identifier, bucket_start))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "amount INT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, identifier))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios_log (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "identifier TEXT NOT NULL, " +
                "amount INT NOT NULL, " +
                "contribution DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS portfolios_worth (" +
                "id INTEGER PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "day INT NOT NULL, " +
                "worth DOUBLE NOT NULL, " +
                "UNIQUE (uuid, day))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS capacities (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "capacity INT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS debt (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "amount DOUBLE NOT NULL DEFAULT 0, " +
                "last_interest_day INT NOT NULL DEFAULT 0, " +
                "interests_paid DOUBLE NOT NULL DEFAULT 0)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS trades (" +
                "id INTEGER PRIMARY KEY, " +
                "identifier TEXT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "value DOUBLE NOT NULL, " +
                "amount INT NOT NULL, " +
                "buy BOOLEAN NOT NULL, " +
                "admin BOOLEAN NOT NULL, " +
                "uuid VARCHAR(36))");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS names (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "name TEXT NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS cpi (" +
                "date TEXT PRIMARY KEY, " +
                "value DOUBLE NOT NULL)");

        safeExec(connection, "CREATE TABLE IF NOT EXISTS web_sessions (" +
                "session_hash VARCHAR(64) PRIMARY KEY, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "last_activity TEXT NOT NULL, " +
                "expires_at TEXT NOT NULL)");
    }

    private void addMissingIndexes(Connection connection) throws SQLException {
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_prices_day_identifier ON prices_day(identifier)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_prices_month_identifier ON prices_month(identifier)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_prices_history_identifier ON prices_history(identifier)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_trades_uuid ON trades(uuid)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_portfolios_worth_day ON portfolios_worth(day)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_web_sessions_player_uuid ON web_sessions(player_uuid)");
        safeExec(connection, "CREATE INDEX IF NOT EXISTS idx_web_sessions_expires_at ON web_sessions(expires_at)");
    }
}
