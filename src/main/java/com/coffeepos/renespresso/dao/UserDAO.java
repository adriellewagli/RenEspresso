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
        String query = "SELECT user_id, username, password_hash, full_name, role, status FROM users "
                + "WHERE LOWER(username) = LOWER(?) AND status = 'active'";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");

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
                                rs.getString("status")
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
        String query = "SELECT password_hash, role FROM users WHERE username = ? "
                + "AND LOWER(role) IN ('admin', 'manager', 'supervisor', 'superadmin') AND status = 'active'";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    return PasswordUtil.checkPassword(plainPassword, storedHash);
                }
            }
        } catch (SQLException e) {
            System.err.println("UserDAO Admin Validation Error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Registers a new user account by hashing the plain password using jBCrypt.
     */
    public static boolean registerUser(User user, String plainPassword) {
        String query = "INSERT INTO users (username, password_hash, full_name, role, status) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            // Hash the plain text password before saving
            String hashedPassword = PasswordUtil.hashPassword(plainPassword);

            stmt.setString(1, user.getUsername());
            stmt.setString(2, hashedPassword);
            stmt.setString(3, user.getFullName());
            stmt.setString(4, user.getRole() != null ? user.getRole().toLowerCase() : "cashier");
            stmt.setString(5, user.getStatus() != null ? user.getStatus().toLowerCase() : "active");

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("UserDAO Registration Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Overloaded registerUser for direct parameter registration.
    public static boolean registerUser(String username, String plainPassword, String fullName, String role) {
        User user = new User(0, username, fullName, role, "active");
        return registerUser(user, plainPassword);
    }

    // FETCH ALL USERS FOR TABLE VIEWS
    public static ObservableList<User> getAllUsers() {
        ObservableList<User> userList = FXCollections.observableArrayList();
        String query = "SELECT user_id, username, full_name, role, status, created_at, updated_at FROM users ORDER BY user_id DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                User user = new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        "",
                        rs.getString("full_name"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at")
                );
                userList.add(user);
            }
        } catch (SQLException e) {
            System.err.println("UserDAO Fetch All Users Error: " + e.getMessage());
            e.printStackTrace();
        }
        return userList;
    }

    public static void seedAdminAccount() {
        String rawPassword = "admin123";
        String hashedPassword = PasswordUtil.hashPassword(rawPassword);

        System.out.println("Generated Hash for admin123: " + hashedPassword);
        // Insert hashedPassword into DB via JDBC PreparedStatement
    }
}