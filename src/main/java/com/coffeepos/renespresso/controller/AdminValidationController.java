package com.coffeepos.renespresso.controller;

import com.coffeepos.renespresso.util.AlertUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

public class AdminValidationController {

    @FXML private TextField adminUsernameField;
    @FXML private PasswordField adminPasswordField;
    @FXML private TextField visibleAdminPasswordField;
    @FXML private SVGPath eyeIconPath;
    @FXML private Button validateButton;
    @FXML private Button cancelButton;

    private boolean isValidated = false;
    private boolean isPasswordVisible = false;

    private static final String EYE_OPEN_PATH =
            "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";

    private static final String EYE_SLASHED_PATH =
            "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.44-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zM11.84 9.02l3.15 3.15.02-.17c0-1.66-1.34-3-3-3l-.17.02z";

    @FXML
    public void initialize() {
        // Sync password fields
        visibleAdminPasswordField.textProperty().bindBidirectional(adminPasswordField.textProperty());

        // Enter key navigation
        adminUsernameField.setOnAction(e -> {
            if (isPasswordVisible) {
                visibleAdminPasswordField.requestFocus();
            } else {
                adminPasswordField.requestFocus();
            }
        });

        adminPasswordField.setOnAction(this::handleValidation);
        visibleAdminPasswordField.setOnAction(this::handleValidation);

        validateButton.setOnAction(this::handleValidation);
        cancelButton.setOnAction(this::handleCancel);
    }

    @FXML
    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;

        visibleAdminPasswordField.setVisible(isPasswordVisible);
        visibleAdminPasswordField.setManaged(isPasswordVisible);

        adminPasswordField.setVisible(!isPasswordVisible);
        adminPasswordField.setManaged(!isPasswordVisible);

        if (isPasswordVisible) {
            visibleAdminPasswordField.requestFocus();
            visibleAdminPasswordField.selectEnd();
            eyeIconPath.setContent(EYE_OPEN_PATH);
            eyeIconPath.setOpacity(1.0);
        } else {
            adminPasswordField.requestFocus();
            adminPasswordField.selectEnd();
            eyeIconPath.setContent(EYE_SLASHED_PATH);
            eyeIconPath.setOpacity(0.55);
        }
    }

    @FXML
    private void handleValidation(ActionEvent event) {
        String username = adminUsernameField.getText().trim();
        String password = adminPasswordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            AlertUtil.showWarning("Validation Required", "Please enter admin credentials.");
            return;
        }

        if (authenticateAdminCredentials(username, password)) {
            isValidated = true;
            closeWindow();
        } else {
            AlertUtil.showError("Authorization Failed", "Invalid admin credentials or insufficient privileges.");
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        isValidated = false;
        closeWindow();
    }

    private boolean authenticateAdminCredentials(String username, String password) {
        // Placeholder authentication: check against DB for ADMIN or SUPERADMIN role
        return ("admin".equalsIgnoreCase(username) || "superadmin".equalsIgnoreCase(username))
                && "admin123".equals(password);
    }

    private void closeWindow() {
        Stage stage = (Stage) validateButton.getScene().getWindow();
        stage.close();
    }

    public boolean isValidated() {
        return isValidated;
    }
}