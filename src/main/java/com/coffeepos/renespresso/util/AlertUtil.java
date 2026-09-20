package com.coffeepos.renespresso.util;

import com.coffeepos.renespresso.controller.AlertController;
import com.coffeepos.renespresso.controller.ConfirmAlertController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;

public class AlertUtil {

    private static final String ALERT_VIEW_PATH = "/com/coffeepos/renespresso/views/CustomAlertView.fxml";
    private static final String CONFIRM_VIEW_PATH = "/com/coffeepos/renespresso/views/ConfirmAlertView.fxml";

    //PALLETE
    private static final String SYSTEM_PRIMARY_COLOR = "#6f4e37"; // Renespresso Coffee Brown

    public enum AlertType {
        INFO("☕", SYSTEM_PRIMARY_COLOR),
        SUCCESS("✔", SYSTEM_PRIMARY_COLOR),
        WARNING("⚠", SYSTEM_PRIMARY_COLOR),
        ERROR("✖", SYSTEM_PRIMARY_COLOR);

        private final String icon;
        private final String accentColor;

        AlertType(String icon, String accentColor) {
            this.icon = icon;
            this.accentColor = accentColor;
        }

        public String getIcon() { return icon; }
        public String getAccentColor() { return accentColor; }
    }

    //INFORM TO
    public static void showInfo(String title, String message) {
        showAlert(AlertType.INFO, title, message);
    }

    public static void showSuccess(String title, String message) {
        showAlert(AlertType.SUCCESS, title, message);
    }

    public static void showWarning(String title, String message) {
        showAlert(AlertType.WARNING, title, message);
    }

    public static void showError(String title, String message) {
        showAlert(AlertType.ERROR, title, message);
    }

    //CONFIRM TO
    public static boolean showConfirmation(String title, String message) {
        return showConfirmModal(title, message, "Confirm", "Cancel");
    }

    public static boolean showYesNo(String title, String message) {
        return showConfirmModal(title, message, "Yes", "No");
    }

    private static void showAlert(AlertType type, String title, String message) {
        try {
            URL xmlUrl = AlertUtil.class.getResource(ALERT_VIEW_PATH);
            if (xmlUrl == null) {
                throw new IllegalStateException("Cannot find FXML file: " + ALERT_VIEW_PATH);
            }

            FXMLLoader loader = new FXMLLoader(xmlUrl);
            Parent root = loader.load();

            AlertController controller = loader.getController();
            controller.setAlertData(type.getIcon(), title, message, type.getAccentColor());

            Stage stage = createModalStage(root);
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean showConfirmModal(String title, String message, String confirmBtnText, String cancelBtnText) {
        try {
            URL xmlUrl = AlertUtil.class.getResource(CONFIRM_VIEW_PATH);
            if (xmlUrl == null) {
                throw new IllegalStateException("Cannot find FXML file: " + CONFIRM_VIEW_PATH);
            }

            FXMLLoader loader = new FXMLLoader(xmlUrl);
            Parent root = loader.load();

            ConfirmAlertController controller = loader.getController();
            controller.setConfirmData(title, message, confirmBtnText, cancelBtnText);

            Stage stage = createModalStage(root);
            stage.showAndWait();

            return controller.isUserConfirmed();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Stage createModalStage(Parent root) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);
        stage.centerOnScreen();
        return stage;
    }
}