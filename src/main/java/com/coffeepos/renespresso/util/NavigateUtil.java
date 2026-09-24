package com.coffeepos.renespresso.util;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class NavigateUtil {

    // Point to the root views folder instead of just the 'id' subfolder
    private static final String VIEWS_PATH = "/com/coffeepos/renespresso/views/";

    public enum WindowMode {
        // PAG NASA LABAS
        AUTH_DIALOG,
        // PAG NASA LOOB
        FULLSCREEN_WORKSPACE
    }

    // HELPERS SA PARAM
    public static void navigateTo(Event event, String fxmlPath, String title, WindowMode mode) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        navigateTo(stage, fxmlPath, title, mode);
    }

    // PARA TAWAG TAWAG NALANG PAG SWITCH NG WINDOWS
    public static void navigateTo(Stage stage, String fxmlPath, String title, WindowMode mode) {
        String fullPath = VIEWS_PATH + fxmlPath;
        try {
            URL xmlUrl = NavigateUtil.class.getResource(fullPath);
            if (xmlUrl == null) {
                throw new IllegalStateException("Cannot find FXML file: " + fullPath);
            }

            FXMLLoader loader = new FXMLLoader(xmlUrl);
            Parent root = loader.load();

            stage.setTitle(title);
            stage.setScene(new Scene(root));

            if (mode == WindowMode.AUTH_DIALOG) {
                // Exit maximized state if switching back from POS to Login
                stage.setMaximized(false);
                stage.setFullScreen(false);

                // Lock window bounds to predefined FXML size
                stage.setResizable(false);
                stage.sizeToScene();
                stage.centerOnScreen();

            } else if (mode == WindowMode.FULLSCREEN_WORKSPACE) {
                // Allow resizing when maximized
                // stage.setResizable(true);

                // Maximize window to fill desktop workspace (includes taskbar)
                // stage.setMaximized(true);

                // OPTIONAL FOR TOUCH/KIOSK TERMINALS:
                // Uncomment the line below for true borderless full screen mode
                stage.setFullScreen(true);
            }

            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.showError("Navigation Error", "Could not load view: " + fullPath);
        }
    }
}