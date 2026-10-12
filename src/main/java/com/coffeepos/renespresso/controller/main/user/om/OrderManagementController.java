package com.coffeepos.renespresso.controller.main.user.om;

import com.coffeepos.renespresso.controller.main.user.UserSession;
import com.coffeepos.renespresso.controller.main.user.UserUi;
import com.coffeepos.renespresso.model.OrderStatus;
import com.coffeepos.renespresso.model.TransactionRecord;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;

import static com.coffeepos.renespresso.controller.main.user.UserUi.peso;

/**
 * Transaction history for the active cashier session.
 * Columns: ORDER ID, STAFF, CREATED / LAST EDIT, TOTAL, STATUS, PAYMENT, ACTIONS.
 */
public class OrderManagementController {

    @FXML private VBox orderRoot;
    @FXML private TextField txtHistorySearch;
    @FXML private TableView<TransactionRecord> tblHistory;
    @FXML private TableColumn<TransactionRecord, String> colOrderId;
    @FXML private TableColumn<TransactionRecord, Void> colStaff;
    @FXML private TableColumn<TransactionRecord, String> colCreated;
    @FXML private TableColumn<TransactionRecord, String> colTotal;
    @FXML private TableColumn<TransactionRecord, Void> colStatus;
    @FXML private TableColumn<TransactionRecord, Void> colPayment;
    @FXML private TableColumn<TransactionRecord, Void> colActions;
    @FXML private Label lblHistorySummary;

    private final UserSession session = UserSession.get();

    @FXML
    public void initialize() {
        setupColumns();
        setupDataBinding();
    }

    private void setupColumns() {
        // ORDER ID
        colOrderId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getOrderId()));
        colOrderId.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    getStyleClass().add("col-order-id");
                }
            }
        });

        // STAFF (Two lines: Name bold, Role muted)
        colStaff.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    TransactionRecord r = getTableView().getItems().get(getIndex());
                    if (r == null) { setGraphic(null); return; }

                    Label nameLbl = new Label(r.getStaffName());
                    nameLbl.getStyleClass().add("staff-name");

                    Label roleLbl = new Label(r.getStaffRole());
                    roleLbl.getStyleClass().add("staff-role");

                    VBox box = new VBox(2, nameLbl, roleLbl);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                }
            }
        });

        // CREATED / LAST EDIT
        colCreated.setCellValueFactory(c -> Bindings.createStringBinding(
                c.getValue()::getFormattedCreatedOrUpdated, c.getValue().statusProperty()));
        colCreated.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().add("col-created");
                }
            }
        });

        // TOTAL
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTotalPaid()));
        colTotal.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().add("col-total");
                }
            }
        });

        // STATUS (Dot + label badge)
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    TransactionRecord r = getTableView().getItems().get(getIndex());
                    if (r == null) { setGraphic(null); return; }

                    OrderStatus status = r.getStatus();
                    Circle dot = new Circle(4);
                    String styleClass = "status-pill-" + status.name().toLowerCase();
                    Color dotColor = switch (status) {
                        case PREPARING -> Color.web("#D97706");
                        case PENDING -> Color.web("#6B7280");
                        case COMPLETED -> Color.web("#10B981");
                        case CANCELLED -> Color.web("#EF4444");
                    };
                    dot.setFill(dotColor);

                    Label textLbl = new Label(status.label());
                    textLbl.getStyleClass().add("status-text");

                    HBox pill = new HBox(6, dot, textLbl);
                    pill.setAlignment(Pos.CENTER_LEFT);
                    pill.getStyleClass().addAll("status-pill", styleClass);
                    pill.setMaxWidth(Region.USE_PREF_SIZE);

                    setGraphic(pill);
                }
            }
        });

        // PAYMENT (Pill badge: Paid, Unpaid, Refunded)
        colPayment.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    TransactionRecord r = getTableView().getItems().get(getIndex());
                    if (r == null) { setGraphic(null); return; }

                    String pStatus = r.getPaymentStatus();
                    Label pill = new Label(pStatus);
                    pill.getStyleClass().addAll("payment-pill", "payment-" + pStatus.toLowerCase());
                    pill.setMaxWidth(Region.USE_PREF_SIZE);
                    setGraphic(pill);
                }
            }
        });

        // ACTIONS (View Receipt button)
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnReceipt = new Button("View Receipt");
            {
                btnReceipt.getStyleClass().add("btn-action-receipt");
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
    }

    private void setupDataBinding() {
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
        return r.getOrderId().toLowerCase().contains(q)
                || r.getStaffName().toLowerCase().contains(q)
                || r.getStaffRole().toLowerCase().contains(q)
                || r.getPaymentStatus().toLowerCase().contains(q)
                || r.getPaymentMethod().toLowerCase().contains(q)
                || r.getStatus().label().toLowerCase().contains(q)
                || r.getTicketNumber().toLowerCase().contains(q)
                || r.getItems().toLowerCase().contains(q);
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
}