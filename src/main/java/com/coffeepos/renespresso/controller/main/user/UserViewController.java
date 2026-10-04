package com.coffeepos.renespresso.controller.main.user;

import com.coffeepos.renespresso.controller.main.user.About.AboutController;
import com.coffeepos.renespresso.controller.main.user.om.OrderManagementController;
import com.coffeepos.renespresso.controller.main.user.pos.PosController;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.print.Printer;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static com.coffeepos.renespresso.controller.main.user.UserUi.peso;

/**
 * Shell of the cashier dashboard: sidebar, Home and Settings.
 * POS, Order Management and About live in their own FXML/controllers (fx:include).
 */
public class UserViewController {

    private static final String ADMIN_CSS = "/com/coffeepos/renespresso/views/styles/admin/dashboard.css";
    private static final String USER_CSS = "/com/coffeepos/renespresso/views/styles.user/user-dashboard.css";
    // --- Shell FXML bindings ---
    @FXML private StackPane rootPane;
    @FXML private VBox sidebar, brandTextBox, sessionBox;
    @FXML private Label lblClock, lblDate, lblCashierName, lblWelcome;
    @FXML private Button btnHome, btnMenu, btnTransactionHistory, btnAbout, btnSettings, btnLogout;

    // Views owned by the shell
    @FXML private VBox homeView, settingsView;

    // Included views (fx:include fx:id="posView" -> posView + posViewController, etc.)
    @FXML private Node posView, orderView, aboutView;
    @FXML private PosController posViewController;
    @FXML private OrderManagementController orderViewController;
    @FXML private AboutController aboutViewController;

    // Home dashboard
    @FXML private Label lblDashSales, lblDashOrders, lblDashNextTicket, lblDashAvg, lblDashItems, lblDashDiscounted, lblDashLast;
    @FXML private VBox recentOrdersBox;

    // Settings
    @FXML private ComboBox<String> cmbPrinter, cmbDefaultPayment;
    @FXML private RadioButton rbPaper80, rbPaper58;
    @FXML private CheckBox chkAutoPrint, chkCashDrawer, chkDualReceipt, chkChime, chkRequireConfirm, chkAutoClearDiscount;

    private static final double SIDEBAR_COLLAPSED_WIDTH = 85.0;
    private static final double SIDEBAR_EXPANDED_WIDTH = 310.0;
    private static final double ICON_BOX = 37.0;
    private static final Path SETTINGS_FILE =
            Paths.get(System.getProperty("user.home"), ".renespresso", "user-settings.properties");

    private final UserSession session = UserSession.get();
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("hh:mm a");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d, yyyy");

    private Timeline clockTimeline;
    private final EventHandler<KeyEvent> shortcutHandler = this::handleShortcut;
    private boolean sidebarCollapsed = true; // starts minimized, like admin
    private Runnable onLogout;

    private Button[] navButtons;
    private String[] navIcons;
    private String[] navLabels;

    @FXML
    public void initialize() {
        UserUi.attachCss(rootPane, ADMIN_CSS);
        UserUi.attachCss(rootPane, USER_CSS);

        lblClock.setText(clockFormat.format(new Date()));
        lblDate.setText(dateFormat.format(new Date()));
        lblCashierName.setText("Cashier — " + session.cashierName);

        setupLiveClock();
        setupSidebar();
        setupSettings();
        setupKeyboardShortcuts();
        showHome();
    }

    // =====================================================================
    //  PUBLIC HOOKS (used by the login flow)
    // =====================================================================
    public void setCashierName(String name) {
        if (name == null || name.isBlank()) return;
        session.cashierName = name.trim();
        lblCashierName.setText("Cashier — " + session.cashierName);
        lblWelcome.setText("Welcome back, Cashier " + session.cashierName + " 👋");
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

    /** F1 Home, F2 Menu, F3 History, F4 About, F5 Settings, Esc closes the payment modal. */
    private void handleShortcut(KeyEvent event) {
        switch (event.getCode()) {
            case ESCAPE -> {
                if (modalOpen()) { posViewController.closeConfirmationModal(); event.consume(); }
            }
            case F1 -> { if (!modalOpen()) { showHome(); event.consume(); } }
            case F2 -> { if (!modalOpen()) { showMenu(); event.consume(); } }
            case F3 -> { if (!modalOpen()) { showTransactionHistory(); event.consume(); } }
            case F4 -> { if (!modalOpen()) { showAbout(); event.consume(); } }
            case F5 -> { if (!modalOpen()) { showSettings(); event.consume(); } }
            default -> { }
        }
    }

    // =====================================================================
    //  SIDEBAR (same behaviour as the admin dashboard)
    // =====================================================================
    private void setupSidebar() {
        navButtons = new Button[]{btnHome, btnMenu, btnTransactionHistory, btnAbout, btnSettings, btnLogout};
        navIcons   = new String[]{"🏠", "📋", "📜", "ℹ", "⚙", "🚪"};
        navLabels  = new String[]{"HOME", "MENU", "TRANSACTION HISTORY", "ABOUT", "SETTINGS", "LOGOUT"};
        for (int i = 0; i < navButtons.length; i++) setupIconButton(navButtons[i], navIcons[i]);
        applySidebarState();
    }

    private void setupIconButton(Button btn, String icon) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("nav-icon");
        iconLabel.setMinWidth(ICON_BOX);
        iconLabel.setPrefWidth(ICON_BOX);
        iconLabel.setMaxWidth(ICON_BOX);
        iconLabel.setAlignment(Pos.CENTER);

        btn.setGraphic(iconLabel);
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
        for (Node view : List.of(homeView, posView, orderView, aboutView, settingsView)) setShown(view, false);

        for (Button btn : List.of(btnHome, btnMenu, btnTransactionHistory, btnAbout, btnSettings)) {
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
        if (posViewController != null) posViewController.refreshTicket();
    }

    @FXML public void showTransactionHistory() { show(orderView, btnTransactionHistory); }
    @FXML public void showAbout() { show(aboutView, btnAbout); }
    @FXML public void showSettings() { show(settingsView, btnSettings); }

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
    private void refreshDashboard() {
        lblDashSales.setText(peso(session.salesTotal));
        lblDashOrders.setText(String.valueOf(session.ordersCount));
        lblDashNextTicket.setText(session.ticketLabel());
        lblDashAvg.setText(peso(session.ordersCount == 0 ? 0 : session.salesTotal / session.ordersCount));
        lblDashItems.setText(String.valueOf(session.itemsSold));
        lblDashDiscounted.setText(String.valueOf(session.discountedOrders));
        lblDashLast.setText(session.lastOrderTime);

        recentOrdersBox.getChildren().clear();
        if (session.transactionHistory.isEmpty()) {
            Label empty = new Label("No orders yet this shift.");
            empty.getStyleClass().add("card-subtext");
            recentOrdersBox.getChildren().add(empty);
            return;
        }
        int shown = Math.min(4, session.transactionHistory.size());
        for (int i = 0; i < shown; i++) {
            TransactionRecord r = session.transactionHistory.get(i);
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
    //  SETTINGS (writes through to UserSession so POS sees changes immediately)
    // =====================================================================
    private void setupSettings() {
        ToggleGroup paperGroup = new ToggleGroup();
        rbPaper80.setToggleGroup(paperGroup);
        rbPaper58.setToggleGroup(paperGroup);

        cmbPrinter.getItems().setAll(detectPrinters());
        cmbDefaultPayment.getItems().setAll(UserSession.PAYMENT_METHODS);

        applyDefaultSettings();
        loadSettings();
        syncSettingsToSession();

        for (CheckBox cb : List.of(chkAutoPrint, chkCashDrawer, chkDualReceipt, chkChime, chkRequireConfirm, chkAutoClearDiscount)) {
            cb.selectedProperty().addListener((o, a, b) -> syncSettingsToSession());
        }
        rbPaper58.selectedProperty().addListener((o, a, b) -> syncSettingsToSession());
        cmbPrinter.valueProperty().addListener((o, a, b) -> syncSettingsToSession());
        cmbDefaultPayment.valueProperty().addListener((o, a, b) -> syncSettingsToSession());
    }

    private void syncSettingsToSession() {
        session.autoPrint = chkAutoPrint.isSelected();
        session.cashDrawer = chkCashDrawer.isSelected();
        session.dualReceipt = chkDualReceipt.isSelected();
        session.chime = chkChime.isSelected();
        session.requireConfirm = chkRequireConfirm.isSelected();
        session.autoClearDiscount = chkAutoClearDiscount.isSelected();
        session.paper58 = rbPaper58.isSelected();
        if (cmbPrinter.getValue() != null) session.printer = cmbPrinter.getValue();
        session.defaultPayment = cmbDefaultPayment.getValue() == null ? "Cash" : cmbDefaultPayment.getValue();
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
        if (payment != null && UserSession.PAYMENT_METHODS.contains(payment)) cmbDefaultPayment.setValue(payment);
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
            UserUi.showInfo(Alert.AlertType.INFORMATION, "Settings Saved", "Your preferences were saved on this terminal.");
        } catch (IOException ex) {
            UserUi.showInfo(Alert.AlertType.ERROR, "Save Failed", ex.getMessage());
        }
    }

    @FXML public void handleResetSettings() { applyDefaultSettings(); }

    @FXML
    public void handleResetQueue() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Reset the ticket counter back to #001? Only do this when starting a new operational day.",
                ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Reset Ticket Sequence");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                session.ticketNumber = 1;
                if (posViewController != null) posViewController.refreshTicket();
                refreshDashboard();
            }
        });
    }

    /** TODO: hook this into your database/DAO layer. */
    @FXML
    public void handleBackupDatabase() {
        UserUi.showInfo(Alert.AlertType.INFORMATION, "Backup Database",
                "The database backup service isn't connected to this screen yet.\n"
                        + "Use \"Export CSV\" on the Transaction History page to save this session's sales.");
    }
}