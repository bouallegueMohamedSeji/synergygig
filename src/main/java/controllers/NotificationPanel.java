package controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import services.ServiceNotification;
import services.ServiceNotification.Notification;
import utils.SessionManager;
import utils.SoundManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Notification bell icon + dropdown panel.
 * Add this component to your top bar.
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
    private Runnable onNotificationClick; // optional callback

    public NotificationPanel() {
        // Bell icon
        bellIcon = new Label("\uD83D\uDD14"); // 🔔
        bellIcon.getStyleClass().add("notification-bell");
        bellIcon.setStyle("-fx-font-size: 18; -fx-cursor: hand;");

        // Badge (unread count)
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

    /** Get the visual node to add to your layout. */
    public StackPane getNode() {
        return bellContainer;
    }

    /** Set the logged-in user's ID and start polling. */
    public void start(int userId) {
        this.userId = userId;
        refreshCount();

        // Poll every 15 seconds for new notifications
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
                        // New notification arrived — play sound
                        SoundManager.getInstance().play(SoundManager.NOTIFICATION_POP);
                    }
                    updateBadge(count);
                });
            } catch (Exception ignored) {}
        }, 5, 15, TimeUnit.SECONDS);
    }

    /** Stop polling. Call when user logs out. */
    public void stop() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
    }

    /** Set a callback when a notification is clicked. */
    public void setOnNotificationClick(Runnable callback) {
        this.onNotificationClick = callback;
    }

    private void refreshCount() {
        new Thread(() -> {
            int count = service.getUnreadCount(userId);
            Platform.runLater(() -> updateBadge(count));
        }).start();
    }

    private void updateBadge(int count) {
        lastUnreadCount = count;
        if (count > 0) {
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
            badge.setVisible(true);
            badge.setManaged(true);
        } else {
            badge.setVisible(false);
            badge.setManaged(false);
        }
    }

    private void togglePopup() {
        SoundManager.getInstance().play(SoundManager.REMINDER_CHIME);
        if (popup != null && popup.isShowing()) {
            popup.hide();
            return;
        }
        showPopup();
    }

    private void showPopup() {
        boolean dark = SessionManager.getInstance().isDarkTheme();

        popup = new Popup();
        popup.setAutoHide(true);

        VBox panel = new VBox(0);
        panel.getStyleClass().add("notification-panel");
        panel.setPrefWidth(360);
        panel.setMaxWidth(360);
        panel.setMaxHeight(480);

        // Header
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
            new Thread(() -> {
                service.markAllRead(userId);
                Platform.runLater(() -> {
                    refreshCount();
                    loadNotifications();
                });
            }).start();
        });

        header.getChildren().addAll(title, spacer, markAll);

        // Notification list
        notifList = new VBox(6);
        notifList.setPadding(new Insets(8));

        ScrollPane scroll = new ScrollPane(notifList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefHeight(400);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

        panel.getChildren().addAll(header, scroll);

        // Apply theme CSS
        String darkCss = getClass().getResource("/css/style.css").toExternalForm();
        panel.getStylesheets().add(darkCss);
        if (!dark) {
            String lightCss = getClass().getResource("/css/light-theme.css").toExternalForm();
            panel.getStylesheets().add(lightCss);
        }

        popup.getContent().add(panel);

        // Position below bell icon
        Window window = bellContainer.getScene().getWindow();
        var bounds = bellContainer.localToScreen(bellContainer.getBoundsInLocal());
        popup.show(window, bounds.getMinX() - 320, bounds.getMaxY() + 4);

        loadNotifications();
    }

    private void loadNotifications() {
        notifList.getChildren().clear();
        new Thread(() -> {
            List<Notification> notifications = service.getNotifications(userId);
            Platform.runLater(() -> {
                if (notifications.isEmpty()) {
                    Label empty = new Label("No notifications yet");
                    empty.getStyleClass().add("notification-empty");
                    notifList.getChildren().add(empty);
                    return;
                }
                for (Notification n : notifications) {
                    notifList.getChildren().add(buildNotificationItem(n));
                }
            });
        }).start();
    }

    private VBox buildNotificationItem(Notification n) {
        VBox item = new VBox(4);
        item.getStyleClass().add("notification-item");
        if (!n.isRead) {
            item.getStyleClass().add("notification-item-unread");
        }

        // Icon based on type
        String icon;
        switch (n.type) {
            case "TASK_ASSIGNED": icon = "\uD83D\uDCCB"; break;  // 📋
            case "TASK_SUBMITTED": icon = "\u2705"; break;         // ✅
            case "TASK_REVIEW": icon = "\u2B50"; break;            // ⭐
            case "MESSAGE": icon = "\uD83D\uDCAC"; break;         // 💬
            case "VOICE_CALL": icon = "\uD83D\uDCDE"; break;      // 📞
            case "VIDEO_CALL": icon = "\uD83D\uDCF9"; break;      // 📹
            case "MISSED_CALL": icon = "\uD83D\uDCF5"; break;     // 📵
            case "INTERVIEW": icon = "\uD83D\uDCC5"; break;       // 📅
            default: icon = "\uD83D\uDD14"; break;                 // 🔔
        }

        HBox titleRow = new HBox(6);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 14;");

        Label titleLbl = new Label(n.title);
        titleLbl.getStyleClass().add("notification-item-title");
        titleLbl.setMaxWidth(260);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label timeLbl = new Label(formatTimeAgo(n.createdAt));
        timeLbl.getStyleClass().add("notification-item-time");

        titleRow.getChildren().addAll(iconLbl, titleLbl, sp, timeLbl);

        Label bodyLbl = new Label(n.body);
        bodyLbl.getStyleClass().add("notification-item-body");
        bodyLbl.setWrapText(true);
        bodyLbl.setMaxWidth(330);

        item.getChildren().addAll(titleRow, bodyLbl);

        item.setOnMouseClicked(e -> {
            SoundManager.getInstance().play(SoundManager.NOTIFICATION_CLEAR);
            if (!n.isRead) {
                new Thread(() -> {
                    service.markRead(n.id);
                    Platform.runLater(() -> {
                        item.getStyleClass().remove("notification-item-unread");
                        refreshCount();
                    });
                }).start();
            }
            if (onNotificationClick != null) {
                onNotificationClick.run();
            }
        });

        return item;
    }

    private String formatTimeAgo(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return "";
        try {
            // Parse ISO datetime (handle "T" separator or space)
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
            return dt.format(DateTimeFormatter.ofPattern("MMM d"));
        } catch (Exception e) {
            return "";
        }
    }
}
