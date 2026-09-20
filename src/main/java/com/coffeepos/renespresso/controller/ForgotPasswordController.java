package com.coffeepos.renespresso.controller;

import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;

public class ForgotPasswordController {

    @FXML
    private TextField emailField;

    @FXML
    private Button sendResetButton;

    @FXML
    private Hyperlink backToLoginLink;

    @FXML
    public void initialize() {
        sendResetButton.setOnAction(this::handleSendResetLink);
        backToLoginLink.setOnAction(this::handleBackToLogin);
    }

    //RESET PASSWORD
    @FXML
    private void handleSendResetLink(ActionEvent event) {
        String email = emailField.getText().trim();

        //BASIC VALIDATION
        if (email.isEmpty()) {
            AlertUtil.showWarning("Validation Error", "Please enter your email address.");
            return;
        }

        //EMAIL FORMAT VALIDATION
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            AlertUtil.showWarning("Invalid Email", "Please enter a valid email address.");
            return;
        }

        // PLACEHOLDER LANG ITO SA DB/EMAIL SERVICE
        boolean isSent = processPasswordReset(email);

        if (isSent) {
            AlertUtil.showSuccess("Reset Link Sent", "If an account exists for " + email + ", a password reset link has been sent.");
            NavigateUtil.navigateTo(event, "LoginView.fxml", "Renespresso - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
        } else {
            AlertUtil.showError("Error", "Unable to process password reset request. Please try again.");
        }
    }

    //PAPUNTANG LOGIN
    @FXML
    private void handleBackToLogin(ActionEvent event) {
        NavigateUtil.navigateTo(event, "LoginView.fxml", "Renespresso - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    //PLACE HOLDER LANG ITO
    private boolean processPasswordReset(String email) {
        return true;
    }
}