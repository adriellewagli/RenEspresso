package com.coffeepos.renespresso.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/** Styled confirmation alerts used before saving or discarding changes. */
public final class ConfirmUtil {

    private ConfirmUtil() {}

    /** "Are you sure you want to save?" -> true if the user confirms. */
    public static boolean confirmSave(Node anchor, String message) {
        return ask(anchor, "Confirm Save", message, "Yes, Save", "Review", false);
    }

    /** "Discard your changes?" -> true if the user wants to discard. */
    public static boolean confirmDiscard(Node anchor) {
        return ask(anchor, "Discard Changes",
                "You have unsaved changes. If you cancel now, they will be lost.",
                "Discard", "Keep Editing", true);
    }

    /** Collects every stylesheet from the node and its parents so alerts match the page. */
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

    private static boolean ask(Node anchor, String title, String message,
                               String yesText, String noText, boolean safeIsNo) {
        ButtonType yes = new ButtonType(yesText, ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType(noText, ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.NONE, message, yes, no);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setGraphic(null);

        if (anchor != null && anchor.getScene() != null) {
            Window owner = anchor.getScene().getWindow();
            alert.initOwner(owner);
            alert.getDialogPane().getStylesheets().addAll(collectStylesheets(anchor));
        }
        alert.getDialogPane().getStyleClass().add("confirm-dialog");

        Button yesBtn = (Button) alert.getDialogPane().lookupButton(yes);
        Button noBtn = (Button) alert.getDialogPane().lookupButton(no);
        yesBtn.getStyleClass().add("dialog-primary");
        noBtn.getStyleClass().add("dialog-secondary");
        // For destructive confirmations, Enter should pick the safe option
        yesBtn.setDefaultButton(!safeIsNo);
        noBtn.setDefaultButton(safeIsNo);

        return alert.showAndWait().filter(b -> b == yes).isPresent();
    }
}