package com.coffeepos.renespresso.controller.main.user;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * State shared by the user dashboard shell and its included views
 * (POS, Order Management, About). One instance per running app.
 */
public final class UserSession {

    private static final UserSession INSTANCE = new UserSession();
    public static UserSession get() { return INSTANCE; }
    private UserSession() { }

    public static final String STORE_NAME = "RenEspresso";
    public static final String TERMINAL_ID = "REG-01";
    public static final List<String> PAYMENT_METHODS = List.of("Cash", "GCash", "Card");

    // --- cashier / shift ---
    public String cashierName = "Rina";
    public int ticketNumber = 42;
    public int ordersCount = 0;
    public double salesTotal = 0.0;
    public int itemsSold = 0;
    public int discountedOrders = 0;
    public String lastOrderTime = "—";

    /** Newest first. Order Management binds its table to this list. */
    public final ObservableList<TransactionRecord> transactionHistory = FXCollections.observableArrayList();

    // --- terminal settings (kept in sync by the Settings view) ---
    public boolean autoPrint = true;
    public boolean cashDrawer = true;
    public boolean dualReceipt = false;
    public boolean chime = true;
    public boolean requireConfirm = true;
    public boolean autoClearDiscount = true;
    public boolean paper58 = false;
    public String printer = "Thermal Receipt POS-80";
    public String defaultPayment = "Cash";

    public int receiptWidth() { return paper58 ? 32 : 42; }

    public String ticketLabel() { return String.format("#%03d", ticketNumber); }

    /** Called by POS after an order is finalized. */
    public void recordOrder(TransactionRecord record, int qty, boolean discounted) {
        transactionHistory.add(0, record);
        salesTotal += record.getTotal();
        ordersCount++;
        itemsSold += qty;
        if (discounted) discountedOrders++;
        lastOrderTime = record.getTime();
        ticketNumber++;
    }
}