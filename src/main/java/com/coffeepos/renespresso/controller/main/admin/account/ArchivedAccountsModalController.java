package com.coffeepos.renespresso.controller.main.admin.account;

import com.coffeepos.renespresso.dao.UserDAO;
import com.coffeepos.renespresso.model.AccountRow;
import com.coffeepos.renespresso.model.User;
import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ArchivedAccountsModalController {

    @FXML
    private TextField searchField;
    @FXML
    private TableView<AccountRow> archivedTable;
    @FXML
    private TableColumn<AccountRow, String> colName, colUsername, colRole, colLastLogin, colCreated;
    @FXML
    private TableColumn<AccountRow, AccountRow> colActions;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);

    private final ObservableList<AccountRow> archivedAccounts = FXCollections.observableArrayList();
    private FilteredList<AccountRow> filtered;

    private boolean isCurrentUserManager() {
        User cur = SessionManager.getCurrentUser();
        return cur != null && "MANAGER".equalsIgnoreCase(cur.getRole());
    }

    @FXML
    public void initialize() {
        setupColumns();

        filtered = new FilteredList<>(archivedAccounts, r -> true);
        SortedList<AccountRow> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(archivedTable.comparatorProperty());
        archivedTable.setItems(sorted);

        VBox emptyBox = new VBox(8);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setPadding(new Insets(30));

        Region emptyIcon = new Region();
        emptyIcon.getStyleClass().add("icon-archive-empty");
        emptyIcon.setMinSize(36, 36);
        emptyIcon.setPrefSize(36, 36);
        emptyIcon.setMaxSize(36, 36);

        Label emptyTitle = new Label("No Archived Accounts");
        emptyTitle.getStyleClass().add("empty-state-title");

        Label emptySub = new Label("When staff or cashier accounts are archived, they will appear here.");
        emptySub.getStyleClass().add("empty-state-subtitle");

        emptyBox.getChildren().addAll(emptyIcon, emptyTitle, emptySub);
        archivedTable.setPlaceholder(emptyBox);

        searchField.textProperty().addListener((o, a, b) -> applySearch());

        loadData();
    }

    private void setupColumns() {
        colName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        colUsername.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().username()));
        colRole.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().role()));
        colLastLogin.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatLastLogin(c.getValue().lastLogin())));
        colCreated.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().created() == null ? "-" : c.getValue().created().format(DATE_FMT)));
        colActions.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));

        colName.setCellFactory(c -> textCell("name-cell"));
        colUsername.setCellFactory(c -> textCell("muted-cell"));
        colRole.setCellFactory(c -> rolePillCell());
        colLastLogin.setCellFactory(c -> textCell("muted-cell"));
        colCreated.setCellFactory(c -> textCell("muted-cell"));
        colActions.setCellFactory(c -> actionsCell());

        colLastLogin.setSortable(false);
        colCreated.setSortable(false);
        colActions.setSortable(false);
    }

    public void loadData() {
        try {
            ObservableList<AccountRow> all = UserDAO.getArchivedAccounts();
            if (isCurrentUserManager()) {
                List<AccountRow> visible = all.stream()
                        .filter(r -> "Cashier".equalsIgnoreCase(r.role()))
                        .toList();
                archivedAccounts.setAll(visible);
            } else {
                archivedAccounts.setAll(all);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Platform.runLater(() -> AlertUtil.showError("Could not load archived accounts", e.getMessage()));
        }
    }

    private void applySearch() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        filtered.setPredicate(r -> {
            if (isCurrentUserManager() && !"Cashier".equalsIgnoreCase(r.role())) {
                return false;
            }
            if (q.isEmpty()) return true;
            return (r.name() != null && r.name().toLowerCase().contains(q))
                    || (r.username() != null && r.username().toLowerCase().contains(q));
        });
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

                Button restore = iconButton("icon-restore", "action-restore");
                restore.setTooltip(new Tooltip("Restore Account"));
                restore.setOnAction(e -> handleRestore(row));

                HBox box = new HBox(8, restore);

                // Admin accounts can NEVER be deleted.
                // Managers can only delete Cashier accounts.
                boolean canDelete = !"Admin".equalsIgnoreCase(row.role());
                if (isCurrentUserManager() && !"Cashier".equalsIgnoreCase(row.role())) {
                    canDelete = false;
                }

                if (canDelete) {
                    Button delete = iconButton("icon-delete", "action-delete");
                    delete.setTooltip(new Tooltip("Delete Permanently"));
                    delete.setOnAction(e -> handleHardDelete(row));
                    box.getChildren().add(delete);
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

    private void handleRestore(AccountRow row) {
        if (!AlertUtil.showYesNo("Restore Account",
                "Restore account for \"" + row.name() + "\" (@" + row.username() + ")?\n\nThis account will be reactivated and returned to the active accounts list.")) {
            return;
        }

        try {
            UserDAO.restoreAccount(row.id());
            archivedAccounts.remove(row);
            AlertUtil.showSuccess("Account Restored", "Account \"" + row.name() + "\" was restored successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Restore Failed", e.getMessage());
        }
    }

    private void handleHardDelete(AccountRow row) {
        if ("Admin".equalsIgnoreCase(row.role())) {
            AlertUtil.showWarning("Not Allowed", "Admin accounts cannot be deleted.");
            return;
        }

        if (isCurrentUserManager() && !"Cashier".equalsIgnoreCase(row.role())) {
            AlertUtil.showWarning("Not Allowed", "Managers can only delete Cashier accounts.");
            return;
        }

        if (!AlertUtil.showYesNo("Permanently Delete Account",
                "Are you sure you want to permanently delete \"" + row.name() + "\" (@" + row.username() + ") from the database?\n\nWarning: This action cannot be undone.")) {
            return;
        }

        try {
            UserDAO.hardDeleteAccount(row.id());
            archivedAccounts.remove(row);
            AlertUtil.showSuccess("Account Deleted", "Account \"" + row.name() + "\" was permanently deleted.");
        } catch (SQLIntegrityConstraintViolationException e) {
            AlertUtil.showWarning("Cannot Delete Account",
                    "Account \"" + row.name() + "\" has recorded transactions and order history in the system.\n\nTo preserve financial and audit integrity, accounts with transaction history cannot be permanently deleted. They will remain safely in the archives.");
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Delete Failed", e.getMessage());
        }
    }

    @FXML
    public void handleClose() {
        Stage stage = (Stage) archivedTable.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }

    private static String formatLastLogin(LocalDateTime dt) {
        if (dt == null) return "Never";
        return dt.format(DATE_FMT) + " · " + dt.format(TIME_FMT);
    }
}
