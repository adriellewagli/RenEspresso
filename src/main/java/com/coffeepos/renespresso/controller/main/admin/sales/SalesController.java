package com.coffeepos.renespresso.controller.main.admin.sales;

import com.coffeepos.renespresso.dao.TransactionDAO;
import com.coffeepos.renespresso.dao.TransactionDAO.DailySale;
import com.coffeepos.renespresso.dao.TransactionDAO.SalesSummary;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.coffeepos.renespresso.controller.main.user.UserUi.peso;

public class SalesController {

    @FXML private Label lblDate;
    @FXML private Label lblUpdated;
    @FXML private Button btnRefresh;

    @FXML private Label lblSalesToday, lblOrdersToday;
    @FXML private Label lblSalesWeek, lblOrdersWeek;
    @FXML private Label lblSalesMonth, lblOrdersMonth;

    @FXML private BarChart<String, Number> salesChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private record SalesResult(SalesSummary summary, List<DailySale> chartData) {}

    @FXML
    public void initialize() {
        if (salesChart != null) salesChart.setAnimated(false);
        if (lblDate != null) lblDate.setText(LocalDateTime.now().format(DATE_FMT));
        loadSales();
    }

    @FXML
    public void loadSales() {
        if (btnRefresh != null) btnRefresh.setDisable(true);

        Task<SalesResult> task = new Task<>() {
            @Override
            protected SalesResult call() {
                SalesSummary summary = TransactionDAO.getSalesSummary();
                List<DailySale> chartData = TransactionDAO.getLast14DaysSales();
                return new SalesResult(summary, chartData);
            }
        };

        task.setOnSucceeded(e -> {
            SalesResult res = task.getValue();
            SalesSummary s = res.summary();

            lblSalesToday.setText(peso(s.todaySales()));
            lblOrdersToday.setText(s.todayOrders() + (s.todayOrders() == 1 ? " order" : " orders"));

            lblSalesWeek.setText(peso(s.weekSales()));
            lblOrdersWeek.setText(s.weekOrders() + (s.weekOrders() == 1 ? " order" : " orders"));

            lblSalesMonth.setText(peso(s.monthSales()));
            lblOrdersMonth.setText(s.monthOrders() + (s.monthOrders() == 1 ? " order" : " orders"));

            if (salesChart != null) {
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName("Sales");
                for (DailySale ds : res.chartData()) {
                    series.getData().add(new XYChart.Data<>(ds.dateLabel(), ds.totalAmount()));
                }
                salesChart.getData().setAll(series);
            }

            if (lblDate != null) {
                lblDate.setText(LocalDateTime.now().format(DATE_FMT));
            }
            if (lblUpdated != null) {
                lblUpdated.setText("Updated " + LocalDateTime.now().format(TIME_FMT));
            }
            if (btnRefresh != null) btnRefresh.setDisable(false);
        });

        task.setOnFailed(e -> {
            if (btnRefresh != null) btnRefresh.setDisable(false);
            task.getException().printStackTrace();
            if (lblUpdated != null) {
                lblUpdated.setText("Error loading sales data");
            }
        });

        Thread t = new Thread(task, "sales-loader");
        t.setDaemon(true);
        t.start();
    }
}