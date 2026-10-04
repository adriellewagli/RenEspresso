package com.coffeepos.renespresso.controller.main.user.om;

import com.coffeepos.renespresso.controller.main.user.TransactionRecord;
import com.coffeepos.renespresso.controller.main.user.UserSession;
import com.coffeepos.renespresso.controller.main.user.UserUi;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.coffeepos.renespresso.controller.main.user.UserUi.peso;

/** Transaction history for the active cashier session. */
public class OrderManagementController {

    private static final String CSS = "/com/coffeepos/renespresso/views/styles.user/om/ordermanagement.css";

    @FXML private VBox orderRoot;
    @FXML private TextField txtHistorySearch;
    @FXML private TableView<TransactionRecord> tblHistory;
    @FXML private Label lblHistorySummary;

    private final UserSession session = UserSession.get();

    @FXML
    @SuppressWarnings("unchecked")
    public void initialize() {
        UserUi.attachCss(orderRoot, CSS);

        TableColumn<TransactionRecord, String> colTicket = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(0);
        TableColumn<TransactionRecord, String> colTime = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(1);
        TableColumn<TransactionRecord, String> colItems = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(2);
        TableColumn<TransactionRecord, String> colDiscount = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(3);
        TableColumn<TransactionRecord, String> colTotal = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(4);
        TableColumn<TransactionRecord, String> colPayment = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(5);

        colTicket.setCellValueFactory(new PropertyValueFactory<>("ticketNumber"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        colItems.setCellValueFactory(new PropertyValueFactory<>("items"));
        colDiscount.setCellValueFactory(new PropertyValueFactory<>("discountApplied"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalPaid"));
        colPayment.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));

        List.of(colTicket, colTime, colItems, colDiscount, colTotal, colPayment)
                .forEach(col -> col.setStyle("-fx-alignment: CENTER;"));

        FilteredList<TransactionRecord> filtered = new FilteredList<>(session.transactionHistory, r -> true);
        txtHistorySearch.textProperty().addListener((obs, o, n) -> filtered.setPredicate(r -> matches(r, n)));
        filtered.addListener((ListChangeListener<TransactionRecord>) c -> updateSummary(filtered));

        SortedList<TransactionRecord> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tblHistory.comparatorProperty());
        tblHistory.setItems(sorted);

        tblHistory.setPlaceholder(new Label("No transactions recorded for this session yet."));
        tblHistory.setRowFactory(tv -> {
            TableRow<TransactionRecord> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) UserUi.showReceiptDialog(row.getItem());
            });
            return row;
        });
        updateSummary(filtered);
    }

    private boolean matches(TransactionRecord r, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase();
        return r.getTicketNumber().toLowerCase().contains(q)
                || r.getTime().toLowerCase().contains(q)
                || r.getItems().toLowerCase().contains(q)
                || r.getDiscountApplied().toLowerCase().contains(q)
                || r.getPaymentMethod().toLowerCase().contains(q);
    }

    private void updateSummary(List<TransactionRecord> visible) {
        if (visible.isEmpty()) {
            lblHistorySummary.setText("No transactions yet");
            return;
        }
        double sum = visible.stream().mapToDouble(TransactionRecord::getTotal).sum();
        lblHistorySummary.setText(visible.size() + " transaction(s)  •  Total " + peso(sum));
    }

    @FXML
    public void handleReprintReceipt() {
        TransactionRecord selected = tblHistory.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UserUi.showInfo(Alert.AlertType.INFORMATION, "No Selection", "Select a transaction first (or double-click a row).");
            return;
        }
        UserUi.showReceiptDialog(selected);
    }

    @FXML
    public void handleExportHistory() {
        if (session.transactionHistory.isEmpty()) {
            UserUi.showInfo(Alert.AlertType.INFORMATION, "Nothing to Export", "There are no transactions in this session yet.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Transaction History");
        chooser.setInitialFileName("transactions_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(orderRoot.getScene().getWindow());
        if (file == null) return;

        List<String> lines = new ArrayList<>();
        lines.add("Ticket,Time,Items,Discount,Payment,Total");
        for (TransactionRecord r : tblHistory.getItems()) {
            lines.add(String.join(",",
                    csv(r.getTicketNumber()), csv(r.getTime()), csv(r.getItems()),
                    csv(r.getDiscountApplied()), csv(r.getPaymentMethod()), String.format("%.2f", r.getTotal())));
        }
        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            UserUi.showInfo(Alert.AlertType.INFORMATION, "Export Complete",
                    "Saved " + tblHistory.getItems().size() + " transaction(s) to:\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            UserUi.showInfo(Alert.AlertType.ERROR, "Export Failed", ex.getMessage());
        }
    }

    private String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }
}