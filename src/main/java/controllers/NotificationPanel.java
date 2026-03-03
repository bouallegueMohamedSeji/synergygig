package controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import services.ServiceNotification;
import services.ServiceNotification.Notification;
import utils.SessionManager;
import utils.AppThreadPool;
import utils.SoundManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Notification bell icon + dropdown panel with swipe-to-delete.
 * Supports MESSAGE, VOICE_CALL, VIDEO_CALL, MISSED_CALL, TASK, INTERVIEW, CONTRACT types
 * with color-coded icons.
 */
public class NotificationPanel {

    private final ServiceNotification service = new ServiceNotification();
    private final StackPane bellContainer;
    private final Label bellIcon;
    private final Label badge;
    private Popup popup;
    private VBox notifList;
    private int userId;
    private int lastUnreadCount = 0;
    private ScheduledExecutorService poller;
    private Runnable onNotificationClick;
    private BiConsumer<String, Integer> onNotificationAction;

    // ── Swipe constants ──
    private static final double DELETE_THRESHOLD = 0.30;  // 30% of width = auto-delete
    private static final double REVEAL_WIDTH     = 72;    // px to reveal delete button
    private static final Duration ANIM_DUR       = Duration.millis(250);

    public NotificationPanel() {
        bellIcon = new Label("\uD83D\uDD14");
        bellIcon.getStyleClass().add("notification-bell");
        bellIcon.setStyle("-fx-font-size: 18; -fx-cursor: hand;");

        badge = new Label("0");
        badge.getStyleClass().add("notification-badge");
        badge.setVisible(false);
        badge.setManaged(false);

        bellContainer = new StackPane(bellIcon, badge);
        bellContainer.setAlignment(Pos.TOP_RIGHT);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        StackPane.setMargin(badge, new Insets(-4, -4, 0, 0));
        bellContainer.setMaxSize(36, 36);
        bellContainer.setMinSize(36, 36);
        bellContainer.setOnMouseClicked(e -> togglePopup());
    }

    public StackPane getNode() { return bellContainer; }

    public void start(int userId) {
        this.userId = userId;
        refreshCount();

        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notif-poller");
            t.setDaemon(true);
            return t;
        });
        poller.scheduleAtFixedRate(() -> {
            try {
                int count = service.getUnreadCount(userId);
                Platform.runLater(() -> {
                    if (count > lastUnreadCount && lastUnreadCount >= 0) {
                        SoundManager.getInstance().play(SoundManager.NOTIFICATION_POP);
                    }
                    updateBadge(count);
                });
            } catch (Exception ignored) {}
        }, 5, 15, TimeUnit.SECONDS);
    }

    public void stop() {
        if (poller != null) { poller.shutdownNow(); poller = null; }
    }

    public void setOnNotificationClick(Runnable callback) {
        this.onNotificationClick = callback;
    }

    /** Set a type-aware callback: receives (notificationType, referenceId) when a notification is clicked. */
    public void setOnNotificationAction(BiConsumer<String, Integer> callback) {
        this.onNotificationAction = callback;
    }

    private void refreshCount() {
        AppThreadPool.io(() -> {
            int count = service.getUnreadCount(userId);
            Platform.runLater(() -> updateBadge(count));
        });
    }

    private void updateBadge(int count) {
        lastUnreadCount = count;
        if (count > 0) {
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
            badge.setVisible(true);  badge.setManaged(true);
        } else {
            badge.setVisible(false); badge.setManaged(false);
        }
    }

    private void togglePopup() {
        SoundManager.getInstance().play(SoundManager.REMINDER_CHIME);
        if (popup != null && popup.isShowing()) { popup.hide(); return; }
        showPopup();
    }

    // ════════════════════════════════════════════════════════
    //  POPUP  (notification center)
    // ════════════════════════════════════════════════════════

    private void showPopup() {
        boolean dark = SessionManager.getInstance().isDarkTheme();
        popup = new Popup();
        popup.setAutoHide(true);

        VBox panel = new VBox(0);
        panel.getStyleClass().add("notification-panel");
        panel.setPrefWidth(380);
        panel.setMaxWidth(380);
        panel.setMaxHeight(520);

        // ── Header ──
        HBox header = new HBox();
        header.getStyleClass().add("notification-panel-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(8);

        Label title = new Label("\uD83D\uDD14 Notifications");
        title.getStyleClass().add("notification-panel-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button markAll = new Button("Mark all read");
        markAll.getStyleClass().add("notification-mark-all");
        markAll.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.NOTIFICATION_CLEAR);
            AppThreadPool.io(() -> {
                service.markAllRead(userId);
                Platform.runLater(() -> { refreshCount(); loadNotifications(); });
            });
        });

        header.getChildren().addAll(title, spacer, markAll);

        // ── List ──
        notifList = new VBox(6);
        notifList.setPadding(new Insets(8));

        ScrollPane scroll = new ScrollPane(notifList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPrefHeight(440);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

        panel.getChildren().addAll(header, scroll);

        String darkCss = getClass().getResource("/css/style.css").toExternalForm();
        panel.getStylesheets().add(darkCss);
        if (!dark) {
            String lightCss = getClass().getResource("/css/light-theme.css").toExternalForm();
            panel.getStylesheets().add(lightCss);
        }

        popup.getContent().add(panel);

        Window window = bellContainer.getScene().getWindow();
        var bounds = bellContainer.localToScreen(bellContainer.getBoundsInLocal());
        popup.show(window, bounds.getMinX() - 340, bounds.getMaxY() + 4);

        loadNotifications();
    }

    // ════════════════════════════════════════════════════════
    //  LOAD & BUILD ITEMS
    // ════════════════════════════════════════════════════════

    private void loadNotifications() {
        notifList.getChildren().clear();
        AppThreadPool.io(() -> {
            List<Notification> notifications = service.getNotifications(userId);
            Platform.runLater(() -> {
                if (notifications.isEmpty()) {
                    VBox emptyBox = new VBox(8);
                    emptyBox.setAlignment(Pos.CENTER);
                    emptyBox.setPadding(new Insets(60, 0, 60, 0));
                    Label bellBig = new Label("\uD83D\uDD14");
                    bellBig.setStyle("-fx-font-size: 36; -fx-text-fill: #4A4A5A;");
                    Label emptyLbl = new Label("All caught up!");
                    emptyLbl.getStyleClass().add("notification-empty");
                    emptyBox.getChildren().addAll(bellBig, emptyLbl);
                    notifList.getChildren().add(emptyBox);
                    return;
                }

                int delay = 0;
                for (Notification n : notifications) {
                    Node card = buildSwipeableItem(n, delay);
                    notifList.getChildren().add(card);
                    delay += 40;
                }
            });
        });
    }

    // ════════════════════════════════════════════════════════
    //  SWIPEABLE NOTIFICATION ITEM
    // ════════════════════════════════════════════════════════

    /**
     * Builds one notification row with:
     *   - A red delete-action layer behind
     *   - A content layer on top that can be swiped left
     *   - Auto-delete when swiped past 30 %
     *   - Partial reveal to expose the delete button
     */
    private Node buildSwipeableItem(Notification n, int animDelayMs) {
        // ── Wrapper (clips overflow, manages collapse animation) ──
        VBox wrapper = new VBox();
        wrapper.getStyleClass().add("notif-swipe-wrapper");
        wrapper.setMaxHeight(Region.USE_PREF_SIZE);

        // ── Delete action layer (behind) ──
        StackPane deleteLayer = new StackPane();
        deleteLayer.getStyleClass().add("notif-delete-layer");
        deleteLayer.setAlignment(Pos.CENTER_RIGHT);
        deleteLayer.setMinHeight(0);

        Button deleteBtn = new Button("\uD83D\uDDD1"); // 🗑
        deleteBtn.getStyleClass().add("notif-delete-btn");
        deleteBtn.setPrefWidth(REVEAL_WIDTH);
        deleteBtn.setMaxWidth(REVEAL_WIDTH);
        deleteBtn.setOnAction(e -> animateDeleteItem(wrapper, n));
        StackPane.setAlignment(deleteBtn, Pos.CENTER_RIGHT);
        deleteLayer.getChildren().add(deleteBtn);

        // ── Content layer (on top, swipeable) ──
        HBox content = new HBox(12);
        content.getStyleClass().add("notif-content");
        if (!n.isRead) content.getStyleClass().add("notif-content-unread");

        // Icon circle
        StackPane iconCircle = buildIconCircle(n);

        // Text column
        VBox textCol = new VBox(2);
        textCol.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        Label titleLbl = new Label(n.title);
        titleLbl.getStyleClass().add("notif-title");
        titleLbl.setMaxWidth(230);

        Label bodyLbl = new Label(n.body != null ? n.body : "");
        bodyLbl.getStyleClass().add("notif-body");
        bodyLbl.setWrapText(true);
        bodyLbl.setMaxWidth(230);
        bodyLbl.setMaxHeight(36);

        HBox timeRow = new HBox(4);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        Label clockIcon = new Label("\uD83D\uDD52"); // 🕒
        clockIcon.setStyle("-fx-font-size: 10;");
        Label timeLbl = new Label(formatTimeAgo(n.createdAt));
        timeLbl.getStyleClass().add("notif-timestamp");
        timeRow.getChildren().addAll(clockIcon, timeLbl);

        textCol.getChildren().addAll(titleLbl, bodyLbl, timeRow);
        content.getChildren().addAll(iconCircle, textCol);

        // ── Stack content over delete layer ──
        StackPane stack = new StackPane(deleteLayer, content);
        stack.setAlignment(Pos.CENTER_LEFT);
        // Bind delete layer height to content height
        deleteLayer.prefHeightProperty().bind(content.heightProperty());

        wrapper.getChildren().add(stack);

        // ── Swipe gesture handling ──
        attachSwipeHandlers(content, wrapper, n);

        // ── Click-to-read ──
        content.setOnMouseClicked(e -> {
            if (Math.abs(content.getTranslateX()) > 5) return; // ignore if mid-swipe
            SoundManager.getInstance().play(SoundManager.NOTIFICATION_CLEAR);
            if (!n.isRead) {
                AppThreadPool.io(() -> {
                    service.markRead(n.id);
                    Platform.runLater(() -> {
                        content.getStyleClass().remove("notif-content-unread");
                        refreshCount();
                    });
                });
            }
            if (onNotificationAction != null && n.type != null && n.referenceId != null) {
                if (popup != null && popup.isShowing()) popup.hide();
                onNotificationAction.accept(n.type, n.referenceId);
            }
            if (onNotificationClick != null) onNotificationClick.run();
        });

        // ── Entrance animation (scale + fade) ──
        wrapper.setOpacity(0);
        wrapper.setScaleX(0.85);
        wrapper.setScaleY(0.85);
        Timeline entrance = new Timeline(new KeyFrame(Duration.millis(animDelayMs + 200),
                new KeyValue(wrapper.opacityProperty(), 1, Interpolator.EASE_BOTH),
                new KeyValue(wrapper.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                new KeyValue(wrapper.scaleYProperty(), 1, Interpolator.EASE_BOTH)
        ));
        entrance.play();

        return wrapper;
    }

    // ── Swipe drag handling ──
    private double dragStartX;
    private double currentOffset;
    private boolean isDragging;

    private void attachSwipeHandlers(HBox content, VBox wrapper, Notification n) {
        content.setOnMousePressed(e -> {
            isDragging = true;
            dragStartX = e.getScreenX();
            currentOffset = content.getTranslateX();
            content.setCursor(Cursor.CLOSED_HAND);
            e.consume();
        });

        content.setOnMouseDragged(e -> {
            if (!isDragging) return;
            double diff = e.getScreenX() - dragStartX;
            double newX = currentOffset + diff;
            // Only allow swiping left (negative)
            if (newX > 0) newX = 0;
            content.setTranslateX(newX);
            e.consume();
        });

        content.setOnMouseReleased(e -> {
            if (!isDragging) return;
            isDragging = false;
            content.setCursor(Cursor.HAND);

            double offsetX = content.getTranslateX();
            double width = content.getWidth();
            double distance = Math.abs(offsetX);

            if (offsetX < 0) {
                if (distance > width * DELETE_THRESHOLD) {
                    // Swipe off screen → delete
                    TranslateTransition tt = new TranslateTransition(Duration.millis(150), content);
                    tt.setToX(-width);
                    tt.setOnFinished(ev -> animateDeleteItem(wrapper, n));
                    tt.play();
                } else if (distance > REVEAL_WIDTH / 2) {
                    // Snap to reveal delete button
                    animateTranslateX(content, -REVEAL_WIDTH);
                } else {
                    // Snap back closed
                    animateTranslateX(content, 0);
                }
            } else {
                animateTranslateX(content, 0);
            }
            e.consume();
        });
    }

    private void animateTranslateX(Node node, double toX) {
        TranslateTransition tt = new TranslateTransition(ANIM_DUR, node);
        tt.setToX(toX);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.play();
    }

    /** Collapse + fade out, then remove from list and fire API delete. */
    private void animateDeleteItem(VBox wrapper, Notification n) {
        // Fade + shrink
        wrapper.setMouseTransparent(true);
        Timeline collapse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(wrapper.opacityProperty(), 1),
                        new KeyValue(wrapper.maxHeightProperty(), wrapper.getHeight()),
                        new KeyValue(wrapper.scaleYProperty(), 1)
                ),
                new KeyFrame(Duration.millis(250),
                        new KeyValue(wrapper.opacityProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(wrapper.maxHeightProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(wrapper.scaleYProperty(), 0.6, Interpolator.EASE_BOTH)
                )
        );
        collapse.setOnFinished(e -> {
            notifList.getChildren().remove(wrapper);
            // If list is now empty, show "All caught up!"
            if (notifList.getChildren().isEmpty()) {
                loadNotifications(); // will show empty state
            }
        });
        collapse.play();

        // Delete on server
        AppThreadPool.io(() -> {
            service.markRead(n.id); // mark as read = dismiss
            Platform.runLater(this::refreshCount);
        });
    }

    // ════════════════════════════════════════════════════════
    //  ICON CIRCLE (color-coded by type)
    // ════════════════════════════════════════════════════════

    private StackPane buildIconCircle(Notification n) {
        String icon;
        String color;
        switch (n.type != null ? n.type : "") {
            // ── Text / Messages ──
            case "MESSAGE":
                icon = "\uD83D\uDCAC"; // 💬
                color = "#6366F1";       // indigo
                break;

            // ── Calls ──
            case "VOICE_CALL":
                icon = "\uD83D\uDCDE"; // 📞
                color = "#22C55E";       // green
                break;
            case "VIDEO_CALL":
                icon = "\uD83D\uDCF9"; // 📹
                color = "#3B82F6";       // blue
                break;
            case "MISSED_CALL":
                icon = "\uD83D\uDCF5"; // 📵
                color = "#EF4444";       // red
                break;

            // ── Tasks ──
            case "TASK_ASSIGNED":
                icon = "\uD83D\uDCCB"; // 📋
                color = "#F59E0B";       // amber
                break;
            case "TASK_SUBMITTED":
                icon = "\u2705";         // ✅
                color = "#10B981";       // emerald
                break;
            case "TASK_REVIEW":
                icon = "\u2B50";         // ⭐
                color = "#F59E0B";       // amber
                break;

            // ── HR / Offers ──
            case "INTERVIEW":
                icon = "\uD83D\uDCC5"; // 📅
                color = "#8B5CF6";       // violet
                break;
            case "CONTRACT":
                icon = "\uD83D\uDCDD"; // 📝
                color = "#06B6D4";       // cyan
                break;
            case "APPLICATION":
                icon = "\uD83D\uDCE9"; // 📩
                color = "#EC4899";       // pink
                break;

            // ── Friends ──
            case "FRIEND_REQUEST":
                icon = "\uD83D\uDC65"; // 👥
                color = "#F59E0B";       // amber
                break;
            case "NEW_FRIEND":
                icon = "\uD83E\uDD1D"; // 🤝
                color = "#2C666E";       // teal
                break;

            default:
                icon = "\uD83D\uDD14"; // 🔔
                color = "#6B7280";       // gray
                break;
        }

        StackPane circle = new StackPane();
        circle.getStyleClass().add("notif-icon-circle");
        circle.setStyle("-fx-background-color: " + color + ";");
        circle.setMinSize(40, 40);
        circle.setMaxSize(40, 40);

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 16; -fx-text-fill: white;");
        circle.getChildren().add(iconLbl);

        return circle;
    }

    // ════════════════════════════════════════════════════════
    //  TIME FORMATTING
    // ════════════════════════════════════════════════════════

    private String formatTimeAgo(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return "";
        try {
            String normalized = isoTime.replace("T", " ");
            if (normalized.contains(".")) {
                normalized = normalized.substring(0, normalized.indexOf('.'));
            }
            LocalDateTime dt = LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            LocalDateTime now = LocalDateTime.now();
            long minutes = ChronoUnit.MINUTES.between(dt, now);
            if (minutes < 1) return "just now";
            if (minutes < 60) return minutes + "m ago";
            long hours = minutes / 60;
            if (hours < 24) return hours + "h ago";
            long days = hours / 24;
            if (days < 7) return days + "d ago";
            long weeks = days / 7;
            if (weeks < 4) return weeks + "w ago";
            return dt.format(DateTimeFormatter.ofPattern("MMM d"));
        } catch (Exception e) {
            return "";
        }
    }
}
