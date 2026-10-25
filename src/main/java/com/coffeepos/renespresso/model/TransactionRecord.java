package com.coffeepos.renespresso.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class TransactionRecord {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private int transactionId;
    private String orderId;
    private final String ticketNumber;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final String time;
    private final String cashier;
    private String staffName;
    private String staffRole;
    private final String paymentMethod;
    private String paymentStatus = "Paid";
    private final List<OrderLine> lines;
    private final int itemCount;
    private final String items;
    private final boolean discounted;
    private final String discountApplied;
    private final double subtotal, vat, discount, total, tendered;
    private final String totalPaid;
    private final String receiptText;
    private final ObjectProperty<OrderStatus> status = new SimpleObjectProperty<>(OrderStatus.PREPARING);

    /**
     * Factory Helper for POS Order Creation (VAT-Inclusive Strategy):
     * Takes the gross menu total (e.g., ₱80.00) and automatically extracts:
     * - Vatable Sales (Subtotal) = Total / 1.12  (e.g., ₱71.43)
     * - 12% VAT Amount           = Total - Subtotal (e.g., ₱8.57)
     */
    public static TransactionRecord createVatInclusiveTransaction(
            String ticketNumber, String cashier, String paymentMethod,
            List<OrderLine> lines, double grossTotal, double discountAmount,
            double tendered, String receiptText) {

        double netTotal = Math.max(0, grossTotal - discountAmount);
        double vatableSales = Math.round((netTotal / 1.12) * 100.0) / 100.0; // Base Price (71.43)
        double vatAmount = Math.round((netTotal - vatableSales) * 100.0) / 100.0;  // 12% VAT (8.57)

        return new TransactionRecord(
                0, null, ticketNumber, LocalDateTime.now(), LocalDateTime.now(),
                cashier, "Cashier", paymentMethod, "Paid", OrderStatus.PREPARING,
                lines, vatableSales, vatAmount, discountAmount, netTotal, tendered, receiptText
        );
    }

    /** Constructor for new transactions placed at POS */
    public TransactionRecord(String ticketNumber, LocalDateTime createdAt, String cashier, String paymentMethod,
                             List<OrderLine> lines, double subtotal, double vat, double discount,
                             double total, double tendered, String receiptText) {
        this(0, null, ticketNumber, createdAt, createdAt, cashier, "Cashier", paymentMethod, "Paid",
                OrderStatus.PREPARING, lines, subtotal, vat, discount, total, tendered, receiptText);
    }

    /** Full constructor for transactions loaded from database */
    public TransactionRecord(int transactionId, String orderId, String ticketNumber,
                             LocalDateTime createdAt, LocalDateTime updatedAt,
                             String staffName, String staffRole, String paymentMethod, String paymentStatus,
                             OrderStatus orderStatus, List<OrderLine> lines, double subtotal, double vat,
                             double discount, double total, double tendered, String receiptText) {
        this(transactionId, orderId, ticketNumber, createdAt, updatedAt, staffName, staffRole, paymentMethod, paymentStatus,
             orderStatus, lines, subtotal, vat, discount, total, tendered, receiptText,
             discount > 0 ? "Senior/PWD (20%)" : "None");
    }

    public TransactionRecord(int transactionId, String orderId, String ticketNumber,
                             LocalDateTime createdAt, LocalDateTime updatedAt,
                             String staffName, String staffRole, String paymentMethod, String paymentStatus,
                             OrderStatus orderStatus, List<OrderLine> lines, double subtotal, double vat,
                             double discount, double total, double tendered, String receiptText, String discountApplied) {
        this.transactionId = transactionId;
        this.orderId = (orderId != null && !orderId.isBlank()) ? orderId : (transactionId > 0 ? "TXN-" + (8400 + transactionId) : "TXN-8401");
        this.ticketNumber = ticketNumber != null ? ticketNumber : (transactionId > 0 ? String.format("#%03d", transactionId) : "#001");
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.time = this.createdAt.format(TIME_FMT);
        this.cashier = staffName != null && !staffName.isBlank() ? staffName : "Cashier";
        this.staffName = this.cashier;
        this.staffRole = staffRole != null && !staffRole.isBlank() ? staffRole : "Cashier";
        this.paymentMethod = paymentMethod != null && !paymentMethod.isBlank() ? paymentMethod : "Cash";
        this.paymentStatus = paymentStatus != null && !paymentStatus.isBlank() ? paymentStatus : "Paid";
        this.lines = lines != null ? List.copyOf(lines) : List.of();
        this.subtotal = subtotal;
        this.vat = vat;
        this.discount = discount;
        this.total = total;
        this.tendered = tendered;
        this.receiptText = receiptText != null ? receiptText : "";

        this.itemCount = this.lines.stream().mapToInt(OrderLine::qty).sum();
        String first = this.lines.isEmpty() ? "" : this.lines.get(0).name();
        this.items = itemCount + " item(s)" + (first.isEmpty() ? "" : " (" + first + (this.lines.size() > 1 ? ", ..." : "") + ")");
        this.discounted = discount > 0;
        this.discountApplied = (discountApplied != null && !discountApplied.isBlank())
                ? discountApplied
                : (discounted ? "Senior/PWD (20%)" : "None");
        this.totalPaid = String.format("\u20B1%.2f", total);
        if (orderStatus != null) {
            this.status.set(orderStatus);
        }
    }

    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
        if (orderId == null || orderId.isBlank()) {
            this.orderId = "TXN-" + (8400 + transactionId);
        }
    }

    public String getOrderId() {
        if (orderId == null || orderId.isBlank()) {
            return transactionId > 0 ? "TXN-" + (8400 + transactionId) : ticketNumber;
        }
        return orderId;
    }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getTicketNumber() { return ticketNumber; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getTime() { return time; }
    public String getCashier() { return cashier; }

    public String getStaffName() {
        if (staffName != null && !staffName.isBlank()) return staffName;
        if (cashier != null && !cashier.isBlank()) return cashier;
        return "Former Staff";
    }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public String getStaffRole() {
        if (staffRole == null || staffRole.isBlank()) return "Cashier";
        return staffRole.substring(0, 1).toUpperCase() + staffRole.substring(1).toLowerCase();
    }
    public void setStaffRole(String staffRole) { this.staffRole = staffRole; }

    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentStatus() {
        return paymentStatus != null && !paymentStatus.isBlank() ? paymentStatus : "Paid";
    }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public List<OrderLine> getLines() { return lines; }
    public int getItemCount() { return itemCount; }
    public String getItems() { return items; }
    public boolean isDiscounted() { return discounted; }
    public String getDiscountApplied() { return discountApplied; }
    public double getSubtotal() { return subtotal; } // Vatable Sales (71.43)
    public double getVat() { return vat; }           // 12% VAT Amount (8.57)
    public double getDiscount() { return discount; }
    public double getTotal() { return total; }       // Gross Menu Price (80.00)
    public double getTendered() { return tendered; }
    public String getTotalPaid() { return totalPaid; }
    public String getReceiptText() { return receiptText; }

    public ObjectProperty<OrderStatus> statusProperty() { return status; }
    public OrderStatus getStatus() { return status.get(); }
    public void setStatus(OrderStatus s) { status.set(s); }

    public LocalDateTime getEffectiveDateTime() {
        if (createdAt == null && updatedAt == null) return LocalDateTime.MIN;
        if (updatedAt != null && createdAt != null && !updatedAt.isEqual(createdAt)) {
            return updatedAt;
        }
        return createdAt != null ? createdAt : updatedAt;
    }

    public String getFormattedCreatedOrUpdated() {
        LocalDateTime dt = getEffectiveDateTime();
        if (dt == null) dt = LocalDateTime.now();
        LocalDate date = dt.toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "Today, " + dt.format(TIME_FMT);
        } else if (date.equals(today.minusDays(1))) {
            return "Yesterday, " + dt.format(TIME_FMT);
        } else {
            return dt.format(DateTimeFormatter.ofPattern("MMM dd, hh:mm a", Locale.ENGLISH));
        }
    }

    public String getTimeAgo() {
        long mins = Duration.between(createdAt, LocalDateTime.now()).toMinutes();
        if (mins < 1) return "Just now";
        if (mins < 60) return mins + (mins == 1 ? " min ago" : " mins ago");
        long hrs = mins / 60;
        return hrs + (hrs == 1 ? " hr ago" : " hrs ago");
    }
}