package com.coffeepos.renespresso.controller.main;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class UserViewController {

    // --- Inner Models ---
    public static class MenuItem {
        private final String name;
        private final String category;
        private final double priceS, priceM, priceL;

        public MenuItem(String name, String category, double priceS, double priceM, double priceL) {
            this.name = name;
            this.category = category;
            this.priceS = priceS;
            this.priceM = priceM;
            this.priceL = priceL;
        }

        public String getName() { return name; }
        public String getCategory() { return category; }
        public double getPriceS() { return priceS; }
        public double getPriceM() { return priceM; }
        public double getPriceL() { return priceL; }
    }

    public static class CartItem {
        private final String name;
        private final String size;
        private final double price;

        public CartItem(String name, String size, double price) {
            this.name = name;
            this.size = size;
            this.price = price;
        }

        public String getName() { return name; }
        public String getSize() { return size; }
        public double getPrice() { return price; }
    }

    public static class TransactionRecord {
        private final String ticketNumber;
        private final String time;
        private final String items;
        private final String discountApplied;
        private final String totalPaid;

        public TransactionRecord(String ticketNumber, String time, String items, String discountApplied, String totalPaid) {
            this.ticketNumber = ticketNumber;
            this.time = time;
            this.items = items;
            this.discountApplied = discountApplied;
            this.totalPaid = totalPaid;
        }

        public String getTicketNumber() { return ticketNumber; }
        public String getTime() { return time; }
        public String getItems() { return items; }
        public String getDiscountApplied() { return discountApplied; }
        public String getTotalPaid() { return totalPaid; }
    }

    // --- FXML Bindings ---
    // Views
    @FXML private VBox homeView, historyView, aboutView, settingsView;
    @FXML private HBox menuView;

    // Sidebar Controls
    @FXML private Button btnHome, btnMenu, btnTransactionHistory, btnAbout, btnSettings, btnLogout;
    @FXML private Label lblClock;

    // Home Metrics
    @FXML private Label lblDashSales, lblDashOrders, lblDashNextTicket;

    // Menu / POS Controls
    @FXML private GridPane menuGrid;
    @FXML private ListView<HBox> cartListView;
    @FXML private Label lblCartTicket, lblSubtotal, lblVat, lblDiscount, lblTotal;
    @FXML private CheckBox chkDiscount;
    @FXML private HBox rowDiscount;

    // Transaction Table
    @FXML private TableView<TransactionRecord> tblHistory;

    // Modal Overlay
    @FXML private StackPane modalOverlay;
    @FXML private Label lblModalTicket, lblModalTotal;

    // --- Session State Variables ---
    private int ticketNumber = 42;
    private int sessionOrdersCount = 0;
    private double sessionSalesTotal = 0.0;

    private final ObservableList<CartItem> cartItems = FXCollections.observableArrayList();
    private final ObservableList<TransactionRecord> transactionHistory = FXCollections.observableArrayList();

    private final List<MenuItem> menuData = List.of(
            new MenuItem("Fresh Brew / Americano", "Hot Coffee", 55.0, 75.0, 95.0),
            new MenuItem("Classic Caffè Latte", "Hot Coffee", 65.0, 85.0, 105.0),
            new MenuItem("Cappuccino", "Hot Coffee", 65.0, 85.0, 105.0),
            new MenuItem("Cafè Mocha", "Hot Coffee", 75.0, 95.0, 115.0),
            new MenuItem("Caramel Macchiato", "Hot Coffee", 80.0, 100.0, 120.0),
            new MenuItem("Spanish Latte", "Hot Coffee", 75.0, 95.0, 115.0)
    );

    @FXML
    public void initialize() {
        // Set clock time
        lblClock.setText(new SimpleDateFormat("hh:mm a").format(new Date()));

        // Table initialization
        setupTableColumns();

        // Render default menu items and refresh cart
        renderMenu("All");
        updateTotals();
    }

    // --- SIDEBAR NAVIGATION ---
    private void hideAllViews() {
        homeView.setVisible(false);
        menuView.setVisible(false);
        historyView.setVisible(false);
        aboutView.setVisible(false);
        settingsView.setVisible(false);

        String defaultStyle = "-fx-background-color: transparent; -fx-text-fill: #F3E9DC; -fx-font-size: 14px; -fx-cursor: hand; -fx-background-radius: 8;";
        btnHome.setStyle(defaultStyle);
        btnMenu.setStyle(defaultStyle);
        btnTransactionHistory.setStyle(defaultStyle);
        btnAbout.setStyle(defaultStyle);
        btnSettings.setStyle(defaultStyle);
    }

    private void highlightButton(Button button) {
        button.setStyle("-fx-background-color: #C08552; -fx-text-fill: #F3E9DC; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8;");
    }

    @FXML
    public void showHome() {
        hideAllViews();
        homeView.setVisible(true);
        highlightButton(btnHome);
    }

    @FXML
    public void showMenu() {
        hideAllViews();
        menuView.setVisible(true);
        highlightButton(btnMenu);
    }

    @FXML
    public void showTransactionHistory() {
        hideAllViews();
        historyView.setVisible(true);
        highlightButton(btnTransactionHistory);
    }

    @FXML
    public void showAbout() {
        hideAllViews();
        aboutView.setVisible(true);
        highlightButton(btnAbout);
    }

    @FXML
    public void showSettings() {
        hideAllViews();
        settingsView.setVisible(true);
        highlightButton(btnSettings);
    }

    @FXML
    public void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to log out?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Log Out Confirmation");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                System.out.println("User logged out successfully.");
            }
        });
    }

    // --- MENU CARD GENERATION ---
    private void renderMenu(String category) {
        menuGrid.getChildren().clear();
        int col = 0, row = 0;

        for (MenuItem item : menuData) {
            if (!category.equals("All") && !item.getCategory().equalsIgnoreCase(category)) continue;

            VBox card = new VBox(10);
            card.setPrefWidth(220);
            card.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 15; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 2);");

            Label cat = new Label(item.getCategory().toUpperCase());
            cat.setStyle("-fx-text-fill: #895737; -fx-font-size: 10px;");

            Label title = new Label(item.getName());
            title.setStyle("-fx-text-fill: #5E3023; -fx-font-weight: bold; -fx-font-size: 14px;");

            HBox sizes = new HBox(6);
            sizes.getChildren().addAll(
                    createSizeChip("S 8oz", item.getPriceS(), item),
                    createSizeChip("M 12oz", item.getPriceM(), item),
                    createSizeChip("L 16oz", item.getPriceL(), item)
            );

            card.getChildren().addAll(cat, title, sizes);
            menuGrid.add(card, col, row);

            col++;
            if (col > 2) {
                col = 0;
                row++;
            }
        }
    }

    private Button createSizeChip(String label, double price, MenuItem item) {
        Button btn = new Button(label + "\n₱" + String.format("%.2f", price));
        btn.setStyle("-fx-background-color: #F3E9DC; -fx-text-fill: #5E3023; -fx-font-size: 10px; -fx-background-radius: 6; -fx-cursor: hand; -fx-text-alignment: center;");
        btn.setOnAction(e -> addToCart(item.getName(), label.split(" ")[0], price));
        return btn;
    }

    @FXML
    public void filterCategory(ActionEvent event) {
        Button btn = (Button) event.getSource();
        renderMenu(btn.getText());
    }

    // --- CART CALCULATIONS ---
    private void addToCart(String name, String size, double price) {
        cartItems.add(new CartItem(name, size, price));
        refreshCartUI();
        updateTotals();
    }

    private void refreshCartUI() {
        cartListView.getItems().clear();
        for (CartItem item : cartItems) {
            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);

            VBox details = new VBox(2);
            Label title = new Label(item.getName() + " (" + item.getSize() + ")");
            title.setStyle("-fx-text-fill: #5E3023; -fx-font-weight: bold; -fx-font-size: 12px;");
            Label pr = new Label("₱" + String.format("%.2f", item.getPrice()));
            pr.setStyle("-fx-text-fill: #895737; -fx-font-size: 11px;");
            details.getChildren().addAll(title, pr);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button btnRemove = new Button("✕");
            btnRemove.setStyle("-fx-background-color: transparent; -fx-text-fill: #5E3023; -fx-cursor: hand; -fx-font-weight: bold;");
            btnRemove.setOnAction(e -> {
                cartItems.remove(item);
                refreshCartUI();
                updateTotals();
            });

            row.getChildren().addAll(details, spacer, btnRemove);
            cartListView.getItems().add(row);
        }
    }

    @FXML
    public void handleDiscountToggle() {
        updateTotals();
    }

    private void updateTotals() {
        double rawSubtotal = cartItems.stream().mapToDouble(CartItem::getPrice).sum();
        boolean isDiscounted = chkDiscount.isSelected();

        double vat = isDiscounted ? 0 : rawSubtotal * 0.12;
        double discountAmount = isDiscounted ? rawSubtotal * 0.20 : 0.0;
        double finalTotal = (rawSubtotal + vat) - discountAmount;

        lblSubtotal.setText("₱" + String.format("%.2f", rawSubtotal));
        lblVat.setText("₱" + String.format("%.2f", vat));

        rowDiscount.setVisible(isDiscounted);
        lblDiscount.setText("-₱" + String.format("%.2f", discountAmount));
        lblTotal.setText("₱" + String.format("%.2f", finalTotal));

        String ticketFormatted = String.format("#%03d", ticketNumber);
        lblCartTicket.setText("will be ticket " + ticketFormatted);
        lblDashNextTicket.setText(ticketFormatted);
    }

    // --- TRANSACTION HISTORY BINDINGS ---
    @SuppressWarnings("unchecked")
    private void setupTableColumns() {
        TableColumn<TransactionRecord, String> colTicket = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(0);
        TableColumn<TransactionRecord, String> colTime = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(1);
        TableColumn<TransactionRecord, String> colItems = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(2);
        TableColumn<TransactionRecord, String> colDiscount = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(3);
        TableColumn<TransactionRecord, String> colTotal = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(4);

        colTicket.setCellValueFactory(new PropertyValueFactory<>("ticketNumber"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        colItems.setCellValueFactory(new PropertyValueFactory<>("items"));
        colDiscount.setCellValueFactory(new PropertyValueFactory<>("discountApplied"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalPaid"));

        tblHistory.setItems(transactionHistory);
    }

    // --- ORDER FINALIZATION MODAL ---
    @FXML
    public void openConfirmationModal() {
        if (cartItems.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please add items to the cart before finalizing.");
            alert.setHeaderText("Empty Cart");
            alert.showAndWait();
            return;
        }
        lblModalTicket.setText("Ticket " + String.format("#%03d", ticketNumber));
        lblModalTotal.setText(lblTotal.getText());
        modalOverlay.setVisible(true);
    }

    @FXML
    public void closeConfirmationModal() {
        modalOverlay.setVisible(false);
    }

    @FXML
    public void confirmAndFinalizeOrder() {
        double currentTotal = Double.parseDouble(lblTotal.getText().replace("₱", ""));

        // Insert new order at top of history log
        String itemsSummary = cartItems.size() + " item(s) (" + cartItems.get(0).getName() + (cartItems.size() > 1 ? ", ..." : "") + ")";
        String timeNow = new SimpleDateFormat("hh:mm a").format(new Date());
        transactionHistory.add(0, new TransactionRecord(
                String.format("#%03d", ticketNumber),
                timeNow,
                itemsSummary,
                chkDiscount.isSelected() ? "Senior/PWD (20%)" : "None",
                lblTotal.getText()
        ));

        // Increment stats
        sessionSalesTotal += currentTotal;
        sessionOrdersCount++;
        ticketNumber++;

        // Update dashboard values
        lblDashSales.setText("₱" + String.format("%.2f", sessionSalesTotal));
        lblDashOrders.setText(String.valueOf(sessionOrdersCount));

        // Reset cart state
        cartItems.clear();
        chkDiscount.setSelected(false);
        refreshCartUI();
        updateTotals();
        closeConfirmationModal();

        // Display completion alert
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Order finalized and sent to receipt printer!");
        alert.setHeaderText("Order Processed");
        alert.showAndWait();
    }
}