package com.coffeepos.renespresso.controller.main.admin.settings;

import com.coffeepos.renespresso.util.ExcelBackupUtil;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.FileChooser;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.coffeepos.renespresso.config.DatabaseManager;

public class SettingsController {

    @FXML private Button btnBackupExcel;
    @FXML private ProgressIndicator progress;
    @FXML private Label lblStatus;

    private Connection getConnection() throws SQLException {
        return DatabaseManager.getConnection();
    }

    @FXML
    private void handleBackupExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Database Backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));
        chooser.setInitialFileName("RenEspresso_Backup_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx");

        File file = chooser.showSaveDialog(btnBackupExcel.getScene().getWindow());
        if (file == null) return; // user cancelled

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                try (Connection conn = getConnection()) {
                    return ExcelBackupUtil.backupToExcel(conn, file);
                }
            }
        };

        btnBackupExcel.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        lblStatus.setText("Backing up database...");

        task.setOnSucceeded(e -> {
            done();
            lblStatus.setText("Backup saved: " + file.getAbsolutePath());
            show(Alert.AlertType.INFORMATION, "Backup Complete",
                    task.getValue() + " tables exported to:\n" + file.getAbsolutePath());
        });

        task.setOnFailed(e -> {
            done();
            Throwable ex = task.getException();
            lblStatus.setText("Backup failed.");
            show(Alert.AlertType.ERROR, "Backup Failed",
                    ex == null ? "Unknown error." : ex.getMessage());
        });

        Thread t = new Thread(task, "excel-backup");
        t.setDaemon(true);
        t.start();
    }

    private void done() {
        btnBackupExcel.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);
    }

    private void show(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}