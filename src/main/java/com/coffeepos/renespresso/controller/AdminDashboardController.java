package com.coffeepos.renespresso.controller;

import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class AdminDashboardController {

    @FXML private Label activeShiftLabel;
    @FXML private Label adminNameLabel;

    @FXML private Label lblTodaySales;
    @FXML private Label lblTotalTransactions;
    @FXML private Label lblTopSeller;
    @FXML private Label lblLowStock;

    @FXML private Button btnDashboard;
    @FXML private Button btnStaff;
    @FXML private Button btnProducts;
    @FXML private Button btnInventory;
    @FXML private Button btnSales;
    @FXML private Button btnLogout;

    @FXML private TableView<AuditLogEntry> auditTable;
    @FXML private TableColumn<AuditLogEntry, String> colTime;
    @FXML private TableColumn<AuditLogEntry, String> colUser;
    @FXML private TableColumn<AuditLogEntry, String> colRole;
    @FXML private TableColumn<AuditLogEntry, String> colAction;
    @FXML private TableColumn<AuditLogEntry, String> colDetails;

    @FXML
    public void initialize() {
        setupAuditTableColumns();
        loadSampleAuditData();

        // Navigation Actions
        btnStaff.setOnAction(this::handleStaffNavigation);
        btnLogout.setOnAction(this::handleLogout);
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

    private void handleStaffNavigation(ActionEvent event) {
        // NavigateUtil hook to Staff/User Management view
        NavigateUtil.navigateTo(event, "UserManagementView.fxml", "Renespresso - User Management", NavigateUtil.WindowMode.FULLSCREEN_WORKSPACE);
    }

    private void handleLogout(ActionEvent event) {
        NavigateUtil.navigateTo(event, "LoginView.fxml", "Renespresso POS - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    // Inner class representation for audit log entries
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