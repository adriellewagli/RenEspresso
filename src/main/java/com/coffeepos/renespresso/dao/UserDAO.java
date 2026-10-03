package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.model.User;
import com.coffeepos.renespresso.util.PasswordUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    // AUTHENTICATE USER LOGIN WITH JBCRYPT
    public static User authenticate(String username, String plainPassword) {
        String query = "SELECT user_id, username, password, full_name, role, is_active FROM users "
                + "WHERE LOWER(username) = LOWER(?) AND is_active = 1";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password");

                    System.out.println("[DEBUG] User found in DB: " + rs.getString("username"));
                    System.out.println("[DEBUG] Stored Hash: " + storedHash);

                    boolean matches = PasswordUtil.checkPassword(plainPassword, storedHash);
                    System.out.println("[DEBUG] Password Matches: " + matches);

                    if (matches) {
                        return new User(
                                rs.getInt("user_id"),
                                rs.getString("username"),
                                rs.getString("full_name"),
                                rs.getString("role"),
                                rs.getBoolean("is_active") // Matches new User constructor
                        );
                    }
                } else {
                    System.out.println("[DEBUG] User not found or inactive.");
                }
            }
        } catch (SQLException e) {
            System.err.println("UserDAO Authentication Error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // ADMIN/SUPERVISOR/MANAGER VALIDATION FOR AUTHORIZATION MODAL WITH JBCRYPT
    public static boolean validateAdminCredentials(String username, String plainPassword) {
        String query = "SELECT password, role FROM users WHERE username = ? "
                + "AND role IN ('ADMIN', 'MANAGER', 'SUPERVISOR') AND is_active = 1";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password");
                    return PasswordUtil.checkPassword(plainPassword, storedHash);
                }
            }
        } catch (SQLException e) {
            System.err.println("UserDAO Admin Validation Error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public static boolean registerUser(User user, String plainPassword) {
        String query = "INSERT INTO users (username, password, full_name, role, is_active) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            String hashedPassword = PasswordUtil.hashPassword(plainPassword);
            stmt.setString(1, user.getUsername());
            stmt.setString(2, hashedPassword);
            stmt.setString(3, user.getFullName());
            stmt.setString(4, user.getRole() != null ? user.getRole().toUpperCase() : "CASHIER");
            stmt.setInt(5, user.isActive() ? 1 : 0);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("UserDAO Registration Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Overloaded registerUser for direct parameter registration.
    public static boolean registerUser(String username, String plainPassword, String fullName, String role) {
        User user = new User(0, username, fullName, role, true);
        return registerUser(user, plainPassword);
    }

}