package com.coffeepos.renespresso.controller.main.admin.home;

import com.coffeepos.renespresso.dao.HomeDAO;
import com.coffeepos.renespresso.dao.HomeDAO.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class HomeController {

    @FXML private Label dateLabel, updatedLabel, errorBanner;
    @FXML private Button refreshBtn;

    @FXML private Label salesTodayLabel, salesDeltaLabel;
    @FXML private Label ordersTodayLabel, avgOrderLabel;
    @FXML private Label itemsSoldLabel;
    @FXML private Label activeStaffLabel, totalStaffLabel;

    @FXML private BarChart<String, Number> salesChart;
    @FXML private VBox topProductsBox, staffBox;

    @FXML private TableView<RecentOrder> recentTable;
    @FXML private TableColumn<RecentOrder, String> colId, colCashier, colTotal, colTime;
    @FXML private TableColumn<RecentOrder, String> colStatus;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("EEE dd", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("MMM dd, hh:mm a", Locale.ENGLISH);
    private static final DecimalFormat MONEY = new DecimalFormat("\u20B1#,##0.00");

    private record Loaded(SalesData sales, String salesError, StaffData staff, String staffError) {}

    private boolean loading = false;

    @FXML
    public void initialize() {
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy", Locale.ENGLISH)));
        salesChart.setAnimated(false);
        setupTable();

        // auto-refresh every minute; the dashboard can also call refresh() when Home is shown
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(60), e -> refresh()));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();

        refresh();
    }

    @FXML
    public void refresh() {
        if (loading) return;
        loading = true;
        refreshBtn.setDisable(true);

        Task<Loaded> task = new Task<>() {
            @Override
            protected Loaded call() {
                SalesData sales = null;
                StaffData staff = null;
                String salesErr = null, staffErr = null;
                try { sales = HomeDAO.loadSales(); }
                catch (Exception e) { e.printStackTrace(); salesErr = e.getMessage(); }
                try { staff = HomeDAO.loadStaff(); }
                catch (Exception e) { e.printStackTrace(); staffErr = e.getMessage(); }
                return new Loaded(sales, salesErr, staff, staffErr);
            }
        };
        task.setOnSucceeded(e -> { apply(task.getValue()); finishLoading(); });
        task.setOnFailed(e -> {
            showError("Could not load dashboard: " + task.getException().getMessage());
            finishLoading();
        });

        Thread t = new Thread(task, "home-loader");
        t.setDaemon(true);
        t.start();
    }

    private void finishLoading() {
        loading = false;
        refreshBtn.setDisable(false);
        updatedLabel.setText("Updated " + LocalDateTime.now().format(TIME_FMT));
    }

    // ------------------------------------------------------------------ APPLY DATA

    private void apply(Loaded d) {
        StringBuilder err = new StringBuilder();
        if (d.salesError() != null) err.append("Sales data unavailable: ").append(d.salesError()).append("  ");
        if (d.staffError() != null) err.append("Staff data unavailable: ").append(d.staffError());
        if (err.length() > 0) showError(err.toString().trim());
        else { errorBanner.setVisible(false); errorBanner.setManaged(false); }

        if (d.sales() != null) applySales(d.sales()); else clearSales();
        if (d.staff() != null) applyStaff(d.staff()); else clearStaff();
    }

    private void applySales(SalesData s) {
        salesTodayLabel.setText(MONEY.format(s.today()));

        salesDeltaLabel.getStyleClass().removeAll("delta-up", "delta-down", "delta-flat");
        if (s.yesterday() <= 0) {
            salesDeltaLabel.setText(s.today() > 0 ? "No sales yesterday" : "No sales yet today");
            salesDeltaLabel.getStyleClass().add("delta-flat");
        } else {
            double pct = (s.today() - s.yesterday()) / s.yesterday() * 100.0;
            boolean up = pct >= 0;
            salesDeltaLabel.setText(String.format("%s %.1f%% vs yesterday", up ? "\u25B2" : "\u25BC", Math.abs(pct)));
            salesDeltaLabel.getStyleClass().add(up ? "delta-up" : "delta-down");
        }

        ordersTodayLabel.setText(String.valueOf(s.ordersToday()));
        avgOrderLabel.setText(s.ordersToday() > 0 ? "Avg " + MONEY.format(s.today() / s.ordersToday()) : "No orders yet");
        itemsSoldLabel.setText(String.valueOf(s.itemsToday()));

        // chart
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (DayTotal d : s.last7())
            series.getData().add(new XYChart.Data<>(d.day().format(DAY_FMT), d.total()));
        salesChart.getData().setAll(series);

        // top products
        topProductsBox.getChildren().clear();
        if (s.top().isEmpty()) {
            topProductsBox.getChildren().add(emptyNote("No sales recorded this week"));
        } else {
            int max = s.top().get(0).qty();
            for (TopProduct p : s.top()) {
                Label name = new Label(p.name());
                name.getStyleClass().add("top-name");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label qty = new Label(p.qty() + " sold");
                qty.getStyleClass().add("top-qty");
                HBox line = new HBox(8, name, spacer, qty);
                line.setAlignment(Pos.CENTER_LEFT);

                ProgressBar bar = new ProgressBar(max == 0 ? 0 : (double) p.qty() / max);
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.getStyleClass().add("top-bar");

                topProductsBox.getChildren().add(new VBox(5, line, bar));
            }
        }

        recentTable.setItems(FXCollections.observableArrayList(s.recent()));
    }

    private void clearSales() {
        for (Label l : new Label[]{salesTodayLabel, ordersTodayLabel, itemsSoldLabel}) l.setText("\u2014");
        salesDeltaLabel.setText(" ");
        avgOrderLabel.setText(" ");
        salesChart.getData().clear();
        topProductsBox.getChildren().setAll(emptyNote("Sales data unavailable"));
        recentTable.getItems().clear();
    }

    private void applyStaff(StaffData s) {
        activeStaffLabel.setText(String.valueOf(s.active()));
        totalStaffLabel.setText("of " + s.total() + " accounts");

        staffBox.getChildren().clear();
        for (RoleCount r : s.roles()) {
            Label pill = new Label(r.role());
            pill.getStyleClass().addAll("pill", "role-" + r.role().toLowerCase());
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label count = new Label(r.active() + " active / " + r.total());
            count.getStyleClass().add("top-qty");
            HBox row = new HBox(8, pill, spacer, count);
            row.setAlignment(Pos.CENTER_LEFT);
            staffBox.getChildren().add(row);
        }
    }

    private void clearStaff() {
        activeStaffLabel.setText("\u2014");
        totalStaffLabel.setText(" ");
        staffBox.getChildren().setAll(emptyNote("Staff data unavailable"));
    }

    // ------------------------------------------------------------------ TABLE

    private void setupTable() {
        colId.setCellValueFactory(c -> new ReadOnlyStringWrapper("#" + c.getValue().id()));
        colCashier.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().cashier()));
        colTotal.setCellValueFactory(c -> new ReadOnlyStringWrapper(MONEY.format(c.getValue().total())));
        colStatus.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().status()));
        colTime.setCellValueFactory(c -> {
            LocalDateTime t = c.getValue().time();
            boolean today = t.toLocalDate().equals(LocalDate.now());
            return new ReadOnlyStringWrapper(t.format(today ? TIME_FMT : DATE_TIME_FMT));
        });

        colId.setCellFactory(c -> textCell("name-cell"));
        colCashier.setCellFactory(c -> textCell("muted-cell"));
        colTotal.setCellFactory(c -> textCell("name-cell"));
        colTime.setCellFactory(c -> textCell("muted-cell"));
        colStatus.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                setText(null);
                if (empty || status == null) { setGraphic(null); return; }
                String nice = status.charAt(0) + status.substring(1).toLowerCase();
                Label pill = new Label(nice);
                pill.getStyleClass().addAll("pill", "status-" + status.toLowerCase());
                setGraphic(pill);
            }
        });

        recentTable.setPlaceholder(new Label("No transactions yet"));
    }

    private TableCell<RecentOrder, String> textCell(String styleClass) {
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

    // ------------------------------------------------------------------ HELPERS

    private Label emptyNote(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("empty-note");
        return l;
    }

    private void showError(String msg) {
        errorBanner.setText(msg);
        errorBanner.setVisible(true);
        errorBanner.setManaged(true);
    }
}