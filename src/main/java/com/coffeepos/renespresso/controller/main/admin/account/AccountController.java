package com.coffeepos.renespresso.controller.main.admin.account;

import com.coffeepos.renespresso.model.AccountRow;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class AccountController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> roleFilter, statusFilter, lastLoginFilter;
    @FXML private Button clearFiltersBtn, addAccountBtn;

    @FXML private TableView<AccountRow> accountTable;
    @FXML private TableColumn<AccountRow, String> colName, colUsername, colRole, colStatus, colLastLogin, colCreated;
    @FXML private TableColumn<AccountRow, AccountRow> colActions;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);

    private final ObservableList<AccountRow> accounts = FXCollections.observableArrayList();
    private FilteredList<AccountRow> filtered;

    @FXML
    public void initialize() {
        setupFilter(roleFilter, "Role", "All Roles", "Admin", "Manager", "Cashier", "Server", "Chef");
        setupFilter(statusFilter, "Status", "All Statuses", "Active", "Inactive", "Suspended");
        setupFilter(lastLoginFilter, "Last Login", "Anytime", "Today", "Last 7 days", "Last 30 days");

        setupColumns();
        loadAccounts();

        filtered = new FilteredList<>(accounts, r -> true);
        SortedList<AccountRow> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(accountTable.comparatorProperty());
        accountTable.setItems(sorted);
        accountTable.setPlaceholder(new Label("No accounts match your filters"));

        searchField.textProperty().addListener((o, a, b) -> applyFilters());
        roleFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        statusFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        lastLoginFilter.valueProperty().addListener((o, a, b) -> applyFilters());
    }

    // ------------------------------------------------------------------ DATA

    /** TODO: replace the sample data with a query against your users table. */
    private void loadAccounts() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        accounts.setAll(
                new AccountRow(1,  "Juan dela Cruz",  "@juan_manager",  "juan@renespresso.com",   "Manager", "Active",    today.atTime(10, 15),              LocalDate.of(2026, 1, 12)),
                new AccountRow(2,  "Maria Santos",    "@maria_cashier", "maria@renespresso.com",  "Cashier", "Active",    today.atTime(12, 35),              LocalDate.of(2026, 2, 5)),
                new AccountRow(3,  "Server Maria",    "@maria_serv",    "mserv@renespresso.com",  "Server",  "Active",    today.atTime(12, 42),              LocalDate.of(2026, 2, 10)),
                new AccountRow(4,  "Chef Juan",       "@chef_juan",     "chef@renespresso.com",   "Chef",    "Active",    today.minusDays(1).atTime(21, 30), LocalDate.of(2026, 1, 15)),
                new AccountRow(5,  "Andres Bonifacio","@andres_admin",  "andres@renespresso.com", "Admin",   "Inactive",  now.minusDays(3),                  LocalDate.of(2025, 9, 21)),
                new AccountRow(6,  "Cashier Ken",     "@ken_cash",      "ken@renespresso.com",    "Cashier", "Suspended", now.minusDays(7),                  LocalDate.of(2026, 3, 1)),
                new AccountRow(7,  "Server Paolo",    "@paolo_serv",    "paolo@renespresso.com",  "Server",  "Active",    today.minusDays(1).atTime(18, 15), LocalDate.of(2026, 2, 18))
        );
    }

    // ------------------------------------------------------------------ FILTERS

    /** Combo box whose closed state reads "Role:  All Roles" like the design. */
    private void setupFilter(ComboBox<String> box, String label, String... items) {
        box.setItems(FXCollections.observableArrayList(items));
        box.getSelectionModel().selectFirst();
        box.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(label + ":");
                name.getStyleClass().add("filter-label");
                Label value = new Label(item);
                value.getStyleClass().add("filter-value");
                HBox box = new HBox(5, name, value);
                box.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(box);
            }
        });
    }

    private void applyFilters() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String role = roleFilter.getValue();
        String status = statusFilter.getValue();
        String login = lastLoginFilter.getValue();

        filtered.setPredicate(r -> {
            if (!q.isEmpty()
                    && !r.name().toLowerCase().contains(q)
                    && !r.username().toLowerCase().contains(q)
                    && !r.email().toLowerCase().contains(q)) return false;

            if (role != null && !role.equals("All Roles") && !r.role().equals(role)) return false;
            if (status != null && !status.equals("All Statuses") && !r.status().equals(status)) return false;
            return matchesLastLogin(r.lastLogin(), login);
        });
    }

    private boolean matchesLastLogin(LocalDateTime last, String option) {
        if (option == null || option.equals("Anytime")) return true;
        if (last == null) return false;
        long days = ChronoUnit.DAYS.between(last.toLocalDate(), LocalDate.now());
        return switch (option) {
            case "Today" -> days == 0;
            case "Last 7 days" -> days <= 7;
            case "Last 30 days" -> days <= 30;
            default -> true;
        };
    }

    @FXML
    public void handleAddAccount() {
        // TODO: open your add-account dialog
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "The add account dialog is not built yet.");
        alert.setHeaderText("Add Account");
        alert.showAndWait();
    }

    @FXML
    public void handleClearFilters() {
        searchField.clear();
        roleFilter.getSelectionModel().selectFirst();
        statusFilter.getSelectionModel().selectFirst();
        lastLoginFilter.getSelectionModel().selectFirst();
    }

    // ------------------------------------------------------------------ TABLE

    private void setupColumns() {
        colName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        colUsername.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().username()));
        colRole.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().role()));
        colStatus.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().status()));
        colLastLogin.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatLastLogin(c.getValue().lastLogin())));
        colCreated.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().created().format(DATE_FMT)));
        colActions.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));

        colName.setCellFactory(c -> textCell("name-cell"));
        colUsername.setCellFactory(c -> textCell("muted-cell"));
        colRole.setCellFactory(c -> rolePillCell());
        colStatus.setCellFactory(c -> statusPillCell());
        colLastLogin.setCellFactory(c -> textCell("muted-cell"));
        colCreated.setCellFactory(c -> textCell("muted-cell"));
        colActions.setCellFactory(c -> actionsCell());

        colLastLogin.setSortable(false);
        colCreated.setSortable(false);
        colActions.setSortable(false);
    }

    private TableCell<AccountRow, String> textCell(String styleClass) {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove(styleClass);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().add(styleClass);
                }
            }
        };
    }

    private TableCell<AccountRow, String> rolePillCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                setText(null);
                if (empty || role == null) {
                    setGraphic(null);
                    return;
                }
                Label pill = new Label(role);
                pill.getStyleClass().addAll("pill", "role-" + role.toLowerCase());
                setGraphic(pill);
            }
        };
    }

    private TableCell<AccountRow, String> statusPillCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                setText(null);
                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }
                Circle dot = new Circle(3);
                dot.getStyleClass().add("status-dot");
                Label pill = new Label(status, dot);
                pill.setGraphicTextGap(5);
                pill.getStyleClass().addAll("pill", "status-" + status.toLowerCase());
                setGraphic(pill);
            }
        };
    }

    private TableCell<AccountRow, AccountRow> actionsCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(AccountRow row, boolean empty) {
                super.updateItem(row, empty);
                setText(null);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                Button edit = iconButton("icon-edit", "action-edit");
                edit.setOnAction(e -> handleEdit(row));
                Button delete = iconButton("icon-delete", "action-delete");
                delete.setOnAction(e -> handleDelete(row));

                HBox box = new HBox(8, edit, delete);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        };
    }

    private Button iconButton(String iconClass, String buttonClass) {
        Region icon = new Region();
        icon.getStyleClass().add(iconClass);
        Button b = new Button();
        b.setGraphic(icon);
        b.getStyleClass().addAll("action-button", buttonClass);
        return b;
    }

    // ------------------------------------------------------------------ ACTIONS

    private void handleEdit(AccountRow row) {
        // TODO: open your edit-account dialog
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Edit dialog for " + row.name() + " is not built yet.");
        alert.setHeaderText("Edit Account");
        alert.showAndWait();
    }

    private void handleDelete(AccountRow row) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the account for " + row.name() + "? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Delete Account");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // TODO: delete from the database first, then remove from the list
                accounts.remove(row);
            }
        });
    }

    // ------------------------------------------------------------------ FORMAT

    private String formatLastLogin(LocalDateTime t) {
        if (t == null) return "Never";
        long days = ChronoUnit.DAYS.between(t.toLocalDate(), LocalDate.now());
        String time = t.format(TIME_FMT);
        if (days <= 0) return "Today, " + time;
        if (days == 1) return "Yesterday, " + time;
        if (days < 7) return days + " days ago";
        if (days < 14) return "1 week ago";
        if (days < 30) return (days / 7) + " weeks ago";
        return t.format(DATE_FMT);
    }
}