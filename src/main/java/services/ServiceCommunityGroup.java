package services;

import com.google.gson.*;
import entities.CommunityGroup;
import utils.ApiClient;
import utils.AppConfig;
import utils.InMemoryCache;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

/**
 * Service for managing community groups.
 * Supports API and JDBC dual mode. Auto-creates table if needed.
 */
public class ServiceCommunityGroup {

    private boolean useApi() { return AppConfig.isApiMode(); }

    // ============ Table Auto-Create ============

    public void ensureTable() {
        // Create table via JDBC if a connection is available (skips silently in pure API mode)
        Connection conn = MyDatabase.getInstance().getConnection();
        if (conn == null) return;
        try (conn; Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS community_groups (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(200) NOT NULL,
                    description TEXT,
                    image_base64 LONGTEXT,
                    creator_id INT NOT NULL,
                    privacy VARCHAR(20) DEFAULT 'PUBLIC',
                    member_count INT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_creator (creator_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        } catch (SQLException e) {
            System.err.println("ServiceCommunityGroup.ensureTable: " + e.getMessage());
        }
    }

    // ============ JSON helpers ============

    private CommunityGroup jsonToGroup(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            try {
                String raw = obj.get("created_at").getAsString().replace("T", " ");
                if (raw.contains(".")) raw = raw.substring(0, raw.indexOf("."));
                createdAt = Timestamp.valueOf(raw);
            } catch (Exception ignored) {}
        }
        return new CommunityGroup(
                obj.get("id").getAsInt(),
                obj.has("name") ? obj.get("name").getAsString() : "",
                obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString() : "",
                obj.has("image_base64") && !obj.get("image_base64").isJsonNull() ? obj.get("image_base64").getAsString() : null,
                obj.has("creator_id") ? obj.get("creator_id").getAsInt() : 0,
                obj.has("privacy") ? obj.get("privacy").getAsString() : "PUBLIC",
                obj.has("member_count") ? obj.get("member_count").getAsInt() : 0,
                createdAt
        );
    }

    // ============ CRUD ============

    public int create(CommunityGroup group) throws SQLException {
        if (useApi()) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", group.getName());
            body.put("description", group.getDescription());
            body.put("creator_id", group.getCreatorId());
            body.put("privacy", group.getPrivacy());
            if (group.getImageBase64() != null) body.put("image_base64", group.getImageBase64());
            try {
                JsonElement resp = ApiClient.post("/community_groups", body);
                if (resp != null && resp.isJsonObject() && resp.getAsJsonObject().has("id"))
                    return resp.getAsJsonObject().get("id").getAsInt();
            } catch (Exception ignored) {}
            InMemoryCache.evictByPrefix("cgroups:");
            return -1;
        }
        String sql = "INSERT INTO community_groups (name, description, image_base64, creator_id, privacy, member_count) VALUES (?, ?, ?, ?, ?, 1)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, group.getName());
            ps.setString(2, group.getDescription());
            ps.setString(3, group.getImageBase64());
            ps.setInt(4, group.getCreatorId());
            ps.setString(5, group.getPrivacy());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int id = keys.next() ? keys.getInt(1) : -1;
            group.setId(id);
            InMemoryCache.evictByPrefix("cgroups:");
            return id;
        }
    }

    public void update(CommunityGroup group) throws SQLException {
        if (useApi()) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", group.getName());
            body.put("description", group.getDescription());
            body.put("privacy", group.getPrivacy());
            if (group.getImageBase64() != null) body.put("image_base64", group.getImageBase64());
            ApiClient.put("/community_groups/" + group.getId(), body);
            InMemoryCache.evictByPrefix("cgroups:");
            return;
        }
        String sql = "UPDATE community_groups SET name = ?, description = ?, image_base64 = ?, privacy = ? WHERE id = ?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group.getName());
            ps.setString(2, group.getDescription());
            ps.setString(3, group.getImageBase64());
            ps.setString(4, group.getPrivacy());
            ps.setInt(5, group.getId());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("cgroups:");
    }

    public void delete(int groupId) throws SQLException {
        if (useApi()) {
            ApiClient.delete("/community_groups/" + groupId);
            InMemoryCache.evictByPrefix("cgroups:");
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM community_groups WHERE id = ?")) {
            ps.setInt(1, groupId);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("cgroups:");
    }

    /** Get all groups. Cached 60s. */
    @SuppressWarnings("unchecked")
    public List<CommunityGroup> getAll() throws SQLException {
        String key = "cgroups:all";
        return InMemoryCache.getOrLoadChecked(key, 60, () -> {
            if (useApi()) {
                List<CommunityGroup> groups = new ArrayList<>();
                JsonElement el = ApiClient.get("/community_groups");
                if (el != null && el.isJsonArray()) {
                    for (JsonElement item : el.getAsJsonArray())
                        groups.add(jsonToGroup(item.getAsJsonObject()));
                }
                return groups;
            }
            return getAllFromDb();
        });
    }

    private List<CommunityGroup> getAllFromDb() throws SQLException {
        List<CommunityGroup> groups = new ArrayList<>();
        String sql = "SELECT g.*, (SELECT COUNT(*) FROM group_members WHERE group_id = g.id) AS member_count FROM community_groups g ORDER BY g.created_at DESC";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                groups.add(new CommunityGroup(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("image_base64"),
                        rs.getInt("creator_id"),
                        rs.getString("privacy"),
                        rs.getInt("member_count"),
                        rs.getTimestamp("created_at")
                ));
            }
        }
        return groups;
    }

    /** Update member count (call after join/leave). */
    public void refreshMemberCount(int groupId) {
        if (useApi()) return;
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE community_groups SET member_count = (SELECT COUNT(*) FROM group_members WHERE group_id = ?) WHERE id = ?")) {
            ps.setInt(1, groupId);
            ps.setInt(2, groupId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        InMemoryCache.evictByPrefix("cgroups:");
    }
}
