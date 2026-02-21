package utils;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Centralized helper for theming Dialog popups.
 * All dialogs get UNDECORATED style + a custom title bar with
 * minimize / maximize / close buttons + drag-to-move support.
 */
public final class DialogHelper {

    private static final String STYLE_CSS = "/css/style.css";
    private static final String LIGHT_CSS = "/css/light-theme.css";

    private DialogHelper() {}

    /* ── CSS-only theming (no stage manipulation) ── */

    public static void theme(DialogPane pane) {
        String darkCss = DialogHelper.class.getResource(STYLE_CSS).toExternalForm();
        if (!pane.getStylesheets().contains(darkCss)) {
            pane.getStylesheets().add(darkCss);
        }
        if (!SessionManager.getInstance().isDarkTheme()) {
            String lightCss = DialogHelper.class.getResource(LIGHT_CSS).toExternalForm();
            if (!pane.getStylesheets().contains(lightCss)) {
                pane.getStylesheets().add(lightCss);
            }
        }
    }

    /* ── Full theming: UNDECORATED + CSS + custom title bar ── */

    public static void theme(Dialog<?> dialog) {
        try { dialog.initStyle(StageStyle.UNDECORATED); } catch (Exception ignored) {}
        DialogPane pane = dialog.getDialogPane();
        theme(pane);
        // Defer title bar injection until the dialog is about to show,
        // so any setHeaderText / setContent calls made after theme() are captured.
        pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && pane.lookup(".dialog-title-bar") == null) {
                injectTitleBar(dialog, pane);
            }
        });
    }

    /* ── Hidden close button so the X works ── */

    public static void hideCloseButton(DialogPane pane) {
        if (!pane.getButtonTypes().contains(ButtonType.CLOSE)) {
            pane.getButtonTypes().add(ButtonType.CLOSE);
        }
        Node closeBtn = pane.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.setVisible(false);
            closeBtn.setManaged(false);
        }
    }

    /* ── Convenience factory ── */

    public static Dialog<Void> createThemedDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(null);
        dialog.setHeaderText(null);
        theme(dialog);
        hideCloseButton(dialog.getDialogPane());
        return dialog;
    }

    /* ════════════════════════════════════════════════════════
       PRIVATE: inject the custom title bar
       ════════════════════════════════════════════════════════ */

    private static void injectTitleBar(Dialog<?> dialog, DialogPane pane) {
        // Ensure a hidden CLOSE button exists for programmatic close
        hideCloseButton(pane);

        // ── Grab texts before we modify anything ──
        String titleText   = dialog.getTitle();
        String headerText  = pane.getHeaderText();
        String contentText = pane.getContentText();
        Node existingContent = pane.getContent();

        // Title bar uses dialog title; fallback to headerText
        String barText = (titleText != null && !titleText.isBlank()) ? titleText
                       : (headerText != null && !headerText.isBlank()) ? headerText : "";

        // ── Title label ──
        Label titleLabel = new Label(barText);
        titleLabel.getStyleClass().add("dialog-title-text");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // ── Window control buttons ──
        Button btnMin   = windowBtn("\u2013", "dialog-window-btn-min");   // –
        Button btnMax   = windowBtn("\u25A1", "dialog-window-btn-max");   // □
        Button btnClose = windowBtn("\u2715", "dialog-window-btn-close"); // ✕

        btnMin.setOnAction(e -> {
            Stage s = getStage(pane);
            if (s != null) s.setIconified(true);
        });
        btnMax.setOnAction(e -> {
            Stage s = getStage(pane);
            if (s != null) s.setMaximized(!s.isMaximized());
        });
        btnClose.setOnAction(e -> {
            // Fire the hidden CLOSE button through Dialog's close mechanism
            Node cn = pane.lookupButton(ButtonType.CLOSE);
            if (cn instanceof ButtonBase) {
                ((ButtonBase) cn).fire();
            } else {
                Window w = pane.getScene() != null ? pane.getScene().getWindow() : null;
                if (w != null) w.hide();
            }
        });

        // ── Title bar container ──
        HBox titleBar = new HBox(8, titleLabel, btnMin, btnMax, btnClose);
        titleBar.getStyleClass().add("dialog-title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);

        // ── Drag support ──
        final double[] offset = new double[2];
        titleBar.setOnMousePressed(ev -> {
            Stage s = getStage(pane);
            if (s != null) {
                offset[0] = s.getX() - ev.getScreenX();
                offset[1] = s.getY() - ev.getScreenY();
            }
        });
        titleBar.setOnMouseDragged(ev -> {
            Stage s = getStage(pane);
            if (s != null) {
                s.setX(ev.getScreenX() + offset[0]);
                s.setY(ev.getScreenY() + offset[1]);
            }
        });
        titleBar.setCursor(Cursor.MOVE);

        // ── Preserve native headerText / contentText as styled labels ──
        boolean hasHeader  = headerText  != null && !headerText.isBlank();
        boolean hasContent = contentText != null && !contentText.isBlank();

        if (hasHeader || (existingContent == null && hasContent)) {
            VBox wrapper = new VBox(8);
            wrapper.getStyleClass().add("dialog-content-wrapper");

            if (hasHeader) {
                Label hl = new Label(headerText);
                hl.getStyleClass().add("dialog-header-label");
                hl.setWrapText(true);
                hl.setMaxWidth(Double.MAX_VALUE);
                wrapper.getChildren().add(hl);
            }

            if (existingContent != null) {
                wrapper.getChildren().add(existingContent);
            } else if (hasContent) {
                Label cl = new Label(contentText);
                cl.getStyleClass().add("dialog-content-label");
                cl.setWrapText(true);
                cl.setMaxWidth(Double.MAX_VALUE);
                wrapper.getChildren().add(cl);
            }

            pane.setContent(wrapper);
            pane.setContentText(null);
        }

        // ── Clear native header chrome, inject title bar ──
        pane.setHeaderText(null);
        pane.setGraphic(null);
        pane.setHeader(titleBar);
        pane.getStyleClass().add("dialog-with-titlebar");
    }

    /** Small factory for the three window-control buttons. */
    private static Button windowBtn(String symbol, String extraClass) {
        Button btn = new Button(symbol);
        btn.getStyleClass().addAll("dialog-window-btn", extraClass);
        btn.setFocusTraversable(false);
        return btn;
    }

    private static Stage getStage(DialogPane pane) {
        if (pane.getScene() != null) {
            Window w = pane.getScene().getWindow();
            if (w instanceof Stage) return (Stage) w;
        }
        return null;
    }
}
