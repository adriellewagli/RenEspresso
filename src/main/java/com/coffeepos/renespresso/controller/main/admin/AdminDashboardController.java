package com.coffeepos.renespresso.controller.main.admin;

import com.coffeepos.renespresso.util.NavigateUtil;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AdminDashboardController {

    private boolean isExpanded = false; // Start minimized

    @FXML private VBox sidebar;
    @FXML private Label lblBrandText;
    @FXML private VBox userInfoBox;

    @FXML private Node homeView, menuView, salesView, transactionView, accountsView, settingsView;

    @FXML private Button btnHome, btnMenu, btnSales, btnTransactions, btnAccounts, btnSettings;
    @FXML private MFXButton btnLogout;

    @FXML private Label dateLabel;

    @FXML
    public void initialize() {
        setSystemDate();
        applySidebarState();
        showHome(); // Home active on load
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

    private void applySidebarState() {
        if (isExpanded) {
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

    private void show(Node view, Button button) {
        hideAllViews();
        setShown(view, true);
        highlightButton(button);
    }

    @FXML public void showHome()         { show(homeView, btnHome); }
    @FXML public void showMenu()         { show(menuView, btnMenu); }
    @FXML public void showSales()        { show(salesView, btnSales); }
    @FXML public void showTransactions() { show(transactionView, btnTransactions); }
    @FXML public void showAccounts()     { show(accountsView, btnAccounts); }
    @FXML public void showSettings()     { show(settingsView, btnSettings); }

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