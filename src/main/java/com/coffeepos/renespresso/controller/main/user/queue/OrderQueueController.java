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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
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
            done.setOnAction(e -> {
                if (AlertUtil.showYesNo("Complete Order", "Mark order " + r.getTicketNumber() + " as completed?")) {
                    session.completeOrder(r);
                    render();
                }
            });
            actions.getChildren().add(done);
        }
        Button details = new Button("Details");
        details.getStyleClass().add("btn-secondary-sm");
        details.setOnAction(e -> toggleOrderDetails(r));
        actions.getChildren().add(details);

        VBox card = new VBox(10, head, meta, new Separator(), lines, totalRow, actions);
        card.getStyleClass().add("order-card");
        if (isSameOrder(r, selected)) {
            card.getStyleClass().add("order-card-selected");
        }

        card.setOnMouseClicked(e -> {
            if (isButtonOrInteractive(e.getTarget())) return;
            toggleOrderDetails(r);
        });
        card.setStyle("-fx-cursor: hand;");
        return card;
    }

    // =====================================================================
    //  DETAIL PANEL & SELECTION
    // =====================================================================
    public void toggleOrderDetails(TransactionRecord r) {
        if (r == null) return;
        if (selected != null && isSameOrder(r, selected) && detailPanel.isVisible()) {
            closeDetails();
            return;
        }
        selectAndShowOrder(r);
    }

    public void selectAndShowOrder(TransactionRecord r) {
        if (r == null) return;
        txtQueueSearch.setText("");
        if (filter != null && r.getStatus() != filter) {
            filter = null;
        }

        TransactionRecord target = session.transactionHistory.stream()
                .filter(rec -> isSameOrder(rec, r))
                .findFirst()
                .orElse(r);

        selected = target;
        render();
        showDetails(target);
    }

    public void showDetails(TransactionRecord r) {
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
        if (r.getLines().isEmpty()) {
            Label noLines = new Label(r.getItems() != null && !r.getItems().isBlank() ? r.getItems() : "No item details");
            noLines.getStyleClass().add("card-subtitle");
            items.add(noLines, 0, 1, 3, 1);
        }

        items.setPadding(new Insets(0, 6, 0, 0));

        ScrollPane itemsScroll = new ScrollPane(items);
        itemsScroll.setFitToWidth(true);
        itemsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        itemsScroll.getStyleClass().add("transparent-scrollpane");
        itemsScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
        VBox.setVgrow(itemsScroll, Priority.ALWAYS);

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

        VBox footerBox = new VBox(12);
        footerBox.setMinHeight(Region.USE_PREF_SIZE);
        footerBox.getChildren().addAll(new Separator(), totals, new Separator(), totalRow);

        if (r.getStatus() == OrderStatus.PREPARING) {
            Button done = new Button("Complete Order");
            done.getStyleClass().add("btn-primary");
            done.setMaxWidth(Double.MAX_VALUE);
            done.setOnAction(e -> {
                if (AlertUtil.showYesNo("Complete Order", "Mark order " + r.getTicketNumber() + " as completed?")) {
                    session.completeOrder(r);
                    render();
                }
            });

            Button cancel = new Button("Cancel Order");
            cancel.getStyleClass().add("btn-danger");
            cancel.setMaxWidth(Double.MAX_VALUE);
            cancel.setOnAction(e -> {
                if (!AlertUtil.showYesNo("Cancel Order",
                        "Cancel order " + r.getTicketNumber() + "? This action requires Admin authorization.")) {
                    return;
                }

                boolean authorized = AlertUtil.requestAdminAuthorization(
                        "Admin Authorization Required",
                        "Please enter Admin or Manager credentials to cancel order " + r.getTicketNumber() + ".",
                        "Authorize Cancel"
                );

                if (!authorized) {
                    return;
                }

                session.cancelOrder(r);
                closeDetails();
                AlertUtil.showSuccess("Order Cancelled",
                        "Order " + r.getTicketNumber() + " has been cancelled with Admin authorization.");
            });
            footerBox.getChildren().addAll(done, cancel);
        }

        Button receiptBtn = new Button("View Receipt");
        receiptBtn.getStyleClass().add("btn-secondary");
        receiptBtn.setMaxWidth(Double.MAX_VALUE);
        receiptBtn.setOnAction(e -> UserUi.showReceiptDialog(r));

        Button back = new Button("Close Details");
        back.getStyleClass().add("btn-secondary");
        back.setMaxWidth(Double.MAX_VALUE);
        back.setOnAction(e -> closeDetails());
        footerBox.getChildren().addAll(receiptBtn, back);

        detailPanel.getChildren().addAll(head, sub, info, itemsScroll, footerBox);

        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
    }

    private void closeDetails() {
        selected = null;
        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
        render();
    }

    private boolean isSameOrder(TransactionRecord a, TransactionRecord b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        if (a.getTransactionId() > 0 && b.getTransactionId() > 0) {
            return a.getTransactionId() == b.getTransactionId();
        }
        return a.getTicketNumber() != null && a.getTicketNumber().equals(b.getTicketNumber());
    }

    private boolean isButtonOrInteractive(Object target) {
        if (target instanceof javafx.scene.Node n) {
            while (n != null) {
                if (n instanceof javafx.scene.control.ButtonBase) return true;
                n = n.getParent();
            }
        }
        return false;
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