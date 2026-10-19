package com.coffeepos.renespresso.controller.main.user;

import com.coffeepos.renespresso.model.OrderLine;
import com.coffeepos.renespresso.model.TransactionRecord;
import com.coffeepos.renespresso.util.SettingsManager;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small helpers shared by the user dashboard controllers. */
public final class UserUi {

    private UserUi() { }

    // ------------------------------------------------------------------ BASIC HELPERS

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

    // ------------------------------------------------------------------ MODERN RECEIPT

    // Shop name, tagline, address, contact, TIN and logo now come from Settings > Store Information
    private static final String FOOTER_MESSAGE = "Thank you for visiting!";

    private static final DateTimeFormatter RECEIPT_DATE =
            DateTimeFormatter.ofPattern("MMM dd, yyyy  •  hh:mm a", Locale.ENGLISH);

    // colors (same palette as the rest of the app)
    private static final String BROWN = "#5E3023";
    private static final String BROWN_MID = "#895737";
    private static final String INK = "#2F2118";
    private static final String MUTED = "#8C7A6A";
    private static final String CREAM = "#F3E9DC";
    private static final String LINE = "#E2D5C3";

    private static final Pattern ITEM_LINE =
            Pattern.compile("^\\s*(\\d+)x\\s+(.+?)\\s+\\u20B1\\s*([\\d,]+\\.\\d{2})\\s*$");
    private static final Pattern TERMINAL_LINE = Pattern.compile("Terminal:\\s*(\\S+)");
    private static final Pattern VARIANT = Pattern.compile("^(.*?)\\s*\\(([^()]*)\\)\\s*$");

    /** A line item shown on the receipt. */
    private record ReceiptItem(int qty, String name, String variant, String price) { }

    public static void showReceiptDialog(TransactionRecord record) {
        boolean hasText = record.getReceiptText() != null && !record.getReceiptText().isEmpty();
        if (!hasText && record.getLines().isEmpty()) {
            showInfo(Alert.AlertType.INFORMATION, "Receipt Unavailable",
                    "No receipt data was stored for " + record.getTicketNumber() + ".");
            return;
        }

        VBox paper = buildPaper(record);

        // outer area: cream background so the white "paper" stands out
        StackPane stage = new StackPane(paper);
        stage.setPadding(new Insets(22));
        stage.setStyle("-fx-background-color: " + CREAM + ";");

        ScrollPane scroll = new ScrollPane(stage);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(450);
        scroll.setPrefViewportHeight(640);
        scroll.setStyle("-fx-background-color: " + CREAM + "; -fx-background: " + CREAM
                + "; -fx-border-color: transparent;");

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Receipt");
        dialog.setHeaderText(null);
        dialog.setGraphic(null);

        DialogPane pane = dialog.getDialogPane();
        pane.setContent(scroll);
        pane.setStyle("-fx-background-color: " + CREAM + ";");

        ButtonType printType = new ButtonType("Print Receipt", ButtonBar.ButtonData.LEFT);
        pane.getButtonTypes().addAll(printType, ButtonType.CLOSE);

        Button printBtn = (Button) pane.lookupButton(printType);
        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        styleDialogButton(printBtn, true);
        styleDialogButton(closeBtn, false);
        closeBtn.setText("Close");

        // print without closing the dialog
        printBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume();
            printNode(paper, pane);
        });

        dialog.showAndWait();
    }

    private static VBox buildPaper(TransactionRecord r) {
        String receiptText = r.getReceiptText() == null ? "" : r.getReceiptText();

        VBox paper = new VBox(14);
        paper.setPadding(new Insets(30, 30, 26, 30));
        paper.setPrefWidth(390);
        paper.setMaxWidth(390);
        paper.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 20;"
                + "-fx-effect: dropshadow(gaussian, rgba(94,48,35,0.18), 22, 0, 0, 6);");

        // ---------- brand header (from Settings > Store Information)
        VBox brand = new VBox(6);
        brand.setAlignment(Pos.CENTER);

        Circle badge = new Circle(27, Color.web(BROWN));
        Region cup = new Region();
        cup.setMinSize(24, 24); cup.setPrefSize(24, 24); cup.setMaxSize(24, 24);
        cup.setStyle("-fx-background-color: " + CREAM + "; -fx-shape: \"M2 21h18v-2H2v2zM20 8h-2V5h2c1.1 0 2 .9 2 2s-.9 2-2 2zm0-5H4v10c0 2.21 1.79 4 4 4h6c2.21 0 4-1.79 4-4v-3h2c2.21 0 4-1.79 4-4s-1.79-4-4-4z\";");
        brand.getChildren().add(new StackPane(badge, cup));

        brand.getChildren().add(label(SettingsManager.getStoreName(),
                "-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + BROWN + ";"));

        String tagline = SettingsManager.getStoreTagline();
        if (!tagline.isBlank()) {
            brand.getChildren().add(label(tagline.toUpperCase(),
                    "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + BROWN_MID + ";"));
        }

        String address = SettingsManager.getStoreAddress();
        String contact = SettingsManager.getStoreContact();
        String tin = SettingsManager.getStoreTin();
        for (String line : new String[]{
                address,
                contact.isBlank() ? "" : "Tel: " + contact,
                tin.isBlank() ? "" : "TIN: " + tin}) {
            if (line.isBlank()) continue;
            Label l = label(line, "-fx-font-size: 12px; -fx-text-fill: " + MUTED + ";");
            l.setWrapText(true);
            l.setTextAlignment(TextAlignment.CENTER);
            l.setAlignment(Pos.CENTER);
            brand.getChildren().add(l);
        }

        // paid pill
        String status = r.getPaymentStatus();
        boolean paid = "Paid".equalsIgnoreCase(status);
        Label pill = label(status.toUpperCase(), "-fx-font-size: 11px; -fx-font-weight: bold;"
                + "-fx-padding: 4 14; -fx-background-radius: 20;"
                + (paid ? "-fx-background-color: #DCEBE3; -fx-text-fill: #2F6F52;"
                : "-fx-background-color: #FBE0DE; -fx-text-fill: #C53030;"));
        VBox pillBox = new VBox(pill);
        pillBox.setAlignment(Pos.CENTER);

        paper.getChildren().addAll(brand, pillBox, dashed());

        // ---------- order info
        VBox info = new VBox(8);
        info.getChildren().add(infoRow("Ticket", r.getTicketNumber(), true));
        info.getChildren().add(infoRow("Date", r.getCreatedAt().format(RECEIPT_DATE), false));
        info.getChildren().add(infoRow("Cashier", r.getStaffName(), false));
        Matcher tm = TERMINAL_LINE.matcher(receiptText);
        if (tm.find()) info.getChildren().add(infoRow("Terminal", tm.group(1), false));
        paper.getChildren().addAll(info, dashed());

        // ---------- items
        paper.getChildren().add(label("ORDER SUMMARY",
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + BROWN_MID + ";"));

        VBox items = new VBox(12);
        for (ReceiptItem it : readItems(r, receiptText)) items.getChildren().add(itemRow(it));
        paper.getChildren().addAll(items, dashed());

        // ---------- totals
        VBox totals = new VBox(8);
        totals.getChildren().add(moneyRow("Vatable Sales", peso(r.getSubtotal()), false, null));
        totals.getChildren().add(moneyRow("VAT (12%)", peso(r.getVat()), false, null));
        if (r.getDiscount() > 0) {
            totals.getChildren().add(moneyRow(r.getDiscountApplied() + " Discount",
                    "- " + peso(r.getDiscount()), false, "#2F6F52"));
        }
        paper.getChildren().add(totals);

        Label totalLbl = label("TOTAL", "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + CREAM + ";");
        Label totalAmt = label(peso(r.getTotal()), "-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
        HBox totalBar = new HBox(totalLbl, spacer(), totalAmt);
        totalBar.setAlignment(Pos.CENTER_LEFT);
        totalBar.setPadding(new Insets(14, 18, 14, 18));
        totalBar.setStyle("-fx-background-color: " + BROWN + "; -fx-background-radius: 14;");
        paper.getChildren().add(totalBar);

        // ---------- payment
        VBox pay = new VBox(8);
        pay.getChildren().add(moneyRow("Payment Method", r.getPaymentMethod(), true, null));
        if (r.getTendered() > 0) {
            pay.getChildren().add(moneyRow("Amount Tendered", peso(r.getTendered()), false, null));
            pay.getChildren().add(moneyRow("Change", peso(Math.max(0, r.getTendered() - r.getTotal())), true, null));
        }
        paper.getChildren().addAll(dashed(), pay, dashed());

        // ---------- footer
        Label thanks = label(FOOTER_MESSAGE, "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + BROWN + ";");
        Label sub = label("This serves as your official receipt.", "-fx-font-size: 11px; -fx-text-fill: " + MUTED + ";");
        VBox footer = new VBox(4, thanks, sub, barcode(r.getTicketNumber()));
        footer.setAlignment(Pos.CENTER);
        VBox.setMargin(footer.getChildren().get(2), new Insets(10, 0, 0, 0));
        Label orderNo = label(r.getOrderId(), "-fx-font-size: 11px; -fx-text-fill: " + MUTED + ";");
        footer.getChildren().add(orderNo);
        paper.getChildren().add(footer);

        return paper;
    }

    /** Reads item lines from the stored receipt text (has prices); falls back to the record's lines. */
    private static List<ReceiptItem> readItems(TransactionRecord r, String receiptText) {
        List<ReceiptItem> out = new ArrayList<>();
        for (String raw : receiptText.split("\\R")) {
            Matcher m = ITEM_LINE.matcher(raw);
            if (m.matches()) {
                out.add(makeItem(Integer.parseInt(m.group(1)), m.group(2).trim(), "₱" + m.group(3)));
            }
        }
        if (out.isEmpty()) {
            for (OrderLine l : r.getLines()) out.add(makeItem(l.qty(), l.name(), ""));
        }
        return out;
    }

    private static ReceiptItem makeItem(int qty, String fullName, String price) {
        Matcher v = VARIANT.matcher(fullName);
        if (v.matches()) return new ReceiptItem(qty, v.group(1).trim(), v.group(2).trim(), price);
        return new ReceiptItem(qty, fullName, "", price);
    }

    // ------------------------------------------------------------------ RECEIPT PIECES

    private static HBox itemRow(ReceiptItem it) {
        Label qty = label(it.qty() + "x", "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + BROWN_MID + ";"
                + "-fx-background-color: #F6EDE0; -fx-background-radius: 8; -fx-padding: 4 8;"
                + "-fx-min-width: 34; -fx-alignment: center;");
        VBox text = new VBox(2, label(it.name(), "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + INK + ";"));
        ((Label) text.getChildren().get(0)).setWrapText(true);
        if (!it.variant().isEmpty()) {
            text.getChildren().add(label(it.variant(), "-fx-font-size: 12px; -fx-text-fill: " + MUTED + ";"));
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        Label price = label(it.price(), "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + INK + ";");
        HBox row = new HBox(12, qty, text, price);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static HBox infoRow(String key, String value, boolean bold) {
        Label k = label(key, "-fx-font-size: 13px; -fx-text-fill: " + MUTED + ";");
        Label v = label(value, "-fx-font-size: 13px; -fx-text-fill: " + INK + ";"
                + (bold ? "-fx-font-weight: bold;" : ""));
        HBox row = new HBox(k, spacer(), v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static HBox moneyRow(String key, String value, boolean bold, String color) {
        String c = color != null ? color : INK;
        Label k = label(key, "-fx-font-size: 13px; -fx-text-fill: " + (color != null ? color : MUTED) + ";");
        Label v = label(value, "-fx-font-size: 14px; -fx-text-fill: " + c + ";" + (bold ? "-fx-font-weight: bold;" : ""));
        HBox row = new HBox(k, spacer(), v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Region dashed() {
        Region r = new Region();
        r.setMinHeight(1); r.setPrefHeight(1); r.setMaxHeight(1);
        r.setStyle("-fx-border-color: " + LINE + "; -fx-border-style: dashed; -fx-border-width: 1 0 0 0;");
        return r;
    }

    /** Decorative barcode generated from the ticket number. */
    private static HBox barcode(String seedText) {
        Random rnd = new Random(seedText == null ? 1 : seedText.hashCode());
        HBox bars = new HBox(2);
        bars.setAlignment(Pos.CENTER);
        for (int i = 0; i < 38; i++) {
            Rectangle bar = new Rectangle(1 + rnd.nextInt(3), 38, Color.web("#3B2A20"));
            bars.getChildren().add(bar);
        }
        return bars;
    }

    private static Label label(String text, String style) {
        Label l = new Label(text);
        l.setStyle(style);
        return l;
    }

    private static Region spacer() {
        Region s = new Region();
        HBox.setHgrow(s, Priority.ALWAYS);
        return s;
    }

    private static void styleDialogButton(Button b, boolean primary) {
        String base = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 12;"
                + "-fx-padding: 12 24; -fx-min-width: 150; -fx-cursor: hand;";
        b.setStyle(base + (primary
                ? "-fx-background-color: " + BROWN + "; -fx-text-fill: " + CREAM + ";"
                : "-fx-background-color: #FFFFFF; -fx-text-fill: " + BROWN
                + "; -fx-border-color: #E7DCCB; -fx-border-radius: 12;"));
    }

    private static void printNode(Node node, Node owner) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showInfo(Alert.AlertType.WARNING, "No printer", "No printer was found on this computer.");
            return;
        }
        if (job.showPrintDialog(owner.getScene().getWindow())) {
            if (job.printPage(node)) job.endJob();
            else showInfo(Alert.AlertType.ERROR, "Print failed", "The receipt could not be printed.");
        }
    }
}