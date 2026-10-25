package com.coffeepos.renespresso.controller.main.admin.menu;

import com.coffeepos.renespresso.dao.MenuDAO;
import com.coffeepos.renespresso.dao.ProductVariantDAO;
import com.coffeepos.renespresso.model.Category;
import com.coffeepos.renespresso.model.Product;
import com.coffeepos.renespresso.model.ProductVariant;
import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.ImageStore;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import com.coffeepos.renespresso.util.ConfirmUtil;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class MenuConfigureController {

    @FXML private Label formTitle, countBadge;
    @FXML private VBox uploadBox, categoryList, variantsBox;
    @FXML private StackPane preview;
    @FXML private TextField nameField, newCatField, qtyField;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private TextArea descField;
    @FXML private ToggleGroup statusGroup;
    @FXML private ToggleButton availableBtn, unavailableBtn;
    @FXML private Button saveBtn;
    @FXML private Button archivedCategoriesBtn;
    @FXML private ScrollPane formScroll;
    @FXML private HBox formBox;

    private static final BigDecimal MAX_PRICE = new BigDecimal("999999.99");

    // smallest the form may shrink; below this the outer page may scroll again
    private static final double MIN_SCALE = 0.5;
    private final Scale formScale = new Scale(1, 1, 0, 0);

    // true while the form fits the window: the outer page must not scroll at all
    private boolean scrollLocked = true;

    private Product existing;
    private List<Category> categories = new ArrayList<>();
    private Runnable onClose = () -> {};
    private String imagePath = "";

    private record VariantUI(ProductVariant variant, TextField sizeF, TextField priceF, HBox row) {}
    private List<VariantUI> variantRows = new ArrayList<>();
    private List<ProductVariant> variantsToDelete = new ArrayList<>();

    @FXML public void initialize() {
        categoryBox.setConverter(new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : c.name(); }
            @Override public Category fromString(String s) { return null; }
        });
        descField.setTextFormatter(new TextFormatter<>(c ->
                c.getControlNewText().length() <= 255 ? c : null));
        qtyField.setTextFormatter(new TextFormatter<>(c ->
                c.getControlNewText().matches("\\d{0,6}") ? c : null));

        statusGroup.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });

        // scale-to-fit: re-fit on window resize and whenever a size row is added or removed
        formBox.getTransforms().add(formScale);
        formScroll.viewportBoundsProperty().addListener((o, a, b) -> fitForm());
        variantsBox.getChildren().addListener((Observable o) -> Platform.runLater(this::fitForm));

        // Block wheel/touchpad scrolling of the outer page (the category list keeps its own)
        formScroll.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (scrollLocked && !insideCategoryScroll(e.getTarget())) e.consume();
        });

        // Snap back to the top if anything still manages to scroll it
        formScroll.vvalueProperty().addListener((o, a, b) -> {
            if (scrollLocked && b.doubleValue() != 0) formScroll.setVvalue(0);
        });
        formScroll.hvalueProperty().addListener((o, a, b) -> {
            if (b.doubleValue() != 0) formScroll.setHvalue(0);
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
        updateArchivedCategoriesCount();

        if (edit) {
            nameField.setText(existing.name());
            descField.setText(existing.description() == null ? "" : existing.description());
            qtyField.setText(String.valueOf(existing.quantity()));
            categories.stream().filter(c -> c.id() == existing.categoryId())
                    .findFirst().ifPresent(categoryBox::setValue);
            availableBtn.setSelected(existing.available());
            unavailableBtn.setSelected(!existing.available());
            imagePath = Objects.toString(ImageStore.pathOf(existing.imagePath()), "");

            try {
                List<ProductVariant> variants = ProductVariantDAO.getVariantsByProductId(existing.id());
                for (ProductVariant pv : variants) addVariantRow(pv);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            qtyField.setText("0");
        }

        qtyField.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                int q = Integer.parseInt(newVal.trim());
                if (q <= 0) {
                    unavailableBtn.setSelected(true);
                } else {
                    availableBtn.setSelected(true);
                }
            } catch (NumberFormatException ignored) {}
        });

        if (variantRows.isEmpty()) addVariantRow(null);
        refreshPreview();
        Platform.runLater(this::fitForm);
    }

    private void fitForm() {
        Bounds vb = formScroll.getViewportBounds();
        if (vb == null) return;
        double w = vb.getWidth() - 2;
        double h = vb.getHeight() - 2;
        if (w <= 0 || h <= 0) return;

        formBox.applyCss();
        double s = 1.0;
        for (int i = 0; i < 8; i++) {
            double contentH = formBox.prefHeight(w / s);
            double next = Math.max(MIN_SCALE, Math.min(1.0, h / contentH));
            if (Math.abs(next - s) < 0.005) { s = next; break; }
            s = next;
        }
        formBox.setMinWidth(w / s);
        formBox.setPrefWidth(w / s);
        formBox.setMaxWidth(w / s);
        formScale.setX(s);
        formScale.setY(s);

        scrollLocked = s > MIN_SCALE + 0.001;
        if (scrollLocked) {
            formScroll.setVvalue(0);
            formScroll.setHvalue(0);
        }
    }

    private boolean insideCategoryScroll(Object target) {
        if (!(target instanceof Node n)) return false;
        while (n != null) {
            if (n.getStyleClass().contains("category-scroll")) return true;
            n = n.getParent();
        }
        return false;
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

    @FXML private void handleAddVariant() {
        addVariantRow(null);
    }

    private void addVariantRow(ProductVariant pv) {
        TextField sizeField = new TextField(pv == null ? "" : pv.getSizeName());
        sizeField.setPromptText("Size (e.g. Medium, 1 pc)");
        sizeField.getStyleClass().add("form-input");
        HBox.setHgrow(sizeField, Priority.ALWAYS);

        TextField priceField = new TextField(pv == null ? "" : String.format(Locale.US, "%.2f", pv.getPrice()));
        priceField.setPromptText("0.00");
        priceField.getStyleClass().add("price-field");
        priceField.setPrefWidth(90);
        priceField.setTextFormatter(new TextFormatter<>(change -> {
            if (",".equals(change.getText())) change.setText(".");
            return change.getControlNewText().matches("\\d{0,7}(\\.\\d{0,2})?") ? change : null;
        }));

        Label peso = new Label("₱");
        peso.getStyleClass().add("input-prefix");
        peso.setMaxHeight(Double.MAX_VALUE);
        HBox priceBox = new HBox(peso, priceField);

        Button delBtn = new Button();
        Region icon = new Region();
        icon.getStyleClass().add("icon-delete");
        delBtn.setGraphic(icon);
        delBtn.getStyleClass().add("category-delete");

        HBox row = new HBox(12, sizeField, priceBox, delBtn);
        row.setAlignment(Pos.CENTER_LEFT);

        VariantUI vui = new VariantUI(pv, sizeField, priceField, row);
        variantRows.add(vui);
        variantsBox.getChildren().add(row);

        delBtn.setOnAction(e -> {
            if (variantRows.size() <= 1) {
                AlertUtil.showWarning("Cannot delete", "A menu item must have at least one size/price.");
                return;
            }
            String sName = sizeField.getText().trim();
            String label = sName.isEmpty() ? "this size" : "\"" + sName + "\"";
            if (!AlertUtil.showYesNo("Delete Size", "Remove " + label + "?")) {
                return;
            }
            variantsBox.getChildren().remove(row);
            variantRows.remove(vui);
            if (pv != null) variantsToDelete.add(pv);
        });
    }

    @FXML private void handleAddCategory() {
        String name = newCatField.getText() == null ? "" : newCatField.getText().trim();
        if (name.isEmpty()) return;
        if (name.length() > 50) {
            AlertUtil.showWarning("Check your input", "Category name is too long (max 50 characters).");
            return;
        }

        Optional<Category> same = categories.stream()
                .filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
        if (same.isPresent()) {
            categoryBox.setValue(same.get());
            newCatField.clear();
            return;
        }

        if (!AlertUtil.requestSecurityAuthorization(
                "Authorize New Category",
                "Enter your username and password to authorize creating category \"" + name + "\".",
                "Authorize Category")) {
            return;
        }

        try {
            Category c = MenuDAO.addCategory(name);
            categories.add(c);
            categoryBox.getItems().add(c);
            categoryBox.setValue(c);
            newCatField.clear();
            refreshCategoryList();
            updateArchivedCategoriesCount();
            new Thread(() -> ImageStore.createCategoryFolder(name), "cat-folder-create").start();
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

            Region archiveIcon = new Region();
            archiveIcon.getStyleClass().add("icon-archive");
            Button archiveBtn = new Button();
            archiveBtn.setGraphic(archiveIcon);
            archiveBtn.getStyleClass().add("category-archive");
            archiveBtn.setTooltip(new Tooltip("Archive Category"));
            archiveBtn.setOnAction(e -> handleArchiveCategory(c));

            HBox row = new HBox(10, dot, name, spacer, archiveBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("category-row");
            categoryList.getChildren().add(row);
        }
    }

    private void handleArchiveCategory(Category c) {
        try {
            int activeDishes = MenuDAO.getActiveProductCountByCategory(c.id());
            if (activeDishes > 0) {
                AlertUtil.showWarning("Cannot Archive Category",
                        "Category \"" + c.name() + "\" currently has " + activeDishes + " active dish(es).\n\n"
                                + "Please archive or reassign all dishes in this category before archiving it.");
                return;
            }

            if (!AlertUtil.showYesNo("Archive Category",
                    "Archive category \"" + c.name() + "\"?\n\nArchived categories will no longer appear in the category dropdown or order screen. You can restore it anytime from Archived Categories.")) {
                return;
            }

            if (!AlertUtil.requestSecurityAuthorization(
                    "Authorize Category Archive",
                    "Enter your username and password to authorize archiving category \"" + c.name() + "\".",
                    "Authorize Archive")) {
                return;
            }

            MenuDAO.archiveCategory(c.id());
            categories.remove(c);
            categoryBox.getItems().remove(c);
            refreshCategoryList();
            updateArchivedCategoriesCount();
            AlertUtil.showSuccess("Category Archived", "Category \"" + c.name() + "\" was moved to archives.");
        } catch (SQLException ex) {
            ex.printStackTrace();
            AlertUtil.showError("Could not archive category", ex.getMessage());
        }
    }

    @FXML
    public void handleOpenArchivedCategories() {
        try {
            URL xmlUrl = getClass().getResource("/com/coffeepos/renespresso/views/main/admin/menu/ArchivedMenuModalView.fxml");
            if (xmlUrl == null) {
                throw new IllegalStateException("Cannot find ArchivedMenuModalView.fxml");
            }

            FXMLLoader loader = new FXMLLoader(xmlUrl);
            Parent modalRoot = loader.load();
            ArchivedMenuModalController controller = loader.getController();
            if (controller != null) {
                controller.selectTab(ArchivedMenuModalController.MenuTab.CATEGORIES);
            }

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);

            if (categoryList.getScene() != null && categoryList.getScene().getWindow() != null) {
                stage.initOwner(categoryList.getScene().getWindow());
            }

            Scene scene = new Scene(modalRoot);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().addAll(ConfirmUtil.collectStylesheets(categoryList));
            stage.setScene(scene);

            stage.setOnShowing(e -> {
                modalRoot.applyCss();
                Window owner = stage.getOwner();
                if (owner != null && owner.getWidth() > 0 && owner.getHeight() > 0) {
                    double w = modalRoot.prefWidth(-1);
                    double h = modalRoot.prefHeight(w);
                    stage.setX(owner.getX() + (owner.getWidth() - (w > 0 ? w : 920)) / 2);
                    stage.setY(owner.getY() + (owner.getHeight() - (h > 0 ? h : 620)) / 2);
                }
            });
            stage.setOnShown(e -> {
                Window owner = stage.getOwner();
                if (owner != null && owner.getWidth() > 0 && owner.getHeight() > 0) {
                    stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
                    stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
                } else {
                    stage.centerOnScreen();
                }
            });

            stage.showAndWait();

            // Refresh categories
            List<Category> updatedCats = MenuDAO.getCategories();
            categories.clear();
            categories.addAll(updatedCats);
            categoryBox.getItems().setAll(updatedCats);
            if (existing != null) {
                for (Category cat : updatedCats) {
                    if (cat.id() == existing.categoryId()) {
                        categoryBox.setValue(cat);
                        break;
                    }
                }
            }
            refreshCategoryList();
            updateArchivedCategoriesCount();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Could not open archives", e.getMessage());
        }
    }

    private void updateArchivedCategoriesCount() {
        try {
            int count = MenuDAO.getArchivedCategoriesCount();
            if (archivedCategoriesBtn != null) {
                archivedCategoriesBtn.setText("Archived Categories (" + count + ")");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isFormDirty() {
        if (existing == null) {
            if (!nameField.getText().trim().isEmpty()) return true;
            if (!descField.getText().trim().isEmpty()) return true;
            if (!qtyField.getText().trim().isEmpty() && !qtyField.getText().trim().equals("0")) return true;
            if (!imagePath.trim().isEmpty()) return true;
            if (variantRows.size() > 1) return true;
            if (!variantRows.isEmpty()) {
                VariantUI first = variantRows.get(0);
                if (!first.sizeF.getText().trim().isEmpty() || !first.priceF.getText().trim().isEmpty()) return true;
            }
            return false;
        } else {
            if (!nameField.getText().trim().equals(existing.name())) return true;
            String curDesc = descField.getText().trim();
            String oldDesc = existing.description() == null ? "" : existing.description();
            if (!curDesc.equals(oldDesc)) return true;
            if (!qtyField.getText().trim().equals(String.valueOf(existing.quantity()))) return true;
            Category selCat = categoryBox.getValue();
            if (selCat != null && selCat.id() != existing.categoryId()) return true;
            String oldImage = Objects.toString(ImageStore.pathOf(existing.imagePath()), "");
            if (!imagePath.trim().equals(oldImage)) return true;
            if (!variantsToDelete.isEmpty()) return true;
            return false;
        }
    }

    @FXML private void handleCancel() {
        if (isFormDirty() && !AlertUtil.showYesNo("Discard Changes", "You have unsaved changes. Discard them?")) {
            return;
        }
        onClose.run();
    }

    @FXML private void handleSave() {
        String name = nameField.getText().trim();
        Category cat = categoryBox.getValue();
        String pathText = imagePath.trim();

        int qty = 0;
        try {
            String qtyStr = qtyField.getText() == null ? "0" : qtyField.getText().trim();
            if (!qtyStr.isEmpty()) qty = Integer.parseInt(qtyStr);
            if (qty < 0) qty = 0;
        } catch (NumberFormatException e) {
            AlertUtil.showWarning("Check your input", "Quantity must be a valid number.");
            return;
        }

        List<ProductVariant> parsedVariants = new ArrayList<>();
        java.util.Set<String> seenSizes = new java.util.HashSet<>();
        for (VariantUI v : variantRows) {
            String sName = v.sizeF.getText().trim();
            BigDecimal sPrice = parsePrice(v.priceF.getText());
            if (sName.isEmpty() || sPrice == null) {
                AlertUtil.showWarning("Check your input", "All sizes must have a valid name and price greater than 0.");
                return;
            }
            if (sName.length() > 50) {
                AlertUtil.showWarning("Check your input", "Size name \"" + sName + "\" is too long (max 50 characters).");
                return;
            }
            if (!seenSizes.add(sName.toLowerCase())) {
                AlertUtil.showWarning("Duplicate Size", "Size variant \"" + sName + "\" is listed more than once.");
                return;
            }
            parsedVariants.add(new ProductVariant(v.variant == null ? 0 : v.variant.getVariantId(), 0, sName, sPrice));
        }

        String warning = null;
        if (name.isEmpty()) warning = "Dish name is required.";
        else if (name.length() > 100) warning = "Dish name is too long (max 100 characters).";
        else if (cat == null) warning = "Please choose or create a category.";
        else if (!pathText.isEmpty() && !new File(pathText).isFile())
            warning = "Image file not found. Please choose the photo again.";
        else if (!pathText.isEmpty() && !ImageStore.isSupported(new File(pathText)))
            warning = "Unsupported image type. Use PNG, JPG, GIF or BMP.";

        if (warning != null) {
            AlertUtil.showWarning("Check your input", warning);
            return;
        }

        try {
            if (MenuDAO.productExistsByName(name, existing != null ? existing.id() : 0)) {
                AlertUtil.showWarning("Duplicate Item", "A menu item named \"" + name + "\" already exists. Please choose a different name.");
                return;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Database Error", "Failed to verify dish name uniqueness: " + e.getMessage());
            return;
        }

        boolean edit = existing != null;
        if (!AlertUtil.showYesNo("Save Menu Item", edit ? "Save changes to \"" + name + "\"?" : "Add \"" + name + "\" to the menu?")) {
            return;
        }

        if (!AlertUtil.requestSecurityAuthorization(
                edit ? "Authorize Dish Update" : "Authorize New Dish",
                edit ? "Enter your username and password to authorize updating \"" + name + "\"."
                     : "Enter your username and password to authorize adding \"" + name + "\" to the menu.",
                edit ? "Authorize Update" : "Authorize Add")) {
            return;
        }

        String oldImage = edit ? existing.imagePath() : null;
        String newImage = oldImage;
        boolean copied = false;

        try {
            if (pathText.isEmpty()) newImage = null;
            else if (new File(pathText).isFile()) {
                newImage = ImageStore.save(new File(pathText), cat != null ? cat.name() : null, name, oldImage);
                copied = true;
            } else if (ImageStore.isStored(pathText)) {
                newImage = ImageStore.fileNameOf(pathText);
            }

            String desc = descField.getText().trim();
            String descOrNull = desc.isEmpty() ? null : desc;
            boolean available = availableBtn.isSelected() && (qty > 0);

            if (edit) {
                MenuDAO.updateProduct(existing.id(), name, cat.id(), descOrNull, newImage, available, qty);
                for (ProductVariant del : variantsToDelete) ProductVariantDAO.deleteVariant(del.getVariantId());
                for (ProductVariant pv : parsedVariants) {
                    if (pv.getVariantId() == 0) ProductVariantDAO.addVariant(existing.id(), pv.getSizeName(), pv.getPrice());
                    else ProductVariantDAO.updateVariant(pv.getVariantId(), pv.getSizeName(), pv.getPrice());
                }
            } else {
                int newId = MenuDAO.addProduct(name, cat.id(), descOrNull, newImage, available, qty);
                for (ProductVariant pv : parsedVariants) ProductVariantDAO.addVariant(newId, pv.getSizeName(), pv.getPrice());
            }

            if (oldImage != null && !Objects.equals(oldImage, newImage)) ImageStore.delete(oldImage);
            AlertUtil.showSuccess("Dish Saved", edit ? "Menu item \"" + name + "\" updated successfully." : "Menu item \"" + name + "\" added successfully.");
            onClose.run();

        } catch (SQLIntegrityConstraintViolationException ex) {
            ex.printStackTrace();
            if (copied) ImageStore.delete(newImage);
            AlertUtil.showWarning("Cannot remove size", "One or more sizes cannot be removed because they are recorded in past transactions.");
        } catch (SQLException | IOException ex) {
            ex.printStackTrace();
            if (copied) ImageStore.delete(newImage);
            AlertUtil.showError("Could not save dish", ex.getMessage());
        }
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