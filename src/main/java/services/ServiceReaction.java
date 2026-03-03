package services;

import com.google.gson.*;
import entities.Reaction;
import utils.ApiClient;
import utils.AppConfig;
import utils.InMemoryCache;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceReaction implements IService<Reaction> {

    private final boolean useApi;

    public ServiceReaction() {
        useApi = AppConfig.isApiMode();
    }

    // ============ JSON ============

    private Reaction jsonToReaction(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        return new Reaction(
                obj.get("id").getAsInt(),
                obj.get("post_id").getAsInt(),
                obj.get("user_id").getAsInt(),
                obj.get("type").getAsString(),
                createdAt
        );
    }

    private List<Reaction> jsonArrayToReactions(JsonElement el) {
        List<Reaction> reactions = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                reactions.add(jsonToReaction(item.getAsJsonObject()));
            }
        }
        return reactions;
    }

    // ============ CRUD ============

    @Override
    public void ajouter(Reaction reaction) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("post_id", reaction.getPostId());
            body.put("user_id", reaction.getUserId());
            body.put("type", reaction.getType());
            ApiClient.post("/reactions", body);
            return;
        }
        String sql = "INSERT INTO reactions (post_id, user_id, type) VALUES (?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reaction.getPostId());
            ps.setInt(2, reaction.getUserId());
            ps.setString(3, reaction.getType());
            ps.executeUpdate();
        }
    }

    @Override
    public void modifier(Reaction reaction) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("type", reaction.getType());
            ApiClient.put("/reactions/" + reaction.getId(), body);
            return;
        }
        String sql = "UPDATE reactions SET type = ? WHERE id = ?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reaction.getType());
            ps.setInt(2, reaction.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/reactions/" + id);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM reactions WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public List<Reaction> recuperer() throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/reactions");
            return jsonArrayToReactions(el);
        }
        List<Reaction> reactions = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM reactions ORDER BY created_at DESC")) {
            while (rs.next()) {
                reactions.add(new Reaction(
                        rs.getInt("id"),
                        rs.getInt("post_id"),
                        rs.getInt("user_id"),
                        rs.getString("type"),
                        rs.getTimestamp("created_at")
                ));
            }
        }
        return reactions;
    }

    /** Get reactions for a specific post. Cached 30s. */
    public List<Reaction> getByPostId(int postId) throws SQLException {
        String key = "reactions:post:" + postId;
        return InMemoryCache.getOrLoadChecked(key, 30, () -> {
            if (useApi) {
                JsonElement el = ApiClient.get("/reactions/post/" + postId);
                return jsonArrayToReactions(el);
            }
            List<Reaction> reactions = new ArrayList<>();
            try (Connection conn = MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM reactions WHERE post_id = ? ORDER BY created_at ASC")) {
                ps.setInt(1, postId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        reactions.add(new Reaction(
                                rs.getInt("id"),
                                rs.getInt("post_id"),
                                rs.getInt("user_id"),
                                rs.getString("type"),
                                rs.getTimestamp("created_at")
                        ));
                    }
                }
            }
            return reactions;
        });
    }

    /** Toggle reaction: if user already reacted with same type, remove it; otherwise add it */
    public boolean toggleReaction(int postId, int userId, String type) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("post_id", postId);
            body.put("user_id", userId);
            body.put("type", type);
            JsonElement el = ApiClient.post("/reactions/toggle", body);
            if (el != null && el.isJsonObject()) {
                return el.getAsJsonObject().has("action") &&
                       "added".equals(el.getAsJsonObject().get("action").getAsString());
            }
            return false;
        }
        // Check if reaction already exists
        try (Connection conn = MyDatabase.getInstance().getConnection()) {
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM reactions WHERE post_id = ? AND user_id = ? AND type = ?")) {
                check.setInt(1, postId);
                check.setInt(2, userId);
                check.setString(3, type);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        // Remove existing reaction
                        int reactionId = rs.getInt("id");
                        try (PreparedStatement del = conn.prepareStatement("DELETE FROM reactions WHERE id = ?")) {
                            del.setInt(1, reactionId);
                            del.executeUpdate();
                        }
                        return false; // removed
                    }
                }
            }
        }
        // Add new reaction
        ajouter(new Reaction(postId, userId, type));
        return true; // added
    }

    /** Check if a user has reacted to a post with a specific type */
    public boolean hasUserReacted(int postId, int userId, String type) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/reactions/check/" + postId + "/" + userId + "/" + type);
            return el != null && el.isJsonObject() && el.getAsJsonObject().has("exists") &&
                   el.getAsJsonObject().get("exists").getAsBoolean();
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM reactions WHERE post_id = ? AND user_id = ? AND type = ?")) {
            ps.setInt(1, postId);
            ps.setInt(2, userId);
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }
}
