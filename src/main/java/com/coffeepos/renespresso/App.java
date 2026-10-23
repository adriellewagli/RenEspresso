package com.coffeepos.renespresso;

import com.coffeepos.renespresso.util.NavigateUtil;
import com.coffeepos.renespresso.util.SessionManager;
import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void init() {
        Runtime.getRuntime().addShutdownHook(new Thread(SessionManager::logout));
    }

    @Override
    public void start(Stage stage) {
        stage.setOnCloseRequest(e -> SessionManager.logout());
        NavigateUtil.navigateTo(stage, "id/LoginView.fxml", "Renespresso - Login", NavigateUtil.WindowMode.AUTH_DIALOG);
    }

    @Override
    public void stop() throws Exception {
        SessionManager.logout();
        super.stop();
    }
}