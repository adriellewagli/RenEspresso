package com.coffeepos.renespresso.controller.id;

import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class RegisterController {

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField usernameField;

    // Main Password Fields
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField visiblePasswordField;
    @FXML
    private SVGPath eyeIconPath;

    // Confirm Password Fields
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private TextField visibleConfirmPasswordField;
    @FXML
    private SVGPath confirmEyeIconPath;

    // Role Selection Field
    @FXML
    private ComboBox<String> roleComboBox;

    @FXML
    private Button signUpButton;

    @FXML
    private Hyperlink loginLink;

    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    // Vector SVG paths
    private static final String EYE_OPEN_PATH =
            "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";

    private static final String EYE_SLASHED_PATH =
            "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.44-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zM11.84 9.02l3.15 3.15.02-.17c0-1.66-1.34-3-3-3l-.17.02z";

    @FXML
    public void initialize() {
        // Synchronize masked and visible password fields
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        visibleConfirmPasswordField.textProperty().bindBidirectional(confirmPasswordField.textProperty());

        // Initialize Role Options
        if (roleComboBox != null) {
            roleComboBox.getItems().setAll("CASHIER", "ADMIN", "SUPERADMIN");
            roleComboBox.setValue("CASHIER");
        }

        // --- ENTER KEY UX NAVIGATION ---
        fullNameField.setOnAction(e -> usernameField.requestFocus());

        usernameField.setOnAction(e -> {
            if (isPasswordVisible) {
                visiblePasswordField.requestFocus();
            } else {
                passwordField.requestFocus();
            }
        });

        passwordField.setOnAction(e -> focusConfirmPassword());
        visiblePasswordField.setOnAction(e -> focusConfirmPassword());

        confirmPasswordField.setOnAction(this::handleSignUp);
        visibleConfirmPasswordField.setOnAction(this::handleSignUp);

        // Buttons & Links
        signUpButton.setOnAction(this::handleSignUp);
        loginLink.setOnAction(this::handleLoginNavigation);
    }

    private void focusConfirmPassword() {
        if (isConfirmPasswordVisible) {
            visibleConfirmPasswordField.requestFocus();
        } else {
            confirmPasswordField.requestFocus();
        }
    }

    // TOGGLE MAIN PASSWORD VISIBILITY
    @FXML
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        visiblePasswordField.setVisible(isPasswordVisible);
        visiblePasswordField.setManaged(isPasswordVisible);

        passwordField.setVisible(!isPasswordVisible);
        passwordField.setManaged(!isPasswordVisible);

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

    // TOGGLE CONFIRM PASSWORD VISIBILITY
    @FXML
    private void toggleConfirmPasswordVisibility() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible;

        visibleConfirmPasswordField.setVisible(isConfirmPasswordVisible);
        visibleConfirmPasswordField.setManaged(isConfirmPasswordVisible);

        confirmPasswordField.setVisible(!isConfirmPasswordVisible);
        confirmPasswordField.setManaged(!isConfirmPasswordVisible);

        if (isConfirmPasswordVisible) {
            visibleConfirmPasswordField.requestFocus();
            visibleConfirmPasswordField.selectEnd();
            confirmEyeIconPath.setContent(EYE_OPEN_PATH);
            confirmEyeIconPath.setOpacity(1.0);
        } else {
            confirmPasswordField.requestFocus();
            confirmPasswordField.selectEnd();
            confirmEyeIconPath.setContent(EYE_SLASHED_PATH);
            confirmEyeIconPath.setOpacity(0.55);
        }
    }

    @FXML
    private void handleSignUp(ActionEvent event) {
        String fullName = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String selectedRole = roleComboBox != null ? roleComboBox.getValue() : "CASHIER";

        // 1. BASIC VALIDATION
        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            AlertUtil.showWarning("Validation Error", "Please fill in all fields to create an account.");
            return;
        }

        // 2. LENGTH VALIDATION
        if (password.length() < 6) {
            AlertUtil.showWarning("Weak Password", "Password must be at least 6 characters long.");
            return;
        }

        // 3. MATCH VALIDATION
        if (!password.equals(confirmPassword)) {
            AlertUtil.showError("Password Mismatch", "Passwords do not match. Please try again.");
            return;
        }

        // 4. ALWAYS TRIGGER ADMIN VALIDATION MODAL ON SIGN UP
        Stage currentStage = (Stage) signUpButton.getScene().getWindow();
        boolean isAuthorized = promptAdminValidation(currentStage);

        // Abort signup if validation fails or popup is closed
        if (!isAuthorized) {
            return;
        }

        // 5. MOCK REGISTRATION
        boolean isCreated = registerUserInDatabase(fullName, username, password, selectedRole);

        if (isCreated) {
            AlertUtil.showSuccess("Success", "Account created successfully as [" + selectedRole + "]! Please log in.");
            NavigateUtil.navigateTo(event, "LoginView.fxml", "Renespresso - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
        } else {
            AlertUtil.showError("Registration Error", "Could not create account. Username might already be taken.");
        }
    }

    private boolean promptAdminValidation(Stage ownerStage) {
        try {
            // Updated path pointing to the 'views' directory
            URL fxmlUrl = getClass().getResource("/com/coffeepos/renespresso/views/id/AdminValidationView.fxml");

            // Fallback relative path to views
            if (fxmlUrl == null) {
                fxmlUrl = getClass().getResource("../views/AdminValidationView.fxml");
            }

            // Flat fallback
            if (fxmlUrl == null) {
                fxmlUrl = getClass().getResource("/views/AdminValidationView.fxml");
            }

            if (fxmlUrl == null) {
                AlertUtil.showError("Resource Error", "AdminValidationView.fxml could not be found in views directory.");
                return false;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            AdminValidationController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Admin Authorization Required");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(ownerStage);
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();

            return controller != null && controller.isValidated();
        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.showError("System Error", "Could not load admin validation window.");
            return false;
        }
    }

    // PAPUNTANG LOGIN
    @FXML
    private void handleLoginNavigation(ActionEvent event) {
        NavigateUtil.navigateTo(event, "id/LoginView.fxml", "Renespresso - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    // PLACEHOLDER FOR DB LOGIC
    private boolean registerUserInDatabase(String fullName, String username, String password, String role) {
        return true;
    }
}