package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import utils.ApiClient;
import utils.AppConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing notifications. Supports API and JDBC modes.
 */
public class ServiceNotification {

    /** Simple notification data holder. */
    public static class Notification {
        public int id;
        public int userId;
        public String type;
        public String title;
        public String body;
        public Integer referenceId;
        public String referenceType;
        public boolean isRead;
        public String createdAt;

        @Override
        public String toString() {
            return title;
        }
    }

    private boolean useApi() {
        return AppConfig.isApiMode();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  GET NOTIFICATIONS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    public List<Notification> getNotifications(int userId) {
        if (useApi()) return getNotificationsApi(userId);
        return getNotificationsJdbc(userId);
    }
    
    private List<Notification> getNotificationsApi(int userId) {
        List<Notification> list = new ArrayList<>();
        try {
            JsonElement resp = ApiClient.get("/notifications/" + userId);
            if (resp != null && resp.isJsonArray()) {
                for (JsonElement el : resp.getAsJsonArray()) {
                    list.add(jsonToNotification(el.getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get notifications: " + e.getMessage());
        }
        return list;
    }

    private List<Notification> getNotificationsJdbc(int userId) {
        List<Notification> list = new ArrayList<>();
        try (Connection conn = utils.MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rowToNotification(rs));
        } catch (Exception e) {
            System.err.println("Failed to get notifications: " + e.getMessage());
        }
        return list;
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  UNREAD COUNT
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    public int getUnreadCount(int userId) {
        if (useApi()) return getUnreadCountApi(userId);
        return getUnreadCountJdbc(userId);
    }

    private int getUnreadCountApi(int userId) {
        try {
            JsonElement resp = ApiClient.get("/notifications/" + userId + "/count");
            if (resp != null && resp.isJsonObject()) {
                return resp.getAsJsonObject().get("unread_count").getAsInt();
            }
        } catch (Exception e) {
            System.err.println("Unread count error: " + e.getMessage());
        }
        return 0;
    }

    private int getUnreadCountJdbc(int userId) {
        try (Connection conn = utils.MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  MARK READ
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    public void markRead(int notifId) {
        if (useApi()) {
            try { ApiClient.put("/notifications/" + notifId + "/read", Map.of()); } catch (Exception e) { e.printStackTrace(); }
        } else {
            try (Connection conn = utils.MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE notifications SET is_read = 1 WHERE id = ?")) {
                ps.setInt(1, notifId);
                ps.executeUpdate();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void markAllRead(int userId) {
        if (useApi()) {
            try { ApiClient.put("/notifications/" + userId + "/read-all", Map.of()); } catch (Exception e) { e.printStackTrace(); }
        } else {
            try (Connection conn = utils.MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0")) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  CREATE NOTIFICATION
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    public void create(int userId, String type, String title, String body, Integer refId, String refType) {
        if (useApi()) {
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("user_id", userId);
                data.put("type", type);
                data.put("title", title);
                data.put("body", body);
                data.put("reference_id", refId);
                data.put("reference_type", refType);
                ApiClient.post("/notifications", data);
            } catch (Exception e) { e.printStackTrace(); }
        } else {
            try (Connection conn = utils.MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO notifications (user_id, type, title, body, reference_id, reference_type) VALUES (?,?,?,?,?,?)")) {
                ps.setInt(1, userId);
                ps.setString(2, type);
                ps.setString(3, title);
                ps.setString(4, body);
                if (refId != null) ps.setInt(5, refId); else ps.setNull(5, Types.INTEGER);
                ps.setString(6, refType);
                ps.executeUpdate();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  NOTIFY HELPERS (convenience methods)
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    public void notifyTaskAssigned(int userId, String taskTitle, String projectName, int taskId) {
        create(userId, "TASK_ASSIGNED",
                "New Task: " + taskTitle,
                "You've been assigned to \"" + taskTitle + "\" in \"" + projectName + "\".",
                taskId, "TASK");
    }

    public void notifyTaskSubmitted(int managerId, String employeeName, String taskTitle, String projectName, int taskId) {
        create(managerId, "TASK_SUBMITTED",
                "Task Submitted: " + taskTitle,
                employeeName + " completed \"" + taskTitle + "\" in \"" + projectName + "\". Review it now.",
                taskId, "TASK");
    }

    public void notifyTaskReviewed(int userId, String taskTitle, String reviewStatus, int rating, int taskId) {
        create(userId, "TASK_REVIEW",
                "Task Reviewed: " + reviewStatus,
                "Your task \"" + taskTitle + "\" was reviewed. Rating: " + rating + "/5.",
                taskId, "TASK");
    }

    public void notifyMessage(int userId, String senderName, String preview, int roomId) {
        create(userId, "MESSAGE",
                "Message from " + senderName,
                preview,
                roomId, "CHAT");
    }

    public void notifyCall(int userId, String callerName, String callType, boolean missed, int callId) {
        create(userId, missed ? "MISSED_CALL" : (callType.equals("video") ? "VIDEO_CALL" : "VOICE_CALL"),
                (missed ? "Missed " : "") + (callType.equals("video") ? "Video" : "Voice") + " Call",
                (missed ? "Missed" : "Incoming") + " " + callType + " call from " + callerName + ".",
                callId, "CALL");
    }

    public void notifyInterview(int userId, String action, String body, int interviewId) {
        create(userId, "INTERVIEW",
                "Interview " + action,
                body,
                interviewId, "INTERVIEW");
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    //  PARSERS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private Notification jsonToNotification(JsonObject obj) {
        Notification n = new Notification();
        n.id = obj.has("id") ? obj.get("id").getAsInt() : 0;
        n.userId = obj.has("user_id") ? obj.get("user_id").getAsInt() : 0;
        n.type = obj.has("type") ? obj.get("type").getAsString() : "GENERAL";
        n.title = obj.has("title") ? obj.get("title").getAsString() : "";
        n.body = obj.has("body") && !obj.get("body").isJsonNull() ? obj.get("body").getAsString() : "";
        n.referenceId = obj.has("reference_id") && !obj.get("reference_id").isJsonNull() ? obj.get("reference_id").getAsInt() : null;
        n.referenceType = obj.has("reference_type") && !obj.get("reference_type").isJsonNull() ? obj.get("reference_type").getAsString() : null;
        n.isRead = obj.has("is_read") && obj.get("is_read").getAsInt() == 1;
        n.createdAt = obj.has("created_at") && !obj.get("created_at").isJsonNull() ? obj.get("created_at").getAsString() : "";
        return n;
    }

    private Notification rowToNotification(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.id = rs.getInt("id");
        n.userId = rs.getInt("user_id");
        n.type = rs.getString("type");
        n.title = rs.getString("title");
        n.body = rs.getString("body");
        n.referenceId = rs.getObject("reference_id") != null ? rs.getInt("reference_id") : null;
        n.referenceType = rs.getString("reference_type");
        n.isRead = rs.getBoolean("is_read");
        Timestamp ts = rs.getTimestamp("created_at");
        n.createdAt = ts != null ? ts.toString() : "";
        return n;
    }
}
