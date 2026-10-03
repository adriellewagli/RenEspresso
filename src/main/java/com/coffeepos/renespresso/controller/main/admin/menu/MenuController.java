package com.coffeepos.renespresso.controller.main.admin.menu;

import com.coffeepos.renespresso.dao.MenuDAO;
import com.coffeepos.renespresso.model.Category;
import com.coffeepos.renespresso.model.Product;
import com.coffeepos.renespresso.util.ImageStore;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MenuController {

    @FXML private VBox root;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> availabilityFilter;
    @FXML private FlowPane categoryChips, dishGrid;
    @FXML private Label countLabel;
    @FXML private Button addDishBtn;

    private static final DecimalFormat MONEY = new DecimalFormat("\u20B1#,##0.00");
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999.99");
    private static final double PHOTO = 106;

    private final ToggleGroup chipGroup = new ToggleGroup();
    private List<Product> allProducts = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private int selectedCategoryId = -1;          // -1 = All
    private boolean rebuildingChips = false;
    private boolean loading = false;

    private record Loaded(List<Category> categories, List<Product> products) {}

    @FXML
    public void initialize() {
        availabilityFilter.setItems(FXCollections.observableArrayList("All items", "Available", "Unavailable"));
        availabilityFilter.getSelectionModel().selectFirst();

        searchField.textProperty().addListener((o, a, b) -> render());
        availabilityFilter.valueProperty().addListener((o, a, b) -> render());

        chipGroup.selectedToggleProperty().addListener((o, old, now) -> {
            if (rebuildingChips) return;
            if (now == null) {                      // never allow "nothing selected"
                if (old != null) old.setSelected(true);
                return;
            }
            selectedCategoryId = (Integer) now.getUserData();
            render();
        });

        loadData();
    }

    // ------------------------------------------------------------------ DATA

    /** Public so the dashboard can call it whenever the Menu view is shown. */
    public void loadData() {
        if (loading) return;
        loading = true;

        Task<Loaded> task = new Task<>() {
            @Override
            protected Loaded call() throws Exception {
                return new Loaded(MenuDAO.getCategories(), MenuDAO.getProducts());
            }
        };
        task.setOnSucceeded(e -> {
            categories = new ArrayList<>(task.getValue().categories());
            allProducts = new ArrayList<>(task.getValue().products());
            buildChips();
            render();
            loading = false;
        });
        task.setOnFailed(e -> {
            loading = false;
            task.getException().printStackTrace();
            alert(Alert.AlertType.ERROR, "Could not load menu", task.getException().getMessage());
        });

        Thread t = new Thread(task, "menu-loader");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ CHIPS + GRID

    private void buildChips() {
        rebuildingChips = true;
        chipGroup.getToggles().clear();
        categoryChips.getChildren().clear();

        addChip("All", -1);
        for (Category c : categories) addChip(c.name(), c.id());

        boolean found = false;
        for (Toggle t : chipGroup.getToggles()) {
            if ((Integer) t.getUserData() == selectedCategoryId) { t.setSelected(true); found = true; }
        }
        if (!found) {
            selectedCategoryId = -1;
            chipGroup.getToggles().get(0).setSelected(true);
        }
        rebuildingChips = false;
    }

    private void addChip(String text, int id) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(chipGroup);
        b.setUserData(id);
        b.getStyleClass().add("chip");
        categoryChips.getChildren().add(b);
    }

    private void render() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String avail = availabilityFilter.getValue();

        List<Product> shown = allProducts.stream()
                .filter(p -> selectedCategoryId == -1 || p.categoryId() == selectedCategoryId)
                .filter(p -> q.isEmpty() || p.name().toLowerCase().contains(q)
                        || p.category().toLowerCase().contains(q)
                        || (p.description() != null && p.description().toLowerCase().contains(q)))
                .filter(p -> avail == null || avail.equals("All items")
                        || (avail.equals("Available") == p.available()))
                .toList();

        dishGrid.getChildren().clear();
        if (shown.isEmpty()) {
            Label empty = new Label(allProducts.isEmpty()
                    ? "No dishes yet. Click \"Add New Dish\" to create your first one."
                    : "No dishes match your search or filters.");
            empty.getStyleClass().add("empty-state");
            dishGrid.getChildren().add(empty);
        } else {
            for (Product p : shown) dishGrid.getChildren().add(buildCard(p));
        }

        long availableCount = allProducts.stream().filter(Product::available).count();
        countLabel.setText(shown.size() == allProducts.size()
                ? allProducts.size() + " dishes  \u00B7  " + availableCount + " available"
                : "Showing " + shown.size() + " of " + allProducts.size() + " dishes");
    }

    // ------------------------------------------------------------------ DISH CARD

    private StackPane buildCard(Product p) {
        // photo with ring
        StackPane photo = new StackPane();
        photo.getStyleClass().add("dish-photo");
        ImageView iv = ImageStore.circleView(p.imagePath(), PHOTO);
        if (iv != null) {
            if (!p.available()) iv.setEffect(new ColorAdjust(0, -1, -0.1, 0));
            photo.getChildren().add(iv);
        } else {
            Label ph = new Label(p.name().isEmpty() ? "?" : p.name().substring(0, 1).toUpperCase());
            ph.getStyleClass().add("dish-placeholder");
            photo.getChildren().add(ph);
        }

        Label category = new Label(p.category().toUpperCase());
        category.getStyleClass().add("dish-category");

        Label name = new Label(p.name());
        name.getStyleClass().add("dish-name");
        name.setWrapText(true);
        name.setTextAlignment(TextAlignment.CENTER);
        name.setAlignment(Pos.TOP_CENTER);
        name.setMaxWidth(156);
        name.setMinHeight(38);

        String desc = p.description() == null ? "" : p.description().trim();
        Label description = new Label(desc);
        description.getStyleClass().add("dish-desc");
        description.setWrapText(true);
        description.setTextAlignment(TextAlignment.CENTER);
        description.setAlignment(Pos.TOP_CENTER);
        description.setMaxWidth(156);
        description.setMinHeight(30);
        description.setPrefHeight(30);
        description.setMaxHeight(30);

        Label price = new Label(MONEY.format(p.price()));
        price.getStyleClass().add("dish-price");

        Button pill = new Button(p.available() ? "Available" : "Unavailable");
        pill.getStyleClass().addAll("avail-pill", p.available() ? "avail-on" : "avail-off");
        pill.setTooltip(new Tooltip(p.available() ? "Click to mark unavailable" : "Click to mark available"));
        pill.setOnAction(e -> toggleAvailability(p));

        VBox body = new VBox(6, photo, category, name, description, price, pill);
        body.setAlignment(Pos.TOP_CENTER);
        body.getStyleClass().add("dish-body");

        // hover actions
        Button edit = iconButton("icon-edit", "action-edit");
        edit.setOnAction(e -> showDishDialog(p));
        Button delete = iconButton("icon-delete", "action-delete");
        delete.setOnAction(e -> handleDelete(p));
        HBox actions = new HBox(6, edit, delete);
        actions.setAlignment(Pos.TOP_RIGHT);
        actions.setPickOnBounds(false);
        actions.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(actions, Pos.TOP_RIGHT);
        StackPane.setMargin(actions, new Insets(10));

        StackPane card = new StackPane(body, actions);
        card.getStyleClass().add("dish-card");
        if (!p.available()) card.getStyleClass().add("dish-off");
        actions.visibleProperty().bind(card.hoverProperty());
        card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) showDishDialog(p); });

        if (!desc.isEmpty()) {                      // full description on hover
            Tooltip tip = new Tooltip(desc);
            tip.setWrapText(true);
            tip.setMaxWidth(260);
            Tooltip.install(description, tip);
        }
        return card;
    }

    private Button iconButton(String iconClass, String buttonClass) {
        Region icon = new Region();
        icon.getStyleClass().add(iconClass);
        Button b = new Button();
        b.setGraphic(icon);
        b.getStyleClass().addAll("dish-action", buttonClass);
        return b;
    }

    // ------------------------------------------------------------------ ACTIONS

    @FXML
    public void handleAddDish() {
        showDishDialog(null);
    }

    private void toggleAvailability(Product p) {
        boolean next = !p.available();
        try {
            MenuDAO.setAvailable(p.id(), next);
            int i = allProducts.indexOf(p);
            if (i >= 0) allProducts.set(i, p.withAvailable(next));
            render();
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Update failed", e.getMessage());
        }
    }

    private void handleDelete(Product p) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + p.name() + "\" from the menu? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Delete Dish");
        confirm.showAndWait().ifPresent(r -> {
            if (r != ButtonType.YES) return;
            try {
                MenuDAO.deleteProduct(p.id());
                ImageStore.delete(p.imagePath());
                allProducts.remove(p);
                render();
            } catch (SQLIntegrityConstraintViolationException e) {
                alert(Alert.AlertType.WARNING, "Can't delete this dish",
                        "\"" + p.name() + "\" appears in past transactions. "
                                + "Mark it as unavailable instead so your sales history stays intact.");
            } catch (SQLException e) {
                e.printStackTrace();
                alert(Alert.AlertType.ERROR, "Delete failed", e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ ADD / EDIT DIALOG

    /** existing == null -> add mode. Saves to the DB when OK is pressed and valid. */
    private void showDishDialog(Product existing) {
        boolean edit = existing != null;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(edit ? "Edit Dish" : "Add New Dish");
        dialog.setHeaderText(edit ? "Edit " + existing.name() : "Add a dish to your menu");
        if (root.getScene() != null) dialog.initOwner(root.getScene().getWindow());
        dialog.getDialogPane().getStylesheets().addAll(root.getStylesheets());
        dialog.getDialogPane().getStyleClass().add("dish-dialog");

        ButtonType saveType = new ButtonType(edit ? "Save Changes" : "Add Dish", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // --- image file path + live preview
        StackPane preview = new StackPane();
        preview.getStyleClass().add("dish-photo");

        TextField pathField = new TextField(edit ? ImageStore.pathOf(existing.imagePath()) : "");
        pathField.setPromptText("Paste an image file path or click Browse");
        pathField.setPrefWidth(250);

        Runnable refreshPreview = () -> {
            preview.getChildren().clear();
            ImageView iv = null;
            String text = pathField.getText().trim();
            if (!text.isEmpty()) {
                File f = new File(text);
                if (f.isFile() && ImageStore.isSupported(f)) iv = ImageStore.circleView(f, 72);
            }
            if (iv != null) {
                preview.getChildren().add(iv);
            } else {
                Label ph = new Label("\uD83C\uDF7D");
                ph.getStyleClass().add("dish-placeholder-small");
                preview.getChildren().add(ph);
            }
        };
        pathField.textProperty().addListener((o, a, b) -> refreshPreview.run());
        refreshPreview.run();

        Button browseBtn = new Button("Browse\u2026");
        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose dish photo");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File f = fc.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (f != null) pathField.setText(f.getAbsolutePath());
        });
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> pathField.clear());

        Label pathLabel = new Label("Image file path");
        pathLabel.setStyle("-fx-font-weight: bold;");
        HBox photoRow = new HBox(14, preview,
                new VBox(6, pathLabel, new HBox(6, pathField, browseBtn, clearBtn)));
        photoRow.setAlignment(Pos.CENTER_LEFT);

        // --- fields
        TextField nameField = new TextField(edit ? existing.name() : "");
        nameField.setPromptText("e.g. Caffe Latte");
        TextField priceField = new TextField(edit ? String.format("%.2f", existing.price()) : "");
        priceField.setPromptText("0.00");

        TextArea descField = new TextArea(edit && existing.description() != null ? existing.description() : "");
        descField.setPromptText("Short description (ingredients, size, notes) - max 255 characters");
        descField.setWrapText(true);
        descField.setPrefRowCount(3);
        descField.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= 255 ? c : null));

        CheckBox availableBox = new CheckBox("Available for sale");
        availableBox.setSelected(!edit || existing.available());

        ComboBox<Category> categoryBox = new ComboBox<>(FXCollections.observableArrayList(categories));
        categoryBox.setPrefWidth(200);
        if (edit) categories.stream().filter(c -> c.id() == existing.categoryId()).findFirst().ifPresent(categoryBox::setValue);
        Button addCatBtn = new Button("+");
        addCatBtn.setTooltip(new Tooltip("New category"));

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #C53030;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(420);

        addCatBtn.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("New Category");
            d.setHeaderText("Create a category");
            d.setContentText("Name:");
            d.initOwner(dialog.getDialogPane().getScene().getWindow());
            d.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).ifPresent(catName -> {
                Optional<Category> same = categories.stream()
                        .filter(c -> c.name().equalsIgnoreCase(catName)).findFirst();
                if (same.isPresent()) { categoryBox.setValue(same.get()); return; }
                try {
                    Category c = MenuDAO.addCategory(catName);
                    categories.add(c);
                    categoryBox.getItems().add(c);
                    categoryBox.setValue(c);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    errorLabel.setText("Could not create category: " + ex.getMessage());
                }
            });
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 10, 0, 10));
        grid.add(photoRow, 0, 0, 2, 1);
        grid.addRow(1, new Label("Name"), nameField);
        grid.addRow(2, new Label("Category"), new HBox(6, categoryBox, addCatBtn));
        grid.addRow(3, new Label("Price (\u20B1)"), priceField);
        grid.addRow(4, new Label("Description"), descField);
        grid.add(availableBox, 1, 5);
        grid.add(errorLabel, 0, 6, 2, 1);
        nameField.setPrefWidth(260);
        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            String name = nameField.getText().trim();
            Category cat = categoryBox.getValue();
            BigDecimal price = parsePrice(priceField.getText());
            String pathText = pathField.getText().trim();

            String err = null;
            if (name.isEmpty()) err = "Dish name is required.";
            else if (name.length() > 100) err = "Dish name is too long (max 100 characters).";
            else if (cat == null) err = "Please choose or create a category.";
            else if (price == null) err = "Enter a valid price greater than 0 (e.g. 150.00).";
            else if (!pathText.isEmpty() && !new File(pathText).isFile())
                err = "Image file not found. Check the path or click Browse.";
            else if (!pathText.isEmpty() && !ImageStore.isSupported(new File(pathText)))
                err = "Unsupported image type. Use PNG, JPG, GIF or BMP.";

            if (err == null) {
                String oldImage = edit ? existing.imagePath() : null;
                String newImage = oldImage;
                boolean copied = false;
                try {
                    if (pathText.isEmpty()) newImage = null;                          // cleared
                    else if (ImageStore.isStored(pathText)) newImage = ImageStore.fileNameOf(pathText); // unchanged
                    else { newImage = ImageStore.save(new File(pathText)); copied = true; }  // new file

                    String desc = descField.getText().trim();
                    String descOrNull = desc.isEmpty() ? null : desc;

                    if (edit) MenuDAO.updateProduct(existing.id(), name, cat.id(), price, descOrNull,
                            newImage, availableBox.isSelected());
                    else MenuDAO.addProduct(name, cat.id(), price, descOrNull,
                            newImage, availableBox.isSelected());

                    if (oldImage != null && !Objects.equals(oldImage, newImage)) ImageStore.delete(oldImage);
                } catch (SQLException | IOException ex) {
                    ex.printStackTrace();
                    if (copied) ImageStore.delete(newImage);       // don't leave an orphan file behind
                    err = "Could not save: " + ex.getMessage();
                }
            }

            if (err != null) {
                errorLabel.setText(err);
                ev.consume();               // keep the dialog open
            } else {
                loadData();
            }
        });

        dialog.showAndWait();
        loadData();                         // pick up any categories created inside the dialog
    }

    private BigDecimal parsePrice(String text) {
        try {
            BigDecimal v = new BigDecimal(text.trim().replace(",", "").replace("\u20B1", ""));
            if (v.signum() <= 0 || v.compareTo(MAX_PRICE) > 0) return null;
            return v.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private void alert(Alert.AlertType type, String header, String msg) {
        Alert a = new Alert(type, msg);
        a.setHeaderText(header);
        a.showAndWait();
    }
}