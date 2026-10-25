package com.coffeepos.renespresso.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DatabaseManager {

    private static HikariDataSource dataSource;
    private static String currentHost = "localhost";
    private static String currentPort = "3306";
    private static String currentDbName = "coffee";
    private static String currentUser = "root";
    private static String currentPassword = "";
    private static String configLocationDescription = "Embedded Defaults";
    private static String lastError = null;

    public record ConnectionTestResult(boolean success, String message, String details) {}

    static {
        loadAndInitialize();
    }

    private DatabaseManager() {
    }

    public static synchronized void loadAndInitialize() {
        Properties properties = loadProperties();

        currentHost = properties.getProperty("db.host", "localhost").trim();
        currentPort = properties.getProperty("db.port", "3306").trim();
        currentDbName = properties.getProperty("db.name", "coffee").trim();
        currentUser = properties.getProperty("db.user", "root").trim();
        currentPassword = properties.getProperty("db.password", "").trim();

        initPool(currentHost, currentPort, currentDbName, currentUser, currentPassword);
    }

    private static Properties loadProperties() {
        Properties props = new Properties();

        // 1. Current working directory (./db.properties)
        File localFile = new File("db.properties");
        if (localFile.isFile() && localFile.canRead()) {
            try (InputStream in = new FileInputStream(localFile)) {
                props.load(in);
                configLocationDescription = localFile.getAbsolutePath();
                return props;
            } catch (IOException e) {
                System.err.println("DatabaseManager: Error reading local db.properties: " + e.getMessage());
            }
        }

        // 2. User home directory (~/.renespresso/db.properties)
        File userHomeFile = Paths.get(System.getProperty("user.home"), ".renespresso", "db.properties").toFile();
        if (userHomeFile.isFile() && userHomeFile.canRead()) {
            try (InputStream in = new FileInputStream(userHomeFile)) {
                props.load(in);
                configLocationDescription = userHomeFile.getAbsolutePath();
                return props;
            } catch (IOException e) {
                System.err.println("DatabaseManager: Error reading user home db.properties: " + e.getMessage());
            }
        }

        // 3. Subdirectory config/db.properties
        File configFile = new File("config/db.properties");
        if (configFile.isFile() && configFile.canRead()) {
            try (InputStream in = new FileInputStream(configFile)) {
                props.load(in);
                configLocationDescription = configFile.getAbsolutePath();
                return props;
            } catch (IOException e) {
                System.err.println("DatabaseManager: Error reading config/db.properties: " + e.getMessage());
            }
        }

        // 4. Classpath resource (/db.properties)
        try (InputStream input = DatabaseManager.class.getResourceAsStream("/db.properties")) {
            if (input != null) {
                props.load(input);
                configLocationDescription = "Classpath (/db.properties)";
                return props;
            }
        } catch (IOException e) {
            System.err.println("DatabaseManager: Error reading classpath db.properties: " + e.getMessage());
        }

        configLocationDescription = "Default Fallback (localhost:3306/coffee)";
        return props;
    }

    private static synchronized void initPool(String host, String port, String dbName, String user, String password) {
        closePool();
        lastError = null;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + dbName
                + "?connectTimeout=5000&socketTimeout=30000&autoReconnect=true");
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("org.mariadb.jdbc.Driver");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(6000);
        config.setMaxLifetime(1800000);

        try {
            dataSource = new HikariDataSource(config);
            ensureSchema();
        } catch (Exception e) {
            lastError = e.getMessage();
            System.err.println("DatabaseManager: Failed to initialize connection pool to " + host + ":" + port + "/" + dbName + ": " + e.getMessage());
        }
    }

    public static ConnectionTestResult testConnection(String host, String port, String dbName, String user, String password) {
        String url = "jdbc:mariadb://" + host + ":" + port + "/" + dbName
                + "?connectTimeout=3000&socketTimeout=3000";
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            return new ConnectionTestResult(false, "MariaDB JDBC Driver not found", e.getMessage());
        }

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String product = conn.getMetaData().getDatabaseProductName();
            String version = conn.getMetaData().getDatabaseProductVersion();
            return new ConnectionTestResult(true,
                    "Connection successful!",
                    "Connected to " + product + " " + version + " on " + host + ":" + port);
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg.contains("Communications link failure") || msg.contains("Connection refused") || msg.contains("timed out")) {
                return new ConnectionTestResult(false,
                        "Server Unreachable",
                        "Could not connect to " + host + ":" + port + ".\n\n"
                                + "• Verify the Server IP address is correct.\n"
                                + "• Ensure MariaDB/MySQL is running on the server computer.\n"
                                + "• Make sure port " + port + " is allowed in the Server's Windows Firewall.");
            } else if (msg.contains("Access denied")) {
                return new ConnectionTestResult(false,
                        "Access Denied",
                        "User '" + user + "' could not log in from this computer.\n\n"
                                + "Ensure the database user has remote permissions ('%') on the server:\n"
                                + "GRANT ALL PRIVILEGES ON " + dbName + ".* TO '" + user + "'@'%' IDENTIFIED BY '...';");
            } else if (msg.contains("Unknown database")) {
                return new ConnectionTestResult(false,
                        "Database Not Found",
                        "Database \"" + dbName + "\" does not exist on server " + host + ".");
            }
            return new ConnectionTestResult(false, "Connection Failed", msg);
        }
    }

    public static synchronized ConnectionTestResult saveAndReconnect(String host, String port, String dbName, String user, String password) {
        // 1. Test before applying
        ConnectionTestResult test = testConnection(host, port, dbName, user, password);
        if (!test.success()) {
            return test;
        }

        // 2. Persist to external db.properties
        File targetFile = new File("db.properties");
        if (!targetFile.exists()) {
            File userHomeFile = Paths.get(System.getProperty("user.home"), ".renespresso", "db.properties").toFile();
            if (userHomeFile.exists()) {
                targetFile = userHomeFile;
            }
        }

        try {
            if (!targetFile.exists()) {
                // If in working directory, create it
                if (!targetFile.createNewFile()) {
                    Path userDir = Paths.get(System.getProperty("user.home"), ".renespresso");
                    Files.createDirectories(userDir);
                    targetFile = userDir.resolve("db.properties").toFile();
                }
            }

            Properties toSave = new Properties();
            toSave.setProperty("db.host", host);
            toSave.setProperty("db.port", port);
            toSave.setProperty("db.name", dbName);
            toSave.setProperty("db.user", user);
            toSave.setProperty("db.password", password);

            try (OutputStream out = new FileOutputStream(targetFile)) {
                toSave.store(out, "RenEspresso Client-to-Server Configuration");
            }
            configLocationDescription = targetFile.getAbsolutePath();
        } catch (IOException e) {
            System.err.println("DatabaseManager: Warning - could not write db.properties: " + e.getMessage());
        }

        // 3. Re-initialize pool
        currentHost = host;
        currentPort = port;
        currentDbName = dbName;
        currentUser = user;
        currentPassword = password;

        initPool(currentHost, currentPort, currentDbName, currentUser, currentPassword);

        if (dataSource != null && isConnected()) {
            return new ConnectionTestResult(true, "Connected & Saved", "Configuration saved to " + targetFile.getName() + " and connected successfully!");
        } else {
            return new ConnectionTestResult(false, "Pool Failed", lastError != null ? lastError : "Failed to establish connection pool.");
        }
    }

    private static void ensureSchema() {
        if (dataSource == null) return;
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS cashier_name VARCHAR(100) NULL AFTER cashier_id");
            st.executeUpdate("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS cashier_role VARCHAR(50) NULL AFTER cashier_name");
            st.executeUpdate("ALTER TABLE products ADD COLUMN IF NOT EXISTS is_archived TINYINT(1) NOT NULL DEFAULT 0");
            st.executeUpdate("ALTER TABLE categories ADD COLUMN IF NOT EXISTS is_archived TINYINT(1) NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            System.err.println("DatabaseManager: Schema check note: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection not available. Server: " + currentHost + ":" + currentPort
                    + " (" + (lastError != null ? lastError : "Check Server Connection Settings") + ")");
        }
        return dataSource.getConnection();
    }

    public static boolean isConnected() {
        if (dataSource == null || dataSource.isClosed()) return false;
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.setQueryTimeout(2);
            s.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
            } catch (Exception ignored) {}
        }
        dataSource = null;
    }

    public static String getHost() { return currentHost; }
    public static String getPort() { return currentPort; }
    public static String getDatabaseName() { return currentDbName; }
    public static String getUser() { return currentUser; }
    public static String getPassword() { return currentPassword; }
    public static String getConfigLocation() { return configLocationDescription; }
    public static String getLastError() { return lastError; }

    public static void main(String[] args) {
        System.out.println("Testing MariaDB connection via HikariCP...");
        try (Connection conn = getConnection()) {
            System.out.println("Connection status: SUCCESS!");
            System.out.println("Connected to: " + conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());
        } catch (SQLException e) {
            System.err.println("Connection status: FAILED!");
            e.printStackTrace();
        } finally {
            closePool();
        }
    }
}