package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.model.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    //AUTH NG LOGIN
    public static User authenticate(String username, String password) {
        String query = "SELECT user_id, username, full_name, role, status FROM users "
                + "WHERE username = ? AND password_hash = ? AND status = 'active'";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("role"),
                            rs.getString("status")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("UserDAO Authentication Error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    //VALIDATION TO
    public static boolean validateAdminCredentials(String username, String password) {
        String query = "SELECT role FROM users WHERE username = ? AND password_hash = ? "
                + "AND role IN ('admin', 'manager', 'supervisor') AND status = 'active'";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("UserDAO Admin Validation Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Registers a new user account into MariaDB using a User instance.
     */
    public static boolean registerUser(User user, String password) {
        String query = "INSERT INTO users (username, password_hash, full_name, role, status) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, password);
            stmt.setString(3, user.getFullName());
            stmt.setString(4, user.getRole().toLowerCase());
            stmt.setString(5, user.getStatus() != null ? user.getStatus().toLowerCase() : "active");

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("UserDAO Registration Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    // Overloaded registerUser for direct parameter registration.
    public static boolean registerUser(String username, String password, String fullName, String role) {
        User user = new User(0, username, fullName, role, "active");
        return registerUser(user, password);
    }

    //SA TABLE TO
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
}