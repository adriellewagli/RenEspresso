package com.coffeepos.renespresso.controller.main.user.pos;

import com.coffeepos.renespresso.controller.main.user.UserSession;
import com.coffeepos.renespresso.dao.MenuDAO;
import com.coffeepos.renespresso.model.CartItem;
import com.coffeepos.renespresso.model.Category;
import com.coffeepos.renespresso.model.OrderLine;
import com.coffeepos.renespresso.model.Product;
import com.coffeepos.renespresso.model.ProductVariant;
import com.coffeepos.renespresso.model.TransactionRecord;
import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.ImageStore;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.coffeepos.renespresso.controller.main.user.UserUi.peso;

public class PosController {

    private static final double PHOTO = 96;

    // --- FXML bindings ---
    @FXML private StackPane posRoot;
    @FXML private FlowPane categoryBar, menuGrid;
    @FXML private TextField txtMenuSearch;

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
    private final SimpleDateFormat receiptDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm a");
    private final ToggleGroup chipGroup = new ToggleGroup();

    private List<Category> categories = new ArrayList<>();
    private List<Product> products = new ArrayList<>();
    private Map<Integer, List<ProductVariant>> variantMap = new HashMap<>();

    private int selectedCategoryId = -1;
    private boolean rebuildingChips = false;
    private boolean loading = false;
    private double currentSubtotal, currentVat, currentDiscount, currentTotal;

    private record Loaded(List<Category> categories, List<Product> products, Map<Integer, List<ProductVariant>> variants) {}

    @FXML
    public void initialize() {
        txtMenuSearch.textProperty().addListener((obs, o, n) -> renderMenu());

        chipGroup.selectedToggleProperty().addListener((o, old, now) -> {
            if (rebuildingChips) return;
            if (now == null) { if (old != null) old.setSelected(true); return; }
            selectedCategoryId = (Integer) now.getUserData();
            renderMenu();
        });

        setupPaymentModal();
        updateTotals();
        reload();
    }

    // --- Called by the dashboard shell ---
    public void refreshTicket() { updateTotals(); }
    public boolean isModalOpen() { return modalOverlay.isVisible(); }

    // =====================================================================
    //  MENU (real data from MenuDAO)
    // =====================================================================
    public void reload() {
        if (loading) return;
        loading = true;

        Task<Loaded> task = new Task<>() {
            @Override protected Loaded call() throws Exception {
                return new Loaded(MenuDAO.getCategories(), MenuDAO.getProducts(), MenuDAO.getAllVariants());
            }
        };
        task.setOnSucceeded(e -> {
            categories = new ArrayList<>(task.getValue().categories());
            products = new ArrayList<>(task.getValue().products());
            variantMap = task.getValue().variants();
            buildChips();
            renderMenu();
            loading = false;
        });
        task.setOnFailed(e -> {
            loading = false;
            task.getException().printStackTrace();
            AlertUtil.showError("Could not load menu", task.getException().getMessage());
        });
        Thread t = new Thread(task, "pos-menu-loader");
        t.setDaemon(true);
        t.start();
    }

    private void buildChips() {
        rebuildingChips = true;
        chipGroup.getToggles().clear();
        categoryBar.getChildren().clear();

        addChip("All", -1);
        for (Category c : categories) addChip(c.name(), c.id());

        boolean found = false;
        for (Toggle t : chipGroup.getToggles()) {
            if ((Integer) t.getUserData() == selectedCategoryId) { t.setSelected(true); found = true; }
        }
        if (!found) {
            selectedCategoryId = -1;
            chipGroup.getToggles().get(0).setSelected(true);
        }
        rebuildingChips = false;
    }

    private void addChip(String text, int id) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(chipGroup);
        b.setUserData(id);
        b.getStyleClass().add("chip");
        categoryBar.getChildren().add(b);
    }

    private void renderMenu() {
        String q = txtMenuSearch.getText() == null ? "" : txtMenuSearch.getText().trim().toLowerCase();

        List<Product> shown = products.stream()
                .filter(p -> selectedCategoryId == -1 || p.categoryId() == selectedCategoryId)
                .filter(p -> q.isEmpty() || p.name().toLowerCase().contains(q)
                        || p.category().toLowerCase().contains(q))
                .toList();

        menuGrid.getChildren().clear();
        if (shown.isEmpty()) {
            Label none = new Label(products.isEmpty()
                    ? "The menu is empty. Ask the admin to add dishes."
                    : "No menu items match your search.");
            none.getStyleClass().add("empty-note");
            menuGrid.getChildren().add(none);
            return;
        }
        for (Product p : shown) menuGrid.getChildren().add(buildCard(p));
    }

    private VBox buildCard(Product p) {
        boolean sellable = p.available() && p.quantity() > 0;

        StackPane photo = new StackPane();
        photo.getStyleClass().add("dish-photo");
        ImageView iv = ImageStore.circleView(p.imagePath(), PHOTO);
        if (iv != null) {
            photo.getChildren().add(iv);
        } else {
            Label ph = new Label(p.name().isEmpty() ? "?" : p.name().substring(0, 1).toUpperCase());
            ph.getStyleClass().add("dish-placeholder");
            photo.getChildren().add(ph);
        }

        Label category = new Label(p.category().toUpperCase());
        category.getStyleClass().add("dish-category");

        Label name = new Label(p.name());
        name.getStyleClass().add("dish-name");
        name.setWrapText(true);
        name.setAlignment(Pos.TOP_CENTER);
        name.setMaxWidth(156);
        name.setMinHeight(38);

        FlowPane sizes = new FlowPane(6, 6);
        sizes.setAlignment(Pos.CENTER);
        sizes.setMaxWidth(156);
        List<ProductVariant> vars = variantMap.getOrDefault(p.id(), List.of());
        for (ProductVariant v : vars) {
            String size = v.getSizeName() == null ? "" : v.getSizeName().trim();
            Button chip = new Button((size.isEmpty() ? "Add" : size) + "\n" + peso(priceOf(v)));
            chip.getStyleClass().add("size-chip");
            chip.setDisable(!sellable);
            chip.setOnAction(e -> addToCart(p, v));
            sizes.getChildren().add(chip);
        }
        if (vars.isEmpty()) {
            Label noPrice = new Label("No prices set");
            noPrice.getStyleClass().add("dish-qty");
            sizes.getChildren().add(noPrice);
        }

        Label stock = new Label(sellable ? p.quantity() + " left" : "Unavailable");
        stock.getStyleClass().add(sellable ? "dish-qty" : "dish-qty-off");

        VBox card = new VBox(6, photo, category, name, sizes, stock);
        card.setAlignment(Pos.TOP_CENTER);
        card.getStyleClass().add("menu-card");
        if (!sellable) card.getStyleClass().add("menu-card-off");
        return card;
    }

    /** getPrice() may be a BigDecimal or a double depending on the model; handle both. */
    private static double priceOf(ProductVariant v) {
        Object price = v.getPrice();
        return ((Number) price).doubleValue();
    }

    // =====================================================================
    //  CART
    // =====================================================================
    private int inCart(int productId) {
        return cartItems.stream().filter(c -> c.getProductId() == productId).mapToInt(CartItem::getQuantity).sum();
    }

    private void addToCart(Product p, ProductVariant v) {
        if (inCart(p.id()) >= p.quantity()) {
            AlertUtil.showWarning("Not enough stock", "Only " + p.quantity() + " of \"" + p.name() + "\" left in stock.");
            return;
        }
        String size = v.getSizeName() == null ? "" : v.getSizeName().trim();
        CartItem existing = null;
        for (CartItem ci : cartItems) {
            if (ci.getProductId() == p.id() && ci.getVariantId() == v.getVariantId()) { existing = ci; break; }
        }
        if (existing != null) existing.increment();
        else cartItems.add(new CartItem(p.id(), v.getVariantId(), p.name(), size, priceOf(v), p.quantity()));
        refreshCartUI();
        updateTotals();
    }

    private void refreshCartUI() {
        cartListView.getItems().clear();
        for (CartItem item : cartItems) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label(item.getName() + (item.getSize().isEmpty() ? "" : " (" + item.getSize() + ")"));
            title.getStyleClass().add("cart-item-title");
            Label pr = new Label(peso(item.getPrice())
                    + (item.getQuantity() > 1 ? "  \u00D7  " + item.getQuantity() + "  =  " + peso(item.getLineTotal()) : ""));
            pr.getStyleClass().add("cart-item-price");
            VBox details = new VBox(2, title, pr);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button btnMinus = new Button("\u2212");
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
            btnPlus.setOnAction(e -> {
                if (inCart(item.getProductId()) >= item.getStock()) {
                    AlertUtil.showWarning("Not enough stock", "No more \"" + item.getName() + "\" in stock.");
                    return;
                }
                item.increment();
                refreshCartUI();
                updateTotals();
            });

            Button btnRemove = new Button("\u2715");
            btnRemove.getStyleClass().add("remove-btn");
            btnRemove.setOnAction(e -> { cartItems.remove(item); refreshCartUI(); updateTotals(); });

            row.getChildren().addAll(details, spacer, btnMinus, lblQty, btnPlus, btnRemove);
            cartListView.getItems().add(row);
        }
    }

    @FXML
    public void handleClearCart() {
        if (cartItems.isEmpty()) return;
        if (!AlertUtil.showYesNo("Clear Order", "Remove all items from the current order?")) return;
        cartItems.clear();
        chkDiscount.setSelected(false);
        refreshCartUI();
        updateTotals();
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
        rowDiscount.setManaged(isDiscounted);
        lblDiscount.setText("-" + peso(discountAmount));
        lblTotal.setText(peso(finalTotal));
        lblCartTicket.setText("Next ticket " + session.ticketLabel());
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
            lblChange.setText("\u20B10.00");
            btnConfirmOrder.setDisable(true);
        } else if (Math.round(cash * 100) < due) {
            lblChange.setText("Short \u20B1" + String.format("%.2f", (due - Math.round(cash * 100)) / 100.0));
            btnConfirmOrder.setDisable(true);
        } else {
            lblChange.setText("\u20B1" + String.format("%.2f", (Math.round(cash * 100) - due) / 100.0));
            btnConfirmOrder.setDisable(false);
        }
    }

    private Double parseMoney(String text) {
        if (text == null) return null;
        String cleaned = text.replace("\u20B1", "").replace(",", "").trim();
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
            AlertUtil.showWarning("Empty Cart", "Please add items to the order before charging.");
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
        try {
            deductStock();
        } catch (SQLException ex) {
            ex.printStackTrace();
            AlertUtil.showError("Could not update stock", ex.getMessage());
            return;
        }

        String ticket = session.ticketLabel();
        List<OrderLine> lines = cartItems.stream()
                .map(c -> new OrderLine(c.getName(), c.getSize(), c.getQuantity(), c.getLineTotal()))
                .toList();
        String receipt = buildReceipt(ticket, paymentMethod, tendered);

        session.recordOrder(new TransactionRecord(ticket, LocalDateTime.now(), session.cashierName,
                paymentMethod, lines, currentSubtotal, currentVat, currentDiscount, currentTotal, tendered, receipt));

        cartItems.clear();
        if (session.autoClearDiscount) chkDiscount.setSelected(false);
        refreshCartUI();
        updateTotals();
        closeConfirmationModal();
        reload();

        AlertUtil.showSuccess("Order Processed", "Ticket " + ticket + " was added to the queue.");
    }

    /** Takes the sold quantities out of stock (uses the same MenuDAO call as the admin menu editor). */
    private void deductStock() throws SQLException {
        Map<Integer, Integer> sold = new HashMap<>();
        for (CartItem ci : cartItems) sold.merge(ci.getProductId(), ci.getQuantity(), Integer::sum);

        for (Map.Entry<Integer, Integer> e : sold.entrySet()) {
            Product p = products.stream().filter(x -> x.id() == e.getKey()).findFirst().orElse(null);
            if (p == null) continue;
            int left = Math.max(0, p.quantity() - e.getValue());
            MenuDAO.updateProduct(p.id(), p.name(), p.categoryId(), p.description(), p.imagePath(), left > 0, left);
        }
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
        if (left.length() > room) left = left.substring(0, Math.max(0, room - 1)) + "\u2026";
        return left + " ".repeat(Math.max(1, width - left.length() - right.length())) + right;
    }
}