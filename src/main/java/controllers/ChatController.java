package controllers;

import entities.Call;
import entities.ChatRoom;
import entities.Message;
import entities.User;
import javafx.animation.*;
import javafx.stage.Window;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import services.ServiceCall;
import services.ServiceChatRoom;
import services.ServiceMessage;
import services.ServiceUser;
import utils.BadWordsService;
import utils.AIAssistantService;
import utils.ApiClient;
import utils.AudioCallService;
import utils.ScreenShareService;
import utils.SessionManager;
import utils.SoundManager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;

public class ChatController {

    // ── FXML bindings ──
    @FXML private ListView<ChatRoom> roomsList;
    @FXML private TextField roomNameField;
    @FXML private Label currentRoomLabel;
    @FXML private Label roomMembersLabel;
    @FXML private ScrollPane messagesScroll;
    @FXML private VBox messagesContainer;
    @FXML private TextArea messageArea;
    @FXML private Button btnSend;
    @FXML private Label inputErrorLabel;
    @FXML private MenuButton chatMenuBtn;
    @FXML private MenuItem deleteChatItem;
    @FXML private VBox searchResultsBox;
    @FXML private StackPane chatRootStack;   // overlay root for toasts
    @FXML private VBox emptyStateBox;        // "Select a conversation" empty state
    @FXML private VBox chatContentBox;       // main chat area (header + messages + input)
    @FXML private Label onlineStatusDot;     // green/gray dot in header
    @FXML private Button btnCall;            // call button in header
    @FXML private Button btnVideoCall;       // video call button in header
    private boolean pendingVideoCall = false; // auto-start screen share on connect
    @FXML private HBox activeCallBar;        // active call overlay bar
    @FXML private Label callTimerLabel;      // call duration label
    @FXML private Button btnMute;            // mute toggle
    @FXML private Button btnEndCall;         // end call button
    @FXML private Button btnAttach;          // image attach button

    // ── Services ──
    private final ServiceChatRoom serviceChat = new ServiceChatRoom();
    private final ServiceMessage serviceMessage = new ServiceMessage();
    private final ServiceUser serviceUser = new ServiceUser();
    private final ServiceCall serviceCall = new ServiceCall();
    private final AudioCallService audioCallService = new AudioCallService();
    private final ScreenShareService screenShareService = new ScreenShareService();

    // ── State ──
    private ChatRoom currentRoom;
    private ScheduledExecutorService scheduler;
    private Map<Integer, User> userCache = new HashMap<>();
    private Message editingMessage = null;
    private boolean isAIRoom = false;

    // ── Favorite rooms ──
    private final Set<Integer> favoriteRoomIds = new LinkedHashSet<>();
    private static final String PREF_FAVORITES = "chat_favorite_rooms";

    // ── Call state ──
    private Call activeCall = null;
    private long callStartTime = 0;
    private Timeline callTimer = null;
    private Timeline activeCallPoller = null;
    private VBox incomingCallOverlay = null;
    @FXML private Button btnScreenShare;
    private javafx.stage.Stage videoCallStage = null;
    private ImageView remoteVideoView = null;
    private Label videoCallNameLabel = null;
    private Label videoCallTimerLabel = null;
    private Button videoMuteBtn = null;
    private Button videoScreenShareBtn = null;
    private Button videoCameraBtn = null;
    private ImageView localPreviewView = null;

    private static final String IMAGE_PREFIX = "[IMAGE]";
    private static final String IMAGE_SUFFIX = "[/IMAGE]";
    private static final String FILE_PREFIX = "[FILE]";
    private static final String FILE_SUFFIX = "[/FILE]";

    /** Track last message count per room for change detection */
    private int lastMessageCount = -1;
    /** Track last known message id to detect truly new messages */
    private int lastMessageId = 0;

    // ── AI in-memory chat ──
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
    private final List<AIChatEntry> aiChatHistory = new ArrayList<>();

    private static final int AI_BOT_SENDER_ID = -99;

    // ═══════════════════════════════════════════
    //  INITIALIZE
    // ═══════════════════════════════════════════

    @FXML
    public void initialize() {
        loadFavorites();
        loadUsers();
        ensureAIRoom();
        loadRooms();

        // Show empty state initially, hide chat content
        showEmptyState();

        roomsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) selectRoom(newVal);
        });

        // ── Rich cell factory with online indicator ──
        roomsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChatRoom item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }

                HBox cell = new HBox(10);
                cell.setPadding(new Insets(8, 10, 8, 10));
                cell.setAlignment(Pos.CENTER_LEFT);

                String displayName = getDisplayName(item);
                boolean isAI = AIAssistantService.AI_ROOM_NAME.equals(item.getName());
                boolean isPriv = item.isPrivate();

                // Room avatar (initials circle or icon)
                StackPane avatar;
                if (isAI) {
                    avatar = createBotAvatar(36);
                } else if (isPriv) {
                    User other = getOtherUser(item);
                    avatar = createAvatarWithStatus(other, 36);
                } else {
                    avatar = createGroupAvatar(displayName, 36);
                }

                VBox textCol = new VBox(2);
                HBox.setHgrow(textCol, Priority.ALWAYS);

                HBox nameRow = new HBox(4);
                nameRow.setAlignment(Pos.CENTER_LEFT);
                if (isAI) {
                    Label pin = new Label("\uD83D\uDCCC");
                    pin.setStyle("-fx-font-size: 10;");
                    nameRow.getChildren().add(pin);
                }
                Label name = new Label(displayName);
                name.getStyleClass().add("room-cell-name");
                nameRow.getChildren().add(name);

                Label subtitle = new Label(isPriv ? "Private" : (isAI ? "AI Assistant" : "Group"));
                subtitle.getStyleClass().add("room-cell-subtitle");

                textCol.getChildren().addAll(nameRow, subtitle);

                // ── Favorite / pin toggle star ──
                boolean isFav = favoriteRoomIds.contains(item.getId());
                Button starBtn = new Button(isFav ? "\u2605" : "\u2606");  // ★ or ☆
                starBtn.getStyleClass().add("fav-toggle-btn");
                if (isFav) starBtn.getStyleClass().add("fav-toggle-btn-active");
                starBtn.setOnAction(ev -> {
                    ev.consume();
                    toggleFavorite(item.getId());
                    roomsList.refresh();
                });

                cell.getChildren().addAll(avatar, textCol, starBtn);
                setGraphic(cell);
                setText(null);
            }
        });

        // TextArea: Shift+Enter = newline, Enter = send, Escape = cancel edit
        messageArea.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                handleSendMessage();
            } else if (e.getCode() == KeyCode.ESCAPE && editingMessage != null) {
                cancelEdit();
            }
        });

        // Clear error when typing
        messageArea.textProperty().addListener((obs, o, n) -> {
            if (n != null && !n.trim().isEmpty()) clearInputError();
        });

        // ── Scheduler: poll on background threads, render on FX thread ──
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "chat-poll");
            t.setDaemon(true);
            return t;
        });
        // Messages: network call on background thread
        scheduler.scheduleAtFixedRate(this::pollMessagesBackground, 2, 2, TimeUnit.SECONDS);
        // Refresh user cache (online status) every 10s
        scheduler.scheduleAtFixedRate(() -> {
            loadUsers();
            Platform.runLater(() -> roomsList.refresh());
        }, 10, 10, TimeUnit.SECONDS);
        // Poll for incoming calls on background thread
        scheduler.scheduleAtFixedRate(this::pollIncomingCallsBackground, 2, 2, TimeUnit.SECONDS);

        // ── Twemoji icons on emoji & attach buttons ──
        Platform.runLater(() -> {
            setupButtonIcon(btnEmoji, "1f60a", 20);  // 😊
            setupButtonIcon(btnAttach, "1f4ce", 20); // 📎
            installHoverCard(btnEmoji, "Emoji Picker", "Choose an emoji to add to your message");
            installHoverCard(btnAttach, "Attach Image", "Send a photo or image from your device");
        });
    }

    // ═══════════════════════════════════════════
    //  EMPTY / CHAT STATE TOGGLE
    // ═══════════════════════════════════════════

    private void showEmptyState() {
        if (emptyStateBox != null) { emptyStateBox.setVisible(true); emptyStateBox.setManaged(true); }
        if (chatContentBox != null) { chatContentBox.setVisible(false); chatContentBox.setManaged(false); }
    }

    private void showChatContent() {
        if (emptyStateBox != null) { emptyStateBox.setVisible(false); emptyStateBox.setManaged(false); }
        if (chatContentBox != null) { chatContentBox.setVisible(true); chatContentBox.setManaged(true); }
    }

    // ═══════════════════════════════════════════
    //  DATA LOADING
    // ═══════════════════════════════════════════

    // ── Favorites persistence ──

    private void loadFavorites() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(ChatController.class);
            String csv = prefs.get(PREF_FAVORITES, "");
            favoriteRoomIds.clear();
            if (!csv.isEmpty()) {
                for (String s : csv.split(",")) {
                    try { favoriteRoomIds.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveFavorites() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(ChatController.class);
            StringBuilder sb = new StringBuilder();
            for (int id : favoriteRoomIds) {
                if (sb.length() > 0) sb.append(",");
                sb.append(id);
            }
            prefs.put(PREF_FAVORITES, sb.toString());
            prefs.flush();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void toggleFavorite(int roomId) {
        if (favoriteRoomIds.contains(roomId)) {
            favoriteRoomIds.remove(roomId);
        } else {
            favoriteRoomIds.add(roomId);
        }
        saveFavorites();
        loadRooms(); // re-sort with new favorites
    }

    private void ensureAIRoom() {
        try {
            List<ChatRoom> rooms = serviceChat.recuperer();
            boolean exists = rooms.stream().anyMatch(r -> AIAssistantService.AI_ROOM_NAME.equals(r.getName()));
            if (!exists) serviceChat.ajouter(new ChatRoom(AIAssistantService.AI_ROOM_NAME));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadUsers() {
        try {
            for (User u : serviceUser.recuperer()) userCache.put(u.getId(), u);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadRooms() {
        try {
            User me = SessionManager.getInstance().getCurrentUser();
            int myId = me != null ? me.getId() : 0;
            List<ChatRoom> rooms = serviceChat.recuperer();

            List<ChatRoom> visible = new ArrayList<>();
            ChatRoom aiRoom = null;

            for (ChatRoom r : rooms) {
                if (AIAssistantService.AI_ROOM_NAME.equals(r.getName())) { aiRoom = r; continue; }
                if (r.isPrivate()) {
                    String n = r.getName();
                    if (n.startsWith("dm_")) {
                        String[] parts = n.split("_");
                        if (parts.length >= 3) {
                            try {
                                int a = Integer.parseInt(parts[1]);
                                int b = Integer.parseInt(parts[2]);
                                if (a == myId || b == myId) visible.add(r);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                } else {
                    visible.add(r);
                }
            }

            // Sort: favorites first, then by date descending
            visible.sort((a, b) -> {
                boolean aFav = favoriteRoomIds.contains(a.getId());
                boolean bFav = favoriteRoomIds.contains(b.getId());
                if (aFav != bFav) return aFav ? -1 : 1;
                if (b.getCreatedAt() != null && a.getCreatedAt() != null)
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                return 0;
            });
            if (aiRoom != null) visible.add(0, aiRoom);
            roomsList.getItems().setAll(visible);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ═══════════════════════════════════════════
    //  DISPLAY NAME + OTHER USER HELPERS
    // ═══════════════════════════════════════════

    private String getDisplayName(ChatRoom room) {
        if (AIAssistantService.AI_ROOM_NAME.equals(room.getName())) return "AI Assistant";
        if (!room.isPrivate()) return room.getName();
        User other = getOtherUser(room);
        if (other != null) return other.getFirstName() + " " + other.getLastName();
        return room.getName();
    }

    private User getOtherUser(ChatRoom room) {
        User me = SessionManager.getInstance().getCurrentUser();
        int myId = me != null ? me.getId() : 0;
        String n = room.getName();
        if (n != null && n.startsWith("dm_")) {
            String[] parts = n.split("_");
            if (parts.length >= 3) {
                try {
                    int a = Integer.parseInt(parts[1]);
                    int b = Integer.parseInt(parts[2]);
                    int otherId = (a == myId) ? b : a;
                    return userCache.get(otherId);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════
    //  SEARCH
    // ═══════════════════════════════════════════

    @FXML
    private void handleSearchKey() {
        String query = roomNameField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            searchResultsBox.setVisible(false); searchResultsBox.setManaged(false);
            return;
        }
        User me = SessionManager.getInstance().getCurrentUser();
        int myId = me != null ? me.getId() : 0;

        List<User> matches = new ArrayList<>();
        for (User u : userCache.values()) {
            if (u.getId() == myId) continue;
            String fullName = (u.getFirstName() + " " + u.getLastName()).toLowerCase();
            if (fullName.contains(query) || u.getEmail().toLowerCase().contains(query)) matches.add(u);
        }

        searchResultsBox.getChildren().clear();
        if (matches.isEmpty()) {
            HBox row = createSearchRow();
            Label icon = new Label("\u2795");
            icon.setStyle("-fx-font-size: 14;");
            Label text = new Label("Create room \"" + roomNameField.getText().trim() + "\"");
            boolean dk = SessionManager.getInstance().isDarkTheme();
            text.setStyle("-fx-text-fill: " + (dk ? "#90DDF0" : "#613039") + "; -fx-font-size: 12;");
            row.getChildren().addAll(icon, text);
            row.setOnMouseClicked(e -> { handleCreateRoom(); hideSearch(); });
            searchResultsBox.getChildren().add(row);
        } else {
            for (User u : matches) {
                HBox row = createSearchRow();
                StackPane av = createAvatarWithStatus(u, 28);

                VBox info = new VBox(1);
                Label nameL = new Label(u.getFirstName() + " " + u.getLastName());
                nameL.getStyleClass().add("search-result-name");
                Label emailL = new Label(u.getEmail());
                emailL.getStyleClass().add("search-result-email");

                Label statusL = new Label(u.isOnline() ? "\u25CF Online" : "\u25CF Offline");
                boolean dkS = SessionManager.getInstance().isDarkTheme();
                statusL.setStyle(u.isOnline()
                        ? "-fx-text-fill: #4ade80; -fx-font-size: 10;"
                        : "-fx-text-fill: " + (dkS ? "#6B6B78" : "#8A7C7F") + "; -fx-font-size: 10;");

                info.getChildren().addAll(nameL, emailL, statusL);
                row.getChildren().addAll(av, info);
                row.setOnMouseClicked(e -> openPrivateChat(u));
                searchResultsBox.getChildren().add(row);
            }
        }
        searchResultsBox.setVisible(true);
        searchResultsBox.setManaged(true);
    }

    private HBox createSearchRow() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("search-result-item");
        boolean dkR = SessionManager.getInstance().isDarkTheme();
        String hoverBg = dkR ? "rgba(44,102,110,0.15)" : "rgba(97,48,57,0.08)";
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: " + hoverBg + "; -fx-background-radius: 8;"));
        row.setOnMouseExited(e -> row.setStyle(""));
        return row;
    }

    private void hideSearch() {
        searchResultsBox.setVisible(false);
        searchResultsBox.setManaged(false);
    }

    // ═══════════════════════════════════════════
    //  OPEN PRIVATE CHAT / CREATE ROOM
    // ═══════════════════════════════════════════

    private void openPrivateChat(User other) {
        hideSearch();
        roomNameField.clear();
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) return;
        int a = Math.min(me.getId(), other.getId());
        int b = Math.max(me.getId(), other.getId());
        String roomName = "dm_" + a + "_" + b;
        try {
            serviceChat.getOrCreateRoom(roomName);
            loadRooms();
            for (ChatRoom r : roomsList.getItems()) {
                if (r.getName().equals(roomName)) { roomsList.getSelectionModel().select(r); break; }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    @FXML
    private void handleCreateRoom() {
        String name = roomNameField.getText().trim();
        if (!name.isEmpty()) {
            try {
                User me = SessionManager.getInstance().getCurrentUser();
                serviceChat.ajouter(new ChatRoom(name, "group", me != null ? me.getId() : 0));
                roomNameField.clear();
                hideSearch();
                loadRooms();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // ═══════════════════════════════════════════
    //  SELECT ROOM
    // ═══════════════════════════════════════════

    private void selectRoom(ChatRoom room) {
        this.currentRoom = room;
        this.isAIRoom = AIAssistantService.AI_ROOM_NAME.equals(room.getName());
        this.lastMessageCount = -1;
        this.lastMessageId = 0;
        String display = getDisplayName(room);

        showChatContent();

        currentRoomLabel.setText(room.isPrivate() ? "\uD83D\uDD12 " + display : "# " + display);

        if (isAIRoom) {
            roomMembersLabel.setText("Powered by Gemini \u2014 type any HR question");
            if (onlineStatusDot != null) { onlineStatusDot.setVisible(false); onlineStatusDot.setManaged(false); }
            AIAssistantService.isHealthy().thenAccept(ok -> Platform.runLater(() ->
                    roomMembersLabel.setText(ok ? "\uD83D\uDFE2 Online \u2014 Powered by Gemini" : "\uD83D\uDD34 Offline \u2014 AI service starting...")));
        } else if (room.isPrivate()) {
            User other = getOtherUser(room);
            if (other != null && onlineStatusDot != null) {
                onlineStatusDot.setVisible(true);
                onlineStatusDot.setManaged(true);
                onlineStatusDot.setText("\u25CF");
                onlineStatusDot.setStyle(other.isOnline()
                        ? "-fx-text-fill: #4ade80; -fx-font-size: 10;"
                        : "-fx-text-fill: #6B6B78; -fx-font-size: 10;");
                roomMembersLabel.setText(other.isOnline() ? "Online" : "Offline");
            } else {
                roomMembersLabel.setText("Private conversation");
                if (onlineStatusDot != null) { onlineStatusDot.setVisible(false); onlineStatusDot.setManaged(false); }
            }
        } else {
            roomMembersLabel.setText("");
            if (onlineStatusDot != null) { onlineStatusDot.setVisible(false); onlineStatusDot.setManaged(false); }
        }

        updateKebabMenuVisibility();
        updateCallButtonVisibility();
        editingMessage = null;
        resetInputBar();

        if (isAIRoom) renderAIChat(); else forceRefreshMessages();
    }

    // ═══════════════════════════════════════════
    //  POLLING + REAL-TIME DETECT
    // ═══════════════════════════════════════════

    /** Called by scheduler on background thread — only re-renders if new messages detected. */
    private void pollMessagesBackground() {
        if (currentRoom == null || isAIRoom) return;
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) return;

        try {
            List<Message> messages = serviceMessage.getByRoom(currentRoom.getId());
            int count = messages.size();
            int latestId = messages.isEmpty() ? 0 : messages.get(messages.size() - 1).getId();

            if (count != lastMessageCount || latestId != lastMessageId) {
                int prevLastId = lastMessageId;
                lastMessageCount = count;
                lastMessageId = latestId;

                Platform.runLater(() -> {
                    if (prevLastId > 0 && latestId > prevLastId) {
                        for (int i = messages.size() - 1; i >= 0; i--) {
                            Message m = messages.get(i);
                            if (m.getId() <= prevLastId) break;
                            if (m.getSenderId() != me.getId()) {
                                User sender = userCache.get(m.getSenderId());
                                String senderName = sender != null ? sender.getFirstName() : "Someone";
                                String preview = m.getContent().startsWith(IMAGE_PREFIX) ? "\uD83D\uDCF7 Image"
                                        : m.getContent().startsWith(FILE_PREFIX) ? "\uD83D\uDCC4 File" : m.getContent();
                                showToast(senderName, preview);
                                SoundManager.getInstance().play(
                                    m.getContent().startsWith(IMAGE_PREFIX) || m.getContent().startsWith(FILE_PREFIX)
                                        ? SoundManager.IMAGE_RECEIVED
                                        : SoundManager.NEW_MESSAGE
                                );
                                break;
                            }
                        }
                    }
                    renderMessages(messages);
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /** Force-render right now (used on room switch). */
    private void forceRefreshMessages() {
        try {
            List<Message> messages = serviceMessage.getByRoom(currentRoom.getId());
            lastMessageCount = messages.size();
            lastMessageId = messages.isEmpty() ? 0 : messages.get(messages.size() - 1).getId();
            renderMessages(messages);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void renderMessages(List<Message> messages) {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        messagesContainer.getChildren().clear();
        int currentUserId = currentUser.getId();
        int lastSenderId = -1;

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            boolean isMe = msg.getSenderId() == currentUserId;
            boolean showAvatar = msg.getSenderId() != lastSenderId;
            boolean isLastInGroup = (i + 1 >= messages.size()) || messages.get(i + 1).getSenderId() != msg.getSenderId();
            messagesContainer.getChildren().add(buildMessageRow(msg, isMe, showAvatar, isLastInGroup));
            lastSenderId = msg.getSenderId();
        }

        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    // ═══════════════════════════════════════════
    //  TOAST NOTIFICATION
    // ═══════════════════════════════════════════

    private void showToast(String senderName, String preview) {
        if (chatRootStack == null) return;

        if (preview.length() > 60) preview = preview.substring(0, 57) + "...";

        VBox toast = new VBox(2);
        toast.getStyleClass().add("toast-notification");
        toast.setMaxWidth(320);
        toast.setMaxHeight(80);
        toast.setPadding(new Insets(12, 16, 12, 16));
        StackPane.setAlignment(toast, Pos.TOP_RIGHT);
        StackPane.setMargin(toast, new Insets(16, 16, 0, 0));
        toast.setMouseTransparent(true);

        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        Label bell = new Label("\uD83D\uDD14");
        bell.setStyle("-fx-font-size: 12;");
        Label title = new Label(senderName);
        title.getStyleClass().add("toast-title");
        header.getChildren().addAll(bell, title);

        Label body = new Label(preview);
        body.getStyleClass().add("toast-body");
        body.setWrapText(true);

        toast.getChildren().addAll(header, body);
        toast.setOpacity(0);
        chatRootStack.getChildren().add(toast);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), toast);
        fadeIn.setToValue(1.0);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(250), toast);
        slideIn.setFromY(-30);
        slideIn.setToY(0);

        new ParallelTransition(fadeIn, slideIn).play();

        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(ev -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e2 -> chatRootStack.getChildren().remove(toast));
            fadeOut.play();
        });
        pause.play();
    }

    // ═══════════════════════════════════════════
    //  AI CHAT RENDERING
    // ═══════════════════════════════════════════

    private void renderAIChat() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        messagesContainer.getChildren().clear();
        boolean lastWasUser = false;
        boolean first = true;

        for (int i = 0; i < aiChatHistory.size(); i++) {
            AIChatEntry entry = aiChatHistory.get(i);
            boolean isMe = entry.fromUser;
            boolean showAvatar = first || (isMe != lastWasUser);
            boolean isLast = (i + 1 >= aiChatHistory.size()) || aiChatHistory.get(i + 1).fromUser != isMe;
            messagesContainer.getChildren().add(buildAIChatRow(entry, currentUser, showAvatar, isLast));
            lastWasUser = isMe;
            first = false;
        }
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    private HBox buildAIChatRow(AIChatEntry entry, User currentUser, boolean showAvatar, boolean isLastInGroup) {
        boolean isMe = entry.fromUser;
        HBox row = new HBox(8);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(isLastInGroup ? 6 : 1, 0, 0, 0));

        if (!isMe) {
            row.getChildren().add(showAvatar ? createBotAvatar(32) : spacer(32));
        }

        VBox col = new VBox(2);
        col.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        col.setMaxWidth(420);

        if (showAvatar && !isMe) {
            Label n = new Label("SynergyBot");
            n.getStyleClass().add("msg-bot-name");
            col.getChildren().add(n);
        }

        Label bubble = new Label(entry.content);
        bubble.getStyleClass().add(isMe ? "msg-bubble-me" : "msg-bubble-bot");
        bubble.setWrapText(true);
        bubble.setMaxWidth(380);
        col.getChildren().add(bubble);

        if (isLastInGroup) {
            Label t = new Label(new SimpleDateFormat("hh:mm a").format(new Date(entry.timestamp)));
            t.getStyleClass().add("msg-timestamp");
            col.getChildren().add(t);
        }

        row.getChildren().add(col);
        if (isMe) row.getChildren().add(showAvatar ? createAvatar(currentUser, 32) : spacer(32));
        return row;
    }

    // ═══════════════════════════════════════════
    //  MESSAGE BUBBLE BUILDER
    // ═══════════════════════════════════════════

    private HBox buildMessageRow(Message msg, boolean isMe, boolean showAvatar, boolean isLastInGroup) {
        boolean isBot = msg.getSenderId() == AI_BOT_SENDER_ID;
        User sender = isBot ? null : userCache.get(msg.getSenderId());
        String senderName = isBot ? "SynergyBot"
                : (sender != null ? sender.getFirstName() + " " + sender.getLastName() : "User #" + msg.getSenderId());

        HBox row = new HBox(8);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(isLastInGroup ? 6 : 1, 0, 0, 0));

        if (!isMe) {
            if (showAvatar) {
                row.getChildren().add(isBot ? createBotAvatar(32) : createAvatar(sender, 32));
            } else {
                row.getChildren().add(spacer(32));
            }
        }

        VBox col = new VBox(2);
        col.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        col.setMaxWidth(420);

        if (showAvatar && !isMe) {
            Label n = new Label(senderName);
            n.getStyleClass().add(isBot ? "msg-bot-name" : "msg-sender-name");
            col.getChildren().add(n);
        }

        boolean isImage = msg.getContent().startsWith(IMAGE_PREFIX) && msg.getContent().endsWith(IMAGE_SUFFIX);
        boolean isFile = msg.getContent().startsWith(FILE_PREFIX) && msg.getContent().endsWith(FILE_SUFFIX);

        if (isImage) {
            try {
                String base64 = msg.getContent().substring(IMAGE_PREFIX.length(),
                        msg.getContent().length() - IMAGE_SUFFIX.length());
                byte[] imgBytes = Base64.getDecoder().decode(base64);
                Image img = new Image(new ByteArrayInputStream(imgBytes));
                ImageView iv = new ImageView(img);
                double displayWidth = Math.min(img.getWidth(), 280);
                iv.setFitWidth(displayWidth);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                double displayHeight = displayWidth * (img.getHeight() / img.getWidth());
                Rectangle clip = new Rectangle(displayWidth, displayHeight);
                clip.setArcWidth(14);
                clip.setArcHeight(14);
                iv.setClip(clip);
                iv.getStyleClass().add("msg-image");
                iv.setCursor(javafx.scene.Cursor.HAND);
                iv.setOnMouseClicked(e -> { if (e.getClickCount() == 1 && !e.isPopupTrigger()) showImagePopup(img); });
                if (isMe) {
                    ContextMenu ctx = new ContextMenu();
                    MenuItem deleteItem = new MenuItem("Delete");
                    deleteItem.setOnAction(e -> deleteMessage(msg));
                    ctx.getItems().add(deleteItem);
                    iv.setOnContextMenuRequested(e -> ctx.show(iv, e.getScreenX(), e.getScreenY()));
                }
                col.getChildren().add(iv);
            } catch (Exception ex) {
                Label fallback = new Label("[Image]");
                fallback.getStyleClass().add(isMe ? "msg-bubble-me" : "msg-bubble-other");
                col.getChildren().add(fallback);
            }
        } else if (isFile) {
            // Parse: fileId|fileName|fileSize|contentType
            try {
                String fileData = msg.getContent().substring(FILE_PREFIX.length(),
                        msg.getContent().length() - FILE_SUFFIX.length());
                String[] parts = fileData.split("\\|", 4);
                String fileId = parts[0];
                String fileName = parts.length > 1 ? parts[1] : "file";
                long fileSize = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
                String contentType = parts.length > 3 ? parts[3] : "application/octet-stream";

                // File card
                HBox fileCard = new HBox(10);
                fileCard.setAlignment(Pos.CENTER_LEFT);
                fileCard.getStyleClass().add("msg-file-card");
                fileCard.setPadding(new Insets(10, 14, 10, 14));
                fileCard.setMaxWidth(320);

                Label icon = new Label(getFileIcon(fileName, contentType));
                icon.setStyle("-fx-font-size: 28;");

                VBox fileInfo = new VBox(2);
                HBox.setHgrow(fileInfo, Priority.ALWAYS);
                Label nameLabel = new Label(fileName);
                nameLabel.getStyleClass().add("msg-file-name");
                nameLabel.setWrapText(true);
                nameLabel.setMaxWidth(200);
                Label sizeLabel = new Label(formatFileSize(fileSize));
                sizeLabel.getStyleClass().add("msg-file-size");
                fileInfo.getChildren().addAll(nameLabel, sizeLabel);

                Button downloadBtn = new Button("⬇");
                downloadBtn.getStyleClass().add("msg-file-download-btn");
                downloadBtn.setOnAction(e -> handleDownloadFile(fileId, fileName));

                fileCard.getChildren().addAll(icon, fileInfo, downloadBtn);

                if (isMe) {
                    ContextMenu ctx = new ContextMenu();
                    MenuItem deleteItem = new MenuItem("Delete");
                    deleteItem.setOnAction(e -> deleteMessage(msg));
                    ctx.getItems().add(deleteItem);
                    fileCard.setOnContextMenuRequested(e -> ctx.show(fileCard, e.getScreenX(), e.getScreenY()));
                }

                col.getChildren().add(fileCard);
            } catch (Exception ex) {
                Label fallback = new Label("[File]");
                fallback.getStyleClass().add(isMe ? "msg-bubble-me" : "msg-bubble-other");
                col.getChildren().add(fallback);
            }
        } else {
            javafx.scene.text.TextFlow bubbleFlow = buildEmojiTextFlow(msg.getContent());
            bubbleFlow.getStyleClass().add(isBot ? "msg-bubble-bot" : (isMe ? "msg-bubble-me" : "msg-bubble-other"));
            bubbleFlow.setMaxWidth(380);
            bubbleFlow.setPrefWidth(Region.USE_COMPUTED_SIZE);

            if (isMe) {
                ContextMenu ctx = new ContextMenu();
                MenuItem editItem = new MenuItem("Edit");
                editItem.setOnAction(e -> startEdit(msg));
                MenuItem deleteItem = new MenuItem("Delete");
                deleteItem.setOnAction(e -> deleteMessage(msg));
                ctx.getItems().addAll(editItem, deleteItem);
                bubbleFlow.setOnContextMenuRequested(e -> ctx.show(bubbleFlow, e.getScreenX(), e.getScreenY()));
                bubbleFlow.setOnMouseClicked(e -> { if (e.getClickCount() == 2) startEdit(msg); });
            }

            col.getChildren().add(bubbleFlow);
        }

        if (isLastInGroup && msg.getTimestamp() != null) {
            Label t = new Label(new SimpleDateFormat("hh:mm a").format(msg.getTimestamp()));
            t.getStyleClass().add("msg-timestamp");
            col.getChildren().add(t);
        }

        row.getChildren().add(col);

        if (isMe) {
            User me = SessionManager.getInstance().getCurrentUser();
            row.getChildren().add(showAvatar ? createAvatar(me, 32) : spacer(32));
        }
        return row;
    }

    // ═══════════════════════════════════════════
    //  EMOJI TEXT-FLOW BUILDER
    // ═══════════════════════════════════════════

    /**
     * Builds a TextFlow where known emoji characters are rendered as
     * Twemoji PNG images and all other text stays as Text nodes.
     */
    private javafx.scene.text.TextFlow buildEmojiTextFlow(String text) {
        javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
        flow.setLineSpacing(2);

        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);

            // Skip variation selectors (fe0f, fe0e) – treat as part of preceding emoji
            if (cp == 0xFE0F || cp == 0xFE0E) {
                i += charCount;
                continue;
            }

            String hex = Integer.toHexString(cp);
            if (utils.EmojiPicker.isKnownEmoji(hex)) {
                // flush preceding text
                if (buf.length() > 0) {
                    javafx.scene.text.Text t = new javafx.scene.text.Text(buf.toString());
                    t.getStyleClass().add("msg-text");
                    flow.getChildren().add(t);
                    buf.setLength(0);
                }
                // add emoji image
                ImageView iv = new ImageView();
                iv.setFitWidth(22);
                iv.setFitHeight(22);
                iv.setSmooth(true);
                Image cached = utils.EmojiPicker.getCachedEmojiImage(hex, 22);
                if (cached != null) {
                    iv.setImage(cached);
                } else {
                    // load asynchronously from CDN
                    String imgUrl = utils.EmojiPicker.getCdnBase() + hex + ".png";
                    javafx.scene.image.Image img = new javafx.scene.image.Image(imgUrl, 22, 22, true, true, true);
                    iv.setImage(img);
                }
                flow.getChildren().add(iv);
            } else {
                buf.appendCodePoint(cp);
            }
            i += charCount;
        }
        // flush remaining text
        if (buf.length() > 0) {
            javafx.scene.text.Text t = new javafx.scene.text.Text(buf.toString());
            t.getStyleClass().add("msg-text");
            flow.getChildren().add(t);
        }

        return flow;
    }

    // ═══════════════════════════════════════════
    //  AVATAR HELPERS
    // ═══════════════════════════════════════════

    private Region spacer(double w) {
        Region s = new Region();
        s.setMinWidth(w); s.setMaxWidth(w);
        return s;
    }

    private StackPane createBotAvatar(double size) {
        StackPane w = new StackPane();
        w.setMinSize(size, size); w.setMaxSize(size, size);
        Circle c = new Circle(size / 2);
        c.getStyleClass().add("msg-bot-avatar-circle");
        Label e = new Label("\uD83E\uDD16");
        e.setStyle("-fx-font-size: " + (int)(size * 0.5) + ";");
        e.setMouseTransparent(true);
        w.getChildren().addAll(c, e);
        return w;
    }

    /** Avatar with online/offline status dot. */
    private StackPane createAvatarWithStatus(User user, double size) {
        StackPane wrapper = new StackPane();
        wrapper.setMinSize(size + 4, size + 4);
        wrapper.setMaxSize(size + 4, size + 4);

        StackPane inner = createAvatar(user, size);

        Circle dot = new Circle(5);
        dot.setFill(user != null && user.isOnline() ? Color.web("#4ade80") : Color.web("#6B6B78"));
        dot.setStroke(Color.web("#0A090C"));
        dot.setStrokeWidth(2);
        StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);

        wrapper.getChildren().addAll(inner, dot);
        return wrapper;
    }

    private StackPane createGroupAvatar(String name, double size) {
        StackPane w = new StackPane();
        w.setMinSize(size, size); w.setMaxSize(size, size);
        Circle c = new Circle(size / 2);
        c.getStyleClass().add("msg-avatar-circle");
        String initial = (name != null && !name.isEmpty()) ? String.valueOf(name.charAt(0)).toUpperCase() : "#";
        Label l = new Label(initial);
        l.getStyleClass().add("msg-avatar-initials");
        l.setStyle("-fx-font-size: " + (int)(size * 0.38) + ";");
        w.getChildren().addAll(c, l);
        return w;
    }

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
            String initials = getInitials(user);
            circle.getStyleClass().add("msg-avatar-circle");
            Label l = new Label(initials);
            l.getStyleClass().add("msg-avatar-initials");
            l.setStyle("-fx-font-size: " + (int)(size * 0.38) + ";");
            wrapper.getChildren().addAll(circle, l);
            return wrapper;
        }
        wrapper.getChildren().add(circle);
        return wrapper;
    }

    private String getInitials(User u) {
        if (u == null) return "?";
        String i = "";
        if (u.getFirstName() != null && !u.getFirstName().isEmpty()) i += u.getFirstName().charAt(0);
        if (u.getLastName() != null && !u.getLastName().isEmpty()) i += u.getLastName().charAt(0);
        return i.isEmpty() ? "?" : i.toUpperCase();
    }

    // ═══════════════════════════════════════════
    //  SEND / EDIT / DELETE
    // ═══════════════════════════════════════════

    @FXML
    private void handleSendMessage() {
        if (currentRoom == null) return;
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String content = messageArea.getText().trim();
        if (content.isEmpty()) {
            showInputError("Please enter a message before sending.");
            return;
        }
        clearInputError();

        // Bad words filter
        BadWordsService.CheckResult badCheck = BadWordsService.check(content);
        if (badCheck.hasBadWords) {
            showInputError("Your message contains inappropriate language. Please revise it.");
            messageArea.setText(badCheck.censoredContent);
            return;
        }

        // AI room
        if (isAIRoom && editingMessage == null) {
            messageArea.clear();
            aiChatHistory.add(new AIChatEntry(true, content));
            renderAIChat();

            Label typingLabel = new Label("SynergyBot is thinking...");
            typingLabel.getStyleClass().add("msg-ai-typing");
            HBox typingRow = new HBox(typingLabel);
            typingRow.setAlignment(Pos.CENTER_LEFT);
            typingRow.setPadding(new Insets(4, 0, 4, 44));
            messagesContainer.getChildren().add(typingRow);
            Platform.runLater(() -> messagesScroll.setVvalue(1.0));

            AIAssistantService.chat(currentUser.getId(), content)
                    .thenAccept(reply -> Platform.runLater(() -> {
                        messagesContainer.getChildren().remove(typingRow);
                        aiChatHistory.add(new AIChatEntry(false, reply));
                        renderAIChat();
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            messagesContainer.getChildren().remove(typingRow);
                            aiChatHistory.add(new AIChatEntry(false, "\u26A0 Error: " + ex.getMessage()));
                            renderAIChat();
                        });
                        return null;
                    });
            return;
        }

        // Normal room
        try {
            if (editingMessage != null) {
                editingMessage.setContent(content);
                serviceMessage.modifier(editingMessage);
                editingMessage = null;
                resetInputBar();
            } else {
                serviceMessage.ajouter(new Message(currentUser.getId(), currentRoom.getId(), content));
                SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
            }
            messageArea.clear();
            forceRefreshMessages();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void startEdit(Message msg) {
        editingMessage = msg;
        messageArea.setText(msg.getContent());
        messageArea.requestFocus();
        messageArea.selectAll();
        btnSend.setText("Save");
        messageArea.setPromptText("Editing message... (Esc to cancel)");
    }

    private void cancelEdit() {
        editingMessage = null;
        messageArea.clear();
        resetInputBar();
    }

    private void resetInputBar() {
        btnSend.setText("Send");
        messageArea.setPromptText("Type a message... (Shift+Enter for new line)");
        clearInputError();
    }

    // ═══════════════════════════════════════════
    //  INPUT VALIDATION
    // ═══════════════════════════════════════════

    private void showInputError(String message) {
        SoundManager.getInstance().play(SoundManager.ERROR);
        if (!messageArea.getStyleClass().contains("chat-input-invalid")) {
            messageArea.getStyleClass().add("chat-input-invalid");
        }
        inputErrorLabel.setText(message);
        inputErrorLabel.setVisible(true);
        inputErrorLabel.setManaged(true);

        double startX = messageArea.getTranslateX();
        Timeline shake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(messageArea.translateXProperty(), startX)),
                new KeyFrame(Duration.millis(50), new KeyValue(messageArea.translateXProperty(), startX - 6)),
                new KeyFrame(Duration.millis(100), new KeyValue(messageArea.translateXProperty(), startX + 6)),
                new KeyFrame(Duration.millis(150), new KeyValue(messageArea.translateXProperty(), startX - 4)),
                new KeyFrame(Duration.millis(200), new KeyValue(messageArea.translateXProperty(), startX + 4)),
                new KeyFrame(Duration.millis(250), new KeyValue(messageArea.translateXProperty(), startX - 2)),
                new KeyFrame(Duration.millis(300), new KeyValue(messageArea.translateXProperty(), startX))
        );
        shake.play();

        PauseTransition autoClear = new PauseTransition(Duration.seconds(3));
        autoClear.setOnFinished(e -> clearInputError());
        autoClear.play();
        messageArea.requestFocus();
    }

    private void clearInputError() {
        messageArea.getStyleClass().remove("chat-input-invalid");
        inputErrorLabel.setVisible(false);
        inputErrorLabel.setManaged(false);
        inputErrorLabel.setText("");
    }

    private Window chatOwnerWindow() {
        return chatRootStack != null && chatRootStack.getScene() != null
                ? chatRootStack.getScene().getWindow() : null;
    }

    private void deleteMessage(Message msg) {
        if (utils.StyledAlert.confirm(chatOwnerWindow(), "Delete Message", "Delete this message?")) {
            try { serviceMessage.supprimer(msg.getId()); forceRefreshMessages(); }
            catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // ═══════════════════════════════════════════
    //  BUTTON ICON + HOVER CARD HELPERS
    // ═══════════════════════════════════════════

    private void setupButtonIcon(Button btn, String hex, int size) {
        btn.setText("");
        ImageView iv = new ImageView();
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setSmooth(true);
        Image cached = utils.EmojiPicker.getCachedEmojiImage(hex, size);
        if (cached != null) {
            iv.setImage(cached);
        } else {
            iv.setImage(new Image(utils.EmojiPicker.getCdnBase() + hex + ".png", size, size, true, true, true));
        }
        btn.setGraphic(iv);
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void installHoverCard(Button btn, String title, String desc) {
        Tooltip tip = new Tooltip();
        tip.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        boolean dark = SessionManager.getInstance().isDarkTheme();

        VBox card = new VBox(4);
        card.setStyle("-fx-padding: 2 0;");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: "
                + (dark ? "#F0EDEE" : "#0D0A0B") + ";");

        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-font-size: 11; -fx-text-fill: "
                + (dark ? "#9E9EA8" : "#8A7C7F") + ";");
        descLbl.setMaxWidth(200);
        descLbl.setWrapText(true);

        card.getChildren().addAll(titleLbl, descLbl);
        tip.setGraphic(card);

        String bg = dark ? "#18171E" : "#FFFFFF";
        String border = dark ? "#2C666E55" : "#E0D6D8";
        String shadow = dark ? "rgba(0,0,0,0.45)" : "rgba(97,48,57,0.12)";

        tip.setStyle("-fx-background-color: " + bg + "; "
                + "-fx-border-color: " + border + "; "
                + "-fx-border-radius: 10; -fx-background-radius: 10; "
                + "-fx-padding: 10 14; "
                + "-fx-effect: dropshadow(gaussian, " + shadow + ", 10, 0, 0, 3);");
        tip.setShowDelay(Duration.millis(100));
        tip.setHideDelay(Duration.millis(200));

        btn.setTooltip(tip);
    }

    // ═══════════════════════════════════════════
    //  EMOJI PICKER
    // ═══════════════════════════════════════════

    @FXML private Button btnEmoji;

    @FXML
    private void handleEmojiPicker() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        javafx.geometry.Bounds bounds = btnEmoji.localToScreen(btnEmoji.getBoundsInLocal());
        double x = bounds.getMinX();
        double y = bounds.getMinY() - 370; // show above the button
        utils.EmojiPicker.show(
                btnEmoji.getScene().getWindow(), x, y,
                emoji -> messageArea.appendText(emoji));
    }

    // ═══════════════════════════════════════════
    //  FILE / IMAGE ATTACH + PREVIEW
    // ═══════════════════════════════════════════

    @FXML
    private void handleAttachImage() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        if (currentRoom == null || isAIRoom) return;
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Attach File");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.xls", "*.xlsx", "*.ppt", "*.pptx", "*.txt"),
                new FileChooser.ExtensionFilter("Archives", "*.zip", "*.rar", "*.7z", "*.tar", "*.gz"),
                new FileChooser.ExtensionFilter("Media", "*.mp3", "*.wav", "*.mp4", "*.avi", "*.mkv")
        );
        File file = fc.showOpenDialog(chatRootStack.getScene().getWindow());
        if (file == null) return;

        // 300 MB limit
        if (file.length() > 300L * 1024 * 1024) {
            showInputError("File is too large (max 300 MB).");
            return;
        }

        String name = file.getName().toLowerCase();
        boolean isImage = name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".bmp");

        if (isImage && file.length() < 5 * 1024 * 1024) {
            // Small images: inline base64 (instant preview)
            sendInlineImage(file, me);
        } else {
            // Everything else: upload to server as file
            sendFile(file, me);
        }
    }

    /** Send a small image inline as base64 in the message content. */
    private void sendInlineImage(File file, User me) {
        try {
            BufferedImage original = ImageIO.read(file);
            if (original == null) { showInputError("Could not read image."); return; }

            int maxDim = 600;
            int w = original.getWidth(), h = original.getHeight();
            if (w > maxDim || h > maxDim) {
                double scale = Math.min((double) maxDim / w, (double) maxDim / h);
                int nw = (int)(w * scale), nh = (int)(h * scale);
                BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = resized.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(original, 0, 0, nw, nh, null);
                g.dispose();
                original = resized;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(original, "jpg", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            String content = IMAGE_PREFIX + base64 + IMAGE_SUFFIX;
            serviceMessage.ajouter(new Message(me.getId(), currentRoom.getId(), content));
            SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
            forceRefreshMessages();
        } catch (Exception e) {
            showInputError("Failed to send image.");
            e.printStackTrace();
        }
    }

    /** Upload a file to the server and send a [FILE] reference message. */
    private void sendFile(File file, User me) {
        showToast("Upload", "Uploading " + file.getName() + "...");

        new Thread(() -> {
            JsonElement resp = ApiClient.uploadFile("/files/upload", file);
            if (resp != null && resp.isJsonObject()) {
                JsonObject obj = resp.getAsJsonObject();
                String fileId = obj.get("file_id").getAsString();
                String fileName = obj.get("filename").getAsString();
                long fileSize = obj.get("size").getAsLong();
                String contentType = obj.get("content_type").getAsString();

                String content = FILE_PREFIX + fileId + "|" + fileName + "|" + fileSize + "|" + contentType + FILE_SUFFIX;

                Platform.runLater(() -> {
                    try {
                        serviceMessage.ajouter(new Message(me.getId(), currentRoom.getId(), content));
                        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
                        forceRefreshMessages();
                    } catch (SQLException e) {
                        showInputError("Failed to send file message.");
                        e.printStackTrace();
                    }
                });
            } else {
                Platform.runLater(() -> showInputError("Failed to upload file."));
            }
        }, "file-upload").start();
    }

    /** Format file size bytes into human-readable string. */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** Determine a file-type icon emoji based on mime type or file extension. */
    private String getFileIcon(String filename, String mimeType) {
        String name = filename.toLowerCase();
        if (mimeType.startsWith("image/")) return "🖼";
        if (mimeType.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv")) return "🎬";
        if (mimeType.startsWith("audio/") || name.endsWith(".mp3") || name.endsWith(".wav")) return "🎵";
        if (name.endsWith(".pdf")) return "📑";
        if (name.endsWith(".doc") || name.endsWith(".docx")) return "📝";
        if (name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".csv")) return "📊";
        if (name.endsWith(".ppt") || name.endsWith(".pptx")) return "📋";
        if (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") || name.endsWith(".gz")) return "📦";
        if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".log")) return "📄";
        if (name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".html") || name.endsWith(".css")) return "💻";
        return "📄";
    }

    /** Handle downloading a file attachment. */
    private void handleDownloadFile(String fileId, String fileName) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save File");
        fc.setInitialFileName(fileName);

        // Add extension filter based on file type
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")).toLowerCase() : "";
        if (!ext.isEmpty()) {
            String typeName;
            switch (ext) {
                case ".png": case ".jpg": case ".jpeg": case ".gif": case ".bmp": case ".webp":
                    typeName = "Image Files"; break;
                case ".pdf": typeName = "PDF Documents"; break;
                case ".doc": case ".docx": typeName = "Word Documents"; break;
                case ".xls": case ".xlsx": typeName = "Excel Spreadsheets"; break;
                case ".ppt": case ".pptx": typeName = "PowerPoint Presentations"; break;
                case ".mp3": case ".wav": case ".ogg": case ".flac": typeName = "Audio Files"; break;
                case ".mp4": case ".avi": case ".mkv": case ".mov": case ".webm": typeName = "Video Files"; break;
                case ".zip": case ".rar": case ".7z": case ".tar": case ".gz": typeName = "Archives"; break;
                case ".txt": case ".md": case ".log": typeName = "Text Files"; break;
                default: typeName = ext.substring(1).toUpperCase() + " Files"; break;
            }
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(typeName, "*" + ext));
        }
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));

        File dest = fc.showSaveDialog(chatRootStack.getScene().getWindow());
        if (dest == null) return;

        showToast("Download", "Downloading " + fileName + "...");

        new Thread(() -> {
            boolean ok = ApiClient.downloadFile("/files/download/" + fileId, dest);
            Platform.runLater(() -> {
                if (ok) {
                    showToast("Download", fileName + " saved successfully!");
                } else {
                    showToast("Download", "Failed to download " + fileName);
                }
            });
        }, "file-download").start();
    }

    private void showImagePopup(Image img) {
        javafx.stage.Stage popup = new javafx.stage.Stage();
        popup.initStyle(javafx.stage.StageStyle.UTILITY);
        popup.setTitle("Image Preview");

        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setFitWidth(Math.min(img.getWidth(), 800));
        iv.setFitHeight(Math.min(img.getHeight(), 600));

        StackPane root = new StackPane(iv);
        boolean dkPop = SessionManager.getInstance().isDarkTheme();
        root.setStyle("-fx-background-color: " + (dkPop ? "#0A090C" : "#F0EDED") + "; -fx-padding: 12;");

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        popup.setScene(scene);
        popup.show();
    }

    // ═══════════════════════════════════════════
    //  ROOM DELETE (Admin)
    // ═══════════════════════════════════════════

    private void updateKebabMenuVisibility() {
        User u = SessionManager.getInstance().getCurrentUser();
        boolean show = u != null && "ADMIN".equalsIgnoreCase(u.getRole()) && currentRoom != null;
        chatMenuBtn.setVisible(show);
        chatMenuBtn.setManaged(show);
    }

    @FXML
    private void handleDeleteRoom() {
        if (currentRoom == null) return;
        User u = SessionManager.getInstance().getCurrentUser();
        if (u == null || !"ADMIN".equalsIgnoreCase(u.getRole())) return;

        if (utils.StyledAlert.confirm(chatOwnerWindow(), "Delete Chat Room",
                "Delete \"" + currentRoom.getName() + "\" and all its messages?\nThis action cannot be undone.")) {
            try {
                serviceChat.supprimer(currentRoom.getId());
                currentRoom = null;
                isAIRoom = false;
                showEmptyState();
                messagesContainer.getChildren().clear();
                aiChatHistory.clear();
                updateKebabMenuVisibility();
                loadRooms();
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // ═══════════════════════════════════════════
    //  AUDIO CALLS
    // ═══════════════════════════════════════════

    /** Show/hide call button based on context (only for private chats). */
    private void updateCallButtonVisibility() {
        if (btnCall == null) return;
        boolean show = currentRoom != null && currentRoom.isPrivate() && !isAIRoom;
        btnCall.setVisible(show);
        btnCall.setManaged(show);
        if (btnVideoCall != null) {
            btnVideoCall.setVisible(show);
            btnVideoCall.setManaged(show);
        }
    }

    /** Initiate a video call (auto-starts screen share once connected). */
    @FXML
    private void handleStartVideoCall() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        pendingVideoCall = true;
        handleStartCall("video");
    }

    /** Initiate a call to the other user in the current private chat. */
    @FXML
    private void handleStartCall() {
        handleStartCall("audio");
    }

    /** Start a call with the given type ("audio" or "video"). */
    private void handleStartCall(String callType) {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        if (currentRoom == null || !currentRoom.isPrivate() || isAIRoom) return;
        User me = SessionManager.getInstance().getCurrentUser();
        User other = getOtherUser(currentRoom);
        if (me == null || other == null) return;

        // Check if other user is online
        if (!other.isOnline()) {
            showToast("Call", other.getFirstName() + " is offline and cannot receive calls.");
            return;
        }

        // Check no active call already
        if (activeCall != null) {
            showToast("Call", "You already have an active call.");
            return;
        }

        Call call = serviceCall.createCall(me.getId(), other.getId(), currentRoom.getId(), callType);
        if (call != null) {
            activeCall = call;
            SoundManager.getInstance().playLoop(SoundManager.OUTGOING_CALL);
            showOutgoingCallOverlay(other, call.isVideoCall());
            // Start polling for acceptance
            pollCallStatus(call.getId());
        } else {
            showToast("Call", "Failed to initiate call.");
        }
    }

    /** Show outgoing call UI (waiting for the other to pick up). */
    private void showOutgoingCallOverlay(User other, boolean isVideo) {
        removeIncomingCallOverlay();

        VBox overlay = new VBox(16);
        overlay.setAlignment(Pos.CENTER);
        overlay.getStyleClass().add("call-overlay");
        overlay.setMaxWidth(340);
        overlay.setMaxHeight(280);
        StackPane.setAlignment(overlay, Pos.CENTER);

        StackPane avatar = createAvatarWithStatus(other, 64);

        Label nameLabel = new Label(other.getFirstName() + " " + other.getLastName());
        nameLabel.getStyleClass().add("call-overlay-name");

        Label statusLabel = new Label(isVideo ? "Video calling..." : "Calling...");
        statusLabel.getStyleClass().add("call-overlay-status");

        // Pulsing animation on status
        FadeTransition pulse = new FadeTransition(Duration.millis(800), statusLabel);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.4);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().addAll("btn-danger", "call-overlay-btn");
        cancelBtn.setOnAction(e -> {
            SoundManager.getInstance().stopLoop();
            if (activeCall != null) {
                serviceCall.endCall(activeCall.getId());
                activeCall = null;
            }
            pendingVideoCall = false;
            chatRootStack.getChildren().remove(overlay);
            pulse.stop();
        });

        overlay.getChildren().addAll(avatar, nameLabel, statusLabel, cancelBtn);
        incomingCallOverlay = overlay;
        chatRootStack.getChildren().add(overlay);
    }

    /** Poll call status to detect when accepted or rejected. */
    private void pollCallStatus(int callId) {
        Timeline poller = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (activeCall == null || activeCall.getId() != callId) return;
            Call c = serviceCall.getCall(callId);
            if (c == null) return;

            if (c.isActive()) {
                activeCall = c;
                SoundManager.getInstance().stopLoop();
                SoundManager.getInstance().play(SoundManager.CALL_CONNECTED);
                removeIncomingCallOverlay();
                startActiveCall(callId);
            } else if (c.isEnded()) {
                SoundManager.getInstance().stopLoop();
                activeCall = null;
                pendingVideoCall = false;
                removeIncomingCallOverlay();
                showToast("Call", "Call ended.");
            }
        }));
        poller.setCycleCount(60); // poll for up to 60s
        poller.play();
    }

    /** Check for incoming calls on background thread. */
    private void pollIncomingCallsBackground() {
        if (activeCall != null) return;
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) return;

        Call incoming = serviceCall.getIncomingCall(me.getId());
        if (incoming != null) {
            Platform.runLater(() -> {
                if (incomingCallOverlay == null) {
                    User caller = userCache.get(incoming.getCallerId());
                    String callerName = caller != null ? caller.getFirstName() + " " + caller.getLastName() : "Unknown";
                    SoundManager.getInstance().playLoop(SoundManager.INCOMING_CALL);
                    showIncomingCallOverlay(incoming, callerName, caller);
                }
            });
        }
    }

    /** Show incoming call overlay with Accept/Reject. */
    private void showIncomingCallOverlay(Call call, String callerName, User caller) {
        removeIncomingCallOverlay();

        VBox overlay = new VBox(16);
        overlay.setAlignment(Pos.CENTER);
        overlay.getStyleClass().add("call-overlay");
        overlay.setMaxWidth(340);
        overlay.setMaxHeight(320);
        StackPane.setAlignment(overlay, Pos.CENTER);

        Label ringIcon = new Label("\uD83D\uDD14");
        ringIcon.setStyle("-fx-font-size: 32;");

        // Ring animation
        RotateTransition ring = new RotateTransition(Duration.millis(100), ringIcon);
        ring.setByAngle(20);
        ring.setCycleCount(6);
        ring.setAutoReverse(true);
        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        SequentialTransition seq = new SequentialTransition(ring, pause);
        seq.setCycleCount(Animation.INDEFINITE);
        seq.play();

        StackPane avatar = caller != null ? createAvatarWithStatus(caller, 64) : createGroupAvatar(callerName, 64);

        Label nameLabel = new Label(callerName);
        nameLabel.getStyleClass().add("call-overlay-name");

        Label statusLabel = new Label(call.isVideoCall() ? "Incoming video call..." : "Incoming audio call...");
        statusLabel.getStyleClass().add("call-overlay-status");

        HBox buttons = new HBox(16);
        buttons.setAlignment(Pos.CENTER);

        Button acceptBtn = new Button("Accept");
        acceptBtn.getStyleClass().addAll("btn-success", "call-overlay-btn");
        acceptBtn.setOnAction(e -> {
            seq.stop();
            SoundManager.getInstance().stopLoop();
            SoundManager.getInstance().play(SoundManager.CALL_CONNECTED);
            serviceCall.acceptCall(call.getId());
            activeCall = call;
            activeCall.setStatus("active");
            // If this is a video call, auto-open the video popup for callee too
            if (call.isVideoCall()) {
                pendingVideoCall = true;
            }
            removeIncomingCallOverlay();
            startActiveCall(call.getId());
        });

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().addAll("btn-danger", "call-overlay-btn");
        rejectBtn.setOnAction(e -> {
            seq.stop();
            SoundManager.getInstance().stopLoop();
            SoundManager.getInstance().play(SoundManager.CALL_ENDED);
            serviceCall.rejectCall(call.getId());
            removeIncomingCallOverlay();
        });

        buttons.getChildren().addAll(acceptBtn, rejectBtn);
        overlay.getChildren().addAll(ringIcon, avatar, nameLabel, statusLabel, buttons);
        incomingCallOverlay = overlay;
        chatRootStack.getChildren().add(overlay);
    }

    /** Remove any call overlay from the stack. */
    private void removeIncomingCallOverlay() {
        if (incomingCallOverlay != null) {
            chatRootStack.getChildren().remove(incomingCallOverlay);
            incomingCallOverlay = null;
        }
    }

    /** Start the active call: show call bar, start audio streaming, poll status. */
    private void startActiveCall(int callId) {
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) return;

        // Show active call bar
        if (activeCallBar != null) {
            activeCallBar.setVisible(true);
            activeCallBar.setManaged(true);
        }

        // Start timer
        callStartTime = System.currentTimeMillis();
        callTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long elapsed = (System.currentTimeMillis() - callStartTime) / 1000;
            long min = elapsed / 60;
            long sec = elapsed % 60;
            String timeStr = String.format("%02d:%02d", min, sec);
            if (callTimerLabel != null) {
                callTimerLabel.setText(timeStr);
            }
            // Sync video popup timer
            if (videoCallTimerLabel != null) {
                videoCallTimerLabel.setText(timeStr);
            }
        }));
        callTimer.setCycleCount(Animation.INDEFINITE);
        callTimer.play();

        // Poll call status every 2s to detect remote hang-up instantly
        activeCallPoller = new Timeline(new KeyFrame(Duration.seconds(2), ev -> {
            if (activeCall == null) return;
            new Thread(() -> {
                Call c = serviceCall.getCall(activeCall.getId());
                if (c != null && (c.isEnded() || "rejected".equals(c.getStatus()) || "missed".equals(c.getStatus()))) {
                    Platform.runLater(() -> {
                        cleanupActiveCall();
                        showToast("Call", "Call ended.");
                    });
                }
            }, "call-status-check").start();
        }));
        activeCallPoller.setCycleCount(Animation.INDEFINITE);
        activeCallPoller.play();

        // Start audio
        audioCallService.start(callId, me.getId(), () -> Platform.runLater(this::handleCallDisconnected));

        // Connect to video relay for screen sharing (receive mode)
        screenShareService.connect(callId, me.getId(), this::handleRemoteFrame, () -> {
            System.out.println("[ScreenShare] Video relay disconnected");
        });

        // Auto-start screen sharing if this was a video call
        if (pendingVideoCall) {
            pendingVideoCall = false;
            // Open popup immediately on FX thread
            Platform.runLater(() -> {
                openVideoCallPopup();
            });
            // Start capture on a background thread (waits for WS connection)
            new Thread(() -> {
                screenShareService.startCapture();
                Platform.runLater(() -> {
                    if (btnScreenShare != null) {
                        btnScreenShare.setText("⏹");
                        if (!btnScreenShare.getStyleClass().contains("screen-sharing-active"))
                            btnScreenShare.getStyleClass().add("screen-sharing-active");
                    }
                    updateVideoShareButton(true);
                });
            }, "video-capture-start").start();
        }
    }

    /** Called when the WebSocket disconnects unexpectedly. */
    private void handleCallDisconnected() {
        cleanupActiveCall();
        showToast("Call", "Call disconnected.");
    }

    /** Toggle microphone mute. */
    @FXML
    private void handleToggleMute() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        boolean muted = audioCallService.toggleMute();
        if (btnMute != null) {
            btnMute.setText(muted ? "\uD83D\uDD07" : "\uD83C\uDF99");
            btnMute.getStyleClass().removeAll("call-muted", "call-unmuted");
            btnMute.getStyleClass().add(muted ? "call-muted" : "call-unmuted");
        }
    }

    /** Toggle screen sharing on/off during an active call. */
    @FXML
    private void handleToggleScreenShare() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        if (activeCall == null) return;

        if (screenShareService.isCapturing()) {
            screenShareService.stopCapture();
            if (btnScreenShare != null) {
                btnScreenShare.setText("\uD83D\uDDA5");
                btnScreenShare.getStyleClass().remove("screen-sharing-active");
            }
            updateVideoShareButton(false);
            showToast("Screen Share", "Screen sharing stopped.");
        } else {
            // Start capture on background thread (may need to wait for WS connection)
            new Thread(() -> {
                screenShareService.startCapture();
                Platform.runLater(() -> {
                    if (btnScreenShare != null) {
                        btnScreenShare.setText("⏹");
                        if (!btnScreenShare.getStyleClass().contains("screen-sharing-active"))
                            btnScreenShare.getStyleClass().add("screen-sharing-active");
                    }
                    updateVideoShareButton(true);
                    showToast("Screen Share", "Sharing your screen (" + screenShareService.getResolution().label + ")");
                });
            }, "screen-share-toggle").start();
        }
    }

    /** Update the video popup screen share button appearance. */
    private void updateVideoShareButton(boolean sharing) {
        if (videoScreenShareBtn == null || videoScreenShareBtn.getGraphic() == null) return;
        try {
            VBox container = (VBox) videoScreenShareBtn.getGraphic();
            Label icon = (Label) container.getChildren().get(0);
            Label label = (Label) container.getChildren().get(1);
            icon.setText(sharing ? "⏹" : "\uD83D\uDDA5");
            label.setText(sharing ? "Stop" : "Share");
            if (sharing) {
                if (!videoScreenShareBtn.getStyleClass().contains("video-popup-btn-active"))
                    videoScreenShareBtn.getStyleClass().add("video-popup-btn-active");
            } else {
                videoScreenShareBtn.getStyleClass().remove("video-popup-btn-active");
            }
        } catch (Exception ignored) {}
    }

    /** Toggle camera on/off during a video call. */
    private void handleToggleCamera() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        if (activeCall == null) return;

        if (screenShareService.getCaptureMode() == utils.ScreenShareService.CaptureMode.WEBCAM
                && screenShareService.isCapturing()) {
            // Turn camera off — stop capturing, switch back to screen mode
            screenShareService.stopCapture();
            screenShareService.setCaptureMode(utils.ScreenShareService.CaptureMode.SCREEN);
            if (localPreviewView != null) localPreviewView.setVisible(false);
            updateVideoCameraButton(false);
            updateVideoShareButton(false);
            showToast("Camera", "Camera turned off.");
        } else {
            // Turn camera on — stop any screen share, switch to webcam
            if (screenShareService.isCapturing()) {
                screenShareService.stopCapture();
            }
            screenShareService.setCaptureMode(utils.ScreenShareService.CaptureMode.WEBCAM);
            updateVideoShareButton(false);

            new Thread(() -> {
                screenShareService.startCapture();
                Platform.runLater(() -> {
                    if (localPreviewView != null) localPreviewView.setVisible(true);
                    updateVideoCameraButton(true);
                    showToast("Camera", "Camera is live.");
                });
            }, "camera-toggle").start();
        }
    }

    /** Update the video popup camera button appearance. */
    private void updateVideoCameraButton(boolean active) {
        if (videoCameraBtn == null || videoCameraBtn.getGraphic() == null) return;
        try {
            VBox container = (VBox) videoCameraBtn.getGraphic();
            Label icon = (Label) container.getChildren().get(0);
            Label label = (Label) container.getChildren().get(1);
            icon.setText(active ? "\uD83D\uDCF9" : "\uD83D\uDCF7");
            label.setText(active ? "Stop" : "Camera");
            if (active) {
                if (!videoCameraBtn.getStyleClass().contains("video-popup-btn-active"))
                    videoCameraBtn.getStyleClass().add("video-popup-btn-active");
            } else {
                videoCameraBtn.getStyleClass().remove("video-popup-btn-active");
            }
        } catch (Exception ignored) {}
    }

    /** Handle incoming screen share frame from remote participant. */
    private void handleRemoteFrame(Image frame) {
        if (videoCallStage == null || !videoCallStage.isShowing()) {
            openVideoCallPopup();
        }
        if (remoteVideoView != null) {
            remoteVideoView.setImage(frame);
        }
    }

    /**
     * Open a modern video call popup window.
     * Dark floating window with: remote video stream, caller info bar,
     * and bottom control bar (mute, screen share, end call).
     */
    private void openVideoCallPopup() {
        if (videoCallStage != null && videoCallStage.isShowing()) return;

        // Hide the in-chat call bar — the video popup has its own controls
        if (activeCallBar != null) {
            activeCallBar.setVisible(false);
            activeCallBar.setManaged(false);
        }

        // ─── Remote video display ───
        remoteVideoView = new ImageView();
        remoteVideoView.setPreserveRatio(true);
        remoteVideoView.setSmooth(true);

        // ─── Top info bar: caller name + timer ───
        String otherName = "Call";
        if (currentRoom != null && currentRoom.isPrivate()) {
            User other = getOtherUser(currentRoom);
            if (other != null) otherName = other.getFirstName() + " " + other.getLastName();
        }

        videoCallNameLabel = new Label(otherName);
        videoCallNameLabel.getStyleClass().add("video-call-name");

        videoCallTimerLabel = new Label("00:00");
        videoCallTimerLabel.getStyleClass().add("video-call-timer");

        Label liveIndicator = new Label("\u25cf");
        liveIndicator.getStyleClass().add("video-live-dot");

        Label liveText = new Label("LIVE");
        liveText.getStyleClass().add("video-live-text");

        HBox liveBox = new HBox(4, liveIndicator, liveText);
        liveBox.setAlignment(Pos.CENTER);
        liveBox.getStyleClass().add("video-live-badge");

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("video-top-bar");
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        topBar.getChildren().addAll(videoCallNameLabel, videoCallTimerLabel, topSpacer, liveBox);

        // ─── Center: video stream area ───
        StackPane videoArea = new StackPane(remoteVideoView);
        videoArea.getStyleClass().add("video-stream-area");
        VBox.setVgrow(videoArea, Priority.ALWAYS);

        // Placeholder when no video yet
        Label waitingIcon = new Label("\uD83D\uDCF9");
        waitingIcon.setStyle("-fx-font-size: 36; -fx-opacity: 0.3;");
        Label waitingLabel = new Label("Waiting for screen share...");
        waitingLabel.getStyleClass().add("video-waiting-label");
        VBox waitingBox = new VBox(8, waitingIcon, waitingLabel);
        waitingBox.setAlignment(Pos.CENTER);
        waitingBox.setMouseTransparent(true);
        videoArea.getChildren().add(waitingBox);

        // Hide placeholder once we get a frame
        remoteVideoView.imageProperty().addListener((obs, old, img) -> {
            if (img != null) waitingBox.setVisible(false);
        });

        // Bind video to fill the area
        remoteVideoView.fitWidthProperty().bind(videoArea.widthProperty().subtract(8));
        remoteVideoView.fitHeightProperty().bind(videoArea.heightProperty().subtract(8));

        // ─── Local camera preview (PiP) ───
        localPreviewView = new ImageView();
        localPreviewView.setPreserveRatio(true);
        localPreviewView.setSmooth(true);
        localPreviewView.setFitWidth(180);
        localPreviewView.setFitHeight(135);
        localPreviewView.setVisible(false);
        localPreviewView.getStyleClass().add("video-local-preview");

        StackPane localPreviewContainer = new StackPane(localPreviewView);
        localPreviewContainer.getStyleClass().add("video-local-preview-container");
        localPreviewContainer.setMaxWidth(188);
        localPreviewContainer.setMaxHeight(143);
        localPreviewContainer.visibleProperty().bind(localPreviewView.visibleProperty());
        localPreviewContainer.managedProperty().bind(localPreviewView.visibleProperty());
        StackPane.setAlignment(localPreviewContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(localPreviewContainer, new javafx.geometry.Insets(0, 12, 12, 0));
        videoArea.getChildren().add(localPreviewContainer);

        // Wire up local preview callback
        screenShareService.setOnLocalFrame(frame -> {
            if (localPreviewView != null) {
                localPreviewView.setImage(frame);
            }
        });

        // ─── Bottom control bar ───
        // Mute button with label
        Label muteIcon = new Label("\uD83C\uDF99");
        muteIcon.getStyleClass().add("video-btn-icon");
        Label muteLabel = new Label("Mute");
        muteLabel.getStyleClass().add("video-btn-label");
        VBox muteContainer = new VBox(4, muteIcon, muteLabel);
        muteContainer.setAlignment(Pos.CENTER);

        videoMuteBtn = new Button();
        videoMuteBtn.setGraphic(muteContainer);
        videoMuteBtn.getStyleClass().addAll("video-popup-btn", "video-popup-btn-mute");
        videoMuteBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            boolean muted = audioCallService.toggleMute();
            muteIcon.setText(muted ? "\uD83D\uDD07" : "\uD83C\uDF99");
            muteLabel.setText(muted ? "Unmute" : "Mute");
            videoMuteBtn.getStyleClass().removeAll("video-popup-btn-active");
            if (muted) videoMuteBtn.getStyleClass().add("video-popup-btn-active");
            if (btnMute != null) {
                btnMute.setText(muted ? "\uD83D\uDD07" : "\uD83C\uDF99");
                btnMute.getStyleClass().removeAll("call-muted", "call-unmuted");
                btnMute.getStyleClass().add(muted ? "call-muted" : "call-unmuted");
            }
        });

        // Screen share button with label
        Label shareIcon = new Label("\uD83D\uDDA5");
        shareIcon.getStyleClass().add("video-btn-icon");
        Label shareLabel = new Label("Share");
        shareLabel.getStyleClass().add("video-btn-label");
        VBox shareContainer = new VBox(4, shareIcon, shareLabel);
        shareContainer.setAlignment(Pos.CENTER);

        videoScreenShareBtn = new Button();
        videoScreenShareBtn.setGraphic(shareContainer);
        videoScreenShareBtn.getStyleClass().addAll("video-popup-btn", "video-popup-btn-share");
        videoScreenShareBtn.setOnAction(e -> {
            // If camera is active, switch back to screen mode first
            if (screenShareService.getCaptureMode() == utils.ScreenShareService.CaptureMode.WEBCAM) {
                screenShareService.stopCapture();
                screenShareService.setCaptureMode(utils.ScreenShareService.CaptureMode.SCREEN);
                if (localPreviewView != null) localPreviewView.setVisible(false);
                updateVideoCameraButton(false);
            }
            handleToggleScreenShare();
        });

        // Camera button with label
        Label camIcon = new Label("📷");
        camIcon.getStyleClass().add("video-btn-icon");
        Label camLabel = new Label("Camera");
        camLabel.getStyleClass().add("video-btn-label");
        VBox camContainer = new VBox(4, camIcon, camLabel);
        camContainer.setAlignment(Pos.CENTER);

        boolean hasCam = utils.ScreenShareService.isWebcamAvailable();
        videoCameraBtn = new Button();
        videoCameraBtn.setGraphic(camContainer);
        videoCameraBtn.getStyleClass().addAll("video-popup-btn", "video-popup-btn-camera");
        videoCameraBtn.setDisable(!hasCam);
        if (!hasCam) {
            camLabel.setText("No Cam");
            videoCameraBtn.setOpacity(0.4);
        }
        videoCameraBtn.setOnAction(e -> handleToggleCamera());

        // End call button with hangup icon (rotated phone)
        Label endIcon = new Label("\u260E");
        endIcon.getStyleClass().add("video-btn-icon");
        endIcon.setStyle("-fx-rotate: 135;");
        Label endLabel = new Label("End");
        endLabel.getStyleClass().add("video-btn-label");
        VBox endContainer = new VBox(4, endIcon, endLabel);
        endContainer.setAlignment(Pos.CENTER);

        Button videoEndBtn = new Button();
        videoEndBtn.setGraphic(endContainer);
        videoEndBtn.getStyleClass().addAll("video-popup-btn", "video-popup-end-btn");
        videoEndBtn.setOnAction(e -> handleEndCall());

        HBox controlBar = new HBox(24);
        controlBar.setAlignment(Pos.CENTER);
        controlBar.getStyleClass().add("video-control-bar");

        // ── Volume slider ──
        Label volIcon = new Label("\uD83D\uDD0A");
        boolean dkVol = SessionManager.getInstance().isDarkTheme();
        volIcon.setStyle("-fx-font-size: 16; -fx-text-fill: " + (dkVol ? "#90DDF0" : "#613039") + ";");
        javafx.scene.control.Slider volSlider = new javafx.scene.control.Slider(0, 3.0,
                utils.AudioDeviceManager.getInstance().getSpeakerVolume());
        volSlider.setMaxWidth(120);
        volSlider.setPrefWidth(120);
        volSlider.getStyleClass().add("call-volume-slider");
        volSlider.valueProperty().addListener((obs, oldV, newV) -> {
            utils.AudioDeviceManager.getInstance().setSpeakerVolume(newV.doubleValue());
            if (newV.doubleValue() < 0.01) volIcon.setText("\uD83D\uDD07");
            else if (newV.doubleValue() < 1.0) volIcon.setText("\uD83D\uDD09");
            else volIcon.setText("\uD83D\uDD0A");
        });
        HBox volBox = new HBox(6, volIcon, volSlider);
        volBox.setAlignment(Pos.CENTER);

        controlBar.getChildren().addAll(volBox, videoMuteBtn, videoScreenShareBtn, videoCameraBtn, videoEndBtn);

        // ─── Assemble layout ───
        VBox root = new VBox();
        root.getStyleClass().add("video-popup-root");
        root.getChildren().addAll(topBar, videoArea, controlBar);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 800, 520);
        scene.setFill(javafx.scene.paint.Color.BLACK);

        // Load stylesheet
        try {
            String css = getClass().getResource("/css/style.css").toExternalForm();
            scene.getStylesheets().add(css);
            if (!SessionManager.getInstance().isDarkTheme()) {
                String lightCss = getClass().getResource("/css/light-theme.css").toExternalForm();
                scene.getStylesheets().add(lightCss);
            }
        } catch (Exception ignored) {}

        videoCallStage = new javafx.stage.Stage();
        videoCallStage.setTitle("Video Call — " + otherName);
        videoCallStage.initStyle(javafx.stage.StageStyle.DECORATED);
        videoCallStage.setScene(scene);
        videoCallStage.setMinWidth(480);
        videoCallStage.setMinHeight(360);
        videoCallStage.setOnCloseRequest(e -> {
            // Don't end the call, just close the video popup
            screenShareService.setOnLocalFrame(null);
            videoCallStage = null;
            remoteVideoView = null;
            localPreviewView = null;
            videoCameraBtn = null;
            // Show the in-chat call bar again since popup is closing
            if (activeCallBar != null && activeCall != null) {
                activeCallBar.setVisible(true);
                activeCallBar.setManaged(true);
            }
        });
        videoCallStage.show();
    }

    /** Close the video call popup. */
    private void closeVideoCallPopup() {
        if (videoCallStage != null) {
            screenShareService.setOnLocalFrame(null);
            videoCallStage.close();
            videoCallStage = null;
            remoteVideoView = null;
            localPreviewView = null;
            videoCameraBtn = null;
        }
    }

    /** End the active call (user clicked End — notify server). */
    @FXML
    private void handleEndCall() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        if (activeCall != null) {
            serviceCall.endCall(activeCall.getId());
        }
        cleanupActiveCall();
    }

    /** Clean up call state locally (no server notification). */
    private void cleanupActiveCall() {
        SoundManager.getInstance().stopLoop();
        SoundManager.getInstance().play(SoundManager.CALL_ENDED);
        audioCallService.stop();
        screenShareService.disconnect();
        closeVideoCallPopup();
        activeCall = null;
        pendingVideoCall = false;

        if (activeCallPoller != null) {
            activeCallPoller.stop();
            activeCallPoller = null;
        }

        if (callTimer != null) {
            callTimer.stop();
            callTimer = null;
        }

        if (activeCallBar != null) {
            activeCallBar.setVisible(false);
            activeCallBar.setManaged(false);
        }
        if (callTimerLabel != null) callTimerLabel.setText("00:00");
        if (btnScreenShare != null) {
            btnScreenShare.setText("\uD83D\uDDA5");
            btnScreenShare.getStyleClass().remove("screen-sharing-active");
        }
    }

    // ═══════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════

    public void stop() {
        if (activeCall != null) {
            serviceCall.endCall(activeCall.getId());
        }
        cleanupActiveCall();
        closeVideoCallPopup();
        if (scheduler != null) scheduler.shutdown();
    }
}
