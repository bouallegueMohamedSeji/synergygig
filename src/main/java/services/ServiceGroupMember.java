package services;

import com.google.gson.*;
import entities.GroupMember;
import utils.ApiClient;
import utils.AppConfig;
import utils.InMemoryCache;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

/**
 * Service for managing group membership.
 * Supports API and JDBC dual mode. Auto-creates table if needed.
 */
public class ServiceGroupMember {

    private boolean useApi() { return AppConfig.isApiMode(); }

    // ============ Table Auto-Create ============

    public void ensureTable() {
        // Create table via JDBC if a connection is available (skips silently in pure API mode)
        Connection conn = MyDatabase.getInstance().getConnection();
        if (conn == null) return;
        try (conn; Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS group_members (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    group_id INT NOT NULL,
                    user_id INT NOT NULL,
                    role VARCHAR(20) DEFAULT 'MEMBER',
                    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_group_user (group_id, user_id),
                    INDEX idx_group (group_id),
                    INDEX idx_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        } catch (SQLException e) {
            System.err.println("ServiceGroupMember.ensureTable: " + e.getMessage());
        }
    }

    // ============ Core Operations ============

    /** Join a group. Uses INSERT IGNORE to be idempotent. */
    public void join(int groupId, int userId, String role) throws SQLException {
        if (useApi()) {
            Map<String, Object> body = new HashMap<>();
            body.put("group_id", groupId);
            body.put("user_id", userId);
            body.put("role", role);
            try { ApiClient.post("/group_members", body); } catch (Exception ignored) {}
            InMemoryCache.evictByPrefix("gmembers:");
            return;
        }
        String sql = "INSERT IGNORE INTO group_members (group_id, user_id, role) VALUES (?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.setString(3, role);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("gmembers:");
    }

    /** Leave a group. */
    public void leave(int groupId, int userId) throws SQLException {
        if (useApi()) {
            try { ApiClient.delete("/group_members?group_id=" + groupId + "&user_id=" + userId); }
            catch (Exception ignored) {}
            InMemoryCache.evictByPrefix("gmembers:");
            return;
        }
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("gmembers:");
    }

    /** Check if user is a member. */
    public boolean isMember(int groupId, int userId) {
        Set<Integer> groupIds = getGroupIdsForUser(userId);
        return groupIds.contains(groupId);
    }

    /** Get all group IDs for a user. Cached 60s. */
    public Set<Integer> getGroupIdsForUser(int userId) {
        String key = "gmembers:user:" + userId;
        return InMemoryCache.getOrLoad(key, 60, () -> {
            Set<Integer> ids = new HashSet<>();
            if (useApi()) {
                try {
                    JsonElement el = ApiClient.get("/group_members?user_id=" + userId);
                    if (el != null && el.isJsonArray()) {
                        for (JsonElement item : el.getAsJsonArray())
                            ids.add(item.getAsJsonObject().get("group_id").getAsInt());
                    }
                } catch (Exception ignored) {}
            } else {
                try (Connection conn = MyDatabase.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT group_id FROM group_members WHERE user_id = ?")) {
                    ps.setInt(1, userId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) ids.add(rs.getInt(1));
                } catch (SQLException ignored) {}
            }
            return ids;
        });
    }

    /** Get all members for a group. Cached 60s. */
    public List<GroupMember> getMembers(int groupId) {
        String key = "gmembers:group:" + groupId;
        return InMemoryCache.getOrLoad(key, 60, () -> {
            List<GroupMember> members = new ArrayList<>();
            if (useApi()) {
                try {
                    JsonElement el = ApiClient.get("/group_members?group_id=" + groupId);
                    if (el != null && el.isJsonArray()) {
                        for (JsonElement item : el.getAsJsonArray()) {
                            JsonObject obj = item.getAsJsonObject();
                            Timestamp joinedAt = null;
                            if (obj.has("joined_at") && !obj.get("joined_at").isJsonNull()) {
                                try {
                                    String raw = obj.get("joined_at").getAsString().replace("T", " ");
                                    if (raw.contains(".")) raw = raw.substring(0, raw.indexOf("."));
                                    joinedAt = Timestamp.valueOf(raw);
                                } catch (Exception ignored) {}
                            }
                            members.add(new GroupMember(
                                    obj.get("id").getAsInt(),
                                    obj.get("group_id").getAsInt(),
                                    obj.get("user_id").getAsInt(),
                                    obj.has("role") ? obj.get("role").getAsString() : "MEMBER",
                                    joinedAt
                            ));
                        }
                    }
                } catch (Exception ignored) {}
            } else {
                try (Connection conn = MyDatabase.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT * FROM group_members WHERE group_id = ? ORDER BY joined_at")) {
                    ps.setInt(1, groupId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        members.add(new GroupMember(
                                rs.getInt("id"),
                                rs.getInt("group_id"),
                                rs.getInt("user_id"),
                                rs.getString("role"),
                                rs.getTimestamp("joined_at")
                        ));
                    }
                } catch (SQLException ignored) {}
            }
            return members;
        });
    }
}
