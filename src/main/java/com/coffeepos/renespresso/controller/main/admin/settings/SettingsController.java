package com.coffeepos.renespresso.controller.main.admin.settings;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.util.ConfirmUtil;
import com.coffeepos.renespresso.util.ExcelBackupUtil;
import com.coffeepos.renespresso.util.SettingsManager;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class SettingsController {

    private static final PseudoClass ON = PseudoClass.getPseudoClass("on");
    private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;

    @FXML private VBox settingsRoot;
    @FXML private StackPane sectionStack;
    @FXML private ToggleGroup navGroup;
    @FXML private Label lblSaveNote;

    // store information
    @FXML private TextField txtShopName, txtTagline, txtAddress, txtContact, txtTin;
    @FXML private ImageView imgLogo;
    @FXML private Label lblLogoPlaceholder;

    // backup section
    @FXML private Button btnBackupExcel;
    @FXML private ProgressIndicator progress;
    @FXML private Label lblStatus;
    @FXML private TextField txtBackupFolder;

    // logo changes wait here until the user clicks Save Changes
    private File pendingLogo = null;
    private boolean removeLogo = false;

    private Connection getConnection() throws SQLException {
        return DatabaseManager.getConnection();
    }

    // ------------------------------------------------------------------ INIT

    @FXML
    public void initialize() {
        fillCombos();
        buildSwitches();

        // left menu: one section visible at a time, and one button always stays selected
        navGroup.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null) {
                if (old != null) old.setSelected(true);
                return;
            }
            showSection(String.valueOf(now.getUserData()));
        });
        showSection("secStore");

        SettingsManager.reload();
        loadStoreInfo();
    }

    private void showSection(String id) {
        for (Node n : sectionStack.getChildren()) {
            boolean show = id.equals(n.getId());
            n.setVisible(show);
            n.setManaged(show);
        }
    }

    /** ComboBoxes list their choices in userData, separated by "|" (see the FXML). */
    @SuppressWarnings("unchecked")
    private void fillCombos() {
        for (Node n : sectionStack.lookupAll(".field-combo")) {
            if (n instanceof ComboBox<?> cb && cb.getUserData() instanceof String s) {
                ((ComboBox<String>) cb).getItems().setAll(s.split("\\|"));
            }
        }
    }

    /** Turns every ToggleButton with the "switch" style class into an on/off switch. */
    private void buildSwitches() {
        for (Node n : sectionStack.lookupAll(".switch")) {
            if (n instanceof ToggleButton tb) makeSwitch(tb);
        }
    }

    private void makeSwitch(ToggleButton tb) {
        Circle knob = new Circle(10, Color.WHITE);
        StackPane track = new StackPane(knob);
        track.getStyleClass().add("switch-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setMinSize(46, 26);
        track.setPrefSize(46, 26);
        track.setMaxSize(46, 26);
        StackPane.setMargin(knob, new Insets(0, 0, 0, 3));

        Runnable apply = () -> {
            boolean on = tb.isSelected();
            track.pseudoClassStateChanged(ON, on);
            TranslateTransition tt = new TranslateTransition(Duration.millis(140), knob);
            tt.setToX(on ? 20 : 0);
            tt.play();
        };
        knob.setTranslateX(tb.isSelected() ? 20 : 0);
        track.pseudoClassStateChanged(ON, tb.isSelected());
        tb.selectedProperty().addListener((o, a, b) -> apply.run());

        tb.setText(null);
        tb.setGraphic(track);
        tb.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    // ------------------------------------------------------------------ STORE INFORMATION

    private void loadStoreInfo() {
        txtShopName.setText(SettingsManager.get(SettingsManager.STORE_NAME));
        txtTagline.setText(SettingsManager.get(SettingsManager.STORE_TAGLINE));
        txtAddress.setText(SettingsManager.get(SettingsManager.STORE_ADDRESS));
        txtContact.setText(SettingsManager.get(SettingsManager.STORE_CONTACT));
        txtTin.setText(SettingsManager.get(SettingsManager.STORE_TIN));

        pendingLogo = null;
        removeLogo = false;
        showLogoPreview(SettingsManager.getLogoImage());
    }

    private void showLogoPreview(Image image) {
        boolean has = image != null;
        imgLogo.setImage(image);
        imgLogo.setVisible(has);
        lblLogoPlaceholder.setVisible(!has);
    }

    @FXML
    private void handleUploadLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Store Logo");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(settingsRoot.getScene().getWindow());
        if (file == null) return;

        if (file.length() > MAX_LOGO_BYTES) {
            show(Alert.AlertType.WARNING, "Logo too large", "Please choose an image of 2 MB or less.");
            return;
        }
        Image preview = new Image(file.toURI().toString(), 256, 256, true, true);
        if (preview.isError()) {
            show(Alert.AlertType.WARNING, "Invalid image", "That file could not be read as an image.");
            return;
        }
        pendingLogo = file;
        removeLogo = false;
        showLogoPreview(preview);
        lblSaveNote.setText("");
    }

    @FXML
    private void handleRemoveLogo() {
        pendingLogo = null;
        removeLogo = true;
        showLogoPreview(null);
        lblSaveNote.setText("");
    }

    /** Header "Save Changes" button. For now it saves Store Information. */
    @FXML
    private void handleSaveSettings() {
        String name = txtShopName.getText().trim();
        String contact = txtContact.getText().trim();
        String tin = txtTin.getText().trim();

        if (name.isEmpty()) {
            show(Alert.AlertType.WARNING, "Check your input", "Shop name is required.");
            return;
        }
        if (!contact.isEmpty() && !contact.matches("[0-9+()\\-\\s]{7,20}")) {
            show(Alert.AlertType.WARNING, "Check your input",
                    "Contact number can only contain digits, spaces, + ( ) and -.");
            return;
        }
        if (!tin.isEmpty() && !tin.matches("[0-9\\-\\s]{9,20}")) {
            show(Alert.AlertType.WARNING, "Check your input",
                    "TIN can only contain digits, spaces and dashes (at least 9 digits).");
            return;
        }

        if (!ConfirmUtil.confirmSave(settingsRoot, "Save the store information?")) return;

        try {
            Map<String, String> values = new HashMap<>();
            values.put(SettingsManager.STORE_NAME, name);
            values.put(SettingsManager.STORE_TAGLINE, txtTagline.getText().trim());
            values.put(SettingsManager.STORE_ADDRESS, txtAddress.getText().trim());
            values.put(SettingsManager.STORE_CONTACT, contact);
            values.put(SettingsManager.STORE_TIN, tin);

            if (pendingLogo != null) {
                File stored = SettingsManager.storeLogoFile(pendingLogo);
                values.put(SettingsManager.STORE_LOGO_PATH, stored.getAbsolutePath());
            } else if (removeLogo) {
                SettingsManager.deleteLogoFiles();
                values.put(SettingsManager.STORE_LOGO_PATH, "");
            }

            SettingsManager.save(values);
            pendingLogo = null;
            removeLogo = false;
            lblSaveNote.setText("Store information saved.");
        } catch (SQLException | IOException ex) {
            ex.printStackTrace();
            show(Alert.AlertType.ERROR, "Save failed", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ BACKUP & SECURITY (still placeholders)

    @FXML
    private void handleRestore() {
        show(Alert.AlertType.INFORMATION, "Coming soon", "Restore from Excel will be available in a later step.");
    }

    @FXML
    private void handleBrowseBackupFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Backup Folder");
        File dir = chooser.showDialog(settingsRoot.getScene().getWindow());
        if (dir != null) txtBackupFolder.setText(dir.getAbsolutePath());
    }

    // ------------------------------------------------------------------ EXCEL BACKUP (unchanged)

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