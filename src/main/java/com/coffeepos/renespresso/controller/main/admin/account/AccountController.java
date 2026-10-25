package com.coffeepos.renespresso.controller.main.admin.account;

import com.coffeepos.renespresso.dao.UserDAO;
import com.coffeepos.renespresso.model.AccountRow;
import com.coffeepos.renespresso.model.User;
import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.ConfirmUtil;
import com.coffeepos.renespresso.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.net.URL;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class AccountController {

    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> roleFilter, statusFilter, lastLoginFilter;
    @FXML
    private Button clearFiltersBtn, addAccountBtn, archivedAccountsBtn;

    @FXML
    private TableView<AccountRow> accountTable;
    @FXML
    private TableColumn<AccountRow, String> colName, colUsername, colRole, colStatus, colLastLogin, colCreated;
    @FXML
    private TableColumn<AccountRow, AccountRow> colActions;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
    private static final String[] ROLES = { "Admin", "Manager", "Cashier" };

    private final ObservableList<AccountRow> accounts = FXCollections.observableArrayList();
    private FilteredList<AccountRow> filtered;

    private int currentUserId = -1;

    public void setCurrentUserId(int id) {
        this.currentUserId = id;
    }

    private User getCurrentUser() {
        return SessionManager.getCurrentUser();
    }

    private int getCurrentUserId() {
        User cur = getCurrentUser();
        return cur != null && cur.getUserId() > 0 ? cur.getUserId() : currentUserId;
    }

    private boolean isCurrentUserManager() {
        User cur = getCurrentUser();
        return cur != null && "MANAGER".equalsIgnoreCase(cur.getRole());
    }

    @FXML
    public void initialize() {
        setupFilterControls();
        setupColumns();

        filtered = new FilteredList<>(accounts, r -> true);
        SortedList<AccountRow> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(accountTable.comparatorProperty());
        accountTable.setItems(sorted);
        accountTable.getSortOrder().add(colRole);
        colRole.setSortType(TableColumn.SortType.ASCENDING);

        Label emptyNote = new Label("No accounts match your filters");
        emptyNote.getStyleClass().add("empty-note");
        accountTable.setPlaceholder(emptyNote);

        loadAccounts();
    }

    private void setupFilterControls() {
        updateRoleFilterOptions();

        statusFilter.getItems().setAll("Status: All Statuses", "Status: Active", "Status: Inactive");
        statusFilter.setValue("Status: All Statuses");

        lastLoginFilter.getItems().setAll("Last Login: Anytime", "Last Login: Today", "Last Login: Last 7 days", "Last Login: Last 30 days");
        lastLoginFilter.setValue("Last Login: Anytime");

        searchField.textProperty().addListener((o, a, b) -> applyFilters());
        roleFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        statusFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        lastLoginFilter.valueProperty().addListener((o, a, b) -> applyFilters());
    }

    private void updateRoleFilterOptions() {
        String cur = roleFilter.getValue();
        if (isCurrentUserManager()) {
            roleFilter.getItems().setAll("Role: All Roles", "Role: Cashier");
        } else {
            roleFilter.getItems().setAll("Role: All Roles", "Role: Admin", "Role: Manager", "Role: Cashier");
        }
        if (cur != null && roleFilter.getItems().contains(cur)) {
            roleFilter.setValue(cur);
        } else {
            roleFilter.setValue("Role: All Roles");
        }
    }

    // ------------------------------------------------------------------ DATA

    public void loadAccounts() {
        try {
            updateRoleFilterOptions();
            if (addAccountBtn != null) {
                addAccountBtn.setText(isCurrentUserManager() ? "+  Add Cashier" : "+  Add Account");
            }
            if (archivedAccountsBtn != null) {
                int count = UserDAO.getArchivedCount();
                archivedAccountsBtn.setText("Archived Accounts (" + count + ")");
            }
            ObservableList<AccountRow> all = UserDAO.getAllAccounts();
            if (isCurrentUserManager()) {
                List<AccountRow> visible = all.stream()
                        .filter(r -> "Cashier".equalsIgnoreCase(r.role()))
                        .toList();
                accounts.setAll(visible);
            } else {
                accounts.setAll(all);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Platform.runLater(() -> AlertUtil.showError("Could not load accounts", e.getMessage()));
        }
    }

    private void applyFilters() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String role = roleFilter.getValue();
        String status = statusFilter.getValue();
        String login = lastLoginFilter.getValue();

        filtered.setPredicate(r -> {
            if (isCurrentUserManager() && !"Cashier".equalsIgnoreCase(r.role())) {
                return false;
            }

            if (!q.isEmpty()) {
                boolean match = (r.name() != null && r.name().toLowerCase().contains(q))
                        || (r.username() != null && r.username().toLowerCase().contains(q));
                if (!match) return false;
            }

            if (role != null && !role.equals("Role: All Roles")) {
                String roleVal = role.replace("Role: ", "");
                if (!r.role().equalsIgnoreCase(roleVal))
                    return false;
            }
            if (status != null && !status.equals("Status: All Statuses")) {
                String statusVal = status.replace("Status: ", "");
                if (!r.status().equalsIgnoreCase(statusVal))
                    return false;
            }
            String loginVal = login != null ? login.replace("Last Login: ", "") : "Anytime";
            return matchesLastLogin(r.lastLogin(), loginVal);
        });
    }

    private boolean matchesLastLogin(LocalDateTime last, String option) {
        if (option == null || option.equals("Anytime"))
            return true;
        if (last == null)
            return false;
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
        roleFilter.setValue("Role: All Roles");
        statusFilter.setValue("Status: All Statuses");
        lastLoginFilter.setValue("Last Login: Anytime");
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
        colRole.setComparator((r1, r2) -> Integer.compare(rolePriority(r1), rolePriority(r2)));
        colStatus.setCellFactory(c -> statusPillCell());
        colLastLogin.setCellFactory(c -> textCell("muted-cell"));
        colCreated.setCellFactory(c -> textCell("muted-cell"));
        colActions.setCellFactory(c -> actionsCell());

        colLastLogin.setSortable(false);
        colCreated.setSortable(false);
        colActions.setSortable(false);
    }

    private static int rolePriority(String role) {
        if (role == null) return 99;
        return switch (role.toUpperCase()) {
            case "ADMIN" -> 1;
            case "MANAGER" -> 2;
            case "CASHIER" -> 3;
            default -> 4;
        };
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
                pill.getStyleClass().addAll("role-pill", "role-" + role.toLowerCase());
                pill.setMinHeight(22);
                pill.setPrefHeight(22);
                pill.setMaxHeight(22);
                pill.setAlignment(Pos.CENTER);
                HBox wrapper = new HBox(pill);
                wrapper.setFillHeight(false);
                wrapper.setAlignment(Pos.CENTER);
                setGraphic(wrapper);
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
                boolean isActive = "Active".equalsIgnoreCase(status);
                Circle dot = new Circle(4);
                dot.setFill(isActive ? Color.web("#10B981") : Color.web("#D97706"));

                Label textLbl = new Label(status);
                textLbl.getStyleClass().add("status-text");

                HBox pill = new HBox(6, dot, textLbl);
                pill.setAlignment(Pos.CENTER);
                pill.getStyleClass().addAll("status-pill", isActive ? "status-pill-active" : "status-pill-inactive");
                pill.setMinHeight(22);
                pill.setPrefHeight(22);
                pill.setMaxHeight(22);

                HBox wrapper = new HBox(pill);
                wrapper.setFillHeight(false);
                wrapper.setAlignment(Pos.CENTER);
                setGraphic(wrapper);
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
                edit.setTooltip(new Tooltip("Edit Account"));
                edit.setOnAction(e -> handleEdit(row));

                HBox box = new HBox(8, edit);

                // Admin accounts can NEVER be archived.
                // Managers can only archive Cashier accounts.
                boolean canArchive = !"Admin".equalsIgnoreCase(row.role());
                if (isCurrentUserManager() && !"Cashier".equalsIgnoreCase(row.role())) {
                    canArchive = false;
                }

                if (canArchive) {
                    Button archive = iconButton("icon-archive", "action-archive");
                    archive.setTooltip(new Tooltip("Archive Account"));
                    archive.setOnAction(e -> handleArchive(row));
                    box.getChildren().add(archive);
                }

                box.setAlignment(Pos.CENTER);
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

    private void handleArchive(AccountRow row) {
        if ("Admin".equalsIgnoreCase(row.role())) {
            AlertUtil.showWarning("Not allowed", "Admin accounts cannot be archived.");
            return;
        }

        if (isCurrentUserManager() && !"Cashier".equalsIgnoreCase(row.role())) {
            AlertUtil.showWarning("Not allowed", "Managers can only archive Cashier accounts.");
            return;
        }

        int loggedInId = getCurrentUserId();
        if (loggedInId > 0 && row.id() == loggedInId) {
            AlertUtil.showWarning("Not allowed", "You can't archive the account you're currently logged in with.");
            return;
        }

        if (!AlertUtil.showYesNo("Archive Account",
                "Are you sure you want to archive \"" + row.name() + "\" (@" + row.username() + ")?\n\nThis account will be deactivated and moved to the Archived Accounts list."))
            return;

        try {
            UserDAO.archiveAccount(row.id());
            accounts.remove(row);
            if (archivedAccountsBtn != null) {
                int count = UserDAO.getArchivedCount();
                archivedAccountsBtn.setText("Archived Accounts (" + count + ")");
            }
            AlertUtil.showSuccess("Account Archived", "Account \"" + row.name() + "\" has been moved to archives.");
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Archive failed", e.getMessage());
        }
    }

    @FXML
    public void handleOpenArchivedAccounts() {
        try {
            URL xmlUrl = getClass().getResource("/com/coffeepos/renespresso/views/main/admin/account/ArchivedAccountsModalView.fxml");
            if (xmlUrl == null) {
                throw new IllegalStateException("Cannot find ArchivedAccountsModalView.fxml");
            }

            FXMLLoader loader = new FXMLLoader(xmlUrl);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);

            if (accountTable.getScene() != null && accountTable.getScene().getWindow() != null) {
                stage.initOwner(accountTable.getScene().getWindow());
            }

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().addAll(ConfirmUtil.collectStylesheets(accountTable));
            stage.setScene(scene);

            stage.setOnShowing(e -> {
                root.applyCss();
                javafx.stage.Window owner = stage.getOwner();
                if (owner != null && owner.getWidth() > 0 && owner.getHeight() > 0) {
                    double w = root.prefWidth(-1);
                    double h = root.prefHeight(w);
                    stage.setX(owner.getX() + (owner.getWidth() - (w > 0 ? w : 920)) / 2);
                    stage.setY(owner.getY() + (owner.getHeight() - (h > 0 ? h : 580)) / 2);
                }
            });
            stage.setOnShown(e -> {
                javafx.stage.Window owner = stage.getOwner();
                if (owner != null && owner.getWidth() > 0 && owner.getHeight() > 0) {
                    stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
                    stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
                } else {
                    stage.centerOnScreen();
                }
            });

            stage.showAndWait();

            // Refresh active table and badge after modal closes
            loadAccounts();
        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.showError("Could not open archives", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ ADD / EDIT
    // DIALOG

    // ------------------------------------------------------------------ ADD / EDIT DIALOG

    private void showAccountDialog(AccountRow existing) {
        boolean edit = existing != null;
        final boolean active = edit && "Active".equals(existing.status());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(edit ? "Edit Account" : "Add Account");
        dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        if (accountTable.getScene() != null)
            dialog.initOwner(accountTable.getScene().getWindow());

        DialogPane pane = dialog.getDialogPane();
        pane.setHeader(null);
        pane.getStylesheets().addAll(ConfirmUtil.collectStylesheets(accountTable));
        pane.getStyleClass().add("account-dialog");

        if (pane.getScene() != null) {
            pane.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
        } else {
            dialog.setOnShown(e -> pane.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT));
        }

        ButtonType saveType = new ButtonType(edit ? "Save Changes" : "Create Account",
                ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField nameField = new TextField(edit ? existing.name() : "");
        TextField userField = new TextField(edit ? existing.username() : "");
        PasswordField passField = new PasswordField();
        PasswordField confirmField = new PasswordField();
        List<String> allowedRoles;
        boolean isRoleDisabled = false;

        if (isCurrentUserManager()) {
            // Manager cannot create co-manager or elevate: locked strictly to Cashier
            allowedRoles = List.of("Cashier");
            isRoleDisabled = true;
        } else if (edit) {
            if ("Admin".equalsIgnoreCase(existing.role())) {
                // Admin role is fixed to admin only: cannot be demoted
                allowedRoles = List.of("Admin");
                isRoleDisabled = true;
            } else {
                // Staff accounts cannot be elevated to Admin (admin role is fixed to admin only)
                allowedRoles = List.of("Manager", "Cashier");
            }
        } else {
            // Admin adding a new account
            allowedRoles = List.of(ROLES);
        }

        ComboBox<String> roleBox = new ComboBox<>(FXCollections.observableArrayList(allowedRoles));
        roleBox.setValue(edit ? existing.role() : allowedRoles.get(0));
        if (isRoleDisabled) {
            roleBox.setDisable(true);
            if (edit && "Admin".equalsIgnoreCase(existing.role())) {
                roleBox.setTooltip(new Tooltip("The Admin role is fixed and cannot be changed."));
            } else if (isCurrentUserManager()) {
                roleBox.setTooltip(new Tooltip("Managers can only add or manage Cashiers."));
            }
        }

        nameField.setPromptText("e.g. Juan Dela Cruz");
        userField.setPromptText("3-30 characters");
        passField.setPromptText(edit ? "Leave blank to keep current" : "Create a strong password");
        confirmField.setPromptText("Re-enter password");

        nameField.getStyleClass().add("form-field");
        userField.getStyleClass().add("form-field");
        roleBox.getStyleClass().add("form-combo");

        Label title = new Label();
        String subText;
        if (!edit) {
            if (isCurrentUserManager()) {
                title.setText("Add New Cashier");
                subText = "Fill in the details below to create a Cashier account";
            } else {
                title.setText("Add New Account");
                subText = "Fill in the details below to create a staff account";
            }
        } else {
            title.setText("Edit Account");
            if ("Admin".equalsIgnoreCase(existing.role())) {
                subText = "Admin role is fixed to Admin only and cannot be changed";
            } else if (isCurrentUserManager()) {
                subText = "Update the details for Cashier " + existing.name();
            } else {
                subText = "Update the details for " + existing.name();
            }
        }
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label(subText);
        subtitle.getStyleClass().add("dialog-subtitle");
        VBox headerBox = new VBox(4, title, subtitle);

        Region divider = new Region();
        divider.getStyleClass().add("dialog-divider");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(18);
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        grid.getColumnConstraints().addAll(half, half);

        grid.add(field("Full name", nameField), 0, 0, 2, 1);
        grid.add(field("Username", userField), 0, 1);
        grid.add(field("Role", roleBox), 1, 1);
        grid.add(field(edit ? "New password" : "Password", passwordField(passField)), 0, 2);
        grid.add(field("Confirm password", passwordField(confirmField)), 1, 2);

        Label hint = new Label((edit ? "Leave both password fields blank to keep the current password. " : "")
                + "Passwords need at least 8 characters, including an uppercase letter, "
                + "a lowercase letter, a number and a special character.");
        hint.getStyleClass().add("form-hint");
        hint.setWrapText(true);
        grid.add(hint, 0, 3, 2, 1);

        VBox content = new VBox(20, headerBox, divider, grid);
        content.setPadding(new Insets(32, 36, 14, 36));
        content.setPrefWidth(680);
        pane.setContent(content);

        Button saveBtn = (Button) pane.lookupButton(saveType);
        Button cancelBtn = (Button) pane.lookupButton(ButtonType.CANCEL);
        saveBtn.getStyleClass().add("dialog-primary");
        cancelBtn.getStyleClass().add("dialog-secondary");

        final String name0 = nameField.getText(), user0 = userField.getText(), role0 = roleBox.getValue();
        BooleanSupplier dirty = () -> !nameField.getText().equals(name0)
                || !userField.getText().equals(user0)
                || !Objects.equals(roleBox.getValue(), role0)
                || !passField.getText().isEmpty()
                || !confirmField.getText().isEmpty();

        saveBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            if (edit && !dirty.getAsBoolean())
                return;

            String name = nameField.getText().trim();
            String username = userField.getText().trim();
            String pw = passField.getText();
            String role = roleBox.getValue();

            try {
                String warning = validate(existing, name, username, pw, confirmField.getText(), role, active);
                if (warning != null) {
                    AlertUtil.showWarning("Check your input", warning);
                    ev.consume();
                    return;
                }

                String question = edit
                        ? "Save the changes to " + name + "'s account?"
                        : "Create an account for " + name + " (" + role + ")?";
                if (!AlertUtil.showYesNo("Save Account", question)) {
                    ev.consume();
                    return;
                }

                if (edit) {
                    UserDAO.updateAccount(existing.id(), name, username, role, active, pw);
                    AlertUtil.showSuccess("Account Updated", "Account for \"" + name + "\" was updated successfully.");
                } else if (!UserDAO.registerUser(
                        new com.coffeepos.renespresso.model.User(0, username, name, role, active), pw)) {
                    AlertUtil.showError("Could not create account", "The account could not be created.");
                    ev.consume();
                    return;
                } else {
                    AlertUtil.showSuccess("Account Created", "Account for \"" + name + "\" was created successfully.");
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                AlertUtil.showError("Database error", ex.getMessage());
                ev.consume();
                return;
            }
            loadAccounts();
        });

        cancelBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            if (dirty.getAsBoolean() && !AlertUtil.showYesNo("Discard Changes", "You have unsaved changes. Discard them?"))
                ev.consume();
        });
        dialog.setOnShown(e -> pane.getScene().getWindow().addEventFilter(
                WindowEvent.WINDOW_CLOSE_REQUEST, ev -> {
                    if (dirty.getAsBoolean() && !AlertUtil.showYesNo("Discard Changes", "You have unsaved changes. Discard them?"))
                        ev.consume();
                }));

        dialog.showAndWait();
    }

    private VBox field(String label, Region control) {
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        control.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(8, l, control);
        GridPane.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private HBox passwordField(PasswordField hidden) {
        TextField visible = new TextField();
        visible.promptTextProperty().bind(hidden.promptTextProperty());
        visible.textProperty().bindBidirectional(hidden.textProperty());
        visible.setVisible(false);
        visible.setManaged(false);

        hidden.getStyleClass().add("form-field-inner");
        visible.getStyleClass().add("form-field-inner");
        HBox.setHgrow(hidden, Priority.ALWAYS);
        HBox.setHgrow(visible, Priority.ALWAYS);
        hidden.setMaxWidth(Double.MAX_VALUE);
        visible.setMaxWidth(Double.MAX_VALUE);

        Region eye = new Region();
        eye.getStyleClass().add("icon-eye");
        Button toggle = new Button();
        toggle.setGraphic(eye);
        toggle.getStyleClass().add("eye-button");
        toggle.setFocusTraversable(false);

        toggle.setOnAction(e -> {
            boolean show = !visible.isVisible();
            visible.setVisible(show);
            visible.setManaged(show);
            hidden.setVisible(!show);
            hidden.setManaged(!show);
            if (show) {
                if (!eye.getStyleClass().contains("icon-eye-off"))
                    eye.getStyleClass().add("icon-eye-off");
            } else {
                eye.getStyleClass().remove("icon-eye-off");
            }
            TextField current = show ? visible : hidden;
            current.requestFocus();
            current.positionCaret(current.getText().length());
        });

        HBox box = new HBox(4, hidden, visible, toggle);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("form-field-box");

        ChangeListener<Boolean> focusListener = (o, was, now) -> {
            boolean focused = hidden.isFocused() || visible.isFocused();
            if (focused) {
                if (!box.getStyleClass().contains("box-focused"))
                    box.getStyleClass().add("box-focused");
            } else {
                box.getStyleClass().remove("box-focused");
            }
        };
        hidden.focusedProperty().addListener(focusListener);
        visible.focusedProperty().addListener(focusListener);
        return box;
    }

    private String validate(AccountRow existing, String name, String username, String pw,
            String confirm, String role, boolean active) throws SQLException {
        boolean edit = existing != null;

        if (name.isEmpty())
            return "Full name is required.";
        if (!username.matches("[A-Za-z0-9._-]{3,30}"))
            return "Username must be 3-30 characters (letters, numbers, . _ -).";
        if (role == null)
            return "Please choose a role.";

        if (!edit || !pw.isEmpty()) {
            String problem = passwordProblem(pw);
            if (problem != null)
                return problem;
            if (!pw.equals(confirm))
                return "Passwords do not match.";
        }

        if (UserDAO.usernameExists(username, edit ? existing.id() : 0))
            return "That username is already taken.";

        if (isCurrentUserManager()) {
            if (!"Cashier".equalsIgnoreCase(role)) {
                return "Managers cannot create co-managers or elevate accounts. Only Cashier role is allowed.";
            }
            if (edit && !"Cashier".equalsIgnoreCase(existing.role())) {
                return "Managers can only manage Cashier accounts.";
            }
        }

        if (edit) {
            int loggedInId = getCurrentUserId();
            if (loggedInId > 0 && existing.id() == loggedInId && !active)
                return "You can't deactivate the account you're logged in with.";

            boolean wasAdmin = "Admin".equalsIgnoreCase(existing.role());
            boolean nowAdmin = "Admin".equalsIgnoreCase(role);
            if (wasAdmin && !nowAdmin) {
                return "Admin role is fixed to Admin only and cannot be changed.";
            }
            if (!wasAdmin && nowAdmin) {
                return "Existing staff accounts cannot be elevated to Admin. Admin role is fixed.";
            }
        }
        return null;
    }

    private String passwordProblem(String pw) {
        if (pw.length() < 8)
            return "Password must be at least 8 characters.";
        if (!pw.matches(".*[A-Z].*"))
            return "Password must include an uppercase letter.";
        if (!pw.matches(".*[a-z].*"))
            return "Password must include a lowercase letter.";
        if (!pw.matches(".*\\d.*"))
            return "Password must include a number.";
        if (!pw.matches(".*[^A-Za-z0-9].*"))
            return "Password must include a special character (e.g. ! @ # $ %).";
        return null;
    }

    // ------------------------------------------------------------------ FORMAT

    private String formatLastLogin(LocalDateTime t) {
        if (t == null)
            return "Never";
        long days = ChronoUnit.DAYS.between(t.toLocalDate(), LocalDate.now());
        String time = t.format(TIME_FMT);
        if (days <= 0)
            return "Today, " + time;
        if (days == 1)
            return "Yesterday, " + time;
        if (days < 7)
            return days + " days ago";
        if (days < 14)
            return "1 week ago";
        if (days < 30)
            return (days / 7) + " weeks ago";
        return t.format(DATE_FMT);
    }
}