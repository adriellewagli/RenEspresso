package com.coffeepos.renespresso.controller.main.admin.transact;

import com.coffeepos.renespresso.controller.main.user.UserUi;
import com.coffeepos.renespresso.dao.TransactionDAO;
import com.coffeepos.renespresso.model.OrderStatus;
import com.coffeepos.renespresso.model.TransactionRecord;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.util.List;

import static com.coffeepos.renespresso.controller.main.user.UserUi.peso;

public class TransactionController {

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbDateFilter;
    @FXML private ComboBox<String> cmbStatusFilter;
    @FXML private ComboBox<String> cmbPaymentFilter;
    @FXML private Button btnClearFilters;

    @FXML private Label lblSummary;
    @FXML private TableView<TransactionRecord> tblTransactions;
    @FXML private TableColumn<TransactionRecord, String> colOrderId;
    @FXML private TableColumn<TransactionRecord, Void> colStaff;
    @FXML private TableColumn<TransactionRecord, String> colCreated;
    @FXML private TableColumn<TransactionRecord, String> colTotal;
    @FXML private TableColumn<TransactionRecord, Void> colStatus;
    @FXML private TableColumn<TransactionRecord, Void> colPayment;
    @FXML private TableColumn<TransactionRecord, Void> colActions;

    /**
     * Optional: if you add <HBox fx:id="pagerBox"/> under the table in the FXML it will be used.
     * If it is missing, the pager is created in code and placed right under the table.
     */
    @FXML private HBox pagerBox;

    // Every row is exactly this tall, so the number of rows that fit on screen is predictable
    private static final double ROW_HEIGHT = 56.0;

    private final ObservableList<TransactionRecord> transactionsList = FXCollections.observableArrayList();
    private final ObservableList<TransactionRecord> pageItems = FXCollections.observableArrayList();
    private FilteredList<TransactionRecord> filteredList;
    private SortedList<TransactionRecord> sortedList;

    private int currentPage = 0;
    private int rowsPerPage = 8;

    @FXML
    public void initialize() {
        setupFilterControls();
        setupColumns();
        setupDataBinding();
        setupPagination();
        loadTransactions();
    }

    // ------------------------------------------------------------------ FILTERS

    private void setupFilterControls() {
        cmbDateFilter.getItems().setAll("Date: All Dates", "Date: Today", "Date: Yesterday", "Date: This Week", "Date: This Month");
        cmbDateFilter.setValue("Date: All Dates");

        cmbStatusFilter.getItems().setAll("Status: All States", "Status: Preparing", "Status: Pending", "Status: Completed", "Status: Cancelled");
        cmbStatusFilter.setValue("Status: All States");

        cmbPaymentFilter.getItems().setAll("Payment: All", "Payment: Paid", "Payment: Unpaid", "Payment: Refunded");
        cmbPaymentFilter.setValue("Payment: All");

        cmbDateFilter.valueProperty().addListener((obs, o, n) -> refreshFilter());
        cmbStatusFilter.valueProperty().addListener((obs, o, n) -> refreshFilter());
        cmbPaymentFilter.valueProperty().addListener((obs, o, n) -> refreshFilter());
        txtSearch.textProperty().addListener((obs, o, n) -> refreshFilter());
    }

    private void refreshFilter() {
        if (filteredList != null) {
            currentPage = 0; // a new filter always starts from page 1
            filteredList.setPredicate(this::applyFilters);
        }
    }

    @FXML
    public void handleClearFilters() {
        txtSearch.clear();
        cmbDateFilter.setValue("Date: All Dates");
        cmbStatusFilter.setValue("Status: All States");
        cmbPaymentFilter.setValue("Payment: All");
    }

    private boolean applyFilters(TransactionRecord r) {
        if (r == null) return false;

        // Search text
        String q = txtSearch.getText();
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

    // ------------------------------------------------------------------ COLUMNS

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

        // STAFF
        // The whole block sits in the middle of the column, so the NAME is centered.
        // Inside the block the ROLE is left-aligned, so it starts under the first letter of the name.
        colStaff.setCellFactory(col -> new TableCell<>() {
            {
                // inline style beats any "-fx-alignment: center-left" coming from the stylesheet
                setStyle("-fx-alignment: CENTER;");
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    TransactionRecord r = getTableView().getItems().get(getIndex());
                    if (r == null) { setGraphic(null); return; }

                    Label nameLbl = new Label(r.getStaffName());
                    nameLbl.getStyleClass().add("staff-name");

                    // Wrapper stretches across the whole cell and centers the name in it
                    HBox wrapper = new HBox(nameLbl);
                    wrapper.setAlignment(Pos.CENTER);
                    wrapper.prefWidthProperty().bind(Bindings.createDoubleBinding(
                            () -> Math.max(0, getWidth() - getInsets().getLeft() - getInsets().getRight()),
                            widthProperty(), insetsProperty()));
                    wrapper.setMinWidth(0);
                    setGraphic(wrapper);
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
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
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
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
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
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
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

    // ------------------------------------------------------------------ DATA + PAGING

    private void setupDataBinding() {
        filteredList = new FilteredList<>(transactionsList, this::applyFilters);
        filteredList.addListener((ListChangeListener<TransactionRecord>) c -> updateSummary(filteredList));

        // Sorting still follows the column headers, but is applied to the WHOLE list
        // (all pages), then we cut out the rows for the current page.
        sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tblTransactions.comparatorProperty());
        tblTransactions.setSortPolicy(tv -> true);   // the table must not re-sort just the current page
        sortedList.addListener((ListChangeListener<TransactionRecord>) c -> refreshPage());

        tblTransactions.setItems(pageItems);
        tblTransactions.setFixedCellSize(ROW_HEIGHT);

        Label placeholder = new Label("No transactions match the selected filters.");
        placeholder.getStyleClass().add("empty-note");
        tblTransactions.setPlaceholder(placeholder);
        tblTransactions.setRowFactory(tv -> {
            TableRow<TransactionRecord> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) UserUi.showReceiptDialog(row.getItem());
            });
            return row;
        });
    }

    private void setupPagination() {
        if (pagerBox == null) {
            pagerBox = new HBox();
            Parent parent = tblTransactions.getParent();
            if (parent instanceof VBox vbox) {
                int idx = vbox.getChildren().indexOf(tblTransactions);
                vbox.getChildren().add(idx + 1, pagerBox);
            } else if (parent instanceof Pane pane) {
                pane.getChildren().add(pagerBox);
            }
        }
        pagerBox.setAlignment(Pos.CENTER);
        pagerBox.setSpacing(6);
        pagerBox.setPadding(new Insets(12, 0, 4, 0));
        pagerBox.setMinHeight(52);
        pagerBox.setPrefHeight(52);
        pagerBox.setMaxHeight(52);

        // Work out how many rows fit, so no scrollbar is ever needed
        tblTransactions.heightProperty().addListener((obs, o, n) -> recalcRowsPerPage());
        tblTransactions.skinProperty().addListener((obs, o, n) -> Platform.runLater(this::recalcRowsPerPage));
        Platform.runLater(this::recalcRowsPerPage);

        refreshPage();
    }

    private void recalcRowsPerPage() {
        double tableHeight = tblTransactions.getHeight();
        if (tableHeight <= 0) return;

        double headerHeight = 45;
        Node header = tblTransactions.lookup(".column-header-background");
        if (header instanceof Region r && r.getHeight() > 0) headerHeight = r.getHeight();

        int rows = (int) Math.floor((tableHeight - headerHeight - 4) / ROW_HEIGHT);
        rows = Math.max(1, Math.min(rows, 100));

        if (rows != rowsPerPage) {
            rowsPerPage = rows;
            refreshPage();
        }
    }

    private int pageCount() {
        return Math.max(1, (int) Math.ceil(sortedList.size() / (double) rowsPerPage));
    }

    private void refreshPage() {
        if (sortedList == null) return;

        int pages = pageCount();
        if (currentPage > pages - 1) currentPage = pages - 1;
        if (currentPage < 0) currentPage = 0;

        int from = currentPage * rowsPerPage;
        int to = Math.min(sortedList.size(), from + rowsPerPage);
        pageItems.setAll(from < to ? sortedList.subList(from, to) : List.of());

        rebuildPager(pages);
    }

    private void goToPage(int page) {
        currentPage = page;
        refreshPage();
    }

    private void rebuildPager(int pages) {
        if (pagerBox == null) return;
        pagerBox.getChildren().clear();

        pagerBox.getChildren().add(pagerButton("\u2039", false, currentPage > 0, () -> goToPage(currentPage - 1)));

        int last = pages - 1;
        int prevShown = -1;
        for (int i = 0; i < pages; i++) {
            boolean show = pages <= 7
                    || i == 0 || i == last
                    || Math.abs(i - currentPage) <= 1
                    || (currentPage <= 2 && i <= 3)
                    || (currentPage >= last - 2 && i >= last - 3);
            if (!show) continue;

            if (prevShown != -1 && i - prevShown > 1) {
                Label dots = new Label("\u2026");
                dots.setStyle("-fx-text-fill: #895737; -fx-font-weight: bold; -fx-padding: 0 4 0 4;");
                pagerBox.getChildren().add(dots);
            }

            final int target = i;
            pagerBox.getChildren().add(pagerButton(String.valueOf(i + 1), i == currentPage, true, () -> goToPage(target)));
            prevShown = i;
        }

        pagerBox.getChildren().add(pagerButton("\u203A", false, currentPage < last, () -> goToPage(currentPage + 1)));
    }

    private Button pagerButton(String text, boolean active, boolean enabled, Runnable action) {
        Button b = new Button(text);
        b.setMinSize(34, 34);
        b.setPrefSize(34, 34);
        b.setMaxSize(34, 34);
        b.setFocusTraversable(false);
        b.getStyleClass().add(active ? "pager-btn-active" : "pager-btn");

        final String base = "-fx-background-radius: 8px; -fx-border-radius: 8px; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 0; -fx-cursor: hand;";
        final String normal = base + "-fx-background-color: transparent; -fx-border-color: #E3D9CD; -fx-text-fill: #5E3023;";
        final String hover  = base + "-fx-background-color: #F3E9DC; -fx-border-color: #C08552; -fx-text-fill: #5E3023;";
        final String on     = base + "-fx-background-color: #5E3023; -fx-border-color: #5E3023; -fx-text-fill: #F3E9DC;";

        b.setStyle(active ? on : normal);
        if (!active) {
            b.setOnMouseEntered(e -> b.setStyle(hover));
            b.setOnMouseExited(e -> b.setStyle(normal));
        }
        b.setDisable(!enabled);
        b.setOnAction(e -> action.run());
        return b;
    }

    @FXML
    public void loadTransactions() {
        Task<List<TransactionRecord>> task = new Task<>() {
            @Override
            protected List<TransactionRecord> call() {
                return TransactionDAO.loadAllTransactions();
            }
        };

        task.setOnSucceeded(e -> {
            List<TransactionRecord> list = task.getValue();
            currentPage = 0;
            transactionsList.setAll(list);
            updateSummary(filteredList != null ? filteredList : transactionsList);
            refreshPage();
        });

        task.setOnFailed(e -> {
            task.getException().printStackTrace();
            lblSummary.setText("Failed to load transactions: " + task.getException().getMessage());
        });

        Thread t = new Thread(task, "admin-transaction-loader");
        t.setDaemon(true);
        t.start();
    }

    private void updateSummary(List<TransactionRecord> visible) {
        if (visible.isEmpty()) {
            lblSummary.setText("No transactions match filters");
            return;
        }
        double sum = visible.stream()
                .filter(r -> r.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(TransactionRecord::getTotal).sum();
        lblSummary.setText(visible.size() + " transaction(s)  \u2022  Total " + peso(sum) + " (cancelled excluded)");
    }
}