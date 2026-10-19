package com.coffeepos.renespresso.controller.main.admin.settings;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.util.ConfirmUtil;
import com.coffeepos.renespresso.util.ExcelBackupUtil;
import com.coffeepos.renespresso.util.SettingsManager;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class SettingsController {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final int MONTHS_SHOWN = 36;

    @FXML private VBox settingsRoot;
    @FXML private StackPane sectionStack;
    @FXML private ToggleGroup navGroup;
    @FXML private Label lblSaveNote;

    // header buttons (Store Information only)
    @FXML private HBox storeActions;
    @FXML private Button btnEdit, btnCancel, btnSave;

    // store information
    @FXML private TextField txtShopName, txtTagline, txtAddress, txtContact, txtTin;

    // backup section
    @FXML private Button btnBackup;
    @FXML private ProgressIndicator progress;
    @FXML private Label lblStatus;

    private Connection getConnection() throws SQLException {
        return DatabaseManager.getConnection();
    }

    // ------------------------------------------------------------------ INIT

    @FXML
    public void initialize() {
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
        setupInputFilters();
        loadStoreInfo();
        setEditing(false);   // fields are locked until Edit is clicked
    }

    private void showSection(String id) {
        for (Node n : sectionStack.getChildren()) {
            boolean show = id.equals(n.getId());
            n.setVisible(show);
            n.setManaged(show);
        }
        // Edit / Save / Cancel only make sense on the Store Information tab
        boolean store = "secStore".equals(id);
        storeActions.setVisible(store);
        storeActions.setManaged(store);
        if (!store) lblSaveNote.setText("");
    }

    /** Contact: digits only, max 11. TIN: digits only, dashes added automatically, max 12 digits. */
    private void setupInputFilters() {
        // Contact number: 09171234567
        UnaryOperator<TextFormatter.Change> contactFilter = change -> {
            String digits = change.getControlNewText().replaceAll("\\D", "");
            if (digits.length() > 11) digits = digits.substring(0, 11);
            change.setRange(0, change.getControlText().length());
            change.setText(digits);
            change.selectRange(digits.length(), digits.length());
            return change;
        };
        txtContact.setTextFormatter(new TextFormatter<>(contactFilter));

        // TIN: 000-000-000-000
        UnaryOperator<TextFormatter.Change> tinFilter = change -> {
            String digits = change.getControlNewText().replaceAll("\\D", "");
            if (digits.length() > 12) digits = digits.substring(0, 12);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digits.length(); i++) {
                if (i > 0 && i % 3 == 0) sb.append('-');
                sb.append(digits.charAt(i));
            }
            String formatted = sb.toString();

            change.setRange(0, change.getControlText().length());
            change.setText(formatted);
            change.selectRange(formatted.length(), formatted.length());
            return change;
        };
        txtTin.setTextFormatter(new TextFormatter<>(tinFilter));
    }

    // ------------------------------------------------------------------ STORE INFORMATION

    private void loadStoreInfo() {
        txtShopName.setText(SettingsManager.get(SettingsManager.STORE_NAME));
        txtTagline.setText(SettingsManager.get(SettingsManager.STORE_TAGLINE));
        txtAddress.setText(SettingsManager.get(SettingsManager.STORE_ADDRESS));
        txtContact.setText(SettingsManager.get(SettingsManager.STORE_CONTACT));
        txtTin.setText(SettingsManager.get(SettingsManager.STORE_TIN));
    }

    /** Locks or unlocks the fields and swaps Edit for Cancel + Save Changes. */
    private void setEditing(boolean editing) {
        for (TextField f : new TextField[]{txtShopName, txtTagline, txtAddress, txtContact, txtTin}) {
            f.setDisable(!editing);
        }
        setShown(btnEdit, !editing);
        setShown(btnCancel, editing);
        setShown(btnSave, editing);
    }

    private static void setShown(Node n, boolean shown) {
        n.setVisible(shown);
        n.setManaged(shown);
    }

    @FXML
    private void handleEdit() {
        lblSaveNote.setText("");
        setEditing(true);
        txtShopName.requestFocus();
    }

    @FXML
    private void handleCancel() {
        loadStoreInfo();     // throw away unsaved changes
        lblSaveNote.setText("");
        setEditing(false);
    }

    /** "Save Changes" button (visible only while editing). */
    @FXML
    private void handleSaveSettings() {
        String name = txtShopName.getText().trim();
        String contact = txtContact.getText().trim();
        String tin = txtTin.getText().trim();

        if (name.isEmpty()) {
            show(Alert.AlertType.WARNING, "Check your input", "Shop name is required.");
            return;
        }
        if (!contact.isEmpty() && !contact.matches("\\d{11}")) {
            show(Alert.AlertType.WARNING, "Check your input",
                    "Contact number must be exactly 11 digits (e.g. 09171234567).");
            return;
        }
        if (!tin.isEmpty() && !tin.matches("\\d{3}-\\d{3}-\\d{3}(-\\d{3})?")) {
            show(Alert.AlertType.WARNING, "Check your input",
                    "TIN must be 9 or 12 digits, like 000-000-000 or 000-000-000-000.");
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

            SettingsManager.save(values);
            lblSaveNote.setText("Store information saved.");
            setEditing(false);   // lock the fields again
        } catch (SQLException ex) {
            ex.printStackTrace();
            show(Alert.AlertType.ERROR, "Save failed", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ BACKUP

    @FXML
    private void handleBackup() {
        Optional<List<YearMonth>> picked = askMonths();
        if (picked.isEmpty()) return;

        List<YearMonth> months = new ArrayList<>(picked.get());
        months.sort(Comparator.naturalOrder());

        String range = months.size() == 1
                ? months.get(0).toString()
                : months.get(0) + "_to_" + months.get(months.size() - 1);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Transaction History Backup");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));
        chooser.setInitialFileName("RenEspresso_Backup_" + range + ".xlsx");

        File out = chooser.showSaveDialog(btnBackup.getScene().getWindow());
        if (out == null) return; // user cancelled

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                try (Connection conn = getConnection()) {
                    return ExcelBackupUtil.backupToExcel(conn, out, months);
                }
            }
        };

        btnBackup.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        lblStatus.setText("Backing up transaction history...");

        task.setOnSucceeded(e -> {
            done();
            lblStatus.setText("Backup saved: " + out.getAbsolutePath());
            show(Alert.AlertType.INFORMATION, "Backup Complete",
                    "Transaction history exported for " + months.size()
                            + " month(s) to:\n" + out.getAbsolutePath());
        });

        task.setOnFailed(e -> {
            done();
            Throwable ex = task.getException();
            lblStatus.setText("Backup failed.");
            show(Alert.AlertType.ERROR, "Backup Failed",
                    ex == null ? "Unknown error." : ex.getMessage());
        });

        Thread t = new Thread(task, "database-backup");
        t.setDaemon(true);
        t.start();
    }

    /** Dialog: tick one or more months of transactions to back up. */
    private Optional<List<YearMonth>> askMonths() {
        Dialog<List<YearMonth>> dlg = new Dialog<>();
        dlg.setTitle("Backup Transaction History");
        dlg.setHeaderText("Choose the months of transactions to back up");
        dlg.initOwner(settingsRoot.getScene().getWindow());

        // newest month first
        List<YearMonth> months = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = 0; i < MONTHS_SHOWN; i++) months.add(now.minusMonths(i));

        Map<YearMonth, BooleanProperty> selected = new LinkedHashMap<>();
        months.forEach(m -> selected.put(m, new SimpleBooleanProperty(false)));
        selected.get(now).set(true); // current month ticked by default

        ListView<YearMonth> list = new ListView<>(FXCollections.observableArrayList(months));
        list.setPrefHeight(260);
        list.setCellFactory(CheckBoxListCell.forListView(selected::get, new StringConverter<>() {
            @Override public String toString(YearMonth ym) { return ym == null ? "" : ym.format(MONTH_LABEL); }
            @Override public YearMonth fromString(String s) { return null; }
        }));

        Button btnAll = new Button("Select all");
        Button btnNone = new Button("Clear");
        btnAll.setOnAction(e -> selected.values().forEach(p -> p.set(true)));
        btnNone.setOnAction(e -> selected.values().forEach(p -> p.set(false)));

        VBox content = new VBox(10, list, new HBox(8, btnAll, btnNone));
        content.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(content);

        ButtonType go = new ButtonType("Continue", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(go, ButtonType.CANCEL);

        // Continue is disabled until at least one month is ticked
        Node goBtn = dlg.getDialogPane().lookupButton(go);
        goBtn.disableProperty().bind(Bindings.createBooleanBinding(
                () -> selected.values().stream().noneMatch(BooleanProperty::get),
                selected.values().toArray(new Observable[0])));

        dlg.setResultConverter(bt -> {
            if (bt != go) return null;
            return selected.entrySet().stream()
                    .filter(en -> en.getValue().get())
                    .map(Map.Entry::getKey)
                    .toList();
        });

        return dlg.showAndWait();
    }

    private void done() {
        btnBackup.setDisable(false);
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