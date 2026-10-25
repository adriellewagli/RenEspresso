package com.coffeepos.renespresso.controller.util;

import com.coffeepos.renespresso.dao.UserDAO;
import com.coffeepos.renespresso.model.User;
import com.coffeepos.renespresso.util.SessionManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class AdminAuthController {

    @FXML private Label iconLabel;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Label lblUsernameHeader;
    @FXML private Label lblPasswordHeader;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;
    @FXML private Button btnCancel;
    @FXML private Button btnAuthorize;

    private boolean authorized = false;
    private String authorizedBy = null;
    private boolean enforceCurrentUser = false;

    @FXML
    public void initialize() {
        // Clear error as user types
        txtUsername.textProperty().addListener((obs, o, n) -> hideError());
        txtPassword.textProperty().addListener((obs, o, n) -> hideError());

        // Pressing Enter in username field moves to password
        txtUsername.setOnAction(e -> txtPassword.requestFocus());

        User current = SessionManager.getCurrentUser();
        if (current != null && current.getRole() != null) {
            String role = current.getRole().toUpperCase();
            if (role.equals("ADMIN") || role.equals("MANAGER")) {
                if (lblUsernameHeader != null) lblUsernameHeader.setText(role + " USERNAME");
                if (lblPasswordHeader != null) lblPasswordHeader.setText(role + " PASSWORD");
                txtUsername.setPromptText("Enter @" + current.getUsername());
            }
        }
        txtUsername.clear();
        txtPassword.clear();
        Platform.runLater(() -> txtUsername.requestFocus());
    }

    public void setAuthData(String title, String message, String authorizeBtnText) {
        if (title != null && !title.isBlank()) {
            titleLabel.setText(title);
        }
        if (message != null && !message.isBlank()) {
            messageLabel.setText(message);
        }
        if (authorizeBtnText != null && !authorizeBtnText.isBlank()) {
            btnAuthorize.setText(authorizeBtnText);
        }
    }

    public void setEnforceCurrentUser(boolean enforce) {
        this.enforceCurrentUser = enforce;
        User current = SessionManager.getCurrentUser();
        if (current != null) {
            String role = current.getRole() != null ? current.getRole().toUpperCase() : "USER";
            if (lblUsernameHeader != null) lblUsernameHeader.setText(role + " USERNAME");
            if (lblPasswordHeader != null) lblPasswordHeader.setText(role + " PASSWORD");
            if (enforce) {
                txtUsername.setPromptText("Enter @" + current.getUsername());
            }
        }
        txtUsername.clear();
        txtPassword.clear();
        Platform.runLater(() -> txtUsername.requestFocus());
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public String getAuthorizedBy() {
        return authorizedBy;
    }

    @FXML
    private void handleAuthorize(ActionEvent event) {
        String username = txtUsername.getText() != null ? txtUsername.getText().trim() : "";
        String password = txtPassword.getText() != null ? txtPassword.getText() : "";

        if (username.isEmpty()) {
            showError("Please enter your username.");
            txtUsername.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter your password.");
            txtPassword.requestFocus();
            return;
        }

        User current = SessionManager.getCurrentUser();
        if (enforceCurrentUser && current != null) {
            if (!username.equalsIgnoreCase(current.getUsername())) {
                showError("Please enter credentials for your currently logged-in account (@" + current.getUsername() + ").");
                txtUsername.requestFocus();
                return;
            }
        }

        boolean valid = UserDAO.validateAdminCredentials(username, password);
        if (!valid) {
            showError("Invalid username or password.");
            txtPassword.clear();
            txtPassword.requestFocus();
            return;
        }

        this.authorized = true;
        this.authorizedBy = username;
        closeStage(event);
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        this.authorized = false;
        closeStage(event);
    }

    private void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void hideError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
    }

    private void closeStage(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}
