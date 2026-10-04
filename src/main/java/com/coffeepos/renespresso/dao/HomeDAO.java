package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeDAO {

    public record DayTotal(LocalDate day, double total) {}
    public record TopProduct(String name, int qty, double revenue) {}
    public record RecentOrder(int id, String cashier, double total, String status, LocalDateTime time) {}
    public record SalesData(double today, double yesterday, int ordersToday, int itemsToday,
                            List<DayTotal> last7, List<TopProduct> top, List<RecentOrder> recent) {}

    public record RoleCount(String role, int active, int total) {}
    public record StaffData(int active, int total, List<RoleCount> roles) {}

    // ------------------------------------------------------------------ SALES

    public static SalesData loadSales() throws SQLException {
        LocalDate today = LocalDate.now();
        try (Connection c = DatabaseManager.getConnection()) {
            double[] t = totals(c, today, today.plusDays(1));
            double[] y = totals(c, today.minusDays(1), today);
            return new SalesData(
                    t[0], y[0], (int) t[1], itemsSold(c, today, today.plusDays(1)),
                    last7Days(c, today),
                    topProducts(c, today.minusDays(6), today.plusDays(1)),
                    recentOrders(c));
        }
    }

    /** returns {sum, count} of completed transactions in [from, too) */
    private static double[] totals(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(total_amount), 0), COUNT(*) FROM transactions "
                + "WHERE status = 'COMPLETED' AND created_at >= ? AND created_at < ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            range(ps, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new double[]{rs.getDouble(1), rs.getInt(2)};
            }
        }
    }

    private static int itemsSold(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(ti.quantity), 0) FROM transaction_items ti "
                + "JOIN transactions t ON t.transaction_id = ti.transaction_id "
                + "WHERE t.status = 'COMPLETED' AND t.created_at >= ? AND t.created_at < ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            range(ps, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static List<DayTotal> last7Days(Connection c, LocalDate today) throws SQLException {
        LocalDate start = today.minusDays(6);
        String sql = "SELECT DATE(created_at) AS d, SUM(total_amount) AS s FROM transactions "
                + "WHERE status = 'COMPLETED' AND created_at >= ? AND created_at < ? "
                + "GROUP BY DATE(created_at)";
        Map<LocalDate, Double> byDay = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            range(ps, start, today.plusDays(1));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) byDay.put(rs.getDate("d").toLocalDate(), rs.getDouble("s"));
            }
        }
        List<DayTotal> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {          // fill days with no sales as 0
            LocalDate d = start.plusDays(i);
            out.add(new DayTotal(d, byDay.getOrDefault(d, 0.0)));
        }
        return out;
    }

    private static List<TopProduct> topProducts(Connection c, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT p.name, SUM(ti.quantity) AS qty, SUM(ti.quantity * ti.price) AS revenue "
                + "FROM transaction_items ti "
                + "JOIN transactions t ON t.transaction_id = ti.transaction_id "
                + "JOIN products p ON p.product_id = ti.product_id "
                + "WHERE t.status = 'COMPLETED' AND t.created_at >= ? AND t.created_at < ? "
                + "GROUP BY p.product_id, p.name ORDER BY qty DESC LIMIT 5";
        List<TopProduct> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            range(ps, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    out.add(new TopProduct(rs.getString("name"), rs.getInt("qty"), rs.getDouble("revenue")));
            }
        }
        return out;
    }

    private static List<RecentOrder> recentOrders(Connection c) throws SQLException {
        String sql = "SELECT t.transaction_id, COALESCE(u.full_name, '-') AS cashier, "
                + "t.total_amount, t.status, t.created_at "
                + "FROM transactions t LEFT JOIN users u ON u.user_id = t.cashier_id "
                + "ORDER BY t.created_at DESC LIMIT 8";
        List<RecentOrder> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new RecentOrder(
                        rs.getInt("transaction_id"),
                        rs.getString("cashier"),
                        rs.getDouble("total_amount"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
            }
        }
        return out;
    }

    private static void range(PreparedStatement ps, LocalDate from, LocalDate to) throws SQLException {
        ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
        ps.setTimestamp(2, Timestamp.valueOf(to.atStartOfDay()));
    }

    // ------------------------------------------------------------------ STAFF

    public static StaffData loadStaff() throws SQLException {
        String sql = "SELECT role, COUNT(*) AS total, COALESCE(SUM(is_active), 0) AS active FROM users "
                + "GROUP BY role ORDER BY FIELD(role, 'ADMIN', 'MANAGER', 'SUPERVISOR', 'CASHIER')";
        List<RoleCount> roles = new ArrayList<>();
        int active = 0, total = 0;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String r = rs.getString("role");
                int a = rs.getInt("active"), t = rs.getInt("total");
                roles.add(new RoleCount(r.charAt(0) + r.substring(1).toLowerCase(), a, t));
                active += a;
                total += t;
            }
        }
        return new StaffData(active, total, roles);
    }
}