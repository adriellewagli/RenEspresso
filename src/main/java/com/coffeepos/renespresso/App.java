package com.coffeepos.renespresso;

import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        NavigateUtil.navigateTo(stage, "LoginView.fxml", "Renespresso - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
    }
}