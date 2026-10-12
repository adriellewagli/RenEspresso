package com.coffeepos.renespresso.controller.main.user;

import com.coffeepos.renespresso.model.OrderStatus;
import com.coffeepos.renespresso.model.TransactionRecord;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.util.Comparator;
import java.util.List;

/**
 * State shared by the cashier dashboard shell and its included views
 * (POS, Order Queue, Transaction History). One instance per running app.
 */
public final class UserSession {

    private static final UserSession INSTANCE = new UserSession();
    public static UserSession get() { return INSTANCE; }
    private UserSession() { }

    public static final String STORE_NAME = "RenEspresso";
    public static final String TERMINAL_ID = "REG-01";
    public static final List<String> PAYMENT_METHODS = List.of("Cash", "GCash", "Card");

    // --- cashier / shift (setCashierName() in the shell fills the real name at login) ---
    public String cashierName = "Cashier";
    public int ticketNumber = 1;
    public int ordersCount = 0;
    public double salesTotal = 0.0;
    public int itemsSold = 0;
    public int discountedOrders = 0;
    public String lastOrderTime = "\u2014";

    /** Newest first. The extractor makes status changes fire list "update" events. */
    public final ObservableList<TransactionRecord> transactionHistory =
            FXCollections.observableArrayList(r -> new Observable[]{r.statusProperty()});

    private final FilteredList<TransactionRecord> preparing =
            new FilteredList<>(transactionHistory, r -> r.getStatus() == OrderStatus.PREPARING);

    /** Orders still being prepared, oldest first (the queue). */
    public final SortedList<TransactionRecord> queue =
            new SortedList<>(preparing, Comparator.comparing(TransactionRecord::getCreatedAt));

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

    /** Called by POS after an order is paid. The order starts in the queue as PREPARING. */
    public void recordOrder(TransactionRecord record) {
        transactionHistory.add(0, record);
        salesTotal += record.getTotal();
        ordersCount++;
        itemsSold += record.getItemCount();
        if (record.isDiscounted()) discountedOrders++;
        lastOrderTime = record.getTime();
        ticketNumber++;
    }

    public void completeOrder(TransactionRecord record) {
        if (record.getStatus() == OrderStatus.PREPARING) record.setStatus(OrderStatus.COMPLETED);
    }

    /** Cancelled orders are taken out of today's totals. */
    public void cancelOrder(TransactionRecord record) {
        if (record.getStatus() != OrderStatus.PREPARING) return;
        record.setStatus(OrderStatus.CANCELLED);
        salesTotal = Math.max(0, salesTotal - record.getTotal());
        ordersCount = Math.max(0, ordersCount - 1);
        itemsSold = Math.max(0, itemsSold - record.getItemCount());
        if (record.isDiscounted()) discountedOrders = Math.max(0, discountedOrders - 1);
    }
}