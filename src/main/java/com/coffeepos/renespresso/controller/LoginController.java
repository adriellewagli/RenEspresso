package com.coffeepos.renespresso.controller;

import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Hyperlink forgotPasswordLink;

    @FXML
    private Button loginButton;

    @FXML
    private Hyperlink registerLink;

    @FXML
    public void initialize() {
        loginButton.setOnAction(this::handleLogin);
        registerLink.setOnAction(this::handleRegisterNavigation);
        forgotPasswordLink.setOnAction(this::handleForgotPasswordNavigation);
    }

    //LOGIN
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        //BASIC VALIDATION
        if (username.isEmpty() || password.isEmpty()) {
            AlertUtil.showWarning("Validation Error", "Please enter both username and password.");
            return;
        }

        // SAMPLE LANG TO SA DB
        boolean isAuthenticated = authenticateUser(username, password);

        if (isAuthenticated) {
            AlertUtil.showSuccess("Welcome", "Login successful!");
            NavigateUtil.navigateTo(event, "ClientView.fxml", "Renespresso POS Terminal", NavigateUtil.WindowMode.FULLSCREEN_WORKSPACE);
        } else {
            AlertUtil.showError("Authentication Failed", "Invalid username or password.");
        }
    }

    //PAPUNTANG REGISTER
    @FXML
    private void handleRegisterNavigation(ActionEvent event) {
        NavigateUtil.navigateTo(event, "RegisterView.fxml", "Renespresso - Register Account", NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    //PAPUNTANG FORGOT PASS
    @FXML
    private void handleForgotPasswordNavigation(ActionEvent event) {
        NavigateUtil.navigateTo(event, "ForgotPasswordView.fxml", "Renespresso - Reset Password", NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    private boolean authenticateUser(String username, String password) {
        //PLACEHOLDER LANG ITO
        return "admin".equalsIgnoreCase(username) && "password".equals(password);
    }
}