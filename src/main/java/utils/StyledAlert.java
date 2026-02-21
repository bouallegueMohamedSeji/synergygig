package utils;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Themed alert / confirm dialogs that match the application design.
 * Replaces raw {@code javafx.scene.control.Alert} everywhere.
 */
public final class StyledAlert {

    private StyledAlert() {}

    /* ═══════════════════════════════════════ */
    /*  PUBLIC API                             */
    /* ═══════════════════════════════════════ */

    /** Show a simple info / success / error / warning alert with a single "Got it" button. */
    public static void show(Window owner, String title, String content, String type) {
        buildAndShow(owner, title, content, type, false, null);
    }

    /**
     * Show a confirmation dialog with Yes / No buttons.
     * @return {@code true} if the user clicked Yes.
     */
    public static boolean confirm(Window owner, String title, String content) {
        AtomicBoolean result = new AtomicBoolean(false);
        buildAndShow(owner, title, content, "confirm", true, result);
        return result.get();
    }

    /* ═══════════════════════════════════════ */
    /*  INTERNALS                              */
    /* ═══════════════════════════════════════ */

    private static void buildAndShow(Window owner, String title, String content,
                                     String type, boolean isConfirm, AtomicBoolean result) {
        boolean dark = SessionManager.getInstance().isDarkTheme();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        /* ── Icon ── */
        String iconSymbol, iconColor;
        switch (type) {
            case "success": iconSymbol = "\u2713"; iconColor = "#34d399"; break;
            case "info":    iconSymbol = "\u2709"; iconColor = dark ? "#90DDF0" : "#613039"; break;
            case "confirm": iconSymbol = "?";      iconColor = dark ? "#90DDF0" : "#613039"; break;
            case "warning": iconSymbol = "\u26A0"; iconColor = "#FBBF24"; break;
            default:        iconSymbol = "\u26A0"; iconColor = "#f87171"; break; // error
        }

        Label icon = new Label(iconSymbol);
        icon.setStyle("-fx-font-size: 22; -fx-text-fill: " + iconColor + "; -fx-font-weight: bold;");
        StackPane iconCircle = new StackPane(icon);
        iconCircle.setStyle("-fx-background-color: " + iconColor + "20; -fx-background-radius: 50; "
                + "-fx-min-width: 44; -fx-min-height: 44; -fx-max-width: 44; -fx-max-height: 44;");
        iconCircle.setAlignment(Pos.CENTER);

        /* ── Text ── */
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: " + (dark ? "#F0EDEE" : "#0D0A0B") + "; -fx-font-size: 14; -fx-font-weight: bold;");

        Label msgLbl = new Label(content);
        msgLbl.setStyle("-fx-text-fill: " + (dark ? "#9E9EA8" : "#4A3F42") + "; -fx-font-size: 12; -fx-line-spacing: 2;");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(280);

        /* ── Buttons ── */
        VBox card;
        if (isConfirm) {
            Button yes = styledBtn("Yes",  true,  dark);
            Button no  = styledBtn("No",   false, dark);

            yes.setOnAction(e -> { result.set(true);  closeDialog(dialog, (StackPane) yes.getScene().getRoot()); });
            no.setOnAction(e ->  { result.set(false); closeDialog(dialog, (StackPane) no.getScene().getRoot()); });

            HBox btnRow = new HBox(10, yes, no);
            btnRow.setAlignment(Pos.CENTER);
            card = new VBox(10, iconCircle, titleLbl, msgLbl, btnRow);
        } else {
            Button ok = styledBtn("Got it", true, dark);
            ok.setOnAction(e -> closeDialog(dialog, (StackPane) ok.getScene().getRoot()));
            card = new VBox(10, iconCircle, titleLbl, msgLbl, ok);
        }

        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20, 24, 16, 24));
        card.setMaxWidth(320);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        String cardBg     = dark ? "#14131A" : "#FFFFFF";
        String cardBorder  = dark ? "#1C1B22" : "#E0D6D8";
        String cardShadow  = dark ? "rgba(0,0,0,0.5)" : "rgba(97,48,57,0.15)";
        card.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 14; "
                + "-fx-border-color: " + cardBorder + "; -fx-border-radius: 14; -fx-border-width: 1; "
                + "-fx-effect: dropshadow(gaussian, " + cardShadow + ", 24, 0, 0, 8);");

        String overlayBg = dark ? "rgba(0,0,0,0.55)" : "rgba(0,0,0,0.3)";
        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: " + overlayBg + ";");
        root.setAlignment(Pos.CENTER);
        root.setOnMouseClicked(e -> {
            if (e.getTarget() == root) {
                if (isConfirm && result != null) result.set(false);
                closeDialog(dialog, root);
            }
        });

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);

        if (owner instanceof Stage) {
            Stage s = (Stage) owner;
            dialog.setWidth(s.getWidth());
            dialog.setHeight(s.getHeight());
            dialog.setX(s.getX());
            dialog.setY(s.getY());
        }

        root.setOpacity(0);

        // Start fade-in, then show blocking dialog
        javafx.application.Platform.runLater(() -> {
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), root);
            fadeIn.setToValue(1);
            fadeIn.play();
        });
        dialog.showAndWait();          // blocking
    }

    /* ── Button factory ── */
    private static Button styledBtn(String text, boolean primary, boolean dark) {
        Button btn = new Button(text);
        String normal, hover;
        if (primary) {
            String g1 = dark ? "#07393C" : "#613039";
            String g2 = dark ? "#2C666E" : "#8C4A56";
            String ft = "#F0EDEE";
            String hg1 = dark ? "#2C666E" : "#8C4A56";
            String hg2 = dark ? "#90DDF0" : "#DE95A2";
            String ht  = dark ? "#0A090C" : "#FFFFFF";
            normal = "-fx-background-color: linear-gradient(to right," + g1 + "," + g2 + "); -fx-text-fill:" + ft + "; "
                    + "-fx-font-size:12; -fx-font-weight:600; -fx-background-radius:8; -fx-padding:7 28; -fx-cursor:hand;";
            hover  = "-fx-background-color: linear-gradient(to right," + hg1 + "," + hg2 + "); -fx-text-fill:" + ht + "; "
                    + "-fx-font-size:12; -fx-font-weight:600; -fx-background-radius:8; -fx-padding:7 28; -fx-cursor:hand;";
        } else {
            // ghost / secondary
            String bg  = dark ? "#1C1B22" : "#F0EDED";
            String bd  = dark ? "#2A2930" : "#E0D6D8";
            String ft  = dark ? "#9E9EA8" : "#4A3F42";
            String hbg = dark ? "#2A2930" : "#E0D6D8";
            String hft = dark ? "#F0EDEE" : "#0D0A0B";
            normal = "-fx-background-color: " + bg + "; -fx-border-color: " + bd + "; -fx-border-radius: 8; "
                    + "-fx-background-radius: 8; -fx-text-fill:" + ft + "; "
                    + "-fx-font-size:12; -fx-font-weight:600; -fx-padding:7 28; -fx-cursor:hand;";
            hover  = "-fx-background-color: " + hbg + "; -fx-border-color: " + bd + "; -fx-border-radius: 8; "
                    + "-fx-background-radius: 8; -fx-text-fill:" + hft + "; "
                    + "-fx-font-size:12; -fx-font-weight:600; -fx-padding:7 28; -fx-cursor:hand;";
        }
        btn.setStyle(normal);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e ->  btn.setStyle(normal));
        return btn;
    }

    private static void closeDialog(Stage dialog, StackPane root) {
        FadeTransition ft = new FadeTransition(Duration.millis(150), root);
        ft.setToValue(0);
        ft.setOnFinished(ev -> dialog.close());
        ft.play();
    }
}
