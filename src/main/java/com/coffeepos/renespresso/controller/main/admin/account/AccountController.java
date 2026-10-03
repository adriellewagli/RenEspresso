package com.coffeepos.renespresso.controller.main.admin.account;

import com.coffeepos.renespresso.dao.UserDAO;
import com.coffeepos.renespresso.model.AccountRow;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import com.coffeepos.renespresso.util.AlertUtil;
import javafx.application.Platform;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
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
    private static final String[] ROLES = {"Admin", "Manager", "Supervisor", "Cashier"};

    private final ObservableList<AccountRow> accounts = FXCollections.observableArrayList();
    private FilteredList<AccountRow> filtered;

    /** Optional: call from the dashboard after login so users can't delete/deactivate themselves. */
    private int currentUserId = -1;
    public void setCurrentUserId(int id) { this.currentUserId = id; }

    @FXML
    public void initialize() {
        setupFilter(roleFilter, "Role", "All Roles", ROLES);
        setupFilter(statusFilter, "Status", "All Statuses", "Active", "Inactive");
        setupFilter(lastLoginFilter, "Last Login", "Anytime", "Today", "Last 7 days", "Last 30 days");

        setupColumns();

        filtered = new FilteredList<>(accounts, r -> true);
        SortedList<AccountRow> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(accountTable.comparatorProperty());
        accountTable.setItems(sorted);
        accountTable.setPlaceholder(new Label("No accounts match your filters"));

        searchField.textProperty().addListener((o, a, b) -> applyFilters());
        roleFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        statusFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        lastLoginFilter.valueProperty().addListener((o, a, b) -> applyFilters());

        loadAccounts();
    }

    // ------------------------------------------------------------------ DATA

    public void loadAccounts() {
        try {
            accounts.setAll(UserDAO.getAllAccounts());
        } catch (SQLException e) {
            e.printStackTrace();
            Platform.runLater(() -> AlertUtil.showError("Could not load accounts", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ FILTERS

    /** Combo box whose closed state reads "Role:  All Roles" like the design. */
    private void setupFilter(ComboBox<String> box, String label, String allLabel, String... items) {
        String[] all = new String[items.length + 1];
        all[0] = allLabel;
        System.arraycopy(items, 0, all, 1, items.length);
        box.setItems(FXCollections.observableArrayList(all));
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
                HBox row = new HBox(5, name, value);
                row.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(row);
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
                    && !r.username().toLowerCase().contains(q)) return false;

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
        colCreated.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().created() == null ? "-" : c.getValue().created().format(DATE_FMT)));
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

    @FXML
    public void handleAddAccount() {
        showAccountDialog(null);
    }

    private void handleEdit(AccountRow row) {
        showAccountDialog(row);
    }

    private void handleDelete(AccountRow row) {
        if (row.id() == currentUserId) {
            AlertUtil.showWarning("Not allowed", "You can't delete the account you're currently logged in with.");
            return;
        }
        try {
            if (row.role().equals("Admin") && row.status().equals("Active")
                    && UserDAO.countOtherActiveAdmins(row.id()) == 0) {
                AlertUtil.showWarning("Not allowed", "This is the only active admin. Create another admin first.");
                return;
            }
        } catch (SQLException e) {
            AlertUtil.showError("Database error", e.getMessage());
            return;
        }

        if (!AlertUtil.showYesNo("Delete Account",
                "Delete the account for " + row.name() + "? This cannot be undone.")) return;

        try {
            UserDAO.deleteAccount(row.id());
            accounts.remove(row);
        } catch (SQLIntegrityConstraintViolationException e) {
            AlertUtil.showWarning("Can't delete this account",
                    row.name() + " is linked to existing transactions. "
                            + "Edit the account and set it to inactive instead.");
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Delete failed", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ ADD / EDIT DIALOG

    private void showAccountDialog(AccountRow existing) {
        boolean edit = existing != null;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(edit ? "Edit Account" : "Add Account");
        dialog.setHeaderText(edit ? "Edit " + existing.name() : "Create a new account");
        if (accountTable.getScene() != null) dialog.initOwner(accountTable.getScene().getWindow());

        ButtonType saveType = new ButtonType(edit ? "Save Changes" : "Create Account",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField nameField = new TextField(edit ? existing.name() : "");
        TextField userField = new TextField(edit ? existing.username() : "");
        PasswordField passField = new PasswordField();
        PasswordField confirmField = new PasswordField();
        ComboBox<String> roleBox = new ComboBox<>(FXCollections.observableArrayList(ROLES));
        roleBox.setValue(edit ? existing.role() : "Cashier");
        CheckBox activeBox = new CheckBox("Account is active");
        activeBox.setSelected(!edit || existing.status().equals("Active"));

        passField.setPromptText(edit ? "Leave blank to keep current" : "At least 6 characters");
        confirmField.setPromptText("Re-enter password");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 10, 0, 10));
        grid.addRow(0, new Label("Full name"), nameField);
        grid.addRow(1, new Label("Username"), userField);
        grid.addRow(2, new Label(edit ? "New password" : "Password"), passField);
        grid.addRow(3, new Label("Confirm"), confirmField);
        grid.addRow(4, new Label("Role"), roleBox);
        grid.add(activeBox, 1, 5);
        nameField.setPrefWidth(260);
        roleBox.setPrefWidth(260);
        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            String name = nameField.getText().trim();
            String username = userField.getText().trim();
            String pw = passField.getText();
            String role = roleBox.getValue();
            boolean active = activeBox.isSelected();

            try {
                String warning = validate(existing, name, username, pw, confirmField.getText(), role, active);
                if (warning != null) {
                    AlertUtil.showWarning("Check your input", warning);
                    ev.consume();                       // keep the dialog open
                    return;
                }

                if (edit) {
                    UserDAO.updateAccount(existing.id(), name, username, role, active, pw);
                } else if (!UserDAO.registerUser(
                        new com.coffeepos.renespresso.model.User(0, username, name, role, active), pw)) {
                    AlertUtil.showError("Could not create account", "The account could not be created.");
                    ev.consume();
                    return;
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                AlertUtil.showError("Database error", ex.getMessage());
                ev.consume();
                return;
            }
            loadAccounts();
        });

        dialog.showAndWait();
    }

    /** Returns a warning message if the input is invalid, otherwise null. */
    private String validate(AccountRow existing, String name, String username, String pw,
                            String confirm, String role, boolean active) throws SQLException {
        boolean edit = existing != null;

        if (name.isEmpty()) return "Full name is required.";
        if (!username.matches("[A-Za-z0-9._-]{3,30}"))
            return "Username must be 3-30 characters (letters, numbers, . _ -).";
        if (role == null) return "Please choose a role.";

        if (!edit || !pw.isEmpty()) {
            if (pw.length() < 6) return "Password must be at least 6 characters.";
            if (!pw.equals(confirm)) return "Passwords do not match.";
        }

        if (UserDAO.usernameExists(username, edit ? existing.id() : 0))
            return "That username is already taken.";

        if (edit) {
            if (existing.id() == currentUserId && !active)
                return "You can't deactivate the account you're logged in with.";
            boolean wasActiveAdmin = existing.role().equals("Admin") && existing.status().equals("Active");
            boolean stillActiveAdmin = role.equals("Admin") && active;
            if (wasActiveAdmin && !stillActiveAdmin && UserDAO.countOtherActiveAdmins(existing.id()) == 0)
                return "This is the only active admin. Create another admin first.";
        }
        return null;
    }

    // ------------------------------------------------------------------ FORMAT / ALERTS

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

    private void warn(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg);
        a.setHeaderText(header);
        a.showAndWait();
    }

    private void error(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.setHeaderText(header);
        a.showAndWait();
    }
}