package com.coffeepos.renespresso.controller;

import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Button signUpButton;

    @FXML
    private Hyperlink loginLink;

    @FXML
    public void initialize() {
        signUpButton.setOnAction(this::handleSignUp);
        loginLink.setOnAction(this::handleLoginNavigation);
    }


    @FXML
    private void handleSignUp(ActionEvent event) {
        String fullName = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        //BASIC VALIDATION
        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            AlertUtil.showWarning("Validation Error", "Please fill in all fields to create an account.");
            return;
        }

        //LENGTH VALIDATION
        if (password.length() < 6) {
            AlertUtil.showWarning("Weak Password", "Password must be at least 6 characters long.");
            return;
        }

        //MATCH VALIDATION
        if (!password.equals(confirmPassword)) {
            AlertUtil.showError("Password Mismatch", "Passwords do not match. Please try again.");
            return;
        }

        //SA DB TO SOON
        boolean isCreated = registerUserInDatabase(fullName, username, password);

        if (isCreated) {
            AlertUtil.showSuccess("Success", "Account created successfully! Please log in.");
            NavigateUtil.navigateTo(event, "LoginView.fxml", "Renespresso - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
        } else {
            AlertUtil.showError("Registration Error", "Could not create account. Username might already be taken.");
        }
    }

    //PAPUNTANG LOGIN
    @FXML
    private void handleLoginNavigation(ActionEvent event) {
        NavigateUtil.navigateTo(event, "LoginView.fxml", "Renespresso - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    //PLACE HOLDER LANG ITO
    private boolean registerUserInDatabase(String fullName, String username, String password) {
        return true;
    }
}