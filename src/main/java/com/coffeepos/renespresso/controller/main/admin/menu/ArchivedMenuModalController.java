package com.coffeepos.renespresso.controller.main.admin.menu;

import com.coffeepos.renespresso.dao.MenuDAO;
import com.coffeepos.renespresso.model.ArchivedCategory;
import com.coffeepos.renespresso.model.Product;
import com.coffeepos.renespresso.model.ProductVariant;
import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.ImageStore;
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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArchivedMenuModalController {

    public enum MenuTab {
        DISHES, CATEGORIES
    }

    @FXML private ToggleButton btnTabDishes, btnTabCategories;
    @FXML private TextField searchField;
    @FXML private VBox dishesBox, categoriesBox;

    // Dishes Table
    @FXML private TableView<Product> archivedDishesTable;
    @FXML private TableColumn<Product, Product> colDishName;
    @FXML private TableColumn<Product, String> colDishCategory;
    @FXML private TableColumn<Product, String> colDishPrices;
    @FXML private TableColumn<Product, String> colDishQty;
    @FXML private TableColumn<Product, Product> colDishActions;

    // Categories Table
    @FXML private TableView<ArchivedCategory> archivedCategoriesTable;
    @FXML private TableColumn<ArchivedCategory, String> colCatName;
    @FXML private TableColumn<ArchivedCategory, String> colCatDishes;
    @FXML private TableColumn<ArchivedCategory, ArchivedCategory> colCatActions;

    private static final DecimalFormat MONEY = new DecimalFormat("\u20B1#,##0.00");
    private final ToggleGroup tabGroup = new ToggleGroup();

    private final ObservableList<Product> dishes = FXCollections.observableArrayList();
    private FilteredList<Product> filteredDishes;

    private final ObservableList<ArchivedCategory> categories = FXCollections.observableArrayList();
    private FilteredList<ArchivedCategory> filteredCategories;

    private Map<Integer, List<ProductVariant>> variantMap = new HashMap<>();
    private MenuTab currentTab = MenuTab.DISHES;

    @FXML
    public void initialize() {
        btnTabDishes.setToggleGroup(tabGroup);
        btnTabCategories.setToggleGroup(tabGroup);
        btnTabDishes.setSelected(true);

        setupDishesTable();
        setupCategoriesTable();

        searchField.textProperty().addListener((o, a, b) -> applySearch());

        loadData();
    }

    private void setupDishesTable() {
        colDishName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colDishCategory.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().category()));
        colDishPrices.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatPrices(c.getValue())));
        colDishQty.setCellValueFactory(c -> new ReadOnlyStringWrapper("Qty: " + c.getValue().quantity()));
        colDishActions.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));

        colDishName.setCellFactory(c -> dishNameCell());
        colDishCategory.setCellFactory(c -> categoryPillCell());
        colDishPrices.setCellFactory(c -> textCell("muted-cell"));
        colDishQty.setCellFactory(c -> textCell("muted-cell"));
        colDishActions.setCellFactory(c -> dishActionsCell());

        colDishActions.setSortable(false);

        filteredDishes = new FilteredList<>(dishes, p -> true);
        SortedList<Product> sorted = new SortedList<>(filteredDishes);
        sorted.comparatorProperty().bind(archivedDishesTable.comparatorProperty());
        archivedDishesTable.setItems(sorted);

        // Placeholder
        VBox emptyBox = createEmptyPlaceholder("No Archived Dishes",
                "When dishes are archived from the menu, they will appear here.");
        archivedDishesTable.setPlaceholder(emptyBox);
    }

    private void setupCategoriesTable() {
        colCatName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        colCatDishes.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().dishCount() == 0 ? "0 dishes (Safe to delete)"
                        : c.getValue().dishCount() + (c.getValue().dishCount() == 1 ? " dish" : " dishes")));
        colCatActions.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));

        colCatName.setCellFactory(c -> textCell("name-cell"));
        colCatDishes.setCellFactory(c -> textCell("muted-cell"));
        colCatActions.setCellFactory(c -> categoryActionsCell());

        colCatActions.setSortable(false);

        filteredCategories = new FilteredList<>(categories, c -> true);
        SortedList<ArchivedCategory> sorted = new SortedList<>(filteredCategories);
        sorted.comparatorProperty().bind(archivedCategoriesTable.comparatorProperty());
        archivedCategoriesTable.setItems(sorted);

        // Placeholder
        VBox emptyBox = createEmptyPlaceholder("No Archived Categories",
                "When categories are archived, they will appear here.");
        archivedCategoriesTable.setPlaceholder(emptyBox);
    }

    private VBox createEmptyPlaceholder(String title, String subtitle) {
        VBox emptyBox = new VBox(8);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setPadding(new Insets(30));

        Region emptyIcon = new Region();
        emptyIcon.getStyleClass().add("icon-archive-empty");
        emptyIcon.setMinSize(36, 36);
        emptyIcon.setPrefSize(36, 36);
        emptyIcon.setMaxSize(36, 36);

        Label emptyTitle = new Label(title);
        emptyTitle.getStyleClass().add("empty-state-title");

        Label emptySub = new Label(subtitle);
        emptySub.getStyleClass().add("empty-state-subtitle");

        emptyBox.getChildren().addAll(emptyIcon, emptyTitle, emptySub);
        return emptyBox;
    }

    public void selectTab(MenuTab tab) {
        this.currentTab = tab;
        if (tab == MenuTab.DISHES) {
            btnTabDishes.setSelected(true);
            dishesBox.setVisible(true);
            dishesBox.setManaged(true);
            categoriesBox.setVisible(false);
            categoriesBox.setManaged(false);
            searchField.setPromptText("Search archived dishes…");
        } else {
            btnTabCategories.setSelected(true);
            dishesBox.setVisible(false);
            dishesBox.setManaged(false);
            categoriesBox.setVisible(true);
            categoriesBox.setManaged(true);
            searchField.setPromptText("Search archived categories…");
        }
        applySearch();
    }

    @FXML
    public void handleSelectDishesTab() {
        selectTab(MenuTab.DISHES);
    }

    @FXML
    public void handleSelectCategoriesTab() {
        selectTab(MenuTab.CATEGORIES);
    }

    public void loadData() {
        try {
            List<Product> dList = MenuDAO.getArchivedProducts();
            List<ArchivedCategory> cList = MenuDAO.getArchivedCategories();
            variantMap = MenuDAO.getAllVariants();

            dishes.setAll(dList);
            categories.setAll(cList);

            updateCounters();
        } catch (SQLException e) {
            e.printStackTrace();
            Platform.runLater(() -> AlertUtil.showError("Load Error", "Could not load archived menu items: " + e.getMessage()));
        }
    }

    private void updateCounters() {
        btnTabDishes.setText("Dishes (" + dishes.size() + ")");
        btnTabCategories.setText("Categories (" + categories.size() + ")");
    }

    private void applySearch() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        if (currentTab == MenuTab.DISHES) {
            filteredDishes.setPredicate(p -> {
                if (q.isEmpty()) return true;
                return (p.name() != null && p.name().toLowerCase().contains(q))
                        || (p.category() != null && p.category().toLowerCase().contains(q))
                        || (p.description() != null && p.description().toLowerCase().contains(q));
            });
        } else {
            filteredCategories.setPredicate(c -> {
                if (q.isEmpty()) return true;
                return c.name() != null && c.name().toLowerCase().contains(q);
            });
        }
    }

    private String formatPrices(Product p) {
        List<ProductVariant> vars = variantMap.get(p.id());
        if (vars == null || vars.isEmpty()) {
            return MONEY.format(p.price());
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vars.size(); i++) {
            ProductVariant v = vars.get(i);
            if (i > 0) sb.append(" \u00B7 ");
            sb.append(v.getSizeName()).append(" ").append(MONEY.format(v.getPrice()));
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // TABLE CELL FACTORIES
    // -------------------------------------------------------------------------

    private TableCell<Product, Product> dishNameCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                Node photoNode;
                ImageView iv = ImageStore.circleView(item.imagePath(), 34);
                if (iv != null) {
                    photoNode = iv;
                } else {
                    Label initial = new Label(item.name().isEmpty() ? "?" : item.name().substring(0, 1).toUpperCase());
                    initial.setMinSize(34, 34);
                    initial.setPrefSize(34, 34);
                    initial.setMaxSize(34, 34);
                    initial.setAlignment(Pos.CENTER);
                    initial.setStyle("-fx-background-color: #FAF2E8; -fx-text-fill: #895737; "
                            + "-fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 100; "
                            + "-fx-border-color: #EBD9C3; -fx-border-radius: 100;");
                    photoNode = initial;
                }

                Label titleLbl = new Label(item.name());
                titleLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #2F2118; -fx-font-size: 13px;");

                String desc = item.description() != null ? item.description().trim() : "";
                Label descLbl = new Label(desc.isEmpty() ? item.category() : desc);
                descLbl.setStyle("-fx-text-fill: #8C7A6A; -fx-font-size: 11px;");
                descLbl.setMaxWidth(200);

                VBox textBox = new VBox(2, titleLbl, descLbl);
                textBox.setAlignment(Pos.CENTER_LEFT);

                HBox row = new HBox(10, photoNode, textBox);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        };
    }

    private TableCell<Product, String> categoryPillCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String cat, boolean empty) {
                super.updateItem(cat, empty);
                setText(null);
                if (empty || cat == null) {
                    setGraphic(null);
                    return;
                }
                Label pill = new Label(cat);
                pill.setStyle("-fx-background-color: #FAF2E8; -fx-text-fill: #895737; "
                        + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 20; "
                        + "-fx-padding: 3 10; -fx-border-color: #EBD9C3; -fx-border-radius: 20;");
                HBox wrapper = new HBox(pill);
                wrapper.setAlignment(Pos.CENTER);
                setGraphic(wrapper);
            }
        };
    }

    private <T> TableCell<T, String> textCell(String styleClass) {
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

    private TableCell<Product, Product> dishActionsCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                Button restore = iconButton("icon-restore", "action-restore");
                restore.setTooltip(new Tooltip("Restore Dish"));
                restore.setOnAction(e -> handleRestoreDish(item));

                Button delete = iconButton("icon-delete", "action-delete");
                delete.setTooltip(new Tooltip("Delete Permanently"));
                delete.setOnAction(e -> handleHardDeleteDish(item));

                HBox box = new HBox(8, restore, delete);
                box.setAlignment(Pos.CENTER);
                setGraphic(box);
            }
        };
    }

    private TableCell<ArchivedCategory, ArchivedCategory> categoryActionsCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(ArchivedCategory item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                Button restore = iconButton("icon-restore", "action-restore");
                restore.setTooltip(new Tooltip("Restore Category"));
                restore.setOnAction(e -> handleRestoreCategory(item));

                Button delete = iconButton("icon-delete", "action-delete");
                delete.setTooltip(new Tooltip("Delete Permanently"));
                delete.setOnAction(e -> handleHardDeleteCategory(item));

                HBox box = new HBox(8, restore, delete);
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

    // -------------------------------------------------------------------------
    // DISH ACTION HANDLERS
    // -------------------------------------------------------------------------

    private void handleRestoreDish(Product p) {
        try {
            boolean catArchived = MenuDAO.isCategoryArchived(p.categoryId());
            if (catArchived) {
                boolean alsoRestore = AlertUtil.showYesNo("Category Archived",
                        "The category for \"" + p.name() + "\" (\"" + p.category() + "\") is currently archived.\n\n"
                                + "Would you like to restore the category as well so this dish can appear on the menu?");
                if (!alsoRestore) return;
                MenuDAO.restoreCategory(p.categoryId());
            } else {
                if (!AlertUtil.showYesNo("Restore Dish", "Restore \"" + p.name() + "\" to the active menu?")) {
                    return;
                }
            }

            MenuDAO.restoreProduct(p.id());
            dishes.remove(p);
            updateCounters();
            AlertUtil.showSuccess("Dish Restored", "\"" + p.name() + "\" was restored to the active menu.");
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Restore Failed", e.getMessage());
        }
    }

    private void handleHardDeleteDish(Product p) {
        try {
            if (MenuDAO.productHasSalesHistory(p.id())) {
                AlertUtil.showWarning("Cannot Delete Dish",
                        "\"" + p.name() + "\" appears in past recorded transactions and sales receipts.\n\n"
                                + "To preserve historical sales reports and receipts, dishes with sales history cannot be permanently deleted. "
                                + "They will remain safely stored in the archives.");
                return;
            }

            if (!AlertUtil.showYesNo("Permanently Delete Dish",
                    "Are you sure you want to permanently delete \"" + p.name() + "\" from the database?\n\n"
                            + "Warning: This action cannot be undone.")) {
                return;
            }

            if (!AlertUtil.requestSecurityAuthorization(
                    "Authorize Permanent Deletion",
                    "Enter your username and password to authorize permanently deleting \"" + p.name() + "\" from the database.",
                    "Authorize Delete")) {
                return;
            }

            MenuDAO.deleteProduct(p.id());
            ImageStore.delete(p.imagePath());
            dishes.remove(p);
            updateCounters();
            AlertUtil.showSuccess("Dish Deleted", "\"" + p.name() + "\" was permanently deleted.");
        } catch (SQLIntegrityConstraintViolationException e) {
            AlertUtil.showWarning("Cannot Delete Dish",
                    "\"" + p.name() + "\" is linked to existing order records. It will remain safely in the archives.");
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Delete Failed", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // CATEGORY ACTION HANDLERS
    // -------------------------------------------------------------------------

    private void handleRestoreCategory(ArchivedCategory c) {
        if (!AlertUtil.showYesNo("Restore Category", "Restore category \"" + c.name() + "\" to active status?")) {
            return;
        }

        try {
            MenuDAO.restoreCategory(c.id());
            categories.remove(c);
            updateCounters();
            AlertUtil.showSuccess("Category Restored", "Category \"" + c.name() + "\" was restored to active status.");
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Restore Failed", e.getMessage());
        }
    }

    private void handleHardDeleteCategory(ArchivedCategory c) {
        try {
            int dishCount = MenuDAO.getTotalProductCountByCategory(c.id());
            if (dishCount > 0) {
                AlertUtil.showWarning("Cannot Delete Category",
                        "Category \"" + c.name() + "\" is still referenced by " + dishCount + " dish(es) in the system (archived or active).\n\n"
                                + "Please delete or reassign those dishes first before permanently deleting this category.");
                return;
            }

            if (!AlertUtil.showYesNo("Permanently Delete Category",
                    "Are you sure you want to permanently delete category \"" + c.name() + "\" from the database?\n\n"
                            + "Warning: This action cannot be undone.")) {
                return;
            }

            if (!AlertUtil.requestSecurityAuthorization(
                    "Authorize Permanent Deletion",
                    "Enter your username and password to authorize permanently deleting category \"" + c.name() + "\" from the database.",
                    "Authorize Delete")) {
                return;
            }

            MenuDAO.deleteCategory(c.id());
            categories.remove(c);
            updateCounters();
            new Thread(() -> ImageStore.deleteCategoryFolder(c.name()), "cat-folder-delete").start();
            AlertUtil.showSuccess("Category Deleted", "Category \"" + c.name() + "\" was permanently deleted.");
        } catch (SQLIntegrityConstraintViolationException e) {
            AlertUtil.showWarning("Cannot Delete Category",
                    "Category \"" + c.name() + "\" has dependent records in the database. Move or remove them first.");
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Delete Failed", e.getMessage());
        }
    }

    @FXML
    public void handleClose() {
        Stage stage = (Stage) btnTabDishes.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }
}
