package com.coffeepos.renespresso.controller.main.admin.sales;

import com.coffeepos.renespresso.dao.SalesDAO;
import com.coffeepos.renespresso.dao.SalesDAO.Period;
import com.coffeepos.renespresso.dao.SalesDAO.Point;
import com.coffeepos.renespresso.dao.SalesDAO.ProductRow;
import com.coffeepos.renespresso.dao.SalesDAO.SalesData;
import com.coffeepos.renespresso.dao.SalesDAO.Slice;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SalesController {

    private static final String BROWN = "#5E3023";
    private static final String BROWN_MID = "#895737";
    private static final String INK = "#2F2118";
    private static final String MUTED = "#8C7A6A";
    private static final String CREAM = "#F6EDE0";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SINCE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    // header
    @FXML private Label lblDate, lblUpdated;
    @FXML private Button btnRefresh;
    @FXML private ToggleGroup periodGroup;
    @FXML private Label lblRangeNote;

    // top KPIs
    @FXML private Label lblSalesToday, lblOrdersToday;
    @FXML private Label lblSalesWeek, lblOrdersWeek;
    @FXML private Label lblSalesMonth, lblOrdersMonth;
    @FXML private Label lblSalesYear, lblOrdersYear;

    // selected-range KPIs
    @FXML private Label lblRangeRevenue, lblRangeOrders, lblAvgOrder;
    @FXML private Label lblCancelled, lblCancelRate, lblVat, lblDiscount;

    // charts
    @FXML private Label lblChartTitle, lblChartSub, lblTrend, lblBest;
    @FXML private BarChart<String, Number> salesChart;
    @FXML private LineChart<String, Number> ordersChart;
    @FXML private BarChart<String, Number> hourChart;

    // lists
    @FXML private VBox boxTopProducts, boxTopCashiers;

    private int requestId = 0;   // ignores slow results from an older period click

    // ------------------------------------------------------------------ INIT

    @FXML
    public void initialize() {
        salesChart.setAnimated(false);
        ordersChart.setAnimated(false);
        hourChart.setAnimated(false);

        ((NumberAxis) salesChart.getYAxis()).setTickLabelFormatter(pesoAxis());
        ((NumberAxis) hourChart.getYAxis()).setTickLabelFormatter(pesoAxis());

        // one period button always stays selected
        periodGroup.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null) {
                if (old != null) old.setSelected(true);
                return;
            }
            loadSales();
        });

        lblDate.setText(LocalDateTime.now().format(DATE_FMT));
        loadSales();
    }

    private static StringConverter<Number> pesoAxis() {
        return new StringConverter<>() {
            @Override public String toString(Number n) { return "₱" + String.format("%,.0f", n.doubleValue()); }
            @Override public Number fromString(String s) { return 0; }
        };
    }

    // ------------------------------------------------------------------ LOAD

    @FXML
    public void loadSales() {
        Toggle sel = periodGroup.getSelectedToggle();
        Period period = sel == null ? Period.DAILY : Period.valueOf(String.valueOf(sel.getUserData()));
        int myRequest = ++requestId;

        btnRefresh.setDisable(true);

        Task<SalesData> task = new Task<>() {
            @Override
            protected SalesData call() throws Exception {
                return SalesDAO.load(period);
            }
        };

        task.setOnSucceeded(e -> {
            if (myRequest != requestId) return;
            show(task.getValue());
            lblDate.setText(LocalDateTime.now().format(DATE_FMT));
            lblUpdated.setText("Updated " + LocalDateTime.now().format(TIME_FMT));
            btnRefresh.setDisable(false);
        });

        task.setOnFailed(e -> {
            if (myRequest != requestId) return;
            btnRefresh.setDisable(false);
            task.getException().printStackTrace();
            lblUpdated.setText("Error loading sales data");
        });

        Thread t = new Thread(task, "sales-loader");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ SHOW

    private void show(SalesData d) {
        Period period = d.period();

        // top KPIs
        var k = d.kpis();
        lblSalesToday.setText(money(k.todaySales()));
        lblOrdersToday.setText(orders(k.todayOrders()));
        lblSalesWeek.setText(money(k.weekSales()));
        lblOrdersWeek.setText(orders(k.weekOrders()));
        lblSalesMonth.setText(money(k.monthSales()));
        lblOrdersMonth.setText(orders(k.monthOrders()));
        lblSalesYear.setText(money(k.yearSales()));
        lblOrdersYear.setText(orders(k.yearOrders()));

        // selected-range KPIs
        var t = d.totals();
        lblRangeNote.setText(d.rangeLabel());
        lblRangeRevenue.setText(money(t.revenue()));
        lblRangeOrders.setText(String.valueOf(t.orders()));
        lblAvgOrder.setText(money(t.orders() == 0 ? 0 : t.revenue() / t.orders()));
        lblCancelled.setText(String.valueOf(t.cancelled()));
        int all = t.orders() + t.cancelled();
        lblCancelRate.setText(all == 0 ? "0% of all orders"
                : String.format("%.1f%% of all orders", t.cancelled() * 100.0 / all));
        lblVat.setText(money(t.vat()));
        lblDiscount.setText(money(t.discount()));

        showTrendCharts(d);
        showHours(d.hours());
        showProducts(d.products());
        showCashiers(d.cashiers());
    }

    private void showTrendCharts(SalesData d) {
        Period period = d.period();
        List<Point> pts = d.series();

        XYChart.Series<String, Number> rev = new XYChart.Series<>();
        XYChart.Series<String, Number> ord = new XYChart.Series<>();
        rev.setName("Revenue");
        ord.setName("Orders");
        for (Point p : pts) {
            rev.getData().add(new XYChart.Data<>(p.label(), p.revenue()));
            ord.getData().add(new XYChart.Data<>(p.label(), p.orders()));
        }
        addTooltips(rev, true);
        addTooltips(ord, false);

        if (salesChart.getXAxis() instanceof javafx.scene.chart.CategoryAxis catX) {
            catX.getCategories().clear();
        }
        if (ordersChart.getXAxis() instanceof javafx.scene.chart.CategoryAxis catX) {
            catX.getCategories().clear();
        }

        salesChart.getData().clear();
        salesChart.getData().add(rev);
        ordersChart.getData().clear();
        ordersChart.getData().add(ord);

        lblChartTitle.setText(period.title + " Revenue");
        lblChartSub.setText(period.range + " \u2022 cancelled orders excluded");

        // current vs previous bucket, and the best bucket
        if (pts.isEmpty()) {
            lblTrend.setText("");
            lblBest.setText("");
            return;
        }
        Point cur = pts.get(pts.size() - 1);
        Point prev = pts.size() > 1 ? pts.get(pts.size() - 2) : null;
        String trend = "Current " + period.unit + " (in progress): " + money(cur.revenue());
        if (prev != null && prev.revenue() > 0) {
            double pct = (cur.revenue() - prev.revenue()) / prev.revenue() * 100;
            trend += "   " + (pct >= 0 ? "\u25B2 " : "\u25BC ") + String.format("%.1f%%", Math.abs(pct))
                    + " vs previous " + period.unit;
        }
        lblTrend.setText(trend);

        Point best = pts.get(0);
        double sum = 0;
        for (Point p : pts) {
            if (p.revenue() > best.revenue()) best = p;
            sum += p.revenue();
        }
        lblBest.setText(best.revenue() <= 0 ? "No sales in this range"
                : "Best " + period.unit + ": " + best.label() + " (" + money(best.revenue()) + ")"
                + "   \u2022   Average per " + period.unit + ": " + money(sum / pts.size()));
    }

    private void showHours(List<Slice> hours) {
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("Revenue");
        for (Slice h : hours) {
            XYChart.Data<String, Number> point = new XYChart.Data<>(hourLabel(Integer.parseInt(h.name())), h.value());
            String tip = hourLabel(Integer.parseInt(h.name())) + ": " + money(h.value()) + " \u2022 " + orders(h.count());
            point.nodeProperty().addListener((o, a, n) -> { if (n != null) Tooltip.install(n, new Tooltip(tip)); });
            s.getData().add(point);
        }
        if (hourChart.getXAxis() instanceof javafx.scene.chart.CategoryAxis catX) {
            catX.getCategories().clear();
        }
        hourChart.getData().clear();
        hourChart.getData().add(s);
    }

    private void showProducts(List<ProductRow> products) {
        boxTopProducts.getChildren().clear();
        if (products.isEmpty()) {
            boxTopProducts.getChildren().add(emptyNote());
            return;
        }
        int rank = 1;
        for (ProductRow p : products) {
            boxTopProducts.getChildren().add(rankRow(rank++, p.name(), p.qty() + " sold", money(p.revenue())));
        }
    }

    private void showCashiers(List<Slice> cashiers) {
        boxTopCashiers.getChildren().clear();
        if (cashiers.isEmpty()) {
            boxTopCashiers.getChildren().add(emptyNote());
            return;
        }
        int rank = 1;
        for (Slice c : cashiers) {
            boxTopCashiers.getChildren().add(rankRow(rank++, c.name(), orders(c.count()), money(c.value())));
        }
    }

    // ------------------------------------------------------------------ SMALL HELPERS

    private static HBox rankRow(int rank, String name, String sub, String amount) {
        Label badge = new Label(String.valueOf(rank));
        badge.setMinSize(28, 28);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle("-fx-background-color: " + CREAM + "; -fx-background-radius: 8;"
                + "-fx-font-weight: bold; -fx-text-fill: " + BROWN_MID + ";");

        Label n = new Label(name);
        n.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + INK + ";");
        Label s = new Label(sub);
        s.setStyle("-fx-font-size: 12px; -fx-text-fill: " + MUTED + ";");
        VBox text = new VBox(2, n, s);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label a = new Label(amount);
        a.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + BROWN + ";");

        HBox row = new HBox(12, badge, text, a);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label emptyNote() {
        Label l = new Label("No sales in this range.");
        l.setStyle("-fx-text-fill: " + MUTED + ";");
        return l;
    }

    private static void addTooltips(XYChart.Series<String, Number> series, boolean isMoney) {
        for (XYChart.Data<String, Number> d : series.getData()) {
            String tip = d.getXValue() + ": " + (isMoney
                    ? money(d.getYValue().doubleValue())
                    : orders(d.getYValue().intValue()));
            d.nodeProperty().addListener((o, a, n) -> { if (n != null) Tooltip.install(n, new Tooltip(tip)); });
        }
    }

    private static String hourLabel(int h) {
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + (h < 12 ? "am" : "pm");
    }

    private static String money(double v) {
        return "₱" + String.format("%,.2f", v);
    }

    private static String orders(int n) {
        return n + (n == 1 ? " order" : " orders");
    }
}