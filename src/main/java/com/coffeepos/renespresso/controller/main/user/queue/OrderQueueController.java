package com.coffeepos.renespresso.controller.main.user.queue;

import com.coffeepos.renespresso.controller.main.user.UserSession;
import com.coffeepos.renespresso.model.OrderLine;
import com.coffeepos.renespresso.model.OrderStatus;
import com.coffeepos.renespresso.model.TransactionRecord;
import com.coffeepos.renespresso.util.AlertUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

import com.coffeepos.renespresso.controller.main.user.UserUi;
import static com.coffeepos.renespresso.controller.main.user.UserUi.peso;

public class OrderQueueController {

    @FXML private TextField txtQueueSearch;
    @FXML private FlowPane statusChips, orderGrid;
    @FXML private VBox detailPanel;

    private final UserSession session = UserSession.get();
    private OrderStatus filter = null;          // null = All
    private TransactionRecord selected = null;

    @FXML
    public void initialize() {
        txtQueueSearch.textProperty().addListener((o, a, b) -> render());
        session.transactionHistory.addListener((ListChangeListener<TransactionRecord>) c -> render());

        // keep the "10 mins ago" labels fresh
        Timeline refresher = new Timeline(new KeyFrame(Duration.seconds(30), e -> render()));
        refresher.setCycleCount(Timeline.INDEFINITE);
        refresher.play();

        render();
    }

    // =====================================================================
    //  LIST
    // =====================================================================
    private void render() {
        List<TransactionRecord> all = session.transactionHistory;

        statusChips.getChildren().setAll(
                chip("All (" + all.size() + ")", null),
                chip("Preparing (" + count(all, OrderStatus.PREPARING) + ")", OrderStatus.PREPARING),
                chip("Completed (" + count(all, OrderStatus.COMPLETED) + ")", OrderStatus.COMPLETED),
                chip("Cancelled (" + count(all, OrderStatus.CANCELLED) + ")", OrderStatus.CANCELLED));

        String q = txtQueueSearch.getText() == null ? "" : txtQueueSearch.getText().trim().toLowerCase();
        List<TransactionRecord> shown = all.stream()
                .filter(r -> filter == null || r.getStatus() == filter)
                .filter(r -> q.isEmpty()
                        || (r.getTicketNumber() != null && r.getTicketNumber().toLowerCase().contains(q))
                        || (r.getItems() != null && r.getItems().toLowerCase().contains(q))
                        || (r.getLines() != null && r.getLines().stream().anyMatch(l -> l.name() != null && l.name().toLowerCase().contains(q))))
                .toList();

        orderGrid.getChildren().clear();
        if (shown.isEmpty()) {
            Label none = new Label(all.isEmpty()
                    ? "No orders yet. Orders appear here after they are paid at the register."
                    : "No orders match your search or filter.");
            none.getStyleClass().add("empty-note");
            orderGrid.getChildren().add(none);
        } else {
            for (TransactionRecord r : shown) orderGrid.getChildren().add(buildCard(r));
        }

        if (selected != null) showDetails(selected);
    }

    private long count(List<TransactionRecord> all, OrderStatus s) {
        return all.stream().filter(r -> r.getStatus() == s).count();
    }

    private ToggleButton chip(String text, OrderStatus status) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add("chip");
        b.setSelected(filter == status);
        b.setOnAction(e -> { filter = status; render(); });
        return b;
    }

    private Label pill(OrderStatus status) {
        Label l = new Label(status.label());
        l.getStyleClass().addAll("pill", status.styleClass());
        return l;
    }

    private VBox buildCard(TransactionRecord r) {
        Label title = new Label("Order " + r.getTicketNumber());
        title.getStyleClass().add("order-title");
        Label sub = new Label(r.getPaymentMethod() + "  \u00B7  " + r.getCashier());
        sub.getStyleClass().add("card-subtitle");
        Region sp1 = new Region();
        HBox.setHgrow(sp1, Priority.ALWAYS);
        HBox head = new HBox(new VBox(2, title, sub), sp1, pill(r.getStatus()));
        head.setAlignment(Pos.TOP_LEFT);

        Label ago = new Label(r.getTimeAgo());
        ago.getStyleClass().add("card-subtitle");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        Label count = new Label(r.getItemCount() + (r.getItemCount() == 1 ? " Item" : " Items"));
        count.getStyleClass().add("order-items");
        HBox meta = new HBox(ago, sp2, count);

        VBox lines = new VBox(6);
        int max = 3;
        List<OrderLine> ls = r.getLines();
        for (int i = 0; i < Math.min(max, ls.size()); i++) {
            Label n = new Label(ls.get(i).label());
            n.getStyleClass().add("order-line");
            Region s = new Region();
            HBox.setHgrow(s, Priority.ALWAYS);
            Label qty = new Label("\u00D7" + ls.get(i).qty());
            qty.getStyleClass().add("order-line-qty");
            lines.getChildren().add(new HBox(n, s, qty));
        }
        if (ls.size() > max) {
            Label more = new Label("+" + (ls.size() - max) + " more");
            more.getStyleClass().add("card-subtitle");
            lines.getChildren().add(more);
        }

        Label totalLbl = new Label("Total Amount");
        totalLbl.getStyleClass().add("card-subtitle");
        Region sp3 = new Region();
        HBox.setHgrow(sp3, Priority.ALWAYS);
        Label total = new Label(r.getTotalPaid());
        total.getStyleClass().add("order-total");
        HBox totalRow = new HBox(totalLbl, sp3, total);
        totalRow.setAlignment(Pos.CENTER_LEFT);

        HBox actions = new HBox(10);
        if (r.getStatus() == OrderStatus.PREPARING) {
            Button done = new Button("Complete Order");
            done.getStyleClass().add("btn-primary-sm");
            done.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(done, Priority.ALWAYS);
            done.setOnAction(e -> session.completeOrder(r));
            actions.getChildren().add(done);
        }
        Button details = new Button("Details");
        details.getStyleClass().add("btn-secondary-sm");
        details.setOnAction(e -> showDetails(r));
        actions.getChildren().add(details);

        VBox card = new VBox(10, head, meta, new Separator(), lines, totalRow, actions);
        card.getStyleClass().add("order-card");
        if (r == selected) card.getStyleClass().add("order-card-selected");
        return card;
    }

    // =====================================================================
    //  DETAIL PANEL
    // =====================================================================
    private void showDetails(TransactionRecord r) {
        selected = r;
        detailPanel.getChildren().clear();

        Label title = new Label("Order " + r.getTicketNumber());
        title.getStyleClass().add("order-title-lg");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox head = new HBox(title, sp, pill(r.getStatus()));
        head.setAlignment(Pos.CENTER_LEFT);

        Label sub = new Label("Paid by " + r.getPaymentMethod());
        sub.getStyleClass().add("card-subtitle");

        VBox info = new VBox(8,
                infoRow("Time Placed", r.getTime() + "  (" + r.getTimeAgo() + ")"),
                infoRow("Cashier", r.getCashier()),
                infoRow("Terminal", UserSession.TERMINAL_ID));
        info.getStyleClass().add("detail-info");

        GridPane items = new GridPane();
        items.setHgap(10);
        items.setVgap(10);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints(48);
        c2.setHalignment(HPos.CENTER);
        ColumnConstraints c3 = new ColumnConstraints(96);
        c3.setHalignment(HPos.RIGHT);
        items.getColumnConstraints().addAll(c1, c2, c3);
        items.add(headerCell("Item"), 0, 0);
        items.add(headerCell("Qty"), 1, 0);
        items.add(headerCell("Price"), 2, 0);
        int row = 1;
        for (OrderLine l : r.getLines()) {
            Label n = new Label(l.label());
            n.getStyleClass().add("detail-item");
            n.setWrapText(true);
            Label q = new Label(String.valueOf(l.qty()));
            q.getStyleClass().add("detail-item");
            Label p = new Label(peso(l.lineTotal()));
            p.getStyleClass().add("detail-price");
            items.add(n, 0, row);
            items.add(q, 1, row);
            items.add(p, 2, row);
            row++;
        }

        VBox totals = new VBox(8, infoRow("VATable Sales", peso(r.getSubtotal())));
        if (r.isDiscounted()) totals.getChildren().add(infoRow("Senior/PWD (20%)", "-" + peso(r.getDiscount())));
        totals.getChildren().add(infoRow("VAT (12%)", peso(r.getVat())));

        Label totalLbl = new Label("TOTAL");
        totalLbl.getStyleClass().add("detail-total-label");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        Label total = new Label(r.getTotalPaid());
        total.getStyleClass().add("order-total-lg");
        HBox totalRow = new HBox(totalLbl, sp2, total);
        totalRow.setAlignment(Pos.CENTER_LEFT);

        detailPanel.getChildren().addAll(head, sub, info, items, new Separator(), totals, new Separator(), totalRow);

        if (r.getStatus() == OrderStatus.PREPARING) {
            Button done = new Button("Complete Order");
            done.getStyleClass().add("btn-primary");
            done.setMaxWidth(Double.MAX_VALUE);
            done.setOnAction(e -> session.completeOrder(r));

            Button cancel = new Button("Cancel Order");
            cancel.getStyleClass().add("btn-danger");
            cancel.setMaxWidth(Double.MAX_VALUE);
            cancel.setOnAction(e -> {
                if (AlertUtil.showYesNo("Cancel Order",
                        "Cancel order " + r.getTicketNumber() + "? It will be removed from today's totals.")) {
                    session.cancelOrder(r);
                }
            });
            detailPanel.getChildren().addAll(done, cancel);
        }

        Button receiptBtn = new Button("View Receipt");
        receiptBtn.getStyleClass().add("btn-secondary");
        receiptBtn.setMaxWidth(Double.MAX_VALUE);
        receiptBtn.setOnAction(e -> UserUi.showReceiptDialog(r));

        Button back = new Button("Go Back");
        back.getStyleClass().add("btn-secondary");
        back.setMaxWidth(Double.MAX_VALUE);
        back.setOnAction(e -> closeDetails());
        detailPanel.getChildren().addAll(receiptBtn, back);

        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
    }

    private void closeDetails() {
        selected = null;
        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
        render();
    }

    private HBox infoRow(String left, String right) {
        Label l = new Label(left);
        l.getStyleClass().add("card-subtitle");
        Region s = new Region();
        HBox.setHgrow(s, Priority.ALWAYS);
        Label r = new Label(right);
        r.getStyleClass().add("detail-price");
        return new HBox(l, s, r);
    }

    private Label headerCell(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("detail-head");
        return l;
    }
}