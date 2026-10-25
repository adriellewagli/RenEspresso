package com.coffeepos.renespresso.controller.main.admin.menu;

import com.coffeepos.renespresso.dao.MenuDAO;
import com.coffeepos.renespresso.model.Category;
import com.coffeepos.renespresso.model.Product;
import com.coffeepos.renespresso.model.ProductVariant;
import com.coffeepos.renespresso.util.AlertUtil;
import com.coffeepos.renespresso.util.ConfirmUtil;
import com.coffeepos.renespresso.util.ImageStore;
import com.coffeepos.renespresso.util.NavigateUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.net.URL;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuController {

    @FXML private VBox root;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> availabilityFilter;
    @FXML private FlowPane categoryChips, dishGrid;
    @FXML private Label countLabel;
    @FXML private Button addDishBtn;
    @FXML private Button archivedMenuBtn;

    private static final DecimalFormat MONEY = new DecimalFormat("\u20B1#,##0.00");
    private static final double PHOTO = 106;

    private static final double MIN_CARD_WIDTH = 190;
    private static final double CARD_SIDE_PADDING = 32;
    private double cardWidth = MIN_CARD_WIDTH;

    private final ToggleGroup chipGroup = new ToggleGroup();
    private List<Product> allProducts = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private Map<Integer, List<ProductVariant>> variantMap = new HashMap<>();

    private int selectedCategoryId = -1;
    private boolean rebuildingChips = false;
    private boolean loading = false;

    private record Loaded(
            List<Category> categories,
            List<Product> products,
            Map<Integer, List<ProductVariant>> variants,
            int archivedDishesCount,
            int archivedCategoriesCount
    ) {}

    @FXML
    public void initialize() {
        availabilityFilter.setItems(FXCollections.observableArrayList("All items", "Available", "Unavailable"));
        availabilityFilter.getSelectionModel().selectFirst();

        searchField.textProperty().addListener((o, a, b) -> render());
        availabilityFilter.valueProperty().addListener((o, a, b) -> render());

        chipGroup.selectedToggleProperty().addListener((o, old, now) -> {
            if (rebuildingChips) return;
            if (now == null) {
                if (old != null) old.setSelected(true);
                return;
            }
            selectedCategoryId = (Integer) now.getUserData();
            render();
        });

        dishGrid.widthProperty().addListener((o, a, b) -> relayoutCards());
        dishGrid.paddingProperty().addListener((o, a, b) -> relayoutCards());

        loadData();
    }

    public void loadData() {
        if (loading) return;
        loading = true;

        Task<Loaded> task = new Task<>() {
            @Override
            protected Loaded call() throws Exception {
                return new Loaded(
                        MenuDAO.getCategories(),
                        MenuDAO.getProducts(),
                        MenuDAO.getAllVariants(),
                        MenuDAO.getArchivedProductsCount(),
                        MenuDAO.getArchivedCategoriesCount()
                );
            }
        };
        task.setOnSucceeded(e -> {
            categories = new ArrayList<>(task.getValue().categories());
            allProducts = new ArrayList<>(task.getValue().products());
            variantMap = task.getValue().variants();
            buildChips();
            render();

            if (archivedMenuBtn != null) {
                int total = task.getValue().archivedDishesCount() + task.getValue().archivedCategoriesCount();
                archivedMenuBtn.setText("Archived Items (" + total + ")");
            }

            loading = false;
        });
        task.setOnFailed(e -> {
            loading = false;
            task.getException().printStackTrace();
            AlertUtil.showError("Could not load menu", task.getException().getMessage());
        });

        Thread t = new Thread(task, "menu-loader");
        t.setDaemon(true);
        t.start();
    }

    private void updateArchivedCounts() {
        try {
            int dCount = MenuDAO.getArchivedProductsCount();
            int cCount = MenuDAO.getArchivedCategoriesCount();
            if (archivedMenuBtn != null) {
                archivedMenuBtn.setText("Archived Items (" + (dCount + cCount) + ")");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

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
                .filter(p -> q.isEmpty()
                        || (p.name() != null && p.name().toLowerCase().contains(q))
                        || (p.category() != null && p.category().toLowerCase().contains(q))
                        || (p.description() != null && p.description().toLowerCase().contains(q)))
                .filter(p -> avail == null || avail.equals("All items")
                        || (avail.equals("Available") == p.available()))
                .toList();

        dishGrid.getChildren().clear();

        cardWidth = computeCardWidth();

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

        Platform.runLater(this::relayoutCards);
    }

    private double computeCardWidth() {
        double w = dishGrid.getWidth();
        if (w <= 0) return MIN_CARD_WIDTH;

        Insets in = dishGrid.getInsets();
        double avail = w - in.getLeft() - in.getRight();
        double gap = dishGrid.getHgap();

        int cols = Math.max(1, (int) Math.floor((avail + gap) / (MIN_CARD_WIDTH + gap)));
        return Math.floor((avail - (cols - 1) * gap) / cols);
    }

    private void relayoutCards() {
        double w = computeCardWidth();
        if (w <= 0) return;
        cardWidth = w;
        for (Node node : dishGrid.getChildren()) {
            if (node instanceof StackPane sp && sp.getStyleClass().contains("dish-card")) {
                applyCardWidth(sp);
            }
        }
    }

    private void applyCardWidth(StackPane card) {
        card.setMinWidth(cardWidth);
        card.setPrefWidth(cardWidth);
        card.setMaxWidth(cardWidth);
        int w = (int) cardWidth;
        card.setStyle("-fx-min-width:" + w + "; -fx-pref-width:" + w + "; -fx-max-width:" + w + ";");
    }

    private StackPane buildCard(Product p) {
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
        name.setMinHeight(38);

        String desc = p.description() == null ? "" : p.description().trim();
        Label description = new Label(desc);
        description.getStyleClass().add("dish-desc");
        description.setWrapText(true);
        description.setTextAlignment(TextAlignment.CENTER);
        description.setAlignment(Pos.TOP_CENTER);
        description.setMinHeight(30);
        description.setPrefHeight(30);
        description.setMaxHeight(30);

        FlowPane variantsPane = new FlowPane(4, 4);
        variantsPane.setAlignment(Pos.TOP_CENTER);

        List<ProductVariant> vars = variantMap.getOrDefault(p.id(), new ArrayList<>());
        for (ProductVariant v : vars) {
            Label vLbl = new Label(v.getSizeName() + " " + MONEY.format(v.getPrice()));
            vLbl.getStyleClass().add("variant-pill");
            variantsPane.getChildren().add(vLbl);
        }
        if (vars.isEmpty()) {
            Label noPrice = new Label("No prices set");
            noPrice.getStyleClass().add("variant-pill");
            variantsPane.getChildren().add(noPrice);
        }

        Label qtyLabel = new Label("Qty: " + p.quantity());
        qtyLabel.getStyleClass().add("dish-qty");
        if (p.quantity() <= 0) {
            qtyLabel.setText("Qty: 0 (Out of Stock)");
            qtyLabel.setStyle("-fx-text-fill: #C0392B; -fx-font-weight: bold;");
        } else if (p.quantity() <= 5) {
            qtyLabel.setText("Qty: " + p.quantity() + " (Low Stock)");
            qtyLabel.getStyleClass().add("dish-qty-low");
            qtyLabel.setStyle("-fx-text-fill: #D97706; -fx-font-weight: bold;");
        }

        Button pill = new Button(p.available() ? "Available" : "Unavailable");
        pill.getStyleClass().addAll("avail-pill", p.available() ? "avail-on" : "avail-off");
        pill.setTooltip(new Tooltip(p.available() ? "Click to mark unavailable" : "Click to mark available"));
        pill.setOnAction(e -> toggleAvailability(p));

        VBox body = new VBox(6, photo, category, name, description, variantsPane, qtyLabel, pill);
        body.setAlignment(Pos.TOP_CENTER);
        body.getStyleClass().add("dish-body");

        Button edit = iconButton("icon-edit", "action-edit");
        edit.setTooltip(new Tooltip("Edit Dish"));
        edit.setOnAction(e -> showDishDialog(p));

        Button archive = iconButton("icon-archive", "action-archive");
        archive.setTooltip(new Tooltip("Archive Dish"));
        archive.setOnAction(e -> handleArchiveDish(p));

        HBox actions = new HBox(6, edit, archive);
        actions.setAlignment(Pos.TOP_RIGHT);
        actions.setPickOnBounds(false);
        actions.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(actions, Pos.TOP_RIGHT);
        StackPane.setMargin(actions, new Insets(10));

        StackPane card = new StackPane(body, actions);
        card.getStyleClass().add("dish-card");
        if (!p.available()) card.getStyleClass().add("dish-off");
        applyCardWidth(card);

        name.maxWidthProperty().bind(card.widthProperty().subtract(CARD_SIDE_PADDING));
        description.maxWidthProperty().bind(card.widthProperty().subtract(CARD_SIDE_PADDING));
        variantsPane.maxWidthProperty().bind(card.widthProperty().subtract(CARD_SIDE_PADDING));

        actions.visibleProperty().bind(card.hoverProperty());
        card.setOnMouseClicked(e -> { if (e.getClickCount() == 2) showDishDialog(p); });

        if (!desc.isEmpty()) {
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
        icon.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Button b = new Button();
        b.setGraphic(icon);
        b.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        b.getStyleClass().addAll("dish-action", buttonClass);
        return b;
    }

    @FXML public void handleAddDish() {
        showDishDialog(null);
    }

    private void toggleAvailability(Product p) {
        if (!p.available() && p.quantity() <= 0) {
            AlertUtil.showWarning("Out of Stock", "Cannot mark as available because quantity is 0. Please update the stock first.");
            return;
        }

        boolean next = !p.available();
        String prompt = next ? "Mark \"" + p.name() + "\" as available?" : "Mark \"" + p.name() + "\" as unavailable?";
        if (!AlertUtil.showYesNo("Change Availability", prompt)) return;

        try {
            MenuDAO.setAvailable(p.id(), next);
            int i = allProducts.indexOf(p);
            if (i >= 0) allProducts.set(i, p.withAvailable(next));
            render();
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Update failed", e.getMessage());
        }
    }

    private void handleArchiveDish(Product p) {
        if (!AlertUtil.showYesNo("Archive Dish",
                "Archive \"" + p.name() + "\" from the menu?\n\nThis dish will be hidden from the active menu and POS order screen. You can restore it anytime from Archived Dishes.")) {
            return;
        }

        if (!AlertUtil.requestSecurityAuthorization("Authorize Dish Archive",
                "Enter your username and password to authorize archiving \"" + p.name() + "\".", "Authorize Archive")) {
            return;
        }

        try {
            MenuDAO.archiveProduct(p.id());
            allProducts.remove(p);
            render();
            updateArchivedCounts();
            AlertUtil.showSuccess("Dish Archived", "\"" + p.name() + "\" was archived successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
            AlertUtil.showError("Archive failed", e.getMessage());
        }
    }

    @FXML
    public void handleOpenArchivedMenu() {
        openArchivedModal(ArchivedMenuModalController.MenuTab.DISHES);
    }

    private void openArchivedModal(ArchivedMenuModalController.MenuTab initialTab) {
        try {
            URL xmlUrl = getClass().getResource("/com/coffeepos/renespresso/views/main/admin/menu/ArchivedMenuModalView.fxml");
            if (xmlUrl == null) {
                throw new IllegalStateException("Cannot find ArchivedMenuModalView.fxml");
            }

            FXMLLoader loader = new FXMLLoader(xmlUrl);
            Parent modalRoot = loader.load();
            ArchivedMenuModalController controller = loader.getController();
            if (controller != null) {
                controller.selectTab(initialTab);
            }

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);

            if (root.getScene() != null && root.getScene().getWindow() != null) {
                stage.initOwner(root.getScene().getWindow());
            }

            Scene scene = new Scene(modalRoot);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().addAll(ConfirmUtil.collectStylesheets(root));
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

            // Refresh menu and chips when modal closes
            loadData();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Could not open archives", e.getMessage());
        }
    }

    private void showDishDialog(Product existing) {
        Runnable[] back = new Runnable[1];
        back[0] = NavigateUtil.<MenuConfigureController>loadInto(
                root, "main/admin/menu/MenuConfigureView.fxml",
                c -> c.init(existing, categories, () -> {
                    if (back[0] != null) back[0].run();
                    loadData();
                }));
    }
}