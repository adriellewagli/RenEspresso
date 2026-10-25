package com.coffeepos.renespresso.controller.util;

import com.coffeepos.renespresso.config.DatabaseManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ServerConfigController {

    @FXML private TextField txtHost;
    @FXML private TextField txtPort;
    @FXML private TextField txtDbName;
    @FXML private TextField txtUser;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblConfigLocation;

    @FXML private VBox statusBox;
    @FXML private Label lblStatusIcon;
    @FXML private Label lblStatusTitle;
    @FXML private Label lblStatusDetails;
    @FXML private ProgressIndicator progressIndicator;

    @FXML private Button btnCancel;
    @FXML private Button btnTest;
    @FXML private Button btnSave;

    private boolean saved = false;

    @FXML
    public void initialize() {
        txtHost.setText(DatabaseManager.getHost());
        txtPort.setText(DatabaseManager.getPort());
        txtDbName.setText(DatabaseManager.getDatabaseName());
        txtUser.setText(DatabaseManager.getUser());
        txtPassword.setText(DatabaseManager.getPassword());

        lblConfigLocation.setText("Active Config: " + DatabaseManager.getConfigLocation());

        if (DatabaseManager.isConnected()) {
            showStatus(true, "Currently Connected", "Connected to database server at " + DatabaseManager.getHost() + ":" + DatabaseManager.getPort());
        } else {
            showStatus(false, "Disconnected", DatabaseManager.getLastError() != null ? DatabaseManager.getLastError() : "No active database connection.");
        }
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void handleTestConnection() {
        String host = txtHost.getText() != null ? txtHost.getText().trim() : "";
        String port = txtPort.getText() != null ? txtPort.getText().trim() : "3306";
        String dbName = txtDbName.getText() != null ? txtDbName.getText().trim() : "coffee";
        String user = txtUser.getText() != null ? txtUser.getText().trim() : "root";
        String password = txtPassword.getText() != null ? txtPassword.getText() : "";

        if (host.isEmpty()) {
            showStatus(false, "Missing Server Host", "Please enter the Server Host or IP address (e.g. 192.168.1.33 or localhost).");
            txtHost.requestFocus();
            return;
        }

        setBusy(true, "Testing Connection...", "Attempting to reach MariaDB on " + host + ":" + port + "...");

        Task<DatabaseManager.ConnectionTestResult> task = new Task<>() {
            @Override
            protected DatabaseManager.ConnectionTestResult call() {
                return DatabaseManager.testConnection(host, port, dbName, user, password);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false, null, null);
            DatabaseManager.ConnectionTestResult res = task.getValue();
            showStatus(res.success(), res.message(), res.details());
        });

        task.setOnFailed(e -> {
            setBusy(false, null, null);
            showStatus(false, "Test Failed", task.getException() != null ? task.getException().getMessage() : "Unknown connection error.");
        });

        Thread t = new Thread(task, "db-test-thread");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleSaveAndConnect(ActionEvent event) {
        String host = txtHost.getText() != null ? txtHost.getText().trim() : "";
        String port = txtPort.getText() != null ? txtPort.getText().trim() : "3306";
        String dbName = txtDbName.getText() != null ? txtDbName.getText().trim() : "coffee";
        String user = txtUser.getText() != null ? txtUser.getText().trim() : "root";
        String password = txtPassword.getText() != null ? txtPassword.getText() : "";

        if (host.isEmpty()) {
            showStatus(false, "Missing Server Host", "Please enter the Server Host or IP address.");
            txtHost.requestFocus();
            return;
        }

        setBusy(true, "Saving & Connecting...", "Testing credentials and establishing connection pool...");

        Task<DatabaseManager.ConnectionTestResult> task = new Task<>() {
            @Override
            protected DatabaseManager.ConnectionTestResult call() {
                return DatabaseManager.saveAndReconnect(host, port, dbName, user, password);
            }
        };

        task.setOnSucceeded(e -> {
            setBusy(false, null, null);
            DatabaseManager.ConnectionTestResult res = task.getValue();
            if (res.success()) {
                this.saved = true;
                showStatus(true, res.message(), res.details());
                lblConfigLocation.setText("Active Config: " + DatabaseManager.getConfigLocation());
                // Delay 400ms so user sees success check before closing
                new Thread(() -> {
                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> closeStage(event));
                }).start();
            } else {
                showStatus(false, res.message(), res.details());
            }
        });

        task.setOnFailed(e -> {
            setBusy(false, null, null);
            showStatus(false, "Save Failed", task.getException() != null ? task.getException().getMessage() : "Could not save settings.");
        });

        Thread t = new Thread(task, "db-save-thread");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        closeStage(event);
    }

    private void showStatus(boolean success, String title, String details) {
        statusBox.setVisible(true);
        statusBox.setManaged(true);
        lblStatusIcon.setText(success ? "✅" : "⚠️");
        lblStatusTitle.setText(title);
        lblStatusTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: " + (success ? "#2E7D32;" : "#C62828;"));
        lblStatusDetails.setText(details);
        statusBox.setStyle("-fx-background-color: " + (success ? "#E8F5E9;" : "#FFEBEE;")
                + " -fx-background-radius: 8px; -fx-padding: 10 12; -fx-border-color: " + (success ? "#A5D6A7;" : "#FFCDD2;") + "; -fx-border-radius: 8px;");
    }

    private void setBusy(boolean busy, String title, String details) {
        btnTest.setDisable(busy);
        btnSave.setDisable(busy);
        btnCancel.setDisable(busy);
        progressIndicator.setVisible(busy);
        if (busy) {
            statusBox.setVisible(true);
            statusBox.setManaged(true);
            lblStatusIcon.setText("⏳");
            lblStatusTitle.setText(title);
            lblStatusTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #5E3023;");
            lblStatusDetails.setText(details);
            statusBox.setStyle("-fx-background-color: #F5EFEB; -fx-background-radius: 8px; -fx-padding: 10 12; -fx-border-color: #E2D3C4; -fx-border-radius: 8px;");
        }
    }

    private void closeStage(ActionEvent event) {
        Node node = (Node) event.getSource();
        if (node != null && node.getScene() != null && node.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }
}
