package controllers;

import entities.ChatRoom;
import entities.Message;
import entities.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import services.ServiceChatRoom;
import services.ServiceMessage;
import services.ServiceUser;
import utils.SessionManager;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatController {

    @FXML
    private ListView<ChatRoom> roomsList;
    @FXML
    private TextField roomNameField;
    @FXML
    private Label currentRoomLabel;
    @FXML
    private ScrollPane messagesScroll;
    @FXML
    private VBox messagesContainer;
    @FXML
    private TextField messageField;

    private ServiceChatRoom serviceChat = new ServiceChatRoom();
    private ServiceMessage serviceMessage = new ServiceMessage();
    private ServiceUser serviceUser = new ServiceUser();

    private ChatRoom currentRoom;
    private ScheduledExecutorService scheduler;

    @FXML
    public void initialize() {
        loadRooms();

        roomsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectRoom(newVal);
            }
        });

        // Auto-refresh messages every 3 seconds
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(this::refreshMessages);
        }, 3, 3, TimeUnit.SECONDS);
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
        currentRoomLabel.setText("# " + room.getName());
        refreshMessages();
    }

    @FXML
    void refreshMessages() {
        if (currentRoom == null)
            return;

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null)
            return;

        try {
            List<Message> messages = serviceMessage.getByRoom(currentRoom.getId());
            messagesContainer.getChildren().clear();

            int currentUserId = currentUser.getId();

            for (Message msg : messages) {
                boolean isMe = msg.getSenderId() == currentUserId;
                addMessageBubble(msg, isMe);
            }

            // Scroll to bottom
            messagesScroll.setVvalue(1.0);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addMessageBubble(Message msg, boolean isMe) {
        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        Label bubble = new Label(msg.getContent());
        bubble.getStyleClass().add(isMe ? "msg-bubble-me" : "msg-bubble-other");
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);

        row.getChildren().add(bubble);
        messagesContainer.getChildren().add(row);
    }

    @FXML
    private void handleSendMessage() {
        if (currentRoom == null)
            return;

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null)
            return;

        String content = messageField.getText().trim();
        if (content.isEmpty())
            return;

        try {
            int senderId = currentUser.getId();
            Message msg = new Message(senderId, currentRoom.getId(), content);
            serviceMessage.ajouter(msg);

            messageField.clear();
            refreshMessages();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (scheduler != null)
            scheduler.shutdown();
    }
}
