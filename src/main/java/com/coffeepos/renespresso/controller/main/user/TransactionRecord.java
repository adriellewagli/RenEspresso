package com.coffeepos.renespresso.controller.main.user;

public class TransactionRecord {
    private final String ticketNumber;
    private final String time;
    private final String items;
    private final String discountApplied;
    private final String totalPaid;
    private final String paymentMethod;
    private final double total;
    private final String receiptText;

    public TransactionRecord(String ticketNumber, String time, String items, String discountApplied,
                             String totalPaid, String paymentMethod, double total, String receiptText) {
        this.ticketNumber = ticketNumber;
        this.time = time;
        this.items = items;
        this.discountApplied = discountApplied;
        this.totalPaid = totalPaid;
        this.paymentMethod = paymentMethod;
        this.total = total;
        this.receiptText = receiptText;
    }

    public String getTicketNumber() { return ticketNumber; }
    public String getTime() { return time; }
    public String getItems() { return items; }
    public String getDiscountApplied() { return discountApplied; }
    public String getTotalPaid() { return totalPaid; }
    public String getPaymentMethod() { return paymentMethod; }
    public double getTotal() { return total; }
    public String getReceiptText() { return receiptText; }
}