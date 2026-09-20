package com.coffeepos.renespresso.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class AlertController {

    @FXML
    private Label iconLabel;

    @FXML
    private Label titleLabel;

    @FXML
    private Label messageLabel;

    @FXML
    private Button okButton;

    //NAKABASED SA PARAMETERS UNG MESSAGE BOX
    public void setAlertData(String icon, String title, String message, String accentColor) {
        iconLabel.setText(icon);
        iconLabel.setStyle("-fx-font-size: 26px; -fx-text-fill: " + accentColor + ";");

        titleLabel.setText(title);
        messageLabel.setText(message);

        okButton.setStyle(
                "-fx-background-color: " + accentColor + ";" +
                        "-fx-text-fill: #fffdf7;" +
                        "-fx-background-radius: 6px;" +
                        "-fx-cursor: hand;"
        );
    }

    @FXML
    private void handleClose(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}