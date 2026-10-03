package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.model.AccountRow;
import com.coffeepos.renespresso.model.User;
import com.coffeepos.renespresso.util.PasswordUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

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

                    if (PasswordUtil.checkPassword(plainPassword, storedHash)) {
                        int userId = rs.getInt("user_id");
                        touchLastLogin(userId);
                        return new User(
                                userId,
                                rs.getString("username"),
                                rs.getString("full_name"),
                                rs.getString("role"),
                                rs.getBoolean("is_active"));
                    }
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
                    return PasswordUtil.checkPassword(plainPassword, rs.getString("password"));
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

            stmt.setString(1, user.getUsername());
            stmt.setString(2, PasswordUtil.hashPassword(plainPassword));
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
        return registerUser(new User(0, username, fullName, role, true), plainPassword);
    }

    // ---------------------------------------------------------------- ACCOUNT MANAGEMENT

    public static ObservableList<AccountRow> getAllAccounts() throws SQLException {
        String query = "SELECT user_id, full_name, username, role, is_active, last_login, created_at "
                + "FROM users ORDER BY created_at DESC";
        ObservableList<AccountRow> list = FXCollections.observableArrayList();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String role = rs.getString("role");
                String niceRole = role.charAt(0) + role.substring(1).toLowerCase();
                Timestamp last = rs.getTimestamp("last_login");
                Timestamp created = rs.getTimestamp("created_at");
                list.add(new AccountRow(
                        rs.getInt("user_id"),
                        rs.getString("full_name"),
                        rs.getString("username"),
                        niceRole,
                        rs.getBoolean("is_active") ? "Active" : "Inactive",
                        last == null ? null : last.toLocalDateTime(),
                        created == null ? null : created.toLocalDateTime()));
            }
        }
        return list;
    }

    public static boolean usernameExists(String username, int excludeId) throws SQLException {
        String query = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?) AND user_id <> ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setInt(2, excludeId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static int countOtherActiveAdmins(int excludeId) throws SQLException {
        String query = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND is_active = 1 AND user_id <> ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, excludeId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static void updateAccount(int id, String fullName, String username, String role,
                                     boolean active, String newPlainPassword) throws SQLException {
        boolean changePw = newPlainPassword != null && !newPlainPassword.isBlank();
        String query = "UPDATE users SET full_name = ?, username = ?, role = ?, is_active = ?"
                + (changePw ? ", password = ?" : "") + " WHERE user_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            int i = 1;
            stmt.setString(i++, fullName);
            stmt.setString(i++, username);
            stmt.setString(i++, role.toUpperCase());
            stmt.setInt(i++, active ? 1 : 0);
            if (changePw) stmt.setString(i++, PasswordUtil.hashPassword(newPlainPassword));
            stmt.setInt(i, id);
            stmt.executeUpdate();
        }
    }

    public static void deleteAccount(int id) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    public static void touchLastLogin(int userId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE users SET last_login = NOW() WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("UserDAO touchLastLogin Error: " + e.getMessage());
        }
    }
}