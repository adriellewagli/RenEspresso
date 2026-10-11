package com.coffeepos.renespresso.controller.main.admin.menu;

import com.coffeepos.renespresso.dao.MenuDAO;
import com.coffeepos.renespresso.model.Category;
import com.coffeepos.renespresso.model.Product;
import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.ImageStore;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.shape.Rectangle;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MenuConfigureController {

    @FXML private Label formTitle, countBadge;
    @FXML private VBox uploadBox, categoryList;
    @FXML private StackPane preview;
    @FXML private TextField nameField, priceField, newCatField;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private TextArea descField;
    @FXML private ToggleGroup statusGroup;
    @FXML private ToggleButton availableBtn, unavailableBtn;
    @FXML private Button saveBtn;

    private static final BigDecimal MAX_PRICE = new BigDecimal("999999.99");

    private Product existing;                       // null -> add mode
    private List<Category> categories = new ArrayList<>();
    private Runnable onClose = () -> {};
    private String imagePath = "";



    @FXML public void initialize() {
        categoryBox.setConverter(new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : c.name(); }
            @Override public Category fromString(String s) { return null; }
        });
        descField.setTextFormatter(new TextFormatter<String>(c ->
                c.getControlNewText().length() <= 255 ? c : null));

        statusGroup.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });
    }

    public void init(Product existing, List<Category> allCategories, Runnable onClose) {
        this.existing = existing;
        this.categories = new ArrayList<>(allCategories);
        this.onClose = onClose;

        boolean edit = existing != null;
        formTitle.setText(edit ? "Edit Menu Item" : "Add Menu Item");
        saveBtn.setText(edit ? "Save Changes" : "Save Menu Item");

        categoryBox.getItems().setAll(categories);
        refreshCategoryList();

        if (edit) {
            nameField.setText(existing.name());
            priceField.setText(String.format("%.2f", existing.price()));
            descField.setText(existing.description() == null ? "" : existing.description());
            categories.stream().filter(c -> c.id() == existing.categoryId())
                    .findFirst().ifPresent(categoryBox::setValue);
            availableBtn.setSelected(existing.available());
            unavailableBtn.setSelected(!existing.available());
            imagePath = Objects.toString(ImageStore.pathOf(existing.imagePath()), "");
        }
        refreshPreview();
    }

    @FXML private void handleUpload(MouseEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose dish photo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = fc.showOpenDialog(uploadBox.getScene().getWindow());
        if (f != null) {
            imagePath = f.getAbsolutePath();
            refreshPreview();
        }
    }

    @FXML private void handleRemovePhoto() {
        imagePath = "";
        refreshPreview();
    }

    private void refreshPreview() {
        preview.getChildren().clear();
        String text = imagePath.trim();
        if (text.isEmpty()) return;
        File f = new File(text);
        if (f.isFile() && ImageStore.isSupported(f)) {
            ImageView iv = roundedSquareView(f, 120, 16);
            if (iv != null) preview.getChildren().add(iv);
        }
    }

    private ImageView roundedSquareView(File f, double size, double radius) {
        Image img = new Image(f.toURI().toString());
        if (img.isError() || img.getWidth() <= 0) return null;

        ImageView iv = new ImageView(img);
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        double s = Math.min(img.getWidth(), img.getHeight());
        iv.setViewport(new Rectangle2D((img.getWidth() - s) / 2, (img.getHeight() - s) / 2, s, s));

        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        iv.setClip(clip);
        return iv;
    }
    @FXML private void handleAddCategory() {
        String name = newCatField.getText() == null ? "" : newCatField.getText().trim();
        if (name.isEmpty()) return;

        Optional<Category> same = categories.stream()
                .filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
        if (same.isPresent()) {
            categoryBox.setValue(same.get());
            newCatField.clear();
            return;
        }
        try {
            Category c = MenuDAO.addCategory(name);
            categories.add(c);
            categoryBox.getItems().add(c);
            categoryBox.setValue(c);
            newCatField.clear();
            refreshCategoryList();
        } catch (SQLException ex) {
            ex.printStackTrace();
            AlertUtil.showError("Could not create category", ex.getMessage());
        }
    }

    private void refreshCategoryList() {
        categoryList.getChildren().clear();
        countBadge.setText(categories.size() + (categories.size() == 1 ? " category" : " categories"));

        for (Category c : categories) {
            Region dot = new Region();
            dot.getStyleClass().add("category-dot");
            Label name = new Label(c.name());
            name.getStyleClass().add("category-name");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Region trash = new Region();
            trash.getStyleClass().add("icon-delete");
            Button del = new Button();
            del.setGraphic(trash);
            del.getStyleClass().add("category-delete");
            del.setOnAction(e -> deleteCategory(c));

            HBox row = new HBox(10, dot, name, spacer, del);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("category-row");
            categoryList.getChildren().add(row);
        }
    }

    private void deleteCategory(Category c) {
        if (!AlertUtil.showYesNo("Delete Category", "Delete the category \"" + c.name() + "\"?")) return;
        try {
            MenuDAO.deleteCategory(c.id());
            categories.remove(c);
            categoryBox.getItems().remove(c);
            refreshCategoryList();
        } catch (SQLIntegrityConstraintViolationException ex) {
            AlertUtil.showWarning("Can't delete this category",
                    "\"" + c.name() + "\" still has dishes. Move or delete those dishes first.");
        } catch (SQLException ex) {
            ex.printStackTrace();
            AlertUtil.showError("Could not delete category", ex.getMessage());
        }
    }

    @FXML private void handleCancel() {
        onClose.run();
    }

    @FXML private void handleSave() {
        boolean edit = existing != null;
        String name = nameField.getText().trim();
        Category cat = categoryBox.getValue();
        BigDecimal price = parsePrice(priceField.getText());
        String pathText = imagePath.trim();

        String warning = null;
        if (name.isEmpty()) warning = "Dish name is required.";
        else if (name.length() > 100) warning = "Dish name is too long (max 100 characters).";
        else if (cat == null) warning = "Please choose or create a category.";
        else if (price == null) warning = "Enter a valid price greater than 0 (e.g. 150.00).";
        else if (!pathText.isEmpty() && !new File(pathText).isFile())
            warning = "Image file not found. Please choose the photo again.";
        else if (!pathText.isEmpty() && !ImageStore.isSupported(new File(pathText)))
            warning = "Unsupported image type. Use PNG, JPG, GIF or BMP.";
        if (warning != null) {
            AlertUtil.showWarning("Check your input", warning);
            return;
        }

        String oldImage = edit ? existing.imagePath() : null;
        String newImage = oldImage;
        boolean copied = false;
        try {
            if (pathText.isEmpty()) newImage = null;
            else if (ImageStore.isStored(pathText)) newImage = ImageStore.fileNameOf(pathText);
            else { newImage = ImageStore.save(new File(pathText)); copied = true; }

            String desc = descField.getText().trim();
            String descOrNull = desc.isEmpty() ? null : desc;
            boolean available = availableBtn.isSelected();

            if (edit) MenuDAO.updateProduct(existing.id(), name, cat.id(), price, descOrNull, newImage, available);
            else MenuDAO.addProduct(name, cat.id(), price, descOrNull, newImage, available);

            if (oldImage != null && !Objects.equals(oldImage, newImage)) ImageStore.delete(oldImage);
        } catch (SQLException | IOException ex) {
            ex.printStackTrace();
            if (copied) ImageStore.delete(newImage);
            AlertUtil.showError("Could not save dish", ex.getMessage());
            return;
        }
        onClose.run();
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
}