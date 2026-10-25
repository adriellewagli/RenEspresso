package com.coffeepos.renespresso.util;

import com.coffeepos.renespresso.controller.util.AdminAuthController;
import com.coffeepos.renespresso.controller.util.AlertController;
import com.coffeepos.renespresso.controller.util.ConfirmAlertController;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;

public class AlertUtil {

    private static final String ALERT_VIEW_PATH = "/com/coffeepos/renespresso/views/util/CustomAlertView.fxml";
    private static final String CONFIRM_VIEW_PATH = "/com/coffeepos/renespresso/views/util/ConfirmAlertView.fxml";
    private static final String ADMIN_AUTH_VIEW_PATH = "/com/coffeepos/renespresso/views/util/AdminAuthModalView.fxml";

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

    // SECURITY / ADMIN AUTHORIZATION
    public static boolean requestAdminAuthorization(String title, String message) {
        return requestAdminAuthorization(title, message, "Authorize Cancel");
    }

    public static boolean requestAdminAuthorization(String title, String message, String authorizeBtnText) {
        return requestSecurityAuthorization(title, message, authorizeBtnText, false);
    }

    public static boolean requestSecurityAuthorization(String title, String message, String authorizeBtnText) {
        return requestSecurityAuthorization(title, message, authorizeBtnText, true);
    }

    public static boolean requestSecurityAuthorization(String title, String message, String authorizeBtnText, boolean enforceCurrentUser) {
        try {
            URL xmlUrl = AlertUtil.class.getResource(ADMIN_AUTH_VIEW_PATH);
            if (xmlUrl == null) {
                throw new IllegalStateException("Cannot find FXML file: " + ADMIN_AUTH_VIEW_PATH);
            }

            FXMLLoader loader = new FXMLLoader(xmlUrl);
            Parent root = loader.load();

            AdminAuthController controller = loader.getController();
            controller.setAuthData(title, message, authorizeBtnText);
            controller.setEnforceCurrentUser(enforceCurrentUser);

            Stage stage = createModalStage(root);
            stage.showAndWait();

            return controller.isAuthorized();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
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

        javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isFocused)
                .findFirst()
                .ifPresent(stage::initOwner);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);

        // centerOnScreen() before the stage is shown has no size to work with, so the box landed off-center.
        // Position it using its real size: once from the preferred size (so it never flashes in the wrong
        // spot) and once more after it is shown (exact size).
        stage.setOnShowing(e -> {
            root.applyCss();
            double w = root.prefWidth(-1);
            double h = root.prefHeight(w);
            centerStage(stage, w, h);
        });
        stage.setOnShown(e -> centerStage(stage, stage.getWidth(), stage.getHeight()));
        return stage;
    }

    /** Centers the stage over its owner window; falls back to the middle of the screen if there is no owner. */
    private static void centerStage(Stage stage, double w, double h) {
        Window owner = stage.getOwner();
        if (owner != null && owner.getWidth() > 0 && owner.getHeight() > 0) {
            stage.setX(owner.getX() + (owner.getWidth() - w) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - h) / 2);
        } else {
            Rectangle2D b = Screen.getPrimary().getVisualBounds();
            stage.setX(b.getMinX() + (b.getWidth() - w) / 2);
            stage.setY(b.getMinY() + (b.getHeight() - h) / 2);
        }
    }
}