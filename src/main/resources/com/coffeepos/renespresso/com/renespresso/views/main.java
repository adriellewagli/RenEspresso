import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Updated to forgotPasswordView.fxml
        Parent root = FXMLLoader.load(getClass().getResource("forgotPasswordView.fxml"));
        Scene scene = new Scene(root);
        
        // Load CSS stylesheet
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        
        primaryStage.setTitle("Renespresso POS - Reset Password");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}