package controllers;

import entities.ChatRoom;
import entities.Message;
import entities.User;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import services.ServiceChatRoom;
import services.ServiceMessage;
import services.ServiceUser;
import utils.AIAssistantService;
import utils.SessionManager;

import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatController {

    @FXML private ListView<ChatRoom> roomsList;
    @FXML private TextField roomNameField;
    @FXML private Label currentRoomLabel;
    @FXML private Label roomMembersLabel;
    @FXML private ScrollPane messagesScroll;
    @FXML private VBox messagesContainer;
    @FXML private TextField messageField;
    @FXML private Button btnSend;
    @FXML private Label inputErrorLabel;
    @FXML private MenuButton chatMenuBtn;
    @FXML private MenuItem deleteChatItem;

    private ServiceChatRoom serviceChat = new ServiceChatRoom();
    private ServiceMessage serviceMessage = new ServiceMessage();
    private ServiceUser serviceUser = new ServiceUser();

    private ChatRoom currentRoom;
    private ScheduledExecutorService scheduler;
    private Map<Integer, User> userCache = new HashMap<>();

    /** Currently editing message — null when not editing */
    private Message editingMessage = null;

    /** Whether the currently selected room is the AI assistant */
    private boolean isAIRoom = false;

    /** In-memory AI conversation: stores {role, content, timestamp} entries */
    private static class AIChatEntry {
        final boolean fromUser;
        final String content;
        final long timestamp;
        AIChatEntry(boolean fromUser, String content) {
            this.fromUser = fromUser;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
    }
    private List<AIChatEntry> aiChatHistory = new ArrayList<>();

    @FXML
    public void initialize() {
        loadUsers();
        ensureAIRoom();
        loadRooms();

        roomsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) selectRoom(newVal);
        });

        // Custom cell factory for rooms list — show room name + last message preview
        roomsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChatRoom item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    VBox cell = new VBox(2);
                    cell.setPadding(new Insets(6, 8, 6, 8));
                    Label name = new Label(item.getName());
                    name.getStyleClass().add("room-cell-name");
                    cell.getChildren().add(name);
                    setGraphic(cell);
                    setText(null);
                }
            }
        });

        // Enter to send, Escape to cancel edit
        messageField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && editingMessage != null) {
                cancelEdit();
            }
        });

        // Clear error state when user starts typing
        messageField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                clearInputError();
            }
        });

        // Auto-refresh messages every 3 seconds
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(this::refreshMessages);
        }, 3, 3, TimeUnit.SECONDS);
    }

    // ═══════════════ Data Loading ═══════════════

    /** Sender ID used for AI bot messages (not a real user). */
    private static final int AI_BOT_SENDER_ID = -99;

    /** Ensure the AI Assistant room exists in the database. */
    private void ensureAIRoom() {
        try {
            List<ChatRoom> rooms = serviceChat.recuperer();
            boolean exists = rooms.stream()
                    .anyMatch(r -> AIAssistantService.AI_ROOM_NAME.equals(r.getName()));
            if (!exists) {
                serviceChat.ajouter(new ChatRoom(AIAssistantService.AI_ROOM_NAME));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadUsers() {
        try {
            for (User u : serviceUser.recuperer()) {
                userCache.put(u.getId(), u);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadRooms() {
        try {
            List<ChatRoom> rooms = serviceChat.recuperer();
            roomsList.getItems().setAll(rooms);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCreateRoom() {
        String name = roomNameField.getText().trim();
        if (!name.isEmpty()) {
            try {
                serviceChat.ajouter(new ChatRoom(name));
                roomNameField.clear();
                loadRooms();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void selectRoom(ChatRoom room) {
        this.currentRoom = room;
        this.isAIRoom = AIAssistantService.AI_ROOM_NAME.equals(room.getName());
        currentRoomLabel.setText("# " + room.getName());

        if (isAIRoom) {
            roomMembersLabel.setText("Powered by Gemini — type any HR question");
            AIAssistantService.isHealthy().thenAccept(ok -> Platform.runLater(() -> {
                if (ok) {
                    roomMembersLabel.setText("🟢 Online — Powered by Gemini");
                } else {
                    roomMembersLabel.setText("🔴 Offline — AI service starting...");
                }
            }));
        } else {
            roomMembersLabel.setText("");
        }
        // Show kebab menu only for admins when a room is selected
        updateKebabMenuVisibility();
        editingMessage = null;
        resetInputBar();

        if (isAIRoom) {
            renderAIChat();
        } else {
            refreshMessages();
        }
    }

    // ═══════════════ Messages ═══════════════

    @FXML
    void refreshMessages() {
        if (currentRoom == null) return;
        // AI room uses in-memory chat — skip DB refresh
        if (isAIRoom) return;

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        try {
            List<Message> messages = serviceMessage.getByRoom(currentRoom.getId());
            messagesContainer.getChildren().clear();

            int currentUserId = currentUser.getId();
            int lastSenderId = -1;

            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                boolean isMe = msg.getSenderId() == currentUserId;
                boolean showAvatar = msg.getSenderId() != lastSenderId;
                boolean isLastInGroup = (i + 1 >= messages.size())
                        || messages.get(i + 1).getSenderId() != msg.getSenderId();

                messagesContainer.getChildren().add(
                        buildMessageRow(msg, isMe, showAvatar, isLastInGroup));
                lastSenderId = msg.getSenderId();
            }

            Platform.runLater(() -> messagesScroll.setVvalue(1.0));

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Render the AI chat from the in-memory history list. */
    private void renderAIChat() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        messagesContainer.getChildren().clear();

        boolean lastWasUser = false;
        boolean firstEntry = true;

        for (int i = 0; i < aiChatHistory.size(); i++) {
            AIChatEntry entry = aiChatHistory.get(i);
            boolean isMe = entry.fromUser;
            boolean showAvatar = firstEntry || (isMe != lastWasUser);
            boolean isLastInGroup = (i + 1 >= aiChatHistory.size())
                    || aiChatHistory.get(i + 1).fromUser != entry.fromUser;

            HBox row = buildAIChatRow(entry, currentUser, showAvatar, isLastInGroup);
            messagesContainer.getChildren().add(row);

            lastWasUser = isMe;
            firstEntry = false;
        }

        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    /** Build a single row for the AI in-memory chat. */
    private HBox buildAIChatRow(AIChatEntry entry, User currentUser, boolean showAvatar, boolean isLastInGroup) {
        boolean isMe = entry.fromUser;

        HBox row = new HBox(8);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(isLastInGroup ? 6 : 1, 0, 0, 0));

        // Avatar on left for bot
        if (!isMe) {
            if (showAvatar) {
                row.getChildren().add(createBotAvatar(32));
            } else {
                Region spacer = new Region();
                spacer.setMinWidth(32);
                spacer.setMaxWidth(32);
                row.getChildren().add(spacer);
            }
        }

        // Bubble column
        VBox bubbleCol = new VBox(2);
        bubbleCol.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        bubbleCol.setMaxWidth(420);

        if (showAvatar && !isMe) {
            Label nameLabel = new Label("SynergyBot");
            nameLabel.getStyleClass().add("msg-bot-name");
            bubbleCol.getChildren().add(nameLabel);
        }

        Label bubble = new Label(entry.content);
        bubble.getStyleClass().add(isMe ? "msg-bubble-me" : "msg-bubble-bot");
        bubble.setWrapText(true);
        bubble.setMaxWidth(380);
        bubbleCol.getChildren().add(bubble);

        // Timestamp on last in group
        if (isLastInGroup) {
            SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a");
            Label timeLabel = new Label(timeFmt.format(new Date(entry.timestamp)));
            timeLabel.getStyleClass().add("msg-timestamp");
            bubbleCol.getChildren().add(timeLabel);
        }

        row.getChildren().add(bubbleCol);

        // Avatar on right for user
        if (isMe) {
            if (showAvatar) {
                row.getChildren().add(createAvatar(currentUser, 32));
            } else {
                Region spacer = new Region();
                spacer.setMinWidth(32);
                spacer.setMaxWidth(32);
                row.getChildren().add(spacer);
            }
        }

        return row;
    }

    // ═══════════════ Message Bubble Builder ═══════════════

    private HBox buildMessageRow(Message msg, boolean isMe, boolean showAvatar, boolean isLastInGroup) {
        boolean isBot = msg.getSenderId() == AI_BOT_SENDER_ID;
        User sender = isBot ? null : userCache.get(msg.getSenderId());
        String senderName = isBot ? "SynergyBot"
                : (sender != null ? sender.getFirstName() + " " + sender.getLastName()
                        : "User #" + msg.getSenderId());

        HBox row = new HBox(8);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(isLastInGroup ? 6 : 1, 0, 0, 0));

        // ── Avatar (left side for others/bot) ──
        if (!isMe) {
            if (showAvatar) {
                if (isBot) {
                    StackPane botAvatar = createBotAvatar(32);
                    row.getChildren().add(botAvatar);
                } else {
                    StackPane avatarWrap = createAvatar(sender, 32);
                    row.getChildren().add(avatarWrap);
                }
            } else {
                Region spacer = new Region();
                spacer.setMinWidth(32);
                spacer.setMaxWidth(32);
                row.getChildren().add(spacer);
            }
        }

        // ── Bubble column (name + bubble + time) ──
        VBox bubbleCol = new VBox(2);
        bubbleCol.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        bubbleCol.setMaxWidth(420);

        // Sender name (only first in group, only for others/bot)
        if (showAvatar && !isMe) {
            Label nameLabel = new Label(senderName);
            nameLabel.getStyleClass().add(isBot ? "msg-bot-name" : "msg-sender-name");
            bubbleCol.getChildren().add(nameLabel);
        }

        // The actual message bubble
        Label bubble = new Label(msg.getContent());
        String bubbleClass = isBot ? "msg-bubble-bot" : (isMe ? "msg-bubble-me" : "msg-bubble-other");
        bubble.getStyleClass().add(bubbleClass);
        bubble.setWrapText(true);
        bubble.setMaxWidth(380);

        // Context menu for edit / delete (only on own messages)
        if (isMe) {
            ContextMenu ctx = new ContextMenu();

            MenuItem editItem = new MenuItem("Edit");
            editItem.getStyleClass().add("msg-ctx-item");
            editItem.setOnAction(e -> startEdit(msg));

            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.getStyleClass().add("msg-ctx-item");
            deleteItem.setOnAction(e -> deleteMessage(msg));

            ctx.getItems().addAll(editItem, deleteItem);
            bubble.setContextMenu(ctx);

            // Also add double-click to edit
            bubble.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) startEdit(msg);
            });
        }

        bubbleCol.getChildren().add(bubble);

        // Timestamp (show on last message in group)
        if (isLastInGroup && msg.getTimestamp() != null) {
            SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a");
            Label timeLabel = new Label(timeFmt.format(msg.getTimestamp()));
            timeLabel.getStyleClass().add("msg-timestamp");
            bubbleCol.getChildren().add(timeLabel);
        }

        row.getChildren().add(bubbleCol);

        // ── Avatar on right for own messages ──
        if (isMe) {
            if (showAvatar) {
                User me = SessionManager.getInstance().getCurrentUser();
                StackPane avatarWrap = createAvatar(me, 32);
                row.getChildren().add(avatarWrap);
            } else {
                Region spacer = new Region();
                spacer.setMinWidth(32);
                spacer.setMaxWidth(32);
                row.getChildren().add(spacer);
            }
        }

        return row;
    }

    /** Build a bot avatar with robot emoji. */
    private StackPane createBotAvatar(double size) {
        StackPane wrapper = new StackPane();
        wrapper.setMinSize(size, size);
        wrapper.setMaxSize(size, size);

        Circle circle = new Circle(size / 2);
        circle.getStyleClass().add("msg-bot-avatar-circle");

        Label emoji = new Label("\uD83E\uDD16");
        emoji.setStyle("-fx-font-size: " + (int)(size * 0.5) + ";");
        emoji.setMouseTransparent(true);

        wrapper.getChildren().addAll(circle, emoji);
        return wrapper;
    }

    /** Build a circular avatar with image or initials fallback. */
    private StackPane createAvatar(User user, double size) {
        StackPane wrapper = new StackPane();
        wrapper.setMinSize(size, size);
        wrapper.setMaxSize(size, size);

        Circle circle = new Circle(size / 2);

        boolean loaded = false;
        if (user != null && user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
            File f = new File(user.getAvatarPath());
            if (f.exists()) {
                try {
                    Image img = new Image(f.toURI().toString(), size, size, true, true);
                    circle.setFill(new ImagePattern(img));
                    loaded = true;
                } catch (Exception ignored) {}
            }
        }

        if (!loaded) {
            // Initials fallback
            String initials = "";
            if (user != null) {
                if (user.getFirstName() != null && !user.getFirstName().isEmpty())
                    initials += user.getFirstName().charAt(0);
                if (user.getLastName() != null && !user.getLastName().isEmpty())
                    initials += user.getLastName().charAt(0);
            }
            if (initials.isEmpty()) initials = "?";

            circle.getStyleClass().add("msg-avatar-circle");

            Label initialsLabel = new Label(initials.toUpperCase());
            initialsLabel.getStyleClass().add("msg-avatar-initials");
            initialsLabel.setStyle("-fx-font-size: " + (int)(size * 0.38) + ";");

            wrapper.getChildren().addAll(circle, initialsLabel);
            return wrapper;
        }

        wrapper.getChildren().add(circle);
        return wrapper;
    }

    // ═══════════════ Send / Edit / Delete ═══════════════

    @FXML
    private void handleSendMessage() {
        if (currentRoom == null) return;

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String content = messageField.getText().trim();
        if (content.isEmpty()) {
            showInputError("Please enter a message before sending.");
            return;
        }
        clearInputError();

        // ── AI Assistant room: route to Gemini ──
        if (isAIRoom && editingMessage == null) {
            messageField.clear();

            // Add user message to in-memory history and render
            aiChatHistory.add(new AIChatEntry(true, content));
            renderAIChat();

            // Show typing indicator
            Label typingLabel = new Label("SynergyBot is thinking...");
            typingLabel.getStyleClass().add("msg-ai-typing");
            HBox typingRow = new HBox(typingLabel);
            typingRow.setAlignment(Pos.CENTER_LEFT);
            typingRow.setPadding(new Insets(4, 0, 4, 44));
            messagesContainer.getChildren().add(typingRow);
            Platform.runLater(() -> messagesScroll.setVvalue(1.0));

            // Call Gemini asynchronously
            AIAssistantService.chat(currentUser.getId(), content)
                    .thenAccept(reply -> Platform.runLater(() -> {
                        messagesContainer.getChildren().remove(typingRow);
                        aiChatHistory.add(new AIChatEntry(false, reply));
                        renderAIChat();
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            messagesContainer.getChildren().remove(typingRow);
                            aiChatHistory.add(new AIChatEntry(false, "⚠ Error: " + ex.getMessage()));
                            renderAIChat();
                        });
                        return null;
                    });
            return;
        }

        // ── Normal chat room ──
        try {
            if (editingMessage != null) {
                // ── Update existing ──
                editingMessage.setContent(content);
                serviceMessage.modifier(editingMessage);
                editingMessage = null;
                resetInputBar();
            } else {
                // ── Send new ──
                Message msg = new Message(currentUser.getId(), currentRoom.getId(), content);
                serviceMessage.ajouter(msg);
            }
            messageField.clear();
            refreshMessages();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void startEdit(Message msg) {
        editingMessage = msg;
        messageField.setText(msg.getContent());
        messageField.requestFocus();
        messageField.selectAll();
        // Visual hint: change send button text
        btnSend.setText("Save");
        btnSend.getStyleClass().removeAll("btn-primary");
        btnSend.getStyleClass().add("btn-primary");
        messageField.setPromptText("Editing message... (Esc to cancel)");
    }

    private void cancelEdit() {
        editingMessage = null;
        messageField.clear();
        resetInputBar();
    }

    private void resetInputBar() {
        btnSend.setText("Send");
        messageField.setPromptText("Type a message...");
        clearInputError();
    }

    // ═══════════════ Input Validation ═══════════════

    /** Show error state on the input: red border, shake animation, error label. */
    private void showInputError(String message) {
        // Add invalid style
        if (!messageField.getStyleClass().contains("chat-input-invalid")) {
            messageField.getStyleClass().add("chat-input-invalid");
        }

        // Show error label
        inputErrorLabel.setText(message);
        inputErrorLabel.setVisible(true);
        inputErrorLabel.setManaged(true);

        // Shake animation
        double startX = messageField.getTranslateX();
        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(messageField.translateXProperty(), startX)),
                new KeyFrame(Duration.millis(50), new KeyValue(messageField.translateXProperty(), startX - 6)),
                new KeyFrame(Duration.millis(100), new KeyValue(messageField.translateXProperty(), startX + 6)),
                new KeyFrame(Duration.millis(150), new KeyValue(messageField.translateXProperty(), startX - 4)),
                new KeyFrame(Duration.millis(200), new KeyValue(messageField.translateXProperty(), startX + 4)),
                new KeyFrame(Duration.millis(250), new KeyValue(messageField.translateXProperty(), startX - 2)),
                new KeyFrame(Duration.millis(300), new KeyValue(messageField.translateXProperty(), startX))
        );
        shake.play();

        // Auto-clear after 3 seconds
        PauseTransition autoClear = new PauseTransition(Duration.seconds(3));
        autoClear.setOnFinished(e -> clearInputError());
        autoClear.play();

        messageField.requestFocus();
    }

    /** Remove the invalid state from the input. */
    private void clearInputError() {
        messageField.getStyleClass().remove("chat-input-invalid");
        inputErrorLabel.setVisible(false);
        inputErrorLabel.setManaged(false);
        inputErrorLabel.setText("");
    }

    private void deleteMessage(Message msg) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this message?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Message");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    serviceMessage.supprimer(msg.getId());
                    refreshMessages();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // ═══════════════ Room Delete (Admin Only) ═══════════════

    /** Show/hide the kebab menu — only visible for ADMIN users when a room is selected. */
    private void updateKebabMenuVisibility() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        boolean isAdmin = currentUser != null && "ADMIN".equalsIgnoreCase(currentUser.getRole());
        boolean show = isAdmin && currentRoom != null;
        chatMenuBtn.setVisible(show);
        chatMenuBtn.setManaged(show);
    }

    /** Delete the current chat room (admin-only). */
    @FXML
    private void handleDeleteRoom() {
        if (currentRoom == null) return;

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null || !"ADMIN".equalsIgnoreCase(currentUser.getRole())) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + currentRoom.getName() + "\" and all its messages?\nThis action cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Chat Room");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    serviceChat.supprimer(currentRoom.getId());
                    currentRoom = null;
                    isAIRoom = false;
                    currentRoomLabel.setText("Select a conversation");
                    roomMembersLabel.setText("");
                    messagesContainer.getChildren().clear();
                    aiChatHistory.clear();
                    updateKebabMenuVisibility();
                    loadRooms();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // ═══════════════ Cleanup ═══════════════

    public void stop() {
        if (scheduler != null) scheduler.shutdown();
    }
}
