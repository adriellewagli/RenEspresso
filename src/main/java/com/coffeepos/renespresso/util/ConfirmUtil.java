package com.coffeepos.renespresso.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Modern confirmation dialogs used before saving or discarding changes. Fully self-styled. */
public final class ConfirmUtil {

    private ConfirmUtil() {}

    private static final String BROWN = "#5E3023";
    private static final String CREAM = "#F3E9DC";
    private static final String INK = "#2F2118";
    private static final String MUTED = "#8C7A6A";

    // Material-style icon shapes
    private static final String CHECK_ICON = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
    private static final String WARN_ICON = "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z";

    // ------------------------------------------------------------------ PUBLIC API

    /** "Save these changes?" -> true if the user confirms. */
    public static boolean confirmSave(Node anchor, String message) {
        return ask(anchor, false, "Save changes?", message, "Yes, Save", "Review");
    }

    /** "Discard your changes?" -> true if the user wants to discard. */
    public static boolean confirmDiscard(Node anchor) {
        return ask(anchor, true, "Discard changes?",
                "You have unsaved changes. If you cancel now, they will be lost.",
                "Discard", "Keep Editing");
    }

    /** Collects every stylesheet from the node and its parents (used by other dialogs). */
    public static List<String> collectStylesheets(Node node) {
        List<String> sheets = new ArrayList<>();
        Node n = node;
        while (n != null) {
            if (n instanceof Parent p) {
                for (String s : p.getStylesheets()) {
                    if (!sheets.contains(s)) sheets.add(s);
                }
            }
            n = n.getParent();
        }
        return sheets;
    }

    // ------------------------------------------------------------------ DIALOG

    private static boolean ask(Node anchor, boolean danger, String title, String message,
                               String yesText, String noText) {
        ButtonType yes = new ButtonType(yesText, ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType(noText, ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initStyle(StageStyle.TRANSPARENT);   // rounded card, no grey window frame
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        if (anchor != null && anchor.getScene() != null) {
            Window owner = anchor.getScene().getWindow();
            dialog.initOwner(owner);
        }

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(yes, no);
        pane.setStyle("-fx-background-color: #FFFDFA; -fx-background-insets: 18; -fx-background-radius: 20;"
                + "-fx-padding: 18; -fx-font-size: 14px;"
                + "-fx-effect: dropshadow(gaussian, rgba(94,48,35,0.28), 18, 0, 0, 5);");
        dialog.setOnShowing(e -> pane.getScene().setFill(Color.TRANSPARENT));

        // padding of the button row (kept transparent so the rounded card shows through)
        String css = ".dialog-pane > .button-bar { -fx-background-color: transparent; }"
                + ".dialog-pane > .button-bar > .container { -fx-padding: 6 34 26 34; -fx-spacing: 12;"
                + " -fx-background-color: transparent; }"
                + ".dialog-pane > .content { -fx-background-color: transparent; }";
        pane.getStylesheets().add("data:text/css;base64,"
                + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8)));

        // ---- content: icon, title, message
        String iconBg = danger ? "#FBE3DF" : "#F6E7D5";
        String iconFg = danger ? "#C0392B" : "#895737";

        Region glyph = new Region();
        glyph.setMinSize(28, 28);
        glyph.setPrefSize(28, 28);
        glyph.setMaxSize(28, 28);
        glyph.setStyle("-fx-background-color: " + iconFg + "; -fx-shape: \""
                + (danger ? WARN_ICON : CHECK_ICON) + "\";");
        StackPane iconCircle = new StackPane(glyph);
        iconCircle.setMinSize(64, 64);
        iconCircle.setPrefSize(64, 64);
        iconCircle.setMaxSize(64, 64);
        iconCircle.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 32;");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + BROWN + ";");

        Label msgLbl = new Label(message);
        msgLbl.setWrapText(true);
        msgLbl.setTextAlignment(TextAlignment.CENTER);
        msgLbl.setAlignment(Pos.CENTER);
        msgLbl.setMaxWidth(340);
        msgLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: " + MUTED + ";");

        VBox content = new VBox(14, iconCircle, titleLbl, msgLbl);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(26, 34, 10, 34));
        content.setPrefWidth(420);
        pane.setContent(content);

        // ---- buttons
        Button yesBtn = (Button) pane.lookupButton(yes);
        Button noBtn = (Button) pane.lookupButton(no);
        String base = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 12;"
                + "-fx-padding: 12 22; -fx-min-width: 140; -fx-cursor: hand;";
        yesBtn.setStyle(base + (danger
                ? "-fx-background-color: #C0392B; -fx-text-fill: #FFFFFF;"
                : "-fx-background-color: " + BROWN + "; -fx-text-fill: " + CREAM + ";"));
        noBtn.setStyle(base + "-fx-background-color: #FFFFFF; -fx-text-fill: " + BROWN
                + "; -fx-border-color: #E7DCCB; -fx-border-radius: 12;");

        // for "discard", Enter should pick the safe option
        yesBtn.setDefaultButton(!danger);
        noBtn.setDefaultButton(danger);

        return dialog.showAndWait().filter(b -> b == yes).isPresent();
    }
}