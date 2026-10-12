package com.coffeepos.renespresso.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TransactionRecord {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final String ticketNumber;
    private final LocalDateTime createdAt;
    private final String time;
    private final String cashier;
    private final String paymentMethod;
    private final List<OrderLine> lines;
    private final int itemCount;
    private final String items;
    private final boolean discounted;
    private final String discountApplied;
    private final double subtotal, vat, discount, total, tendered;
    private final String totalPaid;
    private final String receiptText;
    private final ObjectProperty<OrderStatus> status = new SimpleObjectProperty<>(OrderStatus.PREPARING);

    public TransactionRecord(String ticketNumber, LocalDateTime createdAt, String cashier, String paymentMethod,
                             List<OrderLine> lines, double subtotal, double vat, double discount,
                             double total, double tendered, String receiptText) {
        this.ticketNumber = ticketNumber;
        this.createdAt = createdAt;
        this.time = createdAt.format(TIME_FMT);
        this.cashier = cashier;
        this.paymentMethod = paymentMethod;
        this.lines = List.copyOf(lines);
        this.subtotal = subtotal;
        this.vat = vat;
        this.discount = discount;
        this.total = total;
        this.tendered = tendered;
        this.receiptText = receiptText;

        this.itemCount = lines.stream().mapToInt(OrderLine::qty).sum();
        String first = lines.isEmpty() ? "" : lines.get(0).name();
        this.items = itemCount + " item(s) (" + first + (lines.size() > 1 ? ", ..." : "") + ")";
        this.discounted = discount > 0;
        this.discountApplied = discounted ? "Senior/PWD (20%)" : "None";
        this.totalPaid = String.format("\u20B1%.2f", total);
    }

    public String getTicketNumber() { return ticketNumber; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getTime() { return time; }
    public String getCashier() { return cashier; }
    public String getPaymentMethod() { return paymentMethod; }
    public List<OrderLine> getLines() { return lines; }
    public int getItemCount() { return itemCount; }
    public String getItems() { return items; }
    public boolean isDiscounted() { return discounted; }
    public String getDiscountApplied() { return discountApplied; }
    public double getSubtotal() { return subtotal; }
    public double getVat() { return vat; }
    public double getDiscount() { return discount; }
    public double getTotal() { return total; }
    public double getTendered() { return tendered; }
    public String getTotalPaid() { return totalPaid; }
    public String getReceiptText() { return receiptText; }

    public ObjectProperty<OrderStatus> statusProperty() { return status; }
    public OrderStatus getStatus() { return status.get(); }
    public void setStatus(OrderStatus s) { status.set(s); }

    public String getTimeAgo() {
        long mins = Duration.between(createdAt, LocalDateTime.now()).toMinutes();
        if (mins < 1) return "Just now";
        if (mins < 60) return mins + (mins == 1 ? " min ago" : " mins ago");
        long hrs = mins / 60;
        return hrs + (hrs == 1 ? " hr ago" : " hrs ago");
    }
}