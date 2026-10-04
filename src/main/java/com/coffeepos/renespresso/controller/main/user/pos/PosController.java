package com.coffeepos.renespresso.controller.main.user.pos;

import com.coffeepos.renespresso.controller.main.user.TransactionRecord;
import com.coffeepos.renespresso.controller.main.user.UserSession;
import com.coffeepos.renespresso.controller.main.user.UserUi;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.coffeepos.renespresso.controller.main.user.UserUi.peso;

public class PosController {

    private static final String CSS = "/com/coffeepos/renespresso/views/styles.user/pos/pos.css";
    private static final String IMG_DIR = "/com/coffeepos/renespresso/images/";

    // --- Inner models ---
    public static class MenuItem {
        private final String name, category, imageName;
        private final double priceS, priceM, priceL;

        public MenuItem(String name, String category, double priceS, double priceM, double priceL, String imageName) {
            this.name = name; this.category = category;
            this.priceS = priceS; this.priceM = priceM; this.priceL = priceL;
            this.imageName = imageName;
        }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public double getPriceS() { return priceS; }
        public double getPriceM() { return priceM; }
        public double getPriceL() { return priceL; }
        public String getImageName() { return imageName; }
        public boolean isSingleSize() { return priceS == priceM && priceM == priceL; }
    }

    public static class CartItem {
        private final String name, size;
        private final double price;
        private int quantity = 1;

        public CartItem(String name, String size, double price) {
            this.name = name; this.size = size; this.price = price;
        }
        public String getName() { return name; }
        public String getSize() { return size; }
        public double getPrice() { return price; }
        public int getQuantity() { return quantity; }
        public void increment() { quantity++; }
        public void decrement() { if (quantity > 1) quantity--; }
        public double getLineTotal() { return price * quantity; }
    }

    // --- FXML bindings ---
    @FXML private StackPane posRoot;
    @FXML private HBox categoryBar;
    @FXML private TextField txtMenuSearch;
    @FXML private GridPane menuGrid;

    @FXML private ListView<HBox> cartListView;
    @FXML private Label lblCartTicket, lblSubtotal, lblVat, lblDiscount, lblTotal;
    @FXML private CheckBox chkDiscount;
    @FXML private HBox rowDiscount;

    @FXML private StackPane modalOverlay;
    @FXML private Label lblModalTicket, lblModalTotal, lblChange;
    @FXML private ComboBox<String> cmbPaymentMethod;
    @FXML private HBox rowCash, quickCashBar, rowChange;
    @FXML private TextField txtCashReceived;
    @FXML private Button btnConfirmOrder;

    // --- State ---
    private final UserSession session = UserSession.get();
    private final ObservableList<CartItem> cartItems = FXCollections.observableArrayList();
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("hh:mm a");
    private final SimpleDateFormat receiptDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm a");
    private String currentCategory = "All";
    private double currentSubtotal, currentVat, currentDiscount, currentTotal;

    private final List<MenuItem> menuData = List.of(
            new MenuItem("Fresh Brew / Americano", "Hot Coffee", 55.0, 75.0, 95.0, "freshbrewamericano.jpg"),
            new MenuItem("Classic Caffè Latte", "Hot Coffee", 65.0, 85.0, 105.0, "classiccafelate.jpg"),
            new MenuItem("Cappuccino", "Hot Coffee", 65.0, 85.0, 105.0, "cappucino.jpg"),
            new MenuItem("Cafè Mocha", "Hot Coffee", 75.0, 95.0, 115.0, "cafemocha.jpg"),
            new MenuItem("Caramel Macchiato", "Hot Coffee", 80.0, 100.0, 120.0, "caramelmacchiato.jpg"),
            new MenuItem("Spanish Latte", "Hot Coffee", 75.0, 95.0, 115.0, "spanishlatte.jpg"),

            new MenuItem("Iced Americano", "Iced Coffee", 65.0, 85.0, 105.0, "icedamericano.jpg"),
            new MenuItem("Iced Caffè Latte", "Iced Coffee", 75.0, 105.0, 125.0, "icedlatte.jpg"),
            new MenuItem("Iced Spanish Latte", "Iced Coffee", 85.0, 105.0, 125.0, "icedspanishlatte.jpg"),
            new MenuItem("Iced Caramel Macchiato", "Iced Coffee", 90.0, 110.0, 130.0, "icedcaramelmacchiato.jpg"),
            new MenuItem("Iced Mocha", "Iced Coffee", 85.0, 105.0, 125.0, "icedmocha.jpg"),

            new MenuItem("Classic Milk Tea", "Milk Tea", 60.0, 80.0, 100.0, "classicmilktea.jpg"),
            new MenuItem("Wintermelon Milk Tea", "Milk Tea", 60.0, 80.0, 100.0, "wintermelon.jpg"),
            new MenuItem("Okinawa Milk Tea", "Milk Tea", 65.0, 85.0, 105.0, "okinawa.jpg"),
            new MenuItem("Taro Milk Tea", "Milk Tea", 65.0, 85.0, 105.0, "taro.jpg"),

            new MenuItem("Butter Croissant", "Pastries", 75.0, 75.0, 75.0, "croissant.jpg"),
            new MenuItem("Blueberry Muffin", "Pastries", 70.0, 70.0, 70.0, "blueberrymuffin.jpg"),
            new MenuItem("Choco Chip Cookie", "Pastries", 55.0, 55.0, 55.0, "chocochipcookie.jpg"),
            new MenuItem("Cinnamon Roll", "Pastries", 85.0, 85.0, 85.0, "cinnamonroll.jpg")
    );

    @FXML
    public void initialize() {
        UserUi.attachCss(posRoot, CSS);
        txtMenuSearch.textProperty().addListener((obs, o, n) -> renderMenu(currentCategory));
        setupPaymentModal();
        renderMenu("All");
        updateTotals();
    }

    // --- Called by the dashboard shell ---
    /** Re-reads the ticket number (e.g. after the queue was reset in Settings). */
    public void refreshTicket() { updateTotals(); }
    public boolean isModalOpen() { return modalOverlay.isVisible(); }

    // =====================================================================
    //  MENU
    // =====================================================================
    private void renderMenu(String category) {
        currentCategory = category;
        String query = txtMenuSearch.getText() == null ? "" : txtMenuSearch.getText().trim().toLowerCase();

        menuGrid.getChildren().clear();
        int col = 0, row = 0;

        for (MenuItem item : menuData) {
            if (!category.equals("All") && !item.getCategory().equalsIgnoreCase(category)) continue;
            if (!query.isEmpty() && !item.getName().toLowerCase().contains(query)) continue;

            VBox card = new VBox(8);
            card.setPrefWidth(220);
            card.setAlignment(Pos.TOP_CENTER);
            card.getStyleClass().add("menu-card");

            Label cat = new Label(item.getCategory().toUpperCase());
            cat.getStyleClass().add("menu-card-category");

            ImageView imageView = new ImageView();
            imageView.setFitWidth(130);
            imageView.setFitHeight(100);
            imageView.setPreserveRatio(true);

            boolean imageLoaded = false;
            try (InputStream is = getClass().getResourceAsStream(IMG_DIR + item.getImageName())) {
                if (is != null) {
                    imageView.setImage(new Image(is));
                    imageLoaded = true;
                }
            } catch (Exception e) {
                System.err.println("Could not load image: " + item.getImageName());
            }

            Node imageNode = imageView;
            if (!imageLoaded) {
                Label placeholder = new Label(categoryEmoji(item.getCategory()));
                placeholder.getStyleClass().add("menu-card-placeholder");
                StackPane holder = new StackPane(placeholder);
                holder.setPrefSize(130, 100);
                holder.setMinHeight(100);
                imageNode = holder;
            }

            Label title = new Label(item.getName());
            title.getStyleClass().add("menu-card-title");
            title.setWrapText(true);

            HBox sizes = new HBox(6);
            sizes.setAlignment(Pos.CENTER);
            if (item.isSingleSize()) {
                sizes.getChildren().add(createSingleChip(item.getPriceM(), item));
            } else {
                sizes.getChildren().addAll(
                        createSizeChip("S 8oz", item.getPriceS(), item),
                        createSizeChip("M 12oz", item.getPriceM(), item),
                        createSizeChip("L 16oz", item.getPriceL(), item));
            }

            VBox infoBox = new VBox(2, cat, title);
            infoBox.setAlignment(Pos.CENTER_LEFT);
            infoBox.setMaxWidth(Double.MAX_VALUE);

            card.getChildren().addAll(infoBox, imageNode, sizes);
            menuGrid.add(card, col, row);

            if (++col > 2) { col = 0; row++; }
        }

        if (menuGrid.getChildren().isEmpty()) {
            Label none = new Label("No menu items match your search.");
            none.getStyleClass().add("card-subtext");
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
        btn.getStyleClass().add("size-chip");
        btn.setOnAction(e -> addToCart(item.getName(), label.split(" ")[0], price));
        return btn;
    }

    private Button createSingleChip(double price, MenuItem item) {
        Button btn = new Button("Add to order\n₱" + String.format("%.2f", price));
        btn.getStyleClass().add("size-chip");
        btn.setOnAction(e -> addToCart(item.getName(), "", price));
        return btn;
    }

    @FXML
    public void filterCategory(ActionEvent event) {
        Button btn = (Button) event.getSource();
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
        CartItem existing = null;
        for (CartItem ci : cartItems) {
            if (ci.getName().equals(name) && ci.getSize().equals(size)) { existing = ci; break; }
        }
        if (existing != null) existing.increment(); else cartItems.add(new CartItem(name, size, price));
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
            title.getStyleClass().add("cart-item-title");
            Label pr = new Label("₱" + String.format("%.2f", item.getPrice())
                    + (item.getQuantity() > 1 ? "  ×  " + item.getQuantity() + "  =  ₱" + String.format("%.2f", item.getLineTotal()) : ""));
            pr.getStyleClass().add("cart-item-price");
            details.getChildren().addAll(title, pr);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button btnMinus = new Button("−");
            btnMinus.getStyleClass().add("qty-btn");
            btnMinus.setOnAction(e -> {
                if (item.getQuantity() > 1) item.decrement(); else cartItems.remove(item);
                refreshCartUI();
                updateTotals();
            });
            Label lblQty = new Label(String.valueOf(item.getQuantity()));
            lblQty.getStyleClass().add("cart-item-title");
            lblQty.setMinWidth(18);
            lblQty.setAlignment(Pos.CENTER);
            Button btnPlus = new Button("+");
            btnPlus.getStyleClass().add("qty-btn");
            btnPlus.setOnAction(e -> { item.increment(); refreshCartUI(); updateTotals(); });

            Button btnRemove = new Button("✕");
            btnRemove.getStyleClass().add("remove-btn");
            btnRemove.setOnAction(e -> { cartItems.remove(item); refreshCartUI(); updateTotals(); });

            row.getChildren().addAll(details, spacer, btnMinus, lblQty, btnPlus, btnRemove);
            cartListView.getItems().add(row);
        }
    }

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
    public void handleDiscountToggle() { updateTotals(); }

    private void updateTotals() {
        double rawSubtotal = cartItems.stream().mapToDouble(CartItem::getLineTotal).sum();
        boolean isDiscounted = chkDiscount.isSelected();

        double vat = isDiscounted ? 0 : rawSubtotal * 0.12;
        double discountAmount = isDiscounted ? rawSubtotal * 0.20 : 0.0;
        double finalTotal = (rawSubtotal + vat) - discountAmount;

        currentSubtotal = rawSubtotal;
        currentVat = vat;
        currentDiscount = discountAmount;
        currentTotal = finalTotal;

        lblSubtotal.setText(peso(rawSubtotal));
        lblVat.setText(peso(vat));
        rowDiscount.setVisible(isDiscounted);
        lblDiscount.setText("-" + peso(discountAmount));
        lblTotal.setText(peso(finalTotal));
        lblCartTicket.setText("will be ticket " + session.ticketLabel());
    }

    // =====================================================================
    //  PAYMENT MODAL
    // =====================================================================
    private void setupPaymentModal() {
        cmbPaymentMethod.getItems().setAll(UserSession.PAYMENT_METHODS);
        cmbPaymentMethod.setValue("Cash");
        cmbPaymentMethod.valueProperty().addListener((obs, o, n) -> updateChangeDue());
        txtCashReceived.textProperty().addListener((obs, o, n) -> updateChangeDue());
        txtCashReceived.setOnAction(e -> { if (!btnConfirmOrder.isDisabled()) confirmAndFinalizeOrder(); });
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
        if (!isCash) { btnConfirmOrder.setDisable(false); return; }

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

    @FXML
    public void openConfirmationModal() {
        if (cartItems.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please add items to the cart before finalizing.");
            alert.setHeaderText("Empty Cart");
            alert.showAndWait();
            return;
        }

        String defaultMethod = session.defaultPayment == null ? "Cash" : session.defaultPayment;

        if (!session.requireConfirm) {
            processOrder(defaultMethod, currentTotal);
            return;
        }

        lblModalTicket.setText("Ticket " + session.ticketLabel());
        lblModalTotal.setText(lblTotal.getText());
        cmbPaymentMethod.setValue(defaultMethod);
        txtCashReceived.clear();
        updateChangeDue();
        modalOverlay.setVisible(true);
        if ("Cash".equals(defaultMethod)) Platform.runLater(() -> txtCashReceived.requestFocus());
    }

    @FXML
    public void closeConfirmationModal() { modalOverlay.setVisible(false); }

    @FXML
    public void confirmAndFinalizeOrder() {
        if (cartItems.isEmpty()) { closeConfirmationModal(); return; }
        String method = cmbPaymentMethod.getValue() == null ? "Cash" : cmbPaymentMethod.getValue();
        double tendered = currentTotal;
        if ("Cash".equals(method)) {
            Double cash = parseMoney(txtCashReceived.getText());
            if (cash == null || Math.round(cash * 100) < Math.round(currentTotal * 100)) return;
            tendered = cash;
        }
        processOrder(method, tendered);
    }

    private void processOrder(String paymentMethod, double tendered) {
        double totalPaid = currentTotal;
        int totalQty = cartItems.stream().mapToInt(CartItem::getQuantity).sum();

        String itemsSummary = totalQty + " item(s) (" + cartItems.get(0).getName() + (cartItems.size() > 1 ? ", ..." : "") + ")";
        String timeNow = clockFormat.format(new Date());
        String ticket = session.ticketLabel();
        boolean discounted = chkDiscount.isSelected();
        String receipt = buildReceipt(ticket, paymentMethod, tendered);

        session.recordOrder(new TransactionRecord(
                ticket, timeNow, itemsSummary,
                discounted ? "Senior/PWD (20%)" : "None",
                lblTotal.getText(), paymentMethod, totalPaid, receipt), totalQty, discounted);

        cartItems.clear();
        if (session.autoClearDiscount) chkDiscount.setSelected(false);
        refreshCartUI();
        updateTotals();
        closeConfirmationModal();

        boolean autoPrint = session.autoPrint;
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                autoPrint ? "Order finalized and sent to receipt printer!" : "Order finalized.");
        alert.setHeaderText("Order Processed");
        if (autoPrint) {
            alert.getDialogPane().setExpandableContent(UserUi.receiptArea(receipt));
            alert.getDialogPane().setExpanded(true);
        }
        alert.showAndWait();
    }

    // =====================================================================
    //  RECEIPT
    // =====================================================================
    private String buildReceipt(String ticket, String paymentMethod, double tendered) {
        int w = session.receiptWidth();
        String line = "-".repeat(w);
        StringBuilder sb = new StringBuilder();

        sb.append(center(UserSession.STORE_NAME, w)).append('\n');
        sb.append(center("Coffee Shop POS", w)).append('\n');
        sb.append(line).append('\n');
        sb.append(row("Ticket:", ticket, w)).append('\n');
        sb.append(row("Date:", receiptDateFormat.format(new Date()), w)).append('\n');
        sb.append(row("Cashier:", session.cashierName, w)).append('\n');
        sb.append(row("Terminal:", UserSession.TERMINAL_ID, w)).append('\n');
        sb.append(line).append('\n');

        for (CartItem ci : cartItems) {
            String label = ci.getQuantity() + "x " + ci.getName() + (ci.getSize().isEmpty() ? "" : " (" + ci.getSize() + ")");
            sb.append(row(label, peso(ci.getLineTotal()), w)).append('\n');
        }

        sb.append(line).append('\n');
        sb.append(row("Subtotal", peso(currentSubtotal), w)).append('\n');
        sb.append(row("VAT (12%)", peso(currentVat), w)).append('\n');
        if (currentDiscount > 0) sb.append(row("Senior/PWD (20%)", "-" + peso(currentDiscount), w)).append('\n');
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
        if (session.dualReceipt) {
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
}