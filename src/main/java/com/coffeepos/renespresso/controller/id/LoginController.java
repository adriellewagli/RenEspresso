package com.coffeepos.renespresso.controller.id;

import com.coffeepos.renespresso.dao.UserDAO;
import com.coffeepos.renespresso.model.User;
import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.shape.SVGPath;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField visiblePasswordField;

    @FXML
    private Button togglePasswordButton;

    @FXML
    private SVGPath eyeIconPath;

    @FXML
    private Hyperlink forgotPasswordLink;

    @FXML
    private Button loginButton;

    @FXML
    private Hyperlink registerLink;

    private boolean isPasswordVisible = false;

    private static final String EYE_OPEN_PATH =
            "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";

    private static final String EYE_SLASHED_PATH =
            "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.44-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zM11.84 9.02l3.15 3.15.02-.17c0-1.66-1.34-3-3-3l-.17.02z";

    @FXML
    public void initialize() {
        // Keep masked and visible password synchronized
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());

        // Press Enter in Username -> Move focus to Active Password Field
        usernameField.setOnAction(e -> {
            if (isPasswordVisible) {
                visiblePasswordField.requestFocus();
            } else {
                passwordField.requestFocus();
            }
        });

        // Press Enter in Password Fields -> Trigger Login
        passwordField.setOnAction(this::handleLogin);
        visiblePasswordField.setOnAction(this::handleLogin);

        loginButton.setOnAction(this::handleLogin);
        registerLink.setOnAction(this::handleRegisterNavigation);
        forgotPasswordLink.setOnAction(this::handleForgotPasswordNavigation);
    }

    // TOGGLE SHOW/HIDE PASSWORD
    @FXML
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        visiblePasswordField.setVisible(isPasswordVisible);
        visiblePasswordField.setManaged(isPasswordVisible);

        passwordField.setVisible(!isPasswordVisible);
        passwordField.setManaged(!isPasswordVisible);

        // Retain focus on whichever field is now visible
        if (isPasswordVisible) {
            visiblePasswordField.requestFocus();
            visiblePasswordField.selectEnd();
            eyeIconPath.setContent(EYE_OPEN_PATH);
            eyeIconPath.setOpacity(1.0);
        } else {
            passwordField.requestFocus();
            passwordField.selectEnd();
            eyeIconPath.setContent(EYE_SLASHED_PATH);
            eyeIconPath.setOpacity(0.55);
        }
    }

    // LOGIN
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            AlertUtil.showWarning("Validation Error", "Please enter both username and password.");
            return;
        }

        User user = UserDAO.authenticate(username, password);

        if (user != null) {
            AlertUtil.showSuccess("Welcome", "Login successful! Logging in as " + user.getFullName());

            // Route user depending on role
            if ("admin".equalsIgnoreCase(user.getRole()) || "manager".equalsIgnoreCase(user.getRole())) {
                NavigateUtil.navigateTo(event, "main/AdminDashboardView.fxml", "Renespresso - Admin Dashboard", NavigateUtil.WindowMode.FULLSCREEN_WORKSPACE);
            } else {
                NavigateUtil.navigateTo(event, "ClientView.fxml", "Renespresso POS Terminal", NavigateUtil.WindowMode.FULLSCREEN_WORKSPACE);
            }
        } else {
            AlertUtil.showError("Authentication Failed", "Invalid username or password, or account is inactive.");
        }
    }

    // NAVIGATIONS
    @FXML
    private void handleRegisterNavigation(ActionEvent event) {
        NavigateUtil.navigateTo(event, "id/RegisterView.fxml", "Renespresso - Register Account", NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    @FXML
    private void handleForgotPasswordNavigation(ActionEvent event) {
        NavigateUtil.navigateTo(event, "id/ForgotPasswordView.fxml", "Renespresso - Reset Password", NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    private boolean authenticateUser(String username, String password) {
        return UserDAO.authenticate(username, password) != null;
    }
}