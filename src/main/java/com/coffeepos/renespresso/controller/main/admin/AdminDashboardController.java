package com.coffeepos.renespresso.controller.main.admin;

import com.coffeepos.renespresso.controller.main.admin.account.AccountController;
import com.coffeepos.renespresso.controller.main.admin.home.HomeController;
import com.coffeepos.renespresso.controller.main.admin.menu.MenuController;
import com.coffeepos.renespresso.controller.main.admin.sales.SalesController;
import com.coffeepos.renespresso.controller.main.admin.transact.TransactionController;
import com.coffeepos.renespresso.model.User;
import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.NavigateUtil;
import com.coffeepos.renespresso.util.SessionManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AdminDashboardController {

    private static final String BASE = "/com/coffeepos/renespresso/views/styles/";
    private static final String[] CSS = {
            BASE + "admin/dashboard.css",
            BASE + "admin/home/home.css",
            BASE + "admin/menu/menu.css",
            BASE + "admin/account/account.css",
            BASE + "admin/sales/sales.css",
            BASE + "admin/settings/settings.css",
            BASE + "admin/transact/transact.css",
            BASE + "common/modern.css"          // keep last
    };

    private static final double COLLAPSED_WIDTH = 85.0;
    private static final double EXPANDED_WIDTH = 310.0;
    private static final double ICON_BOX = 37.0;

    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");

    private boolean isExpanded = false; // start minimized

    @FXML private StackPane rootPane;
    @FXML private HBox brandHeader;
    @FXML private VBox sidebar, brandTextBox, sessionBox;
    @FXML private Label lblClock, lblDate, lblUserName;

    @FXML private Node homeView, menuView, salesView, transactionView, accountsView, settingsView;

    @FXML private HomeController homeViewController;
    @FXML private MenuController menuViewController;
    @FXML private AccountController accountsViewController;
    @FXML private TransactionController transactionViewController;
    @FXML private SalesController salesViewController;

    @FXML private Button btnHome, btnMenu, btnSales, btnTransactions, btnAccounts, btnSettings, btnLogout;

    private Button[] pageButtons;   // the 6 pages (these get the active highlight)
    private Button[] navButtons;    // pages + logout
    private String[] navIcons;
    private String[] navLabels;
    private Tooltip[] navTips;
    private Timeline clock;

    @FXML
    public void initialize() {
        for (String path : CSS) attachCss(path);

        pageButtons = new Button[]{btnHome, btnMenu, btnSales, btnTransactions, btnAccounts, btnSettings};
        navButtons  = new Button[]{btnHome, btnMenu, btnSales, btnTransactions, btnAccounts, btnSettings, btnLogout};
        navIcons    = new String[]{"icon-home", "icon-cafe", "icon-sales", "icon-history", "icon-accounts", "icon-settings", "icon-logout"};
        navLabels   = new String[]{"HOME", "MENU MANAGEMENT", "SALES", "TRANSACTION HISTORY", "ACCOUNT MANAGEMENT", "SETTINGS", "LOGOUT"};
        String[] tips = {"Home", "Menu Management", "Sales", "Transaction History", "Account Management", "Settings", "Logout"};

        navTips = new Tooltip[tips.length];
        for (int i = 0; i < navButtons.length; i++) {
            navTips[i] = makeTip(tips[i]);
            setupIconButton(navButtons[i], navIcons[i]);
        }

        startClock();
        populateUserInfo();
        applySidebarState();
        showHome();
    }

    // =====================================================================
    //  CSS
    // =====================================================================
    private void attachCss(String path) {
        var url = getClass().getResource(path);
        if (url == null) {
            System.err.println("CSS not found: " + path);
            return;
        }
        String css = url.toExternalForm();
        if (!rootPane.getStylesheets().contains(css)) rootPane.getStylesheets().add(css);
    }

    // =====================================================================
    //  SIDEBAR (identical behaviour to the cashier sidebar)
    // =====================================================================
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

    private Tooltip makeTip(String text) {
        Tooltip t = new Tooltip(text);
        t.setShowDelay(Duration.millis(150));
        // inline style on purpose: tooltips live in their own window, so rootPane stylesheets don't reach them
        t.setStyle("-fx-background-color: #2F2118; -fx-text-fill: #F3E9DC; -fx-font-size: 12px;"
                + "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 6 10;");
        return t;
    }

    private void startClock() {
        tickClock();
        clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> tickClock()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null && clock != null) clock.stop();
        });
    }

    private void tickClock() {
        LocalDateTime now = LocalDateTime.now();
        lblClock.setText(now.format(CLOCK_FMT));
        lblDate.setText(now.format(DATE_FMT));
    }

    private void populateUserInfo() {
        User me = SessionManager.getCurrentUser();
        if (me == null) return;
        String r = me.getRole() == null ? "" : me.getRole();
        String role = r.isEmpty() ? "Admin" : r.charAt(0) + r.substring(1).toLowerCase();
        lblUserName.setText(role + " \u2014 " + me.getFullName());
    }

    @FXML
    public void toggleSidebar() {
        isExpanded = !isExpanded;
        applySidebarState();
    }

    private void applySidebarState() {
        double width = isExpanded ? EXPANDED_WIDTH : COLLAPSED_WIDTH;
        sidebar.setMinWidth(width);
        sidebar.setPrefWidth(width);
        sidebar.setMaxWidth(width);

        if (brandHeader != null) {
            brandHeader.setAlignment(isExpanded ? Pos.CENTER_LEFT : Pos.CENTER);
        }
        brandTextBox.setVisible(isExpanded);
        brandTextBox.setManaged(isExpanded);
        sessionBox.setVisible(isExpanded);
        sessionBox.setManaged(isExpanded);

        for (int i = 0; i < navButtons.length; i++) {
            navButtons[i].setText(isExpanded ? navLabels[i] : "");
            navButtons[i].setTooltip(isExpanded ? null : navTips[i]);   // name on hover only when collapsed
            navButtons[i].setAlignment(isExpanded ? Pos.CENTER_LEFT : Pos.CENTER);
        }
    }

    // =====================================================================
    //  VIEW SWITCHING
    // =====================================================================
    private void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void hideAllViews() {
        setShown(homeView, false);
        setShown(menuView, false);
        setShown(salesView, false);
        setShown(transactionView, false);
        setShown(accountsView, false);
        setShown(settingsView, false);

        for (Button btn : pageButtons) {
            btn.getStyleClass().remove("nav-button-active");
            if (!btn.getStyleClass().contains("nav-button")) btn.getStyleClass().add("nav-button");
        }
    }

    private void highlightButton(Button button) {
        if (button == null) return;
        button.getStyleClass().remove("nav-button");
        if (!button.getStyleClass().contains("nav-button-active")) button.getStyleClass().add("nav-button-active");
    }

    private void show(Node view, Button button) {
        hideAllViews();
        setShown(view, true);
        highlightButton(button);
    }

    @FXML public void showHome() {
        show(homeView, btnHome);
        if (homeViewController != null) homeViewController.refresh();
    }

    @FXML public void showMenu() {
        show(menuView, btnMenu);
        if (menuViewController != null) menuViewController.loadData();
    }

    @FXML public void showSales() {
        show(salesView, btnSales);
        if (salesViewController != null) salesViewController.loadSales();
    }

    @FXML public void showTransactions() {
        show(transactionView, btnTransactions);
        if (transactionViewController != null) transactionViewController.loadTransactions();
    }

    @FXML public void showAccounts() {
        show(accountsView, btnAccounts);
        if (accountsViewController != null) accountsViewController.loadAccounts();
    }

    @FXML public void showSettings() { show(settingsView, btnSettings); }

    @FXML
    public void handleLogout(ActionEvent event) {
        if (!AlertUtil.showYesNo("Log Out", "Are you sure you want to log out of RenEspresso?")) return;

        SessionManager.logout();
        NavigateUtil.navigateTo(event, "id/LoginView.fxml", "RenEspresso POS - Login",
                NavigateUtil.WindowMode.AUTH_DIALOG);
    }
}