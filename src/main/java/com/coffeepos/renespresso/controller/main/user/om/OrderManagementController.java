package com.coffeepos.renespresso.controller.main.user.om;

import com.coffeepos.renespresso.controller.main.user.UserSession;
import com.coffeepos.renespresso.controller.main.user.UserUi;
import com.coffeepos.renespresso.model.OrderStatus;
import com.coffeepos.renespresso.model.TransactionRecord;
import com.coffeepos.renespresso.util.AlertUtil;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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

/** Transaction history for the active cashier session (columns: Ticket, Time, Items, Discount, Payment, Total, Status). */
public class OrderManagementController {

    @FXML private VBox orderRoot;
    @FXML private TextField txtHistorySearch;
    @FXML private TableView<TransactionRecord> tblHistory;
    @FXML private Label lblHistorySummary;

    private final UserSession session = UserSession.get();

    @FXML
    @SuppressWarnings("unchecked")
    public void initialize() {
        TableColumn<TransactionRecord, String> colTicket = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(0);
        TableColumn<TransactionRecord, String> colTime = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(1);
        TableColumn<TransactionRecord, String> colItems = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(2);
        TableColumn<TransactionRecord, String> colDiscount = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(3);
        TableColumn<TransactionRecord, String> colPayment = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(4);
        TableColumn<TransactionRecord, String> colTotal = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(5);
        TableColumn<TransactionRecord, String> colStatus = (TableColumn<TransactionRecord, String>) tblHistory.getColumns().get(6);
        TableColumn<TransactionRecord, Void> colAction = (TableColumn<TransactionRecord, Void>) tblHistory.getColumns().get(7);

        colTicket.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTicketNumber()));
        colTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTime()));
        colItems.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItems()));
        colDiscount.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDiscountApplied()));
        colPayment.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPaymentMethod()));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTotalPaid()));
        colStatus.setCellValueFactory(c -> Bindings.createStringBinding(
                () -> c.getValue().getStatus().label(), c.getValue().statusProperty()));

        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnReceipt = new Button("View Receipt");
            {
                btnReceipt.getStyleClass().add("btn-secondary-sm");
                btnReceipt.setOnAction(e -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        TransactionRecord record = getTableView().getItems().get(getIndex());
                        if (record != null) {
                            UserUi.showReceiptDialog(record);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    setGraphic(btnReceipt);
                }
            }
        });

        FilteredList<TransactionRecord> filtered = new FilteredList<>(session.transactionHistory, r -> true);
        txtHistorySearch.textProperty().addListener((obs, o, n) -> filtered.setPredicate(r -> matches(r, n)));
        filtered.addListener((ListChangeListener<TransactionRecord>) c -> updateSummary(filtered));

        SortedList<TransactionRecord> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tblHistory.comparatorProperty());
        tblHistory.setItems(sorted);

        Label placeholder = new Label("No transactions recorded for this session yet.");
        placeholder.getStyleClass().add("empty-note");
        tblHistory.setPlaceholder(placeholder);
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
                || r.getPaymentMethod().toLowerCase().contains(q)
                || r.getStatus().label().toLowerCase().contains(q);
    }

    private void updateSummary(List<TransactionRecord> visible) {
        if (visible.isEmpty()) {
            lblHistorySummary.setText("No transactions yet");
            return;
        }
        double sum = visible.stream()
                .filter(r -> r.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(TransactionRecord::getTotal).sum();
        lblHistorySummary.setText(visible.size() + " transaction(s)  \u2022  Total " + peso(sum) + " (cancelled excluded)");
    }

    @FXML
    public void handleReprintReceipt() {
        TransactionRecord selected = tblHistory.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.showInfo("No Selection", "Select a transaction first (or double-click a row).");
            return;
        }
        UserUi.showReceiptDialog(selected);
    }

    @FXML
    public void handleExportHistory() {
        if (session.transactionHistory.isEmpty()) {
            AlertUtil.showInfo("Nothing to Export", "There are no transactions in this session yet.");
                return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Transaction History");
        chooser.setInitialFileName("transactions_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(orderRoot.getScene().getWindow());
        if (file == null) return;

        List<String> lines = new ArrayList<>();
        lines.add("Ticket,Time,Items,Discount,Payment,Total,Status");
        for (TransactionRecord r : tblHistory.getItems()) {
            lines.add(String.join(",",
                    csv(r.getTicketNumber()), csv(r.getTime()), csv(r.getItems()),
                    csv(r.getDiscountApplied()), csv(r.getPaymentMethod()),
                    String.format("%.2f", r.getTotal()), csv(r.getStatus().label())));
        }
        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            AlertUtil.showSuccess("Export Complete",
                    "Saved " + tblHistory.getItems().size() + " transaction(s) to:\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            AlertUtil.showError("Export Failed", ex.getMessage());
        }
    }

    private String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }
}