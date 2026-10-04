package com.coffeepos.renespresso.controller.main.user.About;

import com.coffeepos.renespresso.controller.main.user.UserSession;
import com.coffeepos.renespresso.controller.main.user.UserUi;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;

public class AboutController {

    private static final String CSS = "/com/coffeepos/renespresso/views/styles.user/about/about.css";

    @FXML private VBox aboutRoot;

    @FXML
    public void initialize() {
        UserUi.attachCss(aboutRoot, CSS);
    }

    @FXML
    public void handleContactAdmin() {
        UserUi.showInfo(Alert.AlertType.INFORMATION, "Contact System Administrator",
                "Terminal: " + UserSession.TERMINAL_ID + " (Counter 1)\nCashier: " + UserSession.get().cashierName
                        + "\n\nPlease report issues to your store administrator and mention the terminal ID above.");
    }
}