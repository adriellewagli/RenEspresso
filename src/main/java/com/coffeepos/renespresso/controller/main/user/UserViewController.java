package com.coffeepos.renespresso.controller.main.user;

import com.coffeepos.renespresso.controller.main.user.om.OrderManagementController;
import com.coffeepos.renespresso.controller.main.user.pos.PosController;
import com.coffeepos.renespresso.controller.main.user.queue.OrderQueueController;
import com.coffeepos.renespresso.model.TransactionRecord;
import com.coffeepos.renespresso.model.User;
import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.NavigateUtil;
import com.coffeepos.renespresso.util.SessionManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.coffeepos.renespresso.controller.main.user.UserUi.peso;

public class UserViewController {

    private static final String ADMIN_CSS = "/com/coffeepos/renespresso/views/styles/admin/dashboard.css";
    private static final String USER_CSS = "/com/coffeepos/renespresso/views/styles.user/user-dashboard.css";

    // --- Shell FXML bindings ---
    @FXML private StackPane rootPane;
    @FXML private VBox sidebar, brandTextBox, sessionBox;
    @FXML private Label lblClock, lblDate, lblCashierName, lblWelcome;
    @FXML private Button btnHome, btnMenu, btnOrders, btnTransactionHistory, btnLogout;

    // Views owned by the shell
    @FXML private VBox homeView;

    // Included views (fx:include fx:id="posView" -> posView + posViewController, etc.)
    @FXML private Node posView, queueView, orderView;
    @FXML private PosController posViewController;
    @FXML private OrderQueueController queueViewController;
    @FXML private OrderManagementController orderViewController;

    // Home dashboard
    @FXML private Label lblDashSales, lblDashLast, lblDashOrders, lblDashItems, lblDashQueue,
            lblDashNextTicket, lblDashAvg, lblDashDiscounted, lblQueueCount;
    @FXML private VBox queueBox, recentOrdersBox;


    private static final double SIDEBAR_COLLAPSED_WIDTH = 85.0;
    private static final double SIDEBAR_EXPANDED_WIDTH = 310.0;
    private static final double ICON_BOX = 37.0;

    private final UserSession session = UserSession.get();
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("hh:mm a");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d, yyyy");

    private Timeline clockTimeline;
    private int tick = 0;
    private final EventHandler<KeyEvent> shortcutHandler = this::handleShortcut;
    private boolean sidebarCollapsed = true;
    private Runnable onLogout;

    private Button[] navButtons;
    private String[] navIcons;
    private String[] navLabels;

    @FXML
    public void initialize() {
        UserUi.attachCss(rootPane, ADMIN_CSS);
        UserUi.attachCss(rootPane, USER_CSS);

        User cur = SessionManager.getCurrentUser();
        if (cur != null && cur.getFullName() != null && !cur.getFullName().isBlank()) {
            session.cashierName = cur.getFullName();
        }

        lblClock.setText(clockFormat.format(new Date()));
        lblDate.setText(dateFormat.format(new Date()));
        lblCashierName.setText("Cashier \u2014 " + session.cashierName);
        lblWelcome.setText("Welcome back, " + session.cashierName + " \uD83D\uDC4B");

        setupLiveClock();
        setupSidebar();
        setupKeyboardShortcuts();

        // Home reacts to every new / completed / cancelled order
        session.transactionHistory.addListener((ListChangeListener<TransactionRecord>) c -> refreshDashboard());
        showHome();
    }

    // =====================================================================
    //  PUBLIC HOOKS (used by the login flow)
    // =====================================================================
    public void setCashierName(String name) {
        if (name == null || name.isBlank()) return;
        session.cashierName = name.trim();
        lblCashierName.setText("Cashier \u2014 " + session.cashierName);
        lblWelcome.setText("Welcome back, " + session.cashierName + " \uD83D\uDC4B");
    }

    public void setOnLogout(Runnable onLogout) { this.onLogout = onLogout; }

    // =====================================================================
    //  CLOCK + SHORTCUTS
    // =====================================================================
    private void setupLiveClock() {
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            Date now = new Date();
            lblClock.setText(clockFormat.format(now));
            lblDate.setText(dateFormat.format(now));
            if (++tick % 30 == 0 && homeView.isVisible()) refreshDashboard();   // refresh "x mins ago"
        }));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    private void setupKeyboardShortcuts() {
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, shortcutHandler);
            if (newScene != null) newScene.addEventFilter(KeyEvent.KEY_PRESSED, shortcutHandler);
            else if (clockTimeline != null) clockTimeline.stop();
        });
    }

    private boolean modalOpen() { return posViewController != null && posViewController.isModalOpen(); }

    /** F1 Home, F2 Menu, F3 Orders, F4 History, F5 Settings, Esc closes the payment modal. */
    private void handleShortcut(KeyEvent event) {
        switch (event.getCode()) {
            case ESCAPE -> {
                if (modalOpen()) { posViewController.closeConfirmationModal(); event.consume(); }
            }
            case F1 -> { if (!modalOpen()) { showHome(); event.consume(); } }
            case F2 -> { if (!modalOpen()) { showMenu(); event.consume(); } }
            case F3 -> { if (!modalOpen()) { showOrders(); event.consume(); } }
            case F4 -> { if (!modalOpen()) { showTransactionHistory(); event.consume(); } }
            default -> { }
        }
    }

    // =====================================================================
    //  SIDEBAR (same behaviour as the admin dashboard, every icon is the same size)
    // =====================================================================
    private void setupSidebar() {
        navButtons = new Button[]{btnHome, btnMenu, btnOrders, btnTransactionHistory, btnLogout};
        navIcons   = new String[]{"icon-home", "icon-cafe", "icon-orders", "icon-history", "icon-logout"};
        navLabels  = new String[]{"HOME", "MENU", "ORDERS", "TRANSACTION HISTORY", "LOGOUT"};
        for (int i = 0; i < navButtons.length; i++) setupIconButton(navButtons[i], navIcons[i]);
        applySidebarState();
    }

    /** Vector icon in a fixed 37x37 box; the glyph itself is sized in CSS (.nav-svg = 20x20). */
    private void setupIconButton(Button btn, String iconClass) {
        Region glyph = new Region();
        glyph.getStyleClass().addAll("nav-svg", iconClass);

        StackPane box = new StackPane(glyph);
        box.setMinSize(ICON_BOX, ICON_BOX);
        box.setPrefSize(ICON_BOX, ICON_BOX);
        box.setMaxSize(ICON_BOX, ICON_BOX);

        btn.setGraphic(box);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setGraphicTextGap(12);
        btn.setAlignment(Pos.CENTER_LEFT);
    }

    @FXML
    public void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        applySidebarState();
    }

    private void applySidebarState() {
        boolean expanded = !sidebarCollapsed;
        double width = expanded ? SIDEBAR_EXPANDED_WIDTH : SIDEBAR_COLLAPSED_WIDTH;
        sidebar.setMinWidth(width);
        sidebar.setPrefWidth(width);
        sidebar.setMaxWidth(width);

        brandTextBox.setVisible(expanded);
        brandTextBox.setManaged(expanded);
        sessionBox.setVisible(expanded);
        sessionBox.setManaged(expanded);

        for (int i = 0; i < navButtons.length; i++) navButtons[i].setText(expanded ? navLabels[i] : "");
    }

    // =====================================================================
    //  VIEW SWITCHING
    // =====================================================================
    private void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void hideAllViews() {
        for (Node view : List.of(homeView, posView, queueView, orderView)) setShown(view, false);

        for (Button btn : List.of(btnHome, btnMenu, btnOrders, btnTransactionHistory)) {
            btn.getStyleClass().remove("nav-button-active");
            if (!btn.getStyleClass().contains("nav-button")) btn.getStyleClass().add("nav-button");
        }
    }

    private void highlightButton(Button button) {
        button.getStyleClass().remove("nav-button");
        if (!button.getStyleClass().contains("nav-button-active")) button.getStyleClass().add("nav-button-active");
    }

    private void show(Node view, Button button) {
        hideAllViews();
        setShown(view, true);
        highlightButton(button);
    }

    @FXML public void showHome() { show(homeView, btnHome); refreshDashboard(); }

    @FXML public void showMenu() {
        show(posView, btnMenu);
        if (posViewController != null) { posViewController.reload(); posViewController.refreshTicket(); }
    }

    @FXML public void showOrders() { show(queueView, btnOrders); }
    @FXML public void showTransactionHistory() { show(orderView, btnTransactionHistory); }

    @FXML
    public void handleLogout(ActionEvent event) {
        if (!AlertUtil.showYesNo("Log Out", "Are you sure you want to log out of RenEspresso?")) return;
        SessionManager.logout();
        NavigateUtil.navigateTo(event, "id/LoginView.fxml", "RenEspresso POS - Login",
                NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    // =====================================================================
    //  HOME DASHBOARD
    // =====================================================================
    private void refreshDashboard() {
        lblDashSales.setText(peso(session.salesTotal));
        lblDashLast.setText("Last order: " + session.lastOrderTime);
        lblDashOrders.setText(String.valueOf(session.ordersCount));
        lblDashItems.setText(session.itemsSold + " items sold");
        lblDashQueue.setText(String.valueOf(session.queue.size()));
        lblDashNextTicket.setText(session.ticketLabel());
        lblDashAvg.setText("Avg order " + peso(session.ordersCount == 0 ? 0 : session.salesTotal / session.ordersCount));
        lblDashDiscounted.setText(session.discountedOrders + " discounted");

        renderQueue();
        renderRecent();
    }

    private void renderQueue() {
        queueBox.getChildren().clear();
        int n = session.queue.size();
        lblQueueCount.setText(n == 0 ? "Nothing waiting" : n + (n == 1 ? " order" : " orders") + " preparing, oldest first");

        if (n == 0) {
            Label empty = new Label("The queue is clear. New orders show up here right after payment.");
            empty.getStyleClass().add("empty-note");
            queueBox.getChildren().add(empty);
            return;
        }
        for (TransactionRecord r : session.queue) {
            Label badge = new Label(r.getTicketNumber());
            badge.getStyleClass().add("ticket-badge");

            Label items = new Label(r.getItems());
            items.getStyleClass().add("queue-items");
            Label meta = new Label(r.getTimeAgo() + "  \u00B7  " + r.getPaymentMethod());
            meta.getStyleClass().add("card-subtitle");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label total = new Label(r.getTotalPaid());
            total.getStyleClass().add("queue-total");

            Button receiptBtn = new Button("Receipt");
            receiptBtn.getStyleClass().add("btn-secondary-sm");
            receiptBtn.setOnAction(e -> UserUi.showReceiptDialog(r));

            Button done = new Button("Complete");
            done.getStyleClass().add("btn-primary-sm");
            done.setOnAction(e -> session.completeOrder(r));

            HBox row = new HBox(14, badge, new VBox(2, items, meta), spacer, total, receiptBtn, done);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("queue-row");
            queueBox.getChildren().add(row);
        }
    }

    private void renderRecent() {
        recentOrdersBox.getChildren().clear();
        if (session.transactionHistory.isEmpty()) {
            Label empty = new Label("No orders yet this shift.");
            empty.getStyleClass().add("empty-note");
            recentOrdersBox.getChildren().add(empty);
            return;
        }
        int shown = Math.min(6, session.transactionHistory.size());
        for (int i = 0; i < shown; i++) {
            TransactionRecord r = session.transactionHistory.get(i);
            Label left = new Label(r.getTicketNumber() + "  \u00B7  " + r.getTime());
            left.getStyleClass().add("recent-left");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label status = new Label(r.getStatus().label());
            status.getStyleClass().addAll("pill", r.getStatus().styleClass());
            Label right = new Label(r.getTotalPaid());
            right.getStyleClass().add("recent-right");
            Button receiptBtn = new Button("Receipt");
            receiptBtn.getStyleClass().add("btn-secondary-sm");
            receiptBtn.setOnAction(e -> UserUi.showReceiptDialog(r));
            HBox row = new HBox(10, left, spacer, status, right, receiptBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            recentOrdersBox.getChildren().add(row);
        }
    }
}