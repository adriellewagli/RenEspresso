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

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static com.coffeepos.renespresso.controller.main.user.UserUi.peso;

/**
 * Transaction history for the cashier session.
 * Columns: ORDER ID, STAFF, CREATED / LAST EDIT, TOTAL, STATUS, PAYMENT, ACTIONS.
 */
public class OrderManagementController {

    @FXML private VBox orderRoot;
    @FXML private TextField txtHistorySearch;
    @FXML private ComboBox<String> cmbDateFilter;
    @FXML private ComboBox<String> cmbStatusFilter;
    @FXML private ComboBox<String> cmbPaymentFilter;
    @FXML private ComboBox<String> cmbSortFilter;
    @FXML private Button btnClearFilters;

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
    private final javafx.collections.ObservableList<TransactionRecord> transactionsList = javafx.collections.FXCollections.observableArrayList();
    private FilteredList<TransactionRecord> filtered;

    @FXML
    public void initialize() {
        setupFilterControls();
        setupColumns();
        setupDataBinding();
        loadTransactions();
        session.transactionHistory.addListener((ListChangeListener<TransactionRecord>) c -> loadTransactions());
    }

    private void setupFilterControls() {
        cmbDateFilter.getItems().setAll("Date: All Dates", "Date: Today", "Date: Yesterday", "Date: This Week", "Date: This Month");
        cmbDateFilter.setValue("Date: All Dates");

        cmbStatusFilter.getItems().setAll("Status: All States", "Status: Preparing", "Status: Completed", "Status: Cancelled");
        cmbStatusFilter.setValue("Status: All States");

        cmbPaymentFilter.getItems().setAll("Payment: All", "Payment: Paid", "Payment: Refunded");
        cmbPaymentFilter.setValue("Payment: All");

        cmbSortFilter.getItems().setAll(
                "Sort: Latest to Oldest",
                "Sort: Oldest to Latest",
                "Sort: Highest Amount",
                "Sort: Lowest Amount"
        );
        cmbSortFilter.setValue("Sort: Latest to Oldest");

        cmbDateFilter.valueProperty().addListener((obs, o, n) -> refreshFilter());
        cmbStatusFilter.valueProperty().addListener((obs, o, n) -> refreshFilter());
        cmbPaymentFilter.valueProperty().addListener((obs, o, n) -> refreshFilter());
        cmbSortFilter.valueProperty().addListener((obs, o, n) -> {
            if (tblHistory != null) tblHistory.getSortOrder().clear();
        });
        txtHistorySearch.textProperty().addListener((obs, o, n) -> refreshFilter());
    }

    private void refreshFilter() {
        if (filtered != null) {
            filtered.setPredicate(this::applyFilters);
        }
    }

    @FXML
    public void handleClearFilters() {
        txtHistorySearch.clear();
        cmbDateFilter.setValue("Date: All Dates");
        cmbStatusFilter.setValue("Status: All States");
        cmbPaymentFilter.setValue("Payment: All");
        cmbSortFilter.setValue("Sort: Latest to Oldest");
        if (tblHistory != null) tblHistory.getSortOrder().clear();
    }

    private boolean applyFilters(TransactionRecord r) {
        if (r == null) return false;

        // Search text
        String q = txtHistorySearch.getText();
        if (q != null && !q.isBlank()) {
            String query = q.trim().toLowerCase();
            boolean matchSearch = (r.getOrderId() != null && r.getOrderId().toLowerCase().contains(query))
                    || (r.getStaffName() != null && r.getStaffName().toLowerCase().contains(query))
                    || (r.getStaffRole() != null && r.getStaffRole().toLowerCase().contains(query))
                    || (r.getPaymentStatus() != null && r.getPaymentStatus().toLowerCase().contains(query))
                    || (r.getPaymentMethod() != null && r.getPaymentMethod().toLowerCase().contains(query))
                    || (r.getStatus() != null && r.getStatus().label() != null && r.getStatus().label().toLowerCase().contains(query))
                    || (r.getTicketNumber() != null && r.getTicketNumber().toLowerCase().contains(query))
                    || (r.getItems() != null && r.getItems().toLowerCase().contains(query));
            if (!matchSearch) return false;
        }

        // Date filter
        String dateOpt = cmbDateFilter.getValue();
        if (dateOpt != null && !dateOpt.equals("Date: All Dates")) {
            if (r.getCreatedAt() == null) return false;
            LocalDate recordDate = r.getCreatedAt().toLocalDate();
            LocalDate today = LocalDate.now();
            if (dateOpt.equals("Date: Today")) {
                if (!recordDate.equals(today)) return false;
            } else if (dateOpt.equals("Date: Yesterday")) {
                if (!recordDate.equals(today.minusDays(1))) return false;
            } else if (dateOpt.equals("Date: This Week")) {
                LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
                if (recordDate.isBefore(startOfWeek)) return false;
            } else if (dateOpt.equals("Date: This Month")) {
                if (recordDate.getMonth() != today.getMonth() || recordDate.getYear() != today.getYear()) return false;
            }
        }

        // Status filter
        String statusOpt = cmbStatusFilter.getValue();
        if (statusOpt != null && !statusOpt.equals("Status: All States")) {
            String expected = statusOpt.replace("Status: ", "").trim();
            if (r.getStatus() == null || r.getStatus().label() == null || !r.getStatus().label().equalsIgnoreCase(expected)) return false;
        }

        // Payment filter
        String paymentOpt = cmbPaymentFilter.getValue();
        if (paymentOpt != null && !paymentOpt.equals("Payment: All")) {
            String expected = paymentOpt.replace("Payment: ", "").trim();
            if (r.getPaymentStatus() == null || !r.getPaymentStatus().equalsIgnoreCase(expected)) return false;
        }

        return true;
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

                    String staffName = r.getStaffName();
                    if (staffName == null || staffName.isBlank()) {
                        staffName = "Former Staff";
                    }

                    Label nameLbl = new Label(staffName);
                    nameLbl.getStyleClass().add("staff-name");

                    String role = r.getStaffRole();
                    if (role == null || role.isBlank()) {
                        role = "Staff";
                    }

                    Label roleLbl = new Label(role);
                    roleLbl.getStyleClass().add("staff-role");
                    if ("Former Staff".equalsIgnoreCase(role)) {
                        roleLbl.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 11px; -fx-font-style: italic;");
                    }

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
                    pill.setMinHeight(22);
                    pill.setPrefHeight(22);
                    pill.setMaxHeight(22);
                    pill.setMaxWidth(Region.USE_PREF_SIZE);

                    HBox wrapper = new HBox(pill);
                    wrapper.setFillHeight(false);
                    wrapper.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(wrapper);
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
                    pill.setMinHeight(22);
                    pill.setPrefHeight(22);
                    pill.setMaxHeight(22);
                    pill.setMaxWidth(Region.USE_PREF_SIZE);

                    HBox wrapper = new HBox(pill);
                    wrapper.setFillHeight(false);
                    wrapper.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(wrapper);
                }
            }
        });

        // ACTIONS (View Receipt button)
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnReceipt = new Button("View Receipt");
            {
                btnReceipt.getStyleClass().add("btn-action-receipt");
                btnReceipt.setMinHeight(26);
                btnReceipt.setPrefHeight(26);
                btnReceipt.setMaxHeight(26);
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
                    HBox wrapper = new HBox(btnReceipt);
                    wrapper.setFillHeight(false);
                    wrapper.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(wrapper);
                }
            }
        });
    }

    private void setupDataBinding() {
        filtered = new FilteredList<>(transactionsList, this::applyFilters);
        filtered.addListener((ListChangeListener<TransactionRecord>) c -> updateSummary(filtered));

        SortedList<TransactionRecord> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(Bindings.createObjectBinding(() -> {
            Comparator<TransactionRecord> tableComp = tblHistory.getComparator();
            if (tableComp != null) {
                return tableComp;
            }
            return getSortFilterComparator();
        }, tblHistory.comparatorProperty(), cmbSortFilter.valueProperty()));
        tblHistory.setItems(sorted);

        Label placeholder = new Label("No transactions match the selected filters.");
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

    @FXML
    public void loadTransactions() {
        javafx.concurrent.Task<List<TransactionRecord>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<TransactionRecord> call() {
                return com.coffeepos.renespresso.dao.TransactionDAO.loadAllTransactions();
            }
        };

        task.setOnSucceeded(e -> {
            List<TransactionRecord> list = task.getValue();
            transactionsList.setAll(list);
            updateSummary(filtered != null ? filtered : transactionsList);
        });

        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            lblHistorySummary.setText("Failed to load transactions: " + task.getException().getMessage());
        });

        Thread t = new Thread(task, "cashier-transaction-loader");
        t.setDaemon(true);
        t.start();
    }

    private Comparator<TransactionRecord> getSortFilterComparator() {
        String val = cmbSortFilter != null ? cmbSortFilter.getValue() : null;
        if (val == null || val.contains("Latest to Oldest")) {
            return Comparator.comparing(
                    TransactionRecord::getEffectiveDateTime,
                    Comparator.nullsLast(Comparator.reverseOrder())
            ).thenComparing(TransactionRecord::getTransactionId, Comparator.reverseOrder());
        } else if (val.contains("Oldest to Latest")) {
            return Comparator.comparing(
                    TransactionRecord::getEffectiveDateTime,
                    Comparator.nullsLast(Comparator.naturalOrder())
            ).thenComparing(TransactionRecord::getTransactionId, Comparator.naturalOrder());
        } else if (val.contains("Highest Amount")) {
            return Comparator.comparingDouble(TransactionRecord::getTotal).reversed()
                    .thenComparing(TransactionRecord::getTransactionId, Comparator.reverseOrder());
        } else if (val.contains("Lowest Amount")) {
            return Comparator.comparingDouble(TransactionRecord::getTotal)
                    .thenComparing(TransactionRecord::getTransactionId, Comparator.naturalOrder());
        }
        return Comparator.comparing(
                TransactionRecord::getEffectiveDateTime,
                Comparator.nullsLast(Comparator.reverseOrder())
        );
    }

    private void updateSummary(List<TransactionRecord> visible) {
        if (visible.isEmpty()) {
            lblHistorySummary.setText("No transactions match filters");
            return;
        }
        double sum = visible.stream()
                .filter(r -> r.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(TransactionRecord::getTotal).sum();
        lblHistorySummary.setText(visible.size() + " transaction(s)  \u2022  Total " + peso(sum) + " (cancelled excluded)");
    }
}