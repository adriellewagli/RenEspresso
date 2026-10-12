package com.coffeepos.renespresso.config;

import com.coffeepos.renespresso.util.PasswordUtil;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseSeeder {

    public static void main(String[] args) {
        createUsersTableIfNotExists();
        seedAdminAccount();
        seedCashierAccount();
        seedManagerAccount();
    }

    private static void createUsersTableIfNotExists() {
        String createTableSQL = """
                    CREATE TABLE IF NOT EXISTS users (
                        user_id INT(11) AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(50) NOT NULL UNIQUE,
                        password VARCHAR(255) NOT NULL,
                        full_name VARCHAR(100) NOT NULL,
                        role ENUM('ADMIN', 'CASHIER', 'MANAGER') NOT NULL DEFAULT 'CASHIER',
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
        seedAccountIfNotExists("admin", "admin123", "System Administrator", "ADMIN");
    }

    private static void seedCashierAccount() {
        seedAccountIfNotExists("cashier", "cashier123", "Default Cashier", "CASHIER");
    }

    private static void seedManagerAccount() {
        seedAccountIfNotExists("manager", "manager123", "Default Manager", "MANAGER");
    }

    private static void seedAccountIfNotExists(String username, String plainPassword, String fullName, String role) {
        String checkQuery = "SELECT COUNT(*) FROM users WHERE username = ?";
        String insertQuery = "INSERT INTO users (username, password, full_name, role, is_active) VALUES (?, ?, ?, ?, 1)";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {

            checkStmt.setString(1, username);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                        insertStmt.setString(1, username);
                        insertStmt.setString(2, PasswordUtil.hashPassword(plainPassword));
                        insertStmt.setString(3, fullName);
                        insertStmt.setString(4, role);

                        if (insertStmt.executeUpdate() > 0) {
                            System.out.println("[DatabaseSeeder] Default " + role.toLowerCase() + " account ('" + username + "') created successfully.");
                        }
                    }
                } else {
                    System.out.println("[DatabaseSeeder] " + role + " account ('" + username + "') already exists. Skipping seed.");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseSeeder] Error seeding " + username + ": " + e.getMessage());
        }
    }
}