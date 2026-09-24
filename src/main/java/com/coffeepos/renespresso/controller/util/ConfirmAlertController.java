package com.coffeepos.renespresso.controller.util;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class ConfirmAlertController {

    @FXML
    private Label iconLabel;

    @FXML
    private Label titleLabel;

    @FXML
    private Label messageLabel;

    @FXML
    private Button cancelButton;

    @FXML
    private Button confirmButton;

    private boolean userConfirmed = false;

    //PARAMETERS KUNG SAAN GAGAMITIN
    public void setConfirmData(String title, String message, String confirmText, String cancelText) {
        titleLabel.setText(title);
        messageLabel.setText(message);
        confirmButton.setText(confirmText);
        cancelButton.setText(cancelText);
    }

    public boolean isUserConfirmed() {
        return userConfirmed;
    }

    @FXML
    private void handleConfirm(ActionEvent event) {
        this.userConfirmed = true;
        closeStage(event);
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        this.userConfirmed = false;
        closeStage(event);
    }

    private void closeStage(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}