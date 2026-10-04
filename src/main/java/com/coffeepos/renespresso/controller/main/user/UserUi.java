package com.coffeepos.renespresso.controller.main.user;

import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;

import java.net.URL;

/** Small helpers shared by the user dashboard controllers. */
public final class UserUi {

    private UserUi() { }

    public static String peso(double value) {
        return "₱" + String.format("%.2f", value);
    }

    /** Adds a classpath stylesheet to a node (no-op + log if it can't be found). */
    public static void attachCss(Parent node, String resourcePath) {
        URL url = UserUi.class.getResource(resourcePath);
        if (url == null) {
            System.err.println("Could not find stylesheet: " + resourcePath);
            return;
        }
        String ext = url.toExternalForm();
        if (!node.getStylesheets().contains(ext)) node.getStylesheets().add(ext);
    }

    public static void showInfo(Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    public static TextArea receiptArea(String receipt) {
        TextArea area = new TextArea(receipt);
        area.setEditable(false);
        area.setFont(Font.font("Monospaced", 12));
        area.setPrefColumnCount(UserSession.get().receiptWidth() + 2);
        area.setPrefRowCount(18);
        return area;
    }

    public static void showReceiptDialog(TransactionRecord record) {
        if (record.getReceiptText() == null || record.getReceiptText().isEmpty()) {
            showInfo(Alert.AlertType.INFORMATION, "Receipt Unavailable",
                    "No receipt data was stored for " + record.getTicketNumber() + ".");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Receipt");
        alert.setHeaderText("Receipt — Ticket " + record.getTicketNumber());
        alert.getDialogPane().setContent(receiptArea(record.getReceiptText()));
        alert.showAndWait();
    }
}