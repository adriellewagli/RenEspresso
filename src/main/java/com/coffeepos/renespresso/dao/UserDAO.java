package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.model.AccountRow;
import com.coffeepos.renespresso.model.User;
import com.coffeepos.renespresso.util.PasswordUtil;
import com.coffeepos.renespresso.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class UserDAO {

    static {
        ensureSchema();
    }

    public static void ensureSchema() {
        String sql = "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_archived TINYINT(1) NOT NULL DEFAULT 0";
        try (Connection conn = DatabaseManager.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            try (Connection conn = DatabaseManager.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getColumns(null, null, "users", "is_archived")) {
                    if (!rs.next()) {
                        try (Statement st = conn.createStatement()) {
                            st.execute("ALTER TABLE users ADD COLUMN is_archived TINYINT(1) NOT NULL DEFAULT 0");
                        }
                    }
                }
            } catch (SQLException ex) {
                System.err.println("UserDAO ensureSchema error: " + ex.getMessage());
            }
        }
    }

    // AUTHENTICATE USER LOGIN WITH JBCRYPT
    public static User authenticate(String username, String plainPassword) {
        String query = "SELECT user_id, username, password, full_name, role, is_active FROM users "
                + "WHERE LOWER(username) = LOWER(?) AND COALESCE(is_archived, 0) = 0";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password");

                    if (PasswordUtil.checkPassword(plainPassword, storedHash)) {
                        int userId = rs.getInt("user_id");
                        touchLastLogin(userId);
                        setActiveStatus(userId, true);
                        return new User(
                                userId,
                                rs.getString("username"),
                                rs.getString("full_name"),
                                rs.getString("role"),
                                true);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("UserDAO Authentication Error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static void setActiveStatus(int userId, boolean active) {
        if (userId <= 0) return;
        String query = "UPDATE users SET is_active = ? WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, active ? 1 : 0);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("UserDAO setActiveStatus Error: " + e.getMessage());
        }
    }

    // ADMIN/MANAGER VALIDATION FOR AUTHORIZATION MODAL WITH JBCRYPT
    public static boolean validateAdminCredentials(String username, String plainPassword) {
        if (username == null || plainPassword == null || username.isBlank() || plainPassword.isEmpty()) {
            return false;
        }

        String query = "SELECT password, role FROM users WHERE username = ? "
                + "AND UPPER(role) IN ('ADMIN', 'MANAGER') AND COALESCE(is_archived, 0) = 0";

        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username.trim());

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
        User sessionUser = SessionManager.getCurrentUser();
        if (sessionUser != null && "MANAGER".equalsIgnoreCase(sessionUser.getRole())) {
            if (!"CASHIER".equalsIgnoreCase(user.getRole())) {
                System.err.println("UserDAO Security: Managers cannot create co-managers or admin accounts: " + user.getRole());
                return false;
            }
        }

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
        return registerUser(new User(0, username, fullName, role, false), plainPassword);
    }

    // ---------------------------------------------------------------- ACCOUNT
    // MANAGEMENT

    public static ObservableList<AccountRow> getAllAccounts() throws SQLException {
        ensureSchema();
        String query = "SELECT user_id, full_name, username, role, is_active, last_login, created_at "
                + "FROM users WHERE COALESCE(is_archived, 0) = 0 "
                + "ORDER BY FIELD(UPPER(role), 'ADMIN', 'MANAGER', 'CASHIER'), created_at DESC";
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
        String query = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND user_id <> ?";
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
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement check = conn.prepareStatement("SELECT role FROM users WHERE user_id = ?")) {
                check.setInt(1, id);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        String currentRole = rs.getString("role");
                        boolean wasAdmin = "ADMIN".equalsIgnoreCase(currentRole);
                        boolean nowAdmin = "ADMIN".equalsIgnoreCase(role);
                        if (wasAdmin && !nowAdmin) {
                            throw new SQLException("Admin role is fixed to Admin only and cannot be changed.");
                        }
                        if (!wasAdmin && nowAdmin) {
                            throw new SQLException("Existing staff accounts cannot be elevated to Admin.");
                        }
                        User sessionUser = SessionManager.getCurrentUser();
                        if (sessionUser != null && "MANAGER".equalsIgnoreCase(sessionUser.getRole())) {
                            if (!"CASHIER".equalsIgnoreCase(currentRole) || !"CASHIER".equalsIgnoreCase(role)) {
                                throw new SQLException("Managers cannot create co-managers or elevate accounts.");
                            }
                        }
                    }
                }
            }

            boolean changePw = newPlainPassword != null && !newPlainPassword.isBlank();
            String query = "UPDATE users SET full_name = ?, username = ?, role = ?, is_active = ?"
                    + (changePw ? ", password = ?" : "") + " WHERE user_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                int i = 1;
                stmt.setString(i++, fullName);
                stmt.setString(i++, username);
                stmt.setString(i++, role.toUpperCase());
                stmt.setInt(i++, active ? 1 : 0);
                if (changePw)
                    stmt.setString(i++, PasswordUtil.hashPassword(newPlainPassword));
                stmt.setInt(i, id);
                stmt.executeUpdate();
            }
        }
    }

    public static ObservableList<AccountRow> getArchivedAccounts() throws SQLException {
        ensureSchema();
        String query = "SELECT user_id, full_name, username, role, is_active, last_login, created_at "
                + "FROM users WHERE COALESCE(is_archived, 0) = 1 "
                + "ORDER BY FIELD(UPPER(role), 'ADMIN', 'MANAGER', 'CASHIER'), created_at DESC";
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
                        "Archived",
                        last == null ? null : last.toLocalDateTime(),
                        created == null ? null : created.toLocalDateTime()));
            }
        }
        return list;
    }

    public static int getArchivedCount() {
        ensureSchema();
        User sessionUser = SessionManager.getCurrentUser();
        boolean isManager = sessionUser != null && "MANAGER".equalsIgnoreCase(sessionUser.getRole());
        String query = "SELECT COUNT(*) FROM users WHERE COALESCE(is_archived, 0) = 1"
                + (isManager ? " AND UPPER(role) = 'CASHIER'" : "");
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("UserDAO getArchivedCount error: " + e.getMessage());
        }
        return 0;
    }

    public static void archiveAccount(int id) throws SQLException {
        ensureSchema();
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement check = conn.prepareStatement("SELECT role FROM users WHERE user_id = ?")) {
                check.setInt(1, id);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        String targetRole = rs.getString("role");
                        if ("ADMIN".equalsIgnoreCase(targetRole)) {
                            throw new SQLException("Admin accounts cannot be archived.");
                        }
                        User sessionUser = SessionManager.getCurrentUser();
                        if (sessionUser != null && "MANAGER".equalsIgnoreCase(sessionUser.getRole())) {
                            if (!"CASHIER".equalsIgnoreCase(targetRole)) {
                                throw new SQLException("Managers can only archive Cashier accounts.");
                            }
                        }
                    }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE users SET is_archived = 1, is_active = 0 WHERE user_id = ?")) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }
        }
    }

    public static void restoreAccount(int id) throws SQLException {
        ensureSchema();
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement check = conn.prepareStatement("SELECT role FROM users WHERE user_id = ?")) {
                check.setInt(1, id);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        String targetRole = rs.getString("role");
                        User sessionUser = SessionManager.getCurrentUser();
                        if (sessionUser != null && "MANAGER".equalsIgnoreCase(sessionUser.getRole())) {
                            if (!"CASHIER".equalsIgnoreCase(targetRole)) {
                                throw new SQLException("Managers can only restore Cashier accounts.");
                            }
                        }
                    }
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE users SET is_archived = 0 WHERE user_id = ?")) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }
        }
    }

    public static void hardDeleteAccount(int id) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement check = conn.prepareStatement("SELECT role FROM users WHERE user_id = ?")) {
                check.setInt(1, id);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        String targetRole = rs.getString("role");
                        if ("ADMIN".equalsIgnoreCase(targetRole)) {
                            throw new SQLException("Admin accounts cannot be deleted.");
                        }
                        User sessionUser = SessionManager.getCurrentUser();
                        if (sessionUser != null && "MANAGER".equalsIgnoreCase(sessionUser.getRole())) {
                            if (!"CASHIER".equalsIgnoreCase(targetRole)) {
                                throw new SQLException("Managers can only delete Cashier accounts.");
                            }
                        }
                    }
                }
            }

            conn.setAutoCommit(false);
            try {
                // Unlink past transactions recorded by this staff member (preserves financial totals)
                try (PreparedStatement unlinkStmt = conn.prepareStatement(
                        "UPDATE transactions SET cashier_id = NULL WHERE cashier_id = ?")) {
                    unlinkStmt.setInt(1, id);
                    unlinkStmt.executeUpdate();
                }

                // Delete the user record permanently
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static void deleteAccount(int id) throws SQLException {
        hardDeleteAccount(id);
    }

    public static void touchLastLogin(int userId) {
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn
                        .prepareStatement("UPDATE users SET last_login = NOW() WHERE user_id = ?")) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("UserDAO touchLastLogin Error: " + e.getMessage());
        }
    }
}