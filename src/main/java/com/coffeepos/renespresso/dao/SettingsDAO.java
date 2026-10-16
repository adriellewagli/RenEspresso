package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** Reads and writes the key/value `settings` table. */
public final class SettingsDAO {

    private SettingsDAO() {}

    public static void ensureTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS settings ("
                + "setting_key VARCHAR(100) NOT NULL, "
                + "setting_value TEXT DEFAULT NULL, "
                + "updated_at TIMESTAMP NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(), "
                + "PRIMARY KEY (setting_key)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
        try (Connection conn = DatabaseManager.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    public static Map<String, String> loadAll() throws SQLException {
        Map<String, String> map = new HashMap<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT setting_key, setting_value FROM settings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String v = rs.getString("setting_value");
                map.put(rs.getString("setting_key"), v == null ? "" : v);
            }
        }
        return map;
    }

    /** Saves all values in one transaction (insert new keys, update existing ones). */
    public static void saveAll(Map<String, String> values) throws SQLException {
        String sql = "INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)";
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<String, String> e : values.entrySet()) {
                    ps.setString(1, e.getKey());
                    ps.setString(2, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }
}