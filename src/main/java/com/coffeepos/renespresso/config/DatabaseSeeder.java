package com.coffeepos.renespresso.config;

import com.coffeepos.renespresso.util.PasswordUtil;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseSeeder {

    public static void main (String[] args) {
        createUsersTableIfNotExists();
        seedAdminAccount();
    }

    private static void createUsersTableIfNotExists() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS users (
                user_id INT(11) AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL UNIQUE,
                password VARCHAR(255) NOT NULL,
                full_name VARCHAR(100) NOT NULL,
                role ENUM('ADMIN', 'CASHIER', 'SUPERVISOR', 'MANAGER') NOT NULL DEFAULT 'CASHIER',
                is_active TINYINT(1) NOT NULL DEFAULT 1,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(),
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP()
            )
        """;

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("[DatabaseSeeder] Users table verified/created.");
        } catch (SQLException e) {
            System.err.println("[DatabaseSeeder] Error creating users table: " + e.getMessage());
        }
    }

    private static void seedAdminAccount() {
        String checkQuery = "SELECT COUNT(*) FROM users WHERE username = 'admin'";
        // Updated column names to match the database schema
        String insertQuery = "INSERT INTO users (username, password, full_name, role, is_active) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
             ResultSet rs = checkStmt.executeQuery()) {

            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                    insertStmt.setString(1, "admin");
                    insertStmt.setString(2, PasswordUtil.hashPassword("admin123"));
                    insertStmt.setString(3, "System Administrator");
                    insertStmt.setString(4, "ADMIN"); // Matches the enum requirement
                    insertStmt.setInt(5, 1);          // 1 represents true/active for tinyint(1)

                    if (insertStmt.executeUpdate() > 0) {
                        System.out.println("[DatabaseSeeder] Default admin account created successfully.");
                    }
                }
            } else {
                System.out.println("[DatabaseSeeder] Admin account already exists. Skipping seed.");
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseSeeder] Error seeding database: " + e.getMessage());
        }
    }
}