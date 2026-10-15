package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.model.CartItem;
import com.coffeepos.renespresso.model.OrderLine;
import com.coffeepos.renespresso.model.OrderStatus;
import com.coffeepos.renespresso.model.TransactionRecord;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    public record SalesSummary(double todaySales, double weekSales, double monthSales,
                               int todayOrders, int weekOrders, int monthOrders) {}

    public record DailySale(String dateLabel, double totalAmount) {}

    public static int saveTransaction(TransactionRecord record, List<CartItem> cartItems, int cashierId) throws SQLException {
        String insertTxSQL = """
            INSERT INTO transactions (
                cashier_id, order_id, ticket_number, total_amount, subtotal, vat,
                discount, tendered, payment_method, payment_status, status, receipt_text, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        String insertItemSQL = """
            INSERT INTO transaction_items (transaction_id, variant_id, quantity, price)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)
        """;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int txId = 0;
                String orderId = record.getOrderId();

                try (PreparedStatement ps = conn.prepareStatement(insertTxSQL, Statement.RETURN_GENERATED_KEYS)) {
                    if (cashierId > 0) {
                        ps.setInt(1, cashierId);
                    } else {
                        ps.setNull(1, Types.INTEGER);
                    }
                    ps.setString(2, orderId);
                    ps.setString(3, record.getTicketNumber());
                    ps.setBigDecimal(4, BigDecimal.valueOf(record.getTotal()));    // 80.00 Gross
                    ps.setBigDecimal(5, BigDecimal.valueOf(record.getSubtotal())); // 71.43 Vatable Sales
                    ps.setBigDecimal(6, BigDecimal.valueOf(record.getVat()));      // 8.57  VAT Amount
                    ps.setBigDecimal(7, BigDecimal.valueOf(record.getDiscount()));
                    ps.setBigDecimal(8, BigDecimal.valueOf(record.getTendered()));
                    ps.setString(9, record.getPaymentMethod());
                    ps.setString(10, record.getPaymentStatus());
                    ps.setString(11, record.getStatus().name());
                    ps.setString(12, record.getReceiptText());
                    Timestamp nowTs = Timestamp.valueOf(record.getCreatedAt() != null ? record.getCreatedAt() : LocalDateTime.now());
                    ps.setTimestamp(13, nowTs);
                    ps.setTimestamp(14, nowTs);

                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            txId = rs.getInt(1);
                        }
                    }
                }

                if (txId > 0) {
                    record.setTransactionId(txId);
                    if (orderId == null || orderId.isBlank() || orderId.startsWith("TXN-0") || orderId.equals("TXN-8401")) {
                        String generatedOrderId = "TXN-" + (8400 + txId);
                        record.setOrderId(generatedOrderId);
                        try (PreparedStatement updatePs = conn.prepareStatement("UPDATE transactions SET order_id = ? WHERE transaction_id = ?")) {
                            updatePs.setString(1, generatedOrderId);
                            updatePs.setInt(2, txId);
                            updatePs.executeUpdate();
                        }
                    }

                    if (cartItems != null && !cartItems.isEmpty()) {
                        try (PreparedStatement itemPs = conn.prepareStatement(insertItemSQL)) {
                            for (CartItem ci : cartItems) {
                                if (ci.getVariantId() > 0) {
                                    itemPs.setInt(1, txId);
                                    itemPs.setInt(2, ci.getVariantId());
                                    itemPs.setInt(3, ci.getQuantity());
                                    itemPs.setBigDecimal(4, BigDecimal.valueOf(ci.getPrice()));
                                    itemPs.addBatch();
                                }
                            }
                            itemPs.executeBatch();
                        }
                    }
                }

                conn.commit();
                return txId;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static boolean updateStatus(int transactionId, OrderStatus status) {
        if (transactionId <= 0) return false;
        String sql = "UPDATE transactions SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE transaction_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, transactionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating transaction status: " + e.getMessage());
            return false;
        }
    }

    public static List<TransactionRecord> loadAllTransactions() {
        return loadTransactions("SELECT t.*, u.full_name AS staff_name, u.role AS staff_role " +
                "FROM transactions t LEFT JOIN users u ON u.user_id = t.cashier_id " +
                "ORDER BY t.created_at DESC");
    }

    public static List<TransactionRecord> loadTodayTransactions() {
        return loadTransactions("SELECT t.*, u.full_name AS staff_name, u.role AS staff_role " +
                "FROM transactions t LEFT JOIN users u ON u.user_id = t.cashier_id " +
                "WHERE DATE(t.created_at) = CURDATE() " +
                "ORDER BY t.created_at DESC");
    }

    private static List<TransactionRecord> loadTransactions(String sql) {
        List<TransactionRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int txId = rs.getInt("transaction_id");
                String orderId = rs.getString("order_id");
                String ticketNumber = rs.getString("ticket_number");
                Timestamp createdTs = rs.getTimestamp("created_at");
                Timestamp updatedTs = rs.getTimestamp("updated_at");
                LocalDateTime createdAt = createdTs != null ? createdTs.toLocalDateTime() : LocalDateTime.now();
                LocalDateTime updatedAt = updatedTs != null ? updatedTs.toLocalDateTime() : createdAt;
                String staffName = rs.getString("staff_name");
                String staffRole = rs.getString("staff_role");
                String paymentMethod = rs.getString("payment_method");
                String paymentStatus = rs.getString("payment_status");
                String statusStr = rs.getString("status");
                double total = rs.getDouble("total_amount");
                double subtotal = rs.getDouble("subtotal");
                double vat = rs.getDouble("vat");
                double discount = rs.getDouble("discount");
                double tendered = rs.getDouble("tendered");
                String receiptText = rs.getString("receipt_text");

                OrderStatus status = OrderStatus.COMPLETED;
                if (statusStr != null) {
                    try {
                        status = OrderStatus.valueOf(statusStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        if ("PREPARING".equalsIgnoreCase(statusStr)) status = OrderStatus.PREPARING;
                        else if ("CANCELLED".equalsIgnoreCase(statusStr)) status = OrderStatus.CANCELLED;
                        else if ("PENDING".equalsIgnoreCase(statusStr)) status = OrderStatus.PENDING;
                    }
                }

                List<OrderLine> lines = loadTransactionLines(conn, txId);

                TransactionRecord record = new TransactionRecord(
                        txId, orderId, ticketNumber, createdAt, updatedAt,
                        staffName, staffRole, paymentMethod, paymentStatus,
                        status, lines, subtotal, vat, discount, total, tendered, receiptText
                );
                list.add(record);
            }
        } catch (SQLException e) {
            System.err.println("Error loading transactions: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    private static List<OrderLine> loadTransactionLines(Connection conn, int txId) {
        String sql = """
            SELECT ti.quantity, ti.price, p.name AS product_name, pv.size_name
            FROM transaction_items ti
            JOIN product_variants pv ON pv.variant_id = ti.variant_id
            JOIN products p ON p.id = pv.product_id
            WHERE ti.transaction_id = ?
        """;
        List<OrderLine> lines = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, txId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("product_name");
                    String size = rs.getString("size_name");
                    int qty = rs.getInt("quantity");
                    double price = rs.getDouble("price");
                    double itemTotal = price * qty;
                    lines.add(new OrderLine(name, size != null ? size : "", qty, itemTotal));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading items for tx " + txId + ": " + e.getMessage());
        }
        return lines;
    }

    public static SalesSummary getSalesSummary() {
        double todaySales = 0, weekSales = 0, monthSales = 0;
        int todayOrders = 0, weekOrders = 0, monthOrders = 0;

        String sql = """
            SELECT 
                COALESCE(SUM(CASE WHEN DATE(created_at) = CURDATE() THEN total_amount ELSE 0 END), 0) AS today_sales,
                COUNT(CASE WHEN DATE(created_at) = CURDATE() THEN 1 END) AS today_orders,
                COALESCE(SUM(CASE WHEN YEARWEEK(created_at, 1) = YEARWEEK(CURDATE(), 1) THEN total_amount ELSE 0 END), 0) AS week_sales,
                COUNT(CASE WHEN YEARWEEK(created_at, 1) = YEARWEEK(CURDATE(), 1) THEN 1 END) AS week_orders,
                COALESCE(SUM(CASE WHEN YEAR(created_at) = YEAR(CURDATE()) AND MONTH(created_at) = MONTH(CURDATE()) THEN total_amount ELSE 0 END), 0) AS month_sales,
                COUNT(CASE WHEN YEAR(created_at) = YEAR(CURDATE()) AND MONTH(created_at) = MONTH(CURDATE()) THEN 1 END) AS month_orders
            FROM transactions
            WHERE status != 'CANCELLED'
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                todaySales = rs.getDouble("today_sales");
                todayOrders = rs.getInt("today_orders");
                weekSales = rs.getDouble("week_sales");
                weekOrders = rs.getInt("week_orders");
                monthSales = rs.getDouble("month_sales");
                monthOrders = rs.getInt("month_orders");
            }
        } catch (SQLException e) {
            System.err.println("Error fetching sales summary: " + e.getMessage());
        }

        return new SalesSummary(todaySales, weekSales, monthSales, todayOrders, weekOrders, monthOrders);
    }

    public static List<DailySale> getLast14DaysSales() {
        List<DailySale> list = new ArrayList<>();
        String sql = """
            SELECT DATE_FORMAT(d.dt, '%b %d') AS lbl, COALESCE(SUM(t.total_amount), 0) AS total
            FROM (
                SELECT CURDATE() - INTERVAL (a.a + (10 * b.a)) DAY AS dt
                FROM (SELECT 0 AS a UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 
                      UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 
                      UNION ALL SELECT 8 UNION ALL SELECT 9) AS a
                CROSS JOIN (SELECT 0 AS a UNION ALL SELECT 1) AS b
                WHERE (a.a + (10 * b.a)) < 14
            ) d
            LEFT JOIN transactions t ON DATE(t.created_at) = d.dt AND t.status != 'CANCELLED'
            GROUP BY d.dt, lbl
            ORDER BY d.dt ASC
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new DailySale(rs.getString("lbl"), rs.getDouble("total")));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching daily sales: " + e.getMessage());
        }
        return list;
    }
}