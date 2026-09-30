package com.coffeepos.renespresso.controller.main;

import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AdminDashboardController {

    // Views
    @FXML private ScrollPane dashboardView;
    @FXML private VBox staffView, productsView, inventoryView, salesView, auditView, settingsView;

    // Sidebar Buttons
    @FXML private Button btnDashboard, btnProducts, btnInventory, btnSales, btnStaff, btnAudit, btnSettings, btnLogout;

    // Dashboard Metric Labels
    @FXML private Label dateLabel;
    @FXML private Label lblTodaySales;
    @FXML private Label lblTotalTransactions;
    @FXML private Label lblLowStock;

    // Audit Table Bindings
    @FXML private TableView<AuditLogEntry> auditTable;
    @FXML private TableColumn<AuditLogEntry, String> colTime;
    @FXML private TableColumn<AuditLogEntry, String> colUser;
    @FXML private TableColumn<AuditLogEntry, String> colRole;
    @FXML private TableColumn<AuditLogEntry, String> colAction;
    @FXML private TableColumn<AuditLogEntry, String> colDetails;

    @FXML
    public void initialize() {
        setSystemDate();
        setupAuditTableColumns();
        loadSampleAuditData();

        // Highlight dashboard by default on load
        highlightButton(btnDashboard);
    }

    private void setSystemDate() {
        if (dateLabel != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
            dateLabel.setText(LocalDate.now().format(formatter));
        }
    }

    private void setupAuditTableColumns() {
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        colUser.setCellValueFactory(new PropertyValueFactory<>("user"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colAction.setCellValueFactory(new PropertyValueFactory<>("action"));
        colDetails.setCellValueFactory(new PropertyValueFactory<>("details"));
    }

    private void loadSampleAuditData() {
        ObservableList<AuditLogEntry> logList = FXCollections.observableArrayList(
                new AuditLogEntry("11:42 AM", "msantos", "Cashier", "Order Voided (#1042)", "Approved by Admin"),
                new AuditLogEntry("10:15 AM", "jdelacruz", "Cashier", "Manual Discount (20%)", "Senior Citizen ID Applied"),
                new AuditLogEntry("08:00 AM", "admin", "Admin", "Shift Started", "Terminal 01 Opened")
        );
        auditTable.setItems(logList);
    }

    // --- SIDEBAR NAVIGATION & VIEW SWITCHING ---
    private void hideAllViews() {
        dashboardView.setVisible(false);
        productsView.setVisible(false);
        inventoryView.setVisible(false);
        salesView.setVisible(false);
        staffView.setVisible(false);
        if (auditView != null) auditView.setVisible(false);
        if (settingsView != null) settingsView.setVisible(false);

        String defaultStyle = "-fx-background-color: transparent; -fx-text-fill: #F3E9DC; -fx-font-size: 13px; -fx-cursor: hand; -fx-background-radius: 8;";
        btnDashboard.setStyle(defaultStyle);
        btnProducts.setStyle(defaultStyle);
        btnInventory.setStyle(defaultStyle);
        btnSales.setStyle(defaultStyle);
        btnStaff.setStyle(defaultStyle);
        if (btnAudit != null) btnAudit.setStyle(defaultStyle);
        if (btnSettings != null) btnSettings.setStyle(defaultStyle);
    }

    private void highlightButton(Button button) {
        if (button != null) {
            button.setStyle("-fx-background-color: #C08552; -fx-text-fill: #F3E9DC; -fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8;");
        }
    }

    @FXML
    public void showDashboard() {
        hideAllViews();
        dashboardView.setVisible(true);
        highlightButton(btnDashboard);
    }

    @FXML
    public void showProducts() {
        hideAllViews();
        productsView.setVisible(true);
        highlightButton(btnProducts);
    }

    @FXML
    public void showInventory() {
        hideAllViews();
        inventoryView.setVisible(true);
        highlightButton(btnInventory);
    }

    @FXML
    public void showSales() {
        hideAllViews();
        salesView.setVisible(true);
        highlightButton(btnSales);
    }

    @FXML
    public void showStaff() {
        hideAllViews();
        staffView.setVisible(true);
        highlightButton(btnStaff);
    }

    @FXML
    public void showAuditLogs() {
        hideAllViews();
        if (auditView != null) auditView.setVisible(true);
        highlightButton(btnAudit);
    }

    @FXML
    public void showSettings() {
        hideAllViews();
        if (settingsView != null) settingsView.setVisible(true);
        highlightButton(btnSettings);
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to log out?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Log Out Confirmation");
        alert.setHeaderText("Logging out of RenEspresso Admin Portal");
        alert.setContentText("Any unsaved changes will be lost.");

        // Apply custom styling to confirmation dialog buttons if needed
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // Perform session/user cleanup here if you have a SessionManager
                // e.g., SessionManager.clearSession();

                // Cleanly route back to the Login screen
                NavigateUtil.navigateTo(event, "id/LoginView.fxml", "RenEspresso POS - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
            }
        });
    }

    public static class AuditLogEntry {
        private final String time;
        private final String user;
        private final String role;
        private final String action;
        private final String details;

        public AuditLogEntry(String time, String user, String role, String action, String details) {
            this.time = time;
            this.user = user;
            this.role = role;
            this.action = action;
            this.details = details;
        }

        public String getTime() { return time; }
        public String getUser() { return user; }
        public String getRole() { return role; }
        public String getAction() { return action; }
        public String getDetails() { return details; }
    }
}