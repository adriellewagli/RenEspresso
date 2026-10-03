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
import javafx.scene.control.ContentDisplay;
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

    private static final double COLLAPSED_WIDTH = 85.0;
    private static final double EXPANDED_WIDTH = 310.0;
    private static final double ICON_BOX = 37.0;

    private Button[] navButtons;
    private String[] navIcons;
    private String[] navLabels;

    @FXML
    public void initialize() {
        navButtons = new Button[]{btnHome, btnMenu, btnSales, btnTransactions, btnAccounts, btnSettings};
        navIcons   = new String[]{"🏠", "📋", "📈", "🕒", "👥", "\uD83D\uDD27"};
        navLabels  = new String[]{"HOME", "MENU MANAGEMENT", "SALES", "TRANSACTION HISTORY", "ACCOUNT MANAGEMENT", "SETTINGS"};

        for (int i = 0; i < navButtons.length; i++) {
            setupIconButton(navButtons[i], navIcons[i]);
        }
        setupIconButton(btnLogout, "🚪");

        setSystemDate();
        applySidebarState();
        showHome();
    }

    /** Icon lives in a fixed-width graphic so it never moves when the text appears/disappears. */
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
        double width = isExpanded ? EXPANDED_WIDTH : COLLAPSED_WIDTH;
        sidebar.setMinWidth(width);
        sidebar.setPrefWidth(width);
        sidebar.setMaxWidth(width);

        lblBrandText.setVisible(isExpanded);
        lblBrandText.setManaged(isExpanded);
        userInfoBox.setVisible(isExpanded);
        userInfoBox.setManaged(isExpanded);

        for (int i = 0; i < navButtons.length; i++) {
            navButtons[i].setText(isExpanded ? navLabels[i] : "");
        }
        btnLogout.setText(isExpanded ? "LOGOUT" : "");
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