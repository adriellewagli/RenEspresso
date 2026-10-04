package com.coffeepos.renespresso.controller.main.user;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.print.Printer;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class UserViewController {

    // --- Inner Models ---
    public static class MenuItem {
        private final String name;
        private final String category;
        private final double priceS, priceM, priceL;
        private final String imageName;

        public MenuItem(String name, String category, double priceS, double priceM, double priceL, String imageName) {
            this.name = name;
            this.category = category;
            this.priceS = priceS;
            this.priceM = priceM;
            this.priceL = priceL;
            this.imageName = imageName;
        }

        public String getName() { return name; }
        public String getCategory() { return category; }
        public double getPriceS() { return priceS; }
        public double getPriceM() { return priceM; }
        public double getPriceL() { return priceL; }
        public String getImageName() { return imageName; }

        /** NEW: items such as pastries have one price and no S/M/L sizing. */
        public boolean isSingleSize() { return priceS == priceM && priceM == priceL; }
    }

    public static class CartItem {
        private final String name;
        private final String size;
        private final double price;
        private int quantity = 1; // NEW

        public CartItem(String name, String size, double price) {
            this.name = name;
            this.size = size;
            this.price = price;
        }

        public String getName() { return name; }
        public String getSize() { return size; }
        public double getPrice() { return price; }

        // NEW
        public int getQuantity() { return quantity; }
        public void increment() { quantity++; }
        public void decrement() { if (quantity > 1) quantity--; }
        public double getLineTotal() { return price * quantity; }
    }

    public static class TransactionRecord {
        private final String ticketNumber;
        private final String time;
        private final String items;
        private final String discountApplied;
        private final String totalPaid;
        // NEW
        private final String paymentMethod;
        private final double total;
        private final String receiptText;

        public TransactionRecord(String ticketNumber, String time, String items, String discountApplied, String totalPaid) {
            this(ticketNumber, time, items, discountApplied, totalPaid, "Cash", 0.0, "");
        }

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

    // --- FXML Bindings ---
    @FXML private StackPane rootPane;
    @FXML private VBox homeView, historyView, aboutView, settingsView;
    @FXML private HBox menuView;

    @FXML private Button btnHome, btnMenu, btnTransactionHistory, btnAbout, btnSettings, btnLogout;
    @FXML private Label lblClock;

    @FXML private Label lblDashSales, lblDashOrders, lblDashNextTicket;

    @FXML private GridPane menuGrid;
    @FXML private ListView<HBox> cartListView;
    @FXML private Label lblCartTicket, lblSubtotal, lblVat, lblDiscount, lblTotal;
    @FXML private CheckBox chkDiscount;
    @FXML private HBox rowDiscount;

    @FXML private TableView<TransactionRecord> tblHistory;

    @FXML private StackPane modalOverlay;
    @FXML private Label lblModalTicket, lblModalTotal;

    // NEW bindings: sidebar / session box
    @FXML private VBox sidebar, brandTextBox, sessionBox;
    @FXML private HBox brandBox;
    @FXML private Label lblDate, lblCashierName, lblWelcome;

    // NEW bindings: home dashboard
    @FXML private Label lblDashAvg, lblDashItems, lblDashDiscounted, lblDashLast;
    @FXML private VBox recentOrdersBox;

    // NEW bindings: menu
    @FXML private HBox categoryBar;
    @FXML private TextField txtMenuSearch;

    // NEW bindings: history
    @FXML private TextField txtHistorySearch;
    @FXML private Label lblHistorySummary;

    // NEW bindings: payment modal
    @FXML private ComboBox<String> cmbPaymentMethod;
    @FXML private HBox rowCash, quickCashBar, rowChange;
    @FXML private TextField txtCashReceived;
    @FXML private Label lblChange;
    @FXML private Button btnConfirmOrder;

    // NEW bindings: settings
    @FXML private ComboBox<String> cmbPrinter, cmbDefaultPayment;
    @FXML private RadioButton rbPaper80, rbPaper58;
    @FXML private CheckBox chkAutoPrint, chkCashDrawer, chkDualReceipt, chkChime, chkRequireConfirm, chkAutoClearDiscount;

    // --- Session State Variables ---
    private int ticketNumber = 42;
    private int sessionOrdersCount = 0;
    private double sessionSalesTotal = 0.0;

    // NEW session state
    private int sessionItemsSold = 0;
    private int sessionDiscountedOrders = 0;
    private String lastOrderTime = "—";
    private String cashierName = "Rina";
    private String currentCategory = "All";
    private double currentSubtotal, currentVat, currentDiscount, currentTotal;
    private Runnable onLogout; // set by whoever loads this view (e.g. to return to the login screen)

    private static final String STORE_NAME = "RenEspresso";
    private static final String TERMINAL_ID = "REG-01";
    private static final List<String> PAYMENT_METHODS = List.of("Cash", "GCash", "Card");
    private static final double SIDEBAR_EXPANDED_WIDTH = 260.0;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 85.0;
    private static final Path SETTINGS_FILE =
            Paths.get(System.getProperty("user.home"), ".renespresso", "user-settings.properties");

    private final SimpleDateFormat clockFormat = new SimpleDateFormat("hh:mm a");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d, yyyy");
    private final SimpleDateFormat receiptDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm a");

    private Timeline clockTimeline;
    private final EventHandler<KeyEvent> shortcutHandler = this::handleShortcut;
    private boolean sidebarCollapsed = false;
    private final Map<Button, String> navFullText = new LinkedHashMap<>();

    private final ObservableList<CartItem> cartItems = FXCollections.observableArrayList();
    private final ObservableList<TransactionRecord> transactionHistory = FXCollections.observableArrayList();

    // Map exact image filenames here
    private final List<MenuItem> menuData = List.of(
            // Hot Coffee (existing)
            new MenuItem("Fresh Brew / Americano", "Hot Coffee", 55.0, 75.0, 95.0, "freshbrewamericano.jpg"),
            new MenuItem("Classic Caffè Latte", "Hot Coffee", 65.0, 85.0, 105.0, "classiccafelate.jpg"),
            new MenuItem("Cappuccino", "Hot Coffee", 65.0, 85.0, 105.0, "cappucino.jpg"),
            new MenuItem("Cafè Mocha", "Hot Coffee", 75.0, 95.0, 115.0, "cafemocha.jpg"),
            new MenuItem("Caramel Macchiato", "Hot Coffee", 80.0, 100.0, 120.0, "caramelmacchiato.jpg"),
            new MenuItem("Spanish Latte", "Hot Coffee", 75.0, 95.0, 115.0, "spanishlatte.jpg"),

            // NEW: Iced Coffee
            new MenuItem("Iced Americano", "Iced Coffee", 65.0, 85.0, 105.0, "icedamericano.jpg"),
            new MenuItem("Iced Caffè Latte", "Iced Coffee", 75.0, 105.0, 125.0, "icedlatte.jpg"),
            new MenuItem("Iced Spanish Latte", "Iced Coffee", 85.0, 105.0, 125.0, "icedspanishlatte.jpg"),
            new MenuItem("Iced Caramel Macchiato", "Iced Coffee", 90.0, 110.0, 130.0, "icedcaramelmacchiato.jpg"),
            new MenuItem("Iced Mocha", "Iced Coffee", 85.0, 105.0, 125.0, "icedmocha.jpg"),

            // NEW: Milk Tea
            new MenuItem("Classic Milk Tea", "Milk Tea", 60.0, 80.0, 100.0, "classicmilktea.jpg"),
            new MenuItem("Wintermelon Milk Tea", "Milk Tea", 60.0, 80.0, 100.0, "wintermelon.jpg"),
            new MenuItem("Okinawa Milk Tea", "Milk Tea", 65.0, 85.0, 105.0, "okinawa.jpg"),
            new MenuItem("Taro Milk Tea", "Milk Tea", 65.0, 85.0, 105.0, "taro.jpg"),

            // NEW: Pastries (single price, no sizes)
            new MenuItem("Butter Croissant", "Pastries", 75.0, 75.0, 75.0, "croissant.jpg"),
            new MenuItem("Blueberry Muffin", "Pastries", 70.0, 70.0, 70.0, "blueberrymuffin.jpg"),
            new MenuItem("Choco Chip Cookie", "Pastries", 55.0, 55.0, 55.0, "chocochipcookie.jpg"),
            new MenuItem("Cinnamon Roll", "Pastries", 85.0, 85.0, 85.0, "cinnamonroll.jpg")
    );

    @FXML
    public void initialize() {
        // --- Attach Stylesheet Programmatically ---
        try {
            URL cssUrl = getClass().getResource("/com/coffeepos/renespresso/views/styles/admin/dashboard.css");
            if (cssUrl != null) {
                rootPane.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                System.err.println("Could not find dashboard.css in resources!");
            }
        } catch (Exception e) {
            System.err.println("Error loading CSS: " + e.getMessage());
        }

        lblClock.setText(clockFormat.format(new Date()));
        lblDate.setText(dateFormat.format(new Date()));

        bindManagedToVisible();
        setupLiveClock();
        setupSidebarToggle();
        setupTableColumns();
        setupSettings();
        setupMenuSearch();
        setupPaymentModal();
        setupKeyboardShortcuts();

        renderMenu("All");
        updateTotals();
        refreshDashboard();
    }

    // =====================================================================
    //  NEW: GENERAL SETUP HELPERS
    // =====================================================================

    /** Same behaviour as the admin dashboard: hidden views don't take layout space. */
    private void bindManagedToVisible() {
        for (Region view : List.of(homeView, menuView, historyView, aboutView, settingsView)) {
            view.managedProperty().bind(view.visibleProperty());
        }
    }

    private void setupLiveClock() {
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            Date now = new Date();
            lblClock.setText(clockFormat.format(now));
            lblDate.setText(dateFormat.format(now));
        }));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    private void setupKeyboardShortcuts() {
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, shortcutHandler);
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, shortcutHandler);
            } else if (clockTimeline != null) {
                clockTimeline.stop(); // view was removed (e.g. after logout)
            }
        });
    }

    /** F1 Home, F2 Menu, F3 History, F4 About, F5 Settings, Esc closes the payment modal. */
    private void handleShortcut(KeyEvent event) {
        switch (event.getCode()) {
            case ESCAPE -> {
                if (modalOverlay.isVisible()) {
                    closeConfirmationModal();
                    event.consume();
                }
            }
            case F1 -> { if (!modalOverlay.isVisible()) { showHome(); event.consume(); } }
            case F2 -> { if (!modalOverlay.isVisible()) { showMenu(); event.consume(); } }
            case F3 -> { if (!modalOverlay.isVisible()) { showTransactionHistory(); event.consume(); } }
            case F4 -> { if (!modalOverlay.isVisible()) { showAbout(); event.consume(); } }
            case F5 -> { if (!modalOverlay.isVisible()) { showSettings(); event.consume(); } }
            default -> { }
        }
    }

    /** Lets the login flow set the cashier who is signed in. */
    public void setCashierName(String name) {
        if (name == null || name.isBlank()) return;
        cashierName = name.trim();
        lblCashierName.setText("Cashier — " + cashierName);
        lblWelcome.setText("Welcome back, Cashier " + cashierName + " 👋");
    }

    /** Lets the login flow decide what happens after a confirmed logout (e.g. load the login screen). */
    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    // =====================================================================
    //  NEW: COLLAPSIBLE SIDEBAR (same interaction as the admin dashboard)
    // =====================================================================
    private void setupSidebarToggle() {
        for (Button b : List.of(btnHome, btnMenu, btnTransactionHistory, btnAbout, btnSettings, btnLogout)) {
            navFullText.put(b, b.getText());
        }
        sidebar.setMinWidth(Region.USE_PREF_SIZE);
        sidebar.setMaxWidth(Region.USE_PREF_SIZE);
    }

    @FXML
    public void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;

        brandTextBox.setVisible(!sidebarCollapsed);
        brandTextBox.setManaged(!sidebarCollapsed);
        sessionBox.setVisible(!sidebarCollapsed);
        sessionBox.setManaged(!sidebarCollapsed);
        brandBox.setAlignment(sidebarCollapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        sidebar.setStyle(sidebarCollapsed ? "-fx-padding: 20 10 20 10;" : "");

        for (Map.Entry<Button, String> entry : navFullText.entrySet()) {
            Button button = entry.getKey();
            String full = entry.getValue().trim();
            String icon = full.split("\\s+")[0];
            if (sidebarCollapsed) {
                button.setText(icon);
                button.setAlignment(Pos.CENTER);
                button.setTooltip(new Tooltip(full.substring(icon.length()).trim()));
            } else {
                button.setText(entry.getValue());
                button.setAlignment(Pos.BASELINE_LEFT);
                button.setTooltip(null);
            }
        }

        double target = sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH;
        new Timeline(new KeyFrame(Duration.millis(180), new KeyValue(sidebar.prefWidthProperty(), target))).play();
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

    @FXML public void showHome() { hideAllViews(); homeView.setVisible(true); highlightButton(btnHome); refreshDashboard(); }
    @FXML public void showMenu() { hideAllViews(); menuView.setVisible(true); highlightButton(btnMenu); }
    @FXML public void showTransactionHistory() { hideAllViews(); historyView.setVisible(true); highlightButton(btnTransactionHistory); }
    @FXML public void showAbout() { hideAllViews(); aboutView.setVisible(true); highlightButton(btnAbout); }
    @FXML public void showSettings() { hideAllViews(); settingsView.setVisible(true); highlightButton(btnSettings); }

    @FXML
    public void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to log out?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Log Out Confirmation");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                System.out.println("User logged out successfully.");
                if (onLogout != null) onLogout.run();
            }
        });
    }

    // =====================================================================
    //  HOME DASHBOARD
    // =====================================================================

    /** NEW: refreshes the metric cards, shift highlights and the recent orders list. */
    private void refreshDashboard() {
        lblDashSales.setText(peso(sessionSalesTotal));
        lblDashOrders.setText(String.valueOf(sessionOrdersCount));
        lblDashAvg.setText(peso(sessionOrdersCount == 0 ? 0 : sessionSalesTotal / sessionOrdersCount));
        lblDashItems.setText(String.valueOf(sessionItemsSold));
        lblDashDiscounted.setText(String.valueOf(sessionDiscountedOrders));
        lblDashLast.setText(lastOrderTime);

        recentOrdersBox.getChildren().clear();
        if (transactionHistory.isEmpty()) {
            Label empty = new Label("No orders yet this shift.");
            empty.getStyleClass().add("card-subtext");
            recentOrdersBox.getChildren().add(empty);
            return;
        }
        int shown = Math.min(4, transactionHistory.size());
        for (int i = 0; i < shown; i++) {
            TransactionRecord r = transactionHistory.get(i);
            Label left = new Label(r.getTicketNumber() + "  ·  " + r.getTime());
            left.getStyleClass().add("card-subtext");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label right = new Label(r.getTotalPaid());
            right.getStyleClass().add("about-item-bold");
            recentOrdersBox.getChildren().add(new HBox(left, spacer, right));
        }
    }

    // =====================================================================
    //  MENU
    // =====================================================================
    private void setupMenuSearch() {
        txtMenuSearch.textProperty().addListener((obs, oldText, newText) -> renderMenu(currentCategory));
    }

    // --- MENU CARD GENERATION WITH IMAGES ---
    private void renderMenu(String category) {
        currentCategory = category;
        String query = txtMenuSearch == null || txtMenuSearch.getText() == null
                ? "" : txtMenuSearch.getText().trim().toLowerCase();

        menuGrid.getChildren().clear();
        int col = 0, row = 0;

        for (MenuItem item : menuData) {
            if (!category.equals("All") && !item.getCategory().equalsIgnoreCase(category)) continue;
            if (!query.isEmpty() && !item.getName().toLowerCase().contains(query)) continue;

            VBox card = new VBox(8);
            card.setPrefWidth(220);
            card.setAlignment(Pos.TOP_CENTER);
            card.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 12; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 2);");

            // Category Label
            Label cat = new Label(item.getCategory().toUpperCase());
            cat.setStyle("-fx-text-fill: #895737; -fx-font-size: 10px;");

            // Image Container
            ImageView imageView = new ImageView();
            imageView.setFitWidth(130);
            imageView.setFitHeight(100);
            imageView.setPreserveRatio(true);

            boolean imageLoaded = false;
            try {
                InputStream is = getClass().getResourceAsStream("/com/coffeepos/renespresso/images/" + item.getImageName());
                if (is != null) {
                    imageView.setImage(new Image(is));
                    imageLoaded = true;
                }
            } catch (Exception e) {
                System.err.println("Could not load image: " + item.getImageName());
            }

            // NEW: emoji placeholder when the image file is missing
            Node imageNode = imageView;
            if (!imageLoaded) {
                Label placeholder = new Label(categoryEmoji(item.getCategory()));
                placeholder.setStyle("-fx-font-size: 48px;");
                StackPane holder = new StackPane(placeholder);
                holder.setPrefSize(130, 100);
                holder.setMinHeight(100);
                imageNode = holder;
            }

            // Title
            Label title = new Label(item.getName());
            title.setStyle("-fx-text-fill: #5E3023; -fx-font-weight: bold; -fx-font-size: 13px;");
            title.setWrapText(true);

            // Size Options
            HBox sizes = new HBox(6);
            sizes.setAlignment(Pos.CENTER);
            if (item.isSingleSize()) {
                sizes.getChildren().add(createSingleChip(item.getPriceM(), item));
            } else {
                sizes.getChildren().addAll(
                        createSizeChip("S 8oz", item.getPriceS(), item),
                        createSizeChip("M 12oz", item.getPriceM(), item),
                        createSizeChip("L 16oz", item.getPriceL(), item)
                );
            }

            // Left-align text metadata while centering overall card
            VBox infoBox = new VBox(2, cat, title);
            infoBox.setAlignment(Pos.CENTER_LEFT);
            infoBox.setMaxWidth(Double.MAX_VALUE);

            card.getChildren().addAll(infoBox, imageNode, sizes);
            menuGrid.add(card, col, row);

            col++;
            if (col > 2) {
                col = 0;
                row++;
            }
        }

        // NEW: empty state
        if (menuGrid.getChildren().isEmpty()) {
            Label none = new Label("No menu items match your search.");
            none.setStyle("-fx-text-fill: #895737; -fx-font-size: 13px;");
            menuGrid.add(none, 0, 0);
        }
    }

    private String categoryEmoji(String category) {
        return switch (category) {
            case "Iced Coffee" -> "🧊";
            case "Milk Tea" -> "🧋";
            case "Pastries" -> "🥐";
            default -> "☕";
        };
    }

    private Button createSizeChip(String label, double price, MenuItem item) {
        Button btn = new Button(label + "\n₱" + String.format("%.2f", price));
        btn.setStyle("-fx-background-color: #F3E9DC; -fx-text-fill: #5E3023; -fx-font-size: 10px; -fx-background-radius: 6; -fx-cursor: hand; -fx-text-alignment: center;");
        btn.setOnAction(e -> addToCart(item.getName(), label.split(" ")[0], price));
        return btn;
    }

    /** NEW: single-price chip for items with no sizes (pastries). */
    private Button createSingleChip(double price, MenuItem item) {
        Button btn = new Button("Add to order\n₱" + String.format("%.2f", price));
        btn.setStyle("-fx-background-color: #F3E9DC; -fx-text-fill: #5E3023; -fx-font-size: 10px; -fx-background-radius: 6; -fx-cursor: hand; -fx-text-alignment: center;");
        btn.setOnAction(e -> addToCart(item.getName(), "", price));
        return btn;
    }

    @FXML
    public void filterCategory(ActionEvent event) {
        Button btn = (Button) event.getSource();

        // NEW: move the "active" highlight to the clicked category
        for (var child : categoryBar.getChildren()) {
            if (child instanceof Button b) {
                b.getStyleClass().removeAll("category-btn-active", "category-btn");
                b.getStyleClass().add(b == btn ? "category-btn-active" : "category-btn");
            }
        }
        renderMenu(btn.getText());
    }

    // =====================================================================
    //  CART
    // =====================================================================
    private void addToCart(String name, String size, double price) {
        // NEW: same item + size increases quantity instead of adding a duplicate row
        CartItem existing = null;
        for (CartItem ci : cartItems) {
            if (ci.getName().equals(name) && ci.getSize().equals(size)) {
                existing = ci;
                break;
            }
        }
        if (existing != null) {
            existing.increment();
        } else {
            cartItems.add(new CartItem(name, size, price));
        }
        refreshCartUI();
        updateTotals();
    }

    private void refreshCartUI() {
        cartListView.getItems().clear();
        for (CartItem item : cartItems) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            VBox details = new VBox(2);
            Label title = new Label(item.getName() + (item.getSize().isEmpty() ? "" : " (" + item.getSize() + ")"));
            title.setStyle("-fx-text-fill: #5E3023; -fx-font-weight: bold; -fx-font-size: 12px;");
            Label pr = new Label("₱" + String.format("%.2f", item.getPrice())
                    + (item.getQuantity() > 1 ? "  ×  " + item.getQuantity() + "  =  ₱" + String.format("%.2f", item.getLineTotal()) : ""));
            pr.setStyle("-fx-text-fill: #895737; -fx-font-size: 11px;");
            details.getChildren().addAll(title, pr);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // NEW: quantity stepper
            String stepStyle = "-fx-background-color: #F3E9DC; -fx-text-fill: #5E3023; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 2 8 2 8;";
            Button btnMinus = new Button("−");
            btnMinus.setStyle(stepStyle);
            btnMinus.setOnAction(e -> {
                if (item.getQuantity() > 1) {
                    item.decrement();
                } else {
                    cartItems.remove(item);
                }
                refreshCartUI();
                updateTotals();
            });
            Label lblQty = new Label(String.valueOf(item.getQuantity()));
            lblQty.setStyle("-fx-text-fill: #5E3023; -fx-font-weight: bold; -fx-font-size: 12px;");
            lblQty.setMinWidth(18);
            lblQty.setAlignment(Pos.CENTER);
            Button btnPlus = new Button("+");
            btnPlus.setStyle(stepStyle);
            btnPlus.setOnAction(e -> {
                item.increment();
                refreshCartUI();
                updateTotals();
            });

            Button btnRemove = new Button("✕");
            btnRemove.setStyle("-fx-background-color: transparent; -fx-text-fill: #5E3023; -fx-cursor: hand; -fx-font-weight: bold;");
            btnRemove.setOnAction(e -> {
                cartItems.remove(item);
                refreshCartUI();
                updateTotals();
            });

            row.getChildren().addAll(details, spacer, btnMinus, lblQty, btnPlus, btnRemove);
            cartListView.getItems().add(row);
        }
    }

    /** NEW: empties the whole cart (asks first when there is something in it). */
    @FXML
    public void handleClearCart() {
        if (cartItems.isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Remove all items from the current order?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Clear Order");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                cartItems.clear();
                chkDiscount.setSelected(false);
                refreshCartUI();
                updateTotals();
            }
        });
    }

    @FXML
    public void handleDiscountToggle() {
        updateTotals();
    }

    private void updateTotals() {
        double rawSubtotal = cartItems.stream().mapToDouble(CartItem::getLineTotal).sum();
        boolean isDiscounted = chkDiscount.isSelected();

        double vat = isDiscounted ? 0 : rawSubtotal * 0.12;
        double discountAmount = isDiscounted ? rawSubtotal * 0.20 : 0.0;
        double finalTotal = (rawSubtotal + vat) - discountAmount;

        // NEW: keep the numbers for the receipt and payment modal
        currentSubtotal = rawSubtotal;
        currentVat = vat;
        currentDiscount = discountAmount;
        currentTotal = finalTotal;

        lblSubtotal.setText("₱" + String.format("%.2f", rawSubtotal));
        lblVat.setText("₱" + String.format("%.2f", vat));

        rowDiscount.setVisible(isDiscounted);
        lblDiscount.setText("-₱" + String.format("%.2f", discountAmount));
        lblTotal.setText("₱" + String.format("%.2f", finalTotal));

        String ticketFormatted = String.format("#%03d", ticketNumber);
        lblCartTicket.setText("will be ticket " + ticketFormatted);
        lblDashNextTicket.setText(ticketFormatted);
    }

    // =====================================================================
    //  TRANSACTION HISTORY
    // =====================================================================
    @SuppressWarnings("unchecked")
    private void setupTableColumns() {
        TableColumn<TransactionRecord, String> colTicket = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(0);
        TableColumn<TransactionRecord, String> colTime = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(1);
        TableColumn<TransactionRecord, String> colItems = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(2);
        TableColumn<TransactionRecord, String> colDiscount = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(3);
        TableColumn<TransactionRecord, String> colTotal = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(4);
        TableColumn<TransactionRecord, String> colPayment = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(5);

        colTicket.setCellValueFactory(new PropertyValueFactory<>("ticketNumber"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        colItems.setCellValueFactory(new PropertyValueFactory<>("items"));
        colDiscount.setCellValueFactory(new PropertyValueFactory<>("discountApplied"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalPaid"));
        colPayment.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));

        // Force center alignment on both header titles and cell content
        List.of(colTicket, colTime, colItems, colDiscount, colTotal, colPayment).forEach(col ->
                col.setStyle("-fx-alignment: CENTER;")
        );

        // NEW: searchable + sortable view of the history list
        FilteredList<TransactionRecord> filtered = new FilteredList<>(transactionHistory, r -> true);
        txtHistorySearch.textProperty().addListener((obs, oldText, newText) ->
                filtered.setPredicate(r -> matchesHistoryQuery(r, newText)));
        filtered.addListener((ListChangeListener<TransactionRecord>) c -> updateHistorySummary(filtered));

        SortedList<TransactionRecord> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tblHistory.comparatorProperty());
        tblHistory.setItems(sorted);

        tblHistory.setPlaceholder(new Label("No transactions recorded for this session yet."));
        // NEW: double-click a row to view its receipt
        tblHistory.setRowFactory(tv -> {
            TableRow<TransactionRecord> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) showReceiptDialog(row.getItem());
            });
            return row;
        });
        updateHistorySummary(filtered);
    }

    private boolean matchesHistoryQuery(TransactionRecord r, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase();
        return r.getTicketNumber().toLowerCase().contains(q)
                || r.getTime().toLowerCase().contains(q)
                || r.getItems().toLowerCase().contains(q)
                || r.getDiscountApplied().toLowerCase().contains(q)
                || r.getPaymentMethod().toLowerCase().contains(q);
    }

    private void updateHistorySummary(List<TransactionRecord> visible) {
        if (visible.isEmpty()) {
            lblHistorySummary.setText("No transactions yet");
            return;
        }
        double sum = visible.stream().mapToDouble(TransactionRecord::getTotal).sum();
        lblHistorySummary.setText(visible.size() + " transaction(s)  •  Total " + peso(sum));
    }

    @FXML
    public void handleReprintReceipt() {
        TransactionRecord selected = tblHistory.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo(Alert.AlertType.INFORMATION, "No Selection", "Select a transaction first (or double-click a row).");
            return;
        }
        showReceiptDialog(selected);
    }

    @FXML
    public void handleExportHistory() {
        if (transactionHistory.isEmpty()) {
            showInfo(Alert.AlertType.INFORMATION, "Nothing to Export", "There are no transactions in this session yet.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Transaction History");
        chooser.setInitialFileName("transactions_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(rootPane.getScene().getWindow());
        if (file == null) return;

        List<String> lines = new ArrayList<>();
        lines.add("Ticket,Time,Items,Discount,Payment,Total");
        for (TransactionRecord r : tblHistory.getItems()) {
            lines.add(String.join(",",
                    csv(r.getTicketNumber()), csv(r.getTime()), csv(r.getItems()),
                    csv(r.getDiscountApplied()), csv(r.getPaymentMethod()), String.format("%.2f", r.getTotal())));
        }
        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            showInfo(Alert.AlertType.INFORMATION, "Export Complete", "Saved " + tblHistory.getItems().size() + " transaction(s) to:\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            showInfo(Alert.AlertType.ERROR, "Export Failed", ex.getMessage());
        }
    }

    private String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    // =====================================================================
    //  ORDER FINALIZATION + PAYMENT MODAL
    // =====================================================================
    private void setupPaymentModal() {
        cmbPaymentMethod.getItems().setAll(PAYMENT_METHODS);
        cmbPaymentMethod.setValue("Cash");
        cmbPaymentMethod.valueProperty().addListener((obs, o, n) -> updateChangeDue());
        txtCashReceived.textProperty().addListener((obs, o, n) -> updateChangeDue());
        txtCashReceived.setOnAction(e -> {
            if (!btnConfirmOrder.isDisabled()) confirmAndFinalizeOrder();
        });
    }

    @FXML
    public void handleQuickCash(ActionEvent event) {
        String text = ((Button) event.getSource()).getText();
        if ("Exact".equals(text)) {
            txtCashReceived.setText(String.format("%.2f", currentTotal));
        } else {
            Double amount = parseMoney(text);
            if (amount != null) txtCashReceived.setText(String.format("%.2f", amount));
        }
    }

    private void updateChangeDue() {
        boolean isCash = "Cash".equals(cmbPaymentMethod.getValue());
        for (HBox box : List.of(rowCash, quickCashBar, rowChange)) {
            box.setVisible(isCash);
            box.setManaged(isCash);
        }
        if (!isCash) {
            btnConfirmOrder.setDisable(false);
            return;
        }
        Double cash = parseMoney(txtCashReceived.getText());
        long due = Math.round(currentTotal * 100);
        if (cash == null) {
            lblChange.setText("₱0.00");
            btnConfirmOrder.setDisable(true);
        } else if (Math.round(cash * 100) < due) {
            lblChange.setText("Short ₱" + String.format("%.2f", (due - Math.round(cash * 100)) / 100.0));
            btnConfirmOrder.setDisable(true);
        } else {
            lblChange.setText("₱" + String.format("%.2f", (Math.round(cash * 100) - due) / 100.0));
            btnConfirmOrder.setDisable(false);
        }
    }

    private Double parseMoney(String text) {
        if (text == null) return null;
        String cleaned = text.replace("₱", "").replace(",", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            double v = Double.parseDouble(cleaned);
            return v < 0 ? null : v;
        } catch (NumberFormatException ex) {
            return null;
        }
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

        String defaultMethod = cmbDefaultPayment.getValue() == null ? "Cash" : cmbDefaultPayment.getValue();

        // NEW: honour the "Require confirmation dialog" setting
        if (!chkRequireConfirm.isSelected()) {
            processOrder(defaultMethod, currentTotal);
            return;
        }

        lblModalTicket.setText("Ticket " + String.format("#%03d", ticketNumber));
        lblModalTotal.setText(lblTotal.getText());
        cmbPaymentMethod.setValue(defaultMethod);
        txtCashReceived.clear();
        updateChangeDue();
        modalOverlay.setVisible(true);
        if ("Cash".equals(defaultMethod)) Platform.runLater(() -> txtCashReceived.requestFocus());
    }

    @FXML
    public void closeConfirmationModal() {
        modalOverlay.setVisible(false);
    }

    @FXML
    public void confirmAndFinalizeOrder() {
        if (cartItems.isEmpty()) {
            closeConfirmationModal();
            return;
        }
        String method = cmbPaymentMethod.getValue() == null ? "Cash" : cmbPaymentMethod.getValue();
        double tendered = currentTotal;
        if ("Cash".equals(method)) {
            Double cash = parseMoney(txtCashReceived.getText());
            if (cash == null || Math.round(cash * 100) < Math.round(currentTotal * 100)) return; // button is disabled anyway
            tendered = cash;
        }
        processOrder(method, tendered);
    }

    /** Records the order, updates the session numbers and (optionally) shows the receipt. */
    private void processOrder(String paymentMethod, double tendered) {
        double currentTotalPaid = currentTotal;
        int totalQty = cartItems.stream().mapToInt(CartItem::getQuantity).sum();

        String itemsSummary = totalQty + " item(s) (" + cartItems.get(0).getName() + (cartItems.size() > 1 ? ", ..." : "") + ")";
        String timeNow = clockFormat.format(new Date());
        String ticketFormatted = String.format("#%03d", ticketNumber);
        boolean discounted = chkDiscount.isSelected();

        String receipt = buildReceipt(ticketFormatted, paymentMethod, tendered);

        transactionHistory.add(0, new TransactionRecord(
                ticketFormatted,
                timeNow,
                itemsSummary,
                discounted ? "Senior/PWD (20%)" : "None",
                lblTotal.getText(),
                paymentMethod,
                currentTotalPaid,
                receipt
        ));

        sessionSalesTotal += currentTotalPaid;
        sessionOrdersCount++;
        sessionItemsSold += totalQty;
        if (discounted) sessionDiscountedOrders++;
        lastOrderTime = timeNow;
        ticketNumber++;

        cartItems.clear();
        if (chkAutoClearDiscount.isSelected()) chkDiscount.setSelected(false);
        refreshCartUI();
        updateTotals();
        refreshDashboard();
        closeConfirmationModal();

        boolean autoPrint = chkAutoPrint.isSelected();
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                autoPrint ? "Order finalized and sent to receipt printer!" : "Order finalized.");
        alert.setHeaderText("Order Processed");
        if (autoPrint) {
            alert.getDialogPane().setExpandableContent(receiptArea(receipt));
            alert.getDialogPane().setExpanded(true);
        }
        alert.showAndWait();
    }

    // =====================================================================
    //  RECEIPTS
    // =====================================================================
    private int receiptWidth() {
        return rbPaper58.isSelected() ? 32 : 42;
    }

    String buildReceipt(String ticket, String paymentMethod, double tendered) {
        int w = receiptWidth();
        String line = "-".repeat(w);
        StringBuilder sb = new StringBuilder();

        sb.append(center(STORE_NAME, w)).append('\n');
        sb.append(center("Coffee Shop POS", w)).append('\n');
        sb.append(line).append('\n');
        sb.append(row("Ticket:", ticket, w)).append('\n');
        sb.append(row("Date:", receiptDateFormat.format(new Date()), w)).append('\n');
        sb.append(row("Cashier:", cashierName, w)).append('\n');
        sb.append(row("Terminal:", TERMINAL_ID, w)).append('\n');
        sb.append(line).append('\n');

        for (CartItem ci : cartItems) {
            String label = ci.getQuantity() + "x " + ci.getName() + (ci.getSize().isEmpty() ? "" : " (" + ci.getSize() + ")");
            sb.append(row(label, peso(ci.getLineTotal()), w)).append('\n');
        }

        sb.append(line).append('\n');
        sb.append(row("Subtotal", peso(currentSubtotal), w)).append('\n');
        sb.append(row("VAT (12%)", peso(currentVat), w)).append('\n');
        if (currentDiscount > 0) {
            sb.append(row("Senior/PWD (20%)", "-" + peso(currentDiscount), w)).append('\n');
        }
        sb.append(row("TOTAL", peso(currentTotal), w)).append('\n');
        sb.append(line).append('\n');
        sb.append(row("Payment", paymentMethod, w)).append('\n');
        if ("Cash".equals(paymentMethod)) {
            sb.append(row("Cash", peso(tendered), w)).append('\n');
            sb.append(row("Change", peso(Math.max(0, tendered - currentTotal)), w)).append('\n');
        }
        sb.append(line).append('\n');
        sb.append(center("Thank you! Please come again.", w));

        String body = sb.toString();
        if (chkDualReceipt.isSelected()) {
            return center("*** CUSTOMER COPY ***", w) + "\n" + body
                    + "\n\n\n" + center("*** MERCHANT COPY ***", w) + "\n" + body;
        }
        return body;
    }

    private String center(String text, int width) {
        if (text.length() >= width) return text;
        return " ".repeat((width - text.length()) / 2) + text;
    }

    private String row(String left, String right, int width) {
        int room = width - right.length() - 1;
        if (left.length() > room) left = left.substring(0, Math.max(0, room - 1)) + "…";
        return left + " ".repeat(Math.max(1, width - left.length() - right.length())) + right;
    }

    private TextArea receiptArea(String receipt) {
        TextArea area = new TextArea(receipt);
        area.setEditable(false);
        area.setFont(Font.font("Monospaced", 12));
        area.setPrefColumnCount(receiptWidth() + 2);
        area.setPrefRowCount(18);
        return area;
    }

    private void showReceiptDialog(TransactionRecord record) {
        if (record.getReceiptText() == null || record.getReceiptText().isEmpty()) {
            showInfo(Alert.AlertType.INFORMATION, "Receipt Unavailable", "No receipt data was stored for " + record.getTicketNumber() + ".");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Receipt");
        alert.setHeaderText("Receipt — Ticket " + record.getTicketNumber());
        alert.getDialogPane().setContent(receiptArea(record.getReceiptText()));
        alert.showAndWait();
    }

    // =====================================================================
    //  SETTINGS
    // =====================================================================
    private void setupSettings() {
        ToggleGroup paperGroup = new ToggleGroup();
        rbPaper80.setToggleGroup(paperGroup);
        rbPaper58.setToggleGroup(paperGroup);

        cmbPrinter.getItems().setAll(detectPrinters());
        cmbDefaultPayment.getItems().setAll(PAYMENT_METHODS);

        applyDefaultSettings();
        loadSettings();
    }

    private List<String> detectPrinters() {
        List<String> names = new ArrayList<>(List.of("Thermal Receipt POS-80"));
        try {
            for (Printer p : Printer.getAllPrinters()) {
                if (!names.contains(p.getName())) names.add(p.getName());
            }
        } catch (Throwable ignored) {
            // no printer subsystem available - keep the default entry only
        }
        return names;
    }

    private void applyDefaultSettings() {
        chkAutoPrint.setSelected(true);
        chkCashDrawer.setSelected(true);
        chkDualReceipt.setSelected(false);
        chkChime.setSelected(true);
        chkRequireConfirm.setSelected(true);
        chkAutoClearDiscount.setSelected(true);
        rbPaper80.setSelected(true);
        cmbPrinter.setValue(cmbPrinter.getItems().get(0));
        cmbDefaultPayment.setValue("Cash");
    }

    private void loadSettings() {
        if (!Files.exists(SETTINGS_FILE)) return;
        Properties p = new Properties();
        try (var in = Files.newInputStream(SETTINGS_FILE)) {
            p.load(in);
        } catch (IOException ex) {
            System.err.println("Could not read settings: " + ex.getMessage());
            return;
        }
        chkAutoPrint.setSelected(Boolean.parseBoolean(p.getProperty("autoPrint", "true")));
        chkCashDrawer.setSelected(Boolean.parseBoolean(p.getProperty("cashDrawer", "true")));
        chkDualReceipt.setSelected(Boolean.parseBoolean(p.getProperty("dualReceipt", "false")));
        chkChime.setSelected(Boolean.parseBoolean(p.getProperty("chime", "true")));
        chkRequireConfirm.setSelected(Boolean.parseBoolean(p.getProperty("requireConfirm", "true")));
        chkAutoClearDiscount.setSelected(Boolean.parseBoolean(p.getProperty("autoClearDiscount", "true")));
        if ("58".equals(p.getProperty("paperWidth", "80"))) rbPaper58.setSelected(true); else rbPaper80.setSelected(true);

        String printer = p.getProperty("printer");
        if (printer != null && cmbPrinter.getItems().contains(printer)) cmbPrinter.setValue(printer);
        String payment = p.getProperty("defaultPayment");
        if (payment != null && PAYMENT_METHODS.contains(payment)) cmbDefaultPayment.setValue(payment);
    }

    @FXML
    public void handleSaveSettings() {
        Properties p = new Properties();
        p.setProperty("autoPrint", String.valueOf(chkAutoPrint.isSelected()));
        p.setProperty("cashDrawer", String.valueOf(chkCashDrawer.isSelected()));
        p.setProperty("dualReceipt", String.valueOf(chkDualReceipt.isSelected()));
        p.setProperty("chime", String.valueOf(chkChime.isSelected()));
        p.setProperty("requireConfirm", String.valueOf(chkRequireConfirm.isSelected()));
        p.setProperty("autoClearDiscount", String.valueOf(chkAutoClearDiscount.isSelected()));
        p.setProperty("paperWidth", rbPaper58.isSelected() ? "58" : "80");
        if (cmbPrinter.getValue() != null) p.setProperty("printer", cmbPrinter.getValue());
        if (cmbDefaultPayment.getValue() != null) p.setProperty("defaultPayment", cmbDefaultPayment.getValue());

        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (var out = Files.newOutputStream(SETTINGS_FILE)) {
                p.store(out, "RenEspresso cashier terminal settings");
            }
            showInfo(Alert.AlertType.INFORMATION, "Settings Saved", "Your preferences were saved on this terminal.");
        } catch (IOException ex) {
            showInfo(Alert.AlertType.ERROR, "Save Failed", ex.getMessage());
        }
    }

    @FXML
    public void handleResetSettings() {
        applyDefaultSettings();
    }

    @FXML
    public void handleResetQueue() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Reset the ticket counter back to #001? Only do this when starting a new operational day.",
                ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Reset Ticket Sequence");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                ticketNumber = 1;
                updateTotals();
            }
        });
    }

    /** TODO: hook this into your database/DAO layer. */
    @FXML
    public void handleBackupDatabase() {
        showInfo(Alert.AlertType.INFORMATION, "Backup Database",
                "The database backup service isn't connected to this screen yet.\n"
                        + "Use \"Export CSV\" on the Transaction History page to save this session's sales.");
    }

    @FXML
    public void handleContactAdmin() {
        showInfo(Alert.AlertType.INFORMATION, "Contact System Administrator",
                "Terminal: " + TERMINAL_ID + " (Counter 1)\nCashier: " + cashierName
                        + "\n\nPlease report issues to your store administrator and mention the terminal ID above.");
    }

    // =====================================================================
    //  SMALL HELPERS
    // =====================================================================
    private String peso(double value) {
        return "₱" + String.format("%.2f", value);
    }

    private void showInfo(Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(header);
        alert.showAndWait();
    }
}