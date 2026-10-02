package com.coffeepos.renespresso.controller.main.admin;

import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import io.github.palexdev.materialfx.controls.MFXButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AdminDashboardController {

    private boolean isExpanded = false; // Start minimized

    @FXML private VBox sidebar;
    @FXML private Label lblBrandText;
    @FXML private VBox userInfoBox;

    @FXML private ScrollPane homeView;
    @FXML private VBox menuView, salesView, transactionView, accountsView, settingsView;

    @FXML private Button btnHome, btnMenu, btnSales, btnTransactions, btnAccounts, btnSettings;
    @FXML private MFXButton btnLogout;

    @FXML private Label dateLabel;

    @FXML
    public void initialize() {
        setSystemDate();
        applySidebarState();
        highlightButton(btnHome); // Set Home as active on load
    }

    private void setSystemDate() {
        if (dateLabel != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
            dateLabel.setText(LocalDate.now().format(formatter));
        }
    }

    @FXML
    public void toggleSidebar(MouseEvent event) {
        isExpanded = !isExpanded;
        applySidebarState();
    }

    // Update ONLY this method in your existing AdminDashboardController.java

    private void applySidebarState() {
        if (isExpanded) {
            // Increased from 260.0 to 310.0 to prevent text truncation
            sidebar.setPrefWidth(310.0);
            lblBrandText.setVisible(true);
            lblBrandText.setManaged(true);
            userInfoBox.setVisible(true);
            userInfoBox.setManaged(true);

            btnHome.setText("🏠  HOME");
            btnMenu.setText("📋  MENU MANAGEMENT");
            btnSales.setText("📈  SALES");
            btnTransactions.setText("🕒  TRANSACTION HISTORY");
            btnAccounts.setText("👥  ACCOUNT MANAGEMENT");
            btnSettings.setText("⚙️  SETTINGS");
            btnLogout.setText("🚪  LOGOUT");

            setButtonAlignment(Pos.BASELINE_LEFT);
        } else {
            // Increased from 75.0 to 85.0 to give big icons breathing room
            sidebar.setPrefWidth(85.0);
            lblBrandText.setVisible(false);
            lblBrandText.setManaged(false);
            userInfoBox.setVisible(false);
            userInfoBox.setManaged(false);

            btnHome.setText("🏠");
            btnMenu.setText("📋");
            btnSales.setText("📈");
            btnTransactions.setText("🕒");
            btnAccounts.setText("👥");
            btnSettings.setText("⚙️");
            btnLogout.setText("🚪");

            setButtonAlignment(Pos.CENTER);
        }
    }

    private void setButtonAlignment(Pos position) {
        Button[] navButtons = {btnHome, btnMenu, btnSales, btnTransactions, btnAccounts, btnSettings};
        for (Button btn : navButtons) {
            btn.setAlignment(position);
        }
        btnLogout.setAlignment(position);
    }

    // --- CSS CLASS TOGGLING FIX ---
    private void hideAllViews() {
        homeView.setVisible(false);
        menuView.setVisible(false);
        salesView.setVisible(false);
        transactionView.setVisible(false);
        accountsView.setVisible(false);
        settingsView.setVisible(false);

        // Reset all buttons to default nav-button class
        Button[] navButtons = {btnHome, btnMenu, btnSales, btnTransactions, btnAccounts, btnSettings};
        for (Button btn : navButtons) {
            btn.getStyleClass().remove("nav-button-active");
            if (!btn.getStyleClass().contains("nav-button")) {
                btn.getStyleClass().add("nav-button");
            }
        }
    }

    private void highlightButton(Button button) {
        if (button != null) {
            button.getStyleClass().remove("nav-button");
            if (!button.getStyleClass().contains("nav-button-active")) {
                button.getStyleClass().add("nav-button-active");
            }
        }
    }

    @FXML public void showHome() { hideAllViews(); homeView.setVisible(true); highlightButton(btnHome); }
    @FXML public void showMenu() { hideAllViews(); menuView.setVisible(true); highlightButton(btnMenu); }
    @FXML public void showSales() { hideAllViews(); salesView.setVisible(true); highlightButton(btnSales); }
    @FXML public void showTransactions() { hideAllViews(); transactionView.setVisible(true); highlightButton(btnTransactions); }
    @FXML public void showAccounts() { hideAllViews(); accountsView.setVisible(true); highlightButton(btnAccounts); }
    @FXML public void showSettings() { hideAllViews(); settingsView.setVisible(true); highlightButton(btnSettings); }

    @FXML
    public void handleLogout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to log out?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Log Out Confirmation");
        alert.setHeaderText("Logging out of RenEspresso");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                NavigateUtil.navigateTo(event, "id/LoginView.fxml", "RenEspresso POS - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
            }
        });
    }
}