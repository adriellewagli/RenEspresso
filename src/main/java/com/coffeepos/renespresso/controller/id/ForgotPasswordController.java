package com.coffeepos.renespresso.controller.id;

import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class ForgotPasswordController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private TextField newPasswordTextField;

    @FXML
    private CheckBox showNewPasswordToggle;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private TextField confirmPasswordTextField;

    @FXML
    private CheckBox showConfirmPasswordToggle;

    @FXML
    private Button sendResetButton;

    @FXML
    private Hyperlink backToLoginLink;

    @FXML
    public void initialize() {
        sendResetButton.setOnAction(this::handleSendResetLink);
        backToLoginLink.setOnAction(this::handleBackToLogin);

        // SYNC TEXT FIELDS FOR TOGGLE VISIBILITY
        syncPasswordFields(newPasswordField, newPasswordTextField);
        syncPasswordFields(confirmPasswordField, confirmPasswordTextField);
    }

    private void syncPasswordFields(PasswordField passwordField, TextField textField) {
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!textField.getText().equals(newValue)) {
                textField.setText(newValue);
            }
        });
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!passwordField.getText().equals(newValue)) {
                passwordField.setText(newValue);
            }
        });
    }

    // TOGGLE NEW PASSWORD VISIBILITY
    @FXML
    private void toggleNewPasswordVisibility(ActionEvent event) {
        boolean show = showNewPasswordToggle.isSelected();
        newPasswordTextField.setVisible(show);
        newPasswordTextField.setManaged(show);
        newPasswordField.setVisible(!show);
        newPasswordField.setManaged(!show);
    }

    // TOGGLE CONFIRM PASSWORD VISIBILITY
    @FXML
    private void toggleConfirmPasswordVisibility(ActionEvent event) {
        boolean show = showConfirmPasswordToggle.isSelected();
        confirmPasswordTextField.setVisible(show);
        confirmPasswordTextField.setManaged(show);
        confirmPasswordField.setVisible(!show);
        confirmPasswordField.setManaged(!show);
    }

    // RESET PASSWORD
    @FXML
    private void handleSendResetLink(ActionEvent event) {
        String username = usernameField.getText().trim();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // VALIDATIONS
        if (username.isEmpty()) {
            AlertUtil.showWarning("Validation Error", "Please enter your username.");
            return;
        }

        if (newPassword.isEmpty()) {
            AlertUtil.showWarning("Validation Error", "Please enter your new password.");
            return;
        }

        if (newPassword.length() < 6) {
            AlertUtil.showWarning("Weak Password", "Password must be at least 6 characters long.");
            return;
        }

        if (confirmPassword.isEmpty()) {
            AlertUtil.showWarning("Validation Error", "Please confirm your new password.");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            AlertUtil.showWarning("Password Mismatch", "New password and confirm password do not match.");
            return;
        }

        // DB/Backend integration call
        boolean isResetSuccessful = processPasswordReset(username, newPassword);

        if (isResetSuccessful) {
            AlertUtil.showSuccess("Password Reset", "Your password has been successfully updated.");
            NavigateUtil.navigateTo(event, "id/LoginView.fxml", "Renespresso - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
        } else {
            AlertUtil.showError("Error", "Unable to update password. Please verify your username and try again.");
        }
    }

    // BACK TO LOGIN
    @FXML
    private void handleBackToLogin(ActionEvent event) {
        NavigateUtil.navigateTo(event, "id/LoginView.fxml", "Renespresso - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    // PLACEHOLDER FOR DB LOGIC
    private boolean processPasswordReset(String username, String newPassword) {
        return true;
    }
}