package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Queries for the Sales page. Cancelled transactions are never counted as revenue. */
public final class SalesDAO {

    private SalesDAO() {}

    // ------------------------------------------------------------------ PERIODS

    public enum Period {
        DAILY("Daily", "day", "Last 30 days",
                "DATE_FORMAT(created_at, '%Y-%m-%d')"),
        WEEKLY("Weekly", "week", "Last 12 weeks",
                "DATE_FORMAT(DATE_SUB(DATE(created_at), INTERVAL WEEKDAY(created_at) DAY), '%Y-%m-%d')"),
        MONTHLY("Monthly", "month", "Last 12 months",
                "DATE_FORMAT(created_at, '%Y-%m')"),
        QUARTERLY("Quarterly", "quarter", "Last 8 quarters",
                "CONCAT(YEAR(created_at), '-Q', QUARTER(created_at))"),
        YEARLY("Yearly", "year", "Last 5 years",
                "CAST(YEAR(created_at) AS CHAR)");

        public final String title, unit, range;
        private final String sqlKey;

        Period(String title, String unit, String range, String sqlKey) {
            this.title = title;
            this.unit = unit;
            this.range = range;
            this.sqlKey = sqlKey;
        }

        /** First day of the oldest bucket shown. */
        LocalDate start(LocalDate today) {
            return switch (this) {
                case DAILY -> today.minusDays(29);
                case WEEKLY -> today.with(DayOfWeek.MONDAY).minusWeeks(11);
                case MONTHLY -> today.withDayOfMonth(1).minusMonths(11);
                case QUARTERLY -> today.withDayOfMonth(1)
                        .withMonth((today.getMonthValue() - 1) / 3 * 3 + 1).minusMonths(21);
                case YEARLY -> today.withDayOfYear(1).minusYears(4);
            };
        }

        LocalDate next(LocalDate d) {
            return switch (this) {
                case DAILY -> d.plusDays(1);
                case WEEKLY -> d.plusWeeks(1);
                case MONTHLY -> d.plusMonths(1);
                case QUARTERLY -> d.plusMonths(3);
                case YEARLY -> d.plusYears(1);
            };
        }

        /** Must match the value produced by sqlKey for the same bucket. */
        String key(LocalDate d) {
            return switch (this) {
                case DAILY, WEEKLY -> d.toString();
                case MONTHLY -> String.format("%d-%02d", d.getYear(), d.getMonthValue());
                case QUARTERLY -> d.getYear() + "-Q" + ((d.getMonthValue() - 1) / 3 + 1);
                case YEARLY -> String.valueOf(d.getYear());
            };
        }

        String label(LocalDate d) {
            return switch (this) {
                case DAILY, WEEKLY -> d.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH));
                case MONTHLY -> d.format(DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH));
                case QUARTERLY -> "Q" + ((d.getMonthValue() - 1) / 3 + 1) + " " + d.getYear();
                case YEARLY -> String.valueOf(d.getYear());
            };
        }
    }

    // ------------------------------------------------------------------ RESULT TYPES

    public record Kpis(double todaySales, int todayOrders, double weekSales, int weekOrders,
                       double monthSales, int monthOrders, double yearSales, int yearOrders) {}

    public record Point(String label, double revenue, int orders) {}

    public record RangeTotals(double revenue, int orders, int cancelled, double vat, double discount) {}

    /** A named amount: payment method, hour of day or cashier. */
    public record Slice(String name, double value, int count) {}

    public record ProductRow(String name, int qty, double revenue) {}

    public record SalesData(Period period, LocalDate start, Kpis kpis, List<Point> series,
                            RangeTotals totals, List<Slice> payments, List<Slice> hours,
                            List<ProductRow> products, List<Slice> cashiers) {}

    // ------------------------------------------------------------------ LOAD

    public static SalesData load(Period period) throws SQLException {
        LocalDate today = LocalDate.now();
        LocalDate start = period.start(today);
        Timestamp from = Timestamp.valueOf(start.atStartOfDay());

        try (Connection conn = DatabaseManager.getConnection()) {
            return new SalesData(period, start,
                    kpis(conn),
                    series(conn, period, start, today, from),
                    totals(conn, from),
                    payments(conn, from),
                    hours(conn, from),
                    products(conn, from),
                    cashiers(conn, from));
        }
    }

    private static Kpis kpis(Connection conn) throws SQLException {
        String sql = """
            SELECT
                COALESCE(SUM(CASE WHEN DATE(created_at) = CURDATE() THEN total_amount END), 0) AS today_sales,
                COUNT(CASE WHEN DATE(created_at) = CURDATE() THEN 1 END) AS today_orders,
                COALESCE(SUM(CASE WHEN YEARWEEK(created_at, 1) = YEARWEEK(CURDATE(), 1) THEN total_amount END), 0) AS week_sales,
                COUNT(CASE WHEN YEARWEEK(created_at, 1) = YEARWEEK(CURDATE(), 1) THEN 1 END) AS week_orders,
                COALESCE(SUM(CASE WHEN YEAR(created_at) = YEAR(CURDATE()) AND MONTH(created_at) = MONTH(CURDATE()) THEN total_amount END), 0) AS month_sales,
                COUNT(CASE WHEN YEAR(created_at) = YEAR(CURDATE()) AND MONTH(created_at) = MONTH(CURDATE()) THEN 1 END) AS month_orders,
                COALESCE(SUM(CASE WHEN YEAR(created_at) = YEAR(CURDATE()) THEN total_amount END), 0) AS year_sales,
                COUNT(CASE WHEN YEAR(created_at) = YEAR(CURDATE()) THEN 1 END) AS year_orders
            FROM transactions
            WHERE status <> 'CANCELLED'
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new Kpis(rs.getDouble("today_sales"), rs.getInt("today_orders"),
                        rs.getDouble("week_sales"), rs.getInt("week_orders"),
                        rs.getDouble("month_sales"), rs.getInt("month_orders"),
                        rs.getDouble("year_sales"), rs.getInt("year_orders"));
            }
        }
        return new Kpis(0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** One point per bucket, including buckets with no sales (so the chart has no gaps). */
    private static List<Point> series(Connection conn, Period period, LocalDate start,
                                      LocalDate today, Timestamp from) throws SQLException {
        String sql = "SELECT " + period.sqlKey + " AS k, COALESCE(SUM(total_amount), 0) AS rev, COUNT(*) AS cnt "
                + "FROM transactions WHERE created_at >= ? AND status <> 'CANCELLED' GROUP BY k";

        Map<String, double[]> found = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found.put(rs.getString("k"), new double[]{rs.getDouble("rev"), rs.getInt("cnt")});
                }
            }
        }

        List<Point> points = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = period.next(d)) {
            double[] v = found.getOrDefault(period.key(d), new double[]{0, 0});
            points.add(new Point(period.label(d), v[0], (int) v[1]));
        }
        return points;
    }

    private static RangeTotals totals(Connection conn, Timestamp from) throws SQLException {
        String sql = """
            SELECT
                COALESCE(SUM(CASE WHEN status <> 'CANCELLED' THEN total_amount END), 0) AS rev,
                COUNT(CASE WHEN status <> 'CANCELLED' THEN 1 END) AS orders,
                COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled,
                COALESCE(SUM(CASE WHEN status <> 'CANCELLED' THEN vat END), 0) AS vat,
                COALESCE(SUM(CASE WHEN status <> 'CANCELLED' THEN discount END), 0) AS discount
            FROM transactions
            WHERE created_at >= ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, from);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RangeTotals(rs.getDouble("rev"), rs.getInt("orders"),
                            rs.getInt("cancelled"), rs.getDouble("vat"), rs.getDouble("discount"));
                }
            }
        }
        return new RangeTotals(0, 0, 0, 0, 0);
    }

    private static List<Slice> payments(Connection conn, Timestamp from) throws SQLException {
        String sql = """
            SELECT COALESCE(payment_method, 'Unknown') AS m, SUM(total_amount) AS rev, COUNT(*) AS cnt
            FROM transactions
            WHERE created_at >= ? AND status <> 'CANCELLED'
            GROUP BY m
            ORDER BY rev DESC
        """;
        List<Slice> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(new Slice(rs.getString("m"), rs.getDouble("rev"), rs.getInt("cnt")));
            }
        }
        return list;
    }

    /** Always 24 entries (0..23); name is the hour number. */
    private static List<Slice> hours(Connection conn, Timestamp from) throws SQLException {
        String sql = """
            SELECT HOUR(created_at) AS h, SUM(total_amount) AS rev, COUNT(*) AS cnt
            FROM transactions
            WHERE created_at >= ? AND status <> 'CANCELLED'
            GROUP BY h
        """;
        double[] rev = new double[24];
        int[] cnt = new int[24];
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int h = rs.getInt("h");
                    if (h >= 0 && h < 24) {
                        rev[h] = rs.getDouble("rev");
                        cnt[h] = rs.getInt("cnt");
                    }
                }
            }
        }
        List<Slice> list = new ArrayList<>();
        for (int h = 0; h < 24; h++) list.add(new Slice(String.valueOf(h), rev[h], cnt[h]));
        return list;
    }

    private static List<ProductRow> products(Connection conn, Timestamp from) throws SQLException {
        String sql = """
            SELECT p.name AS name, SUM(ti.quantity) AS qty, SUM(ti.quantity * ti.price) AS rev
            FROM transaction_items ti
            JOIN transactions t ON t.transaction_id = ti.transaction_id
            JOIN product_variants pv ON pv.variant_id = ti.variant_id
            JOIN products p ON p.id = pv.product_id
            WHERE t.created_at >= ? AND t.status <> 'CANCELLED'
            GROUP BY p.id, p.name
            ORDER BY qty DESC, rev DESC
            LIMIT 8
        """;
        List<ProductRow> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(new ProductRow(rs.getString("name"), rs.getInt("qty"), rs.getDouble("rev")));
            }
        }
        return list;
    }

    private static List<Slice> cashiers(Connection conn, Timestamp from) throws SQLException {
        String sql = """
            SELECT COALESCE(u.full_name, 'Unknown') AS name, SUM(t.total_amount) AS rev, COUNT(*) AS cnt
            FROM transactions t
            LEFT JOIN users u ON u.user_id = t.cashier_id
            WHERE t.created_at >= ? AND t.status <> 'CANCELLED'
            GROUP BY t.cashier_id, u.full_name
            ORDER BY rev DESC
            LIMIT 5
        """;
        List<Slice> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(new Slice(rs.getString("name"), rs.getDouble("rev"), rs.getInt("cnt")));
            }
        }
        return list;
    }
}