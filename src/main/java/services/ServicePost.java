package services;

import com.google.gson.*;
import entities.Post;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;
import utils.InMemoryCache;
import java.sql.*;
import java.util.*;

public class ServicePost implements IService<Post> {

    private final boolean useApi;

    private static final String CACHE_KEY = "posts:all";
    private static final int CACHE_TTL = 30;

    public ServicePost() {
        useApi = AppConfig.isApiMode();
    }

    // ============ JSON helpers ============

    private Post jsonToPost(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            // Parse as UTC — server stores timestamps in UTC
            String raw = obj.get("created_at").getAsString().replace("T", " ");
            if (raw.contains(".")) raw = raw.substring(0, raw.indexOf(".")); // trim fractional seconds
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            createdAt = Timestamp.from(ldt.atZone(java.time.ZoneOffset.UTC).toInstant());
        }
        String imageBase64 = null;
        if (obj.has("image_base64") && !obj.get("image_base64").isJsonNull()) {
            imageBase64 = obj.get("image_base64").getAsString();
        }
        Post p = new Post(
                obj.get("id").getAsInt(),
                obj.get("author_id").getAsInt(),
                obj.get("content").getAsString(),
                imageBase64,
                obj.has("likes_count") ? obj.get("likes_count").getAsInt() : 0,
                obj.has("comments_count") ? obj.get("comments_count").getAsInt() : 0,
                createdAt
        );
        if (obj.has("shares_count") && !obj.get("shares_count").isJsonNull())
            p.setSharesCount(obj.get("shares_count").getAsInt());
        if (obj.has("visibility") && !obj.get("visibility").isJsonNull())
            p.setVisibility(obj.get("visibility").getAsString());
        if (obj.has("group_id") && !obj.get("group_id").isJsonNull())
            p.setGroupId(obj.get("group_id").getAsInt());
        return p;
    }

    private List<Post> jsonArrayToPosts(JsonElement el) {
        List<Post> posts = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                posts.add(jsonToPost(item.getAsJsonObject()));
            }
        }
        return posts;
    }

    // ============ CRUD ============

    @Override
    public void ajouter(Post post) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("author_id", post.getAuthorId());
            body.put("content", post.getContent());
            if (post.getImageBase64() != null) body.put("image_base64", post.getImageBase64());
            body.put("visibility", post.getVisibility() != null ? post.getVisibility() : "PUBLIC");
            if (post.getGroupId() != null) body.put("group_id", post.getGroupId());
            ApiClient.post("/posts", body);
            return;
        }
        String sql = "INSERT INTO posts (author_id, content, image_base64, visibility, group_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, post.getAuthorId());
            ps.setString(2, post.getContent());
            ps.setString(3, post.getImageBase64());
            ps.setString(4, post.getVisibility() != null ? post.getVisibility() : "PUBLIC");
            if (post.getGroupId() != null) ps.setInt(5, post.getGroupId());
            else ps.setNull(5, Types.INTEGER);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("posts:");
    }

    @Override
    public void modifier(Post post) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("content", post.getContent());
            if (post.getImageBase64() != null) body.put("image_base64", post.getImageBase64());
            body.put("visibility", post.getVisibility() != null ? post.getVisibility() : "PUBLIC");
            ApiClient.put("/posts/" + post.getId(), body);
            return;
        }
        String sql = "UPDATE posts SET content = ?, image_base64 = ?, visibility = ? WHERE id = ?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, post.getContent());
            ps.setString(2, post.getImageBase64());
            ps.setString(3, post.getVisibility() != null ? post.getVisibility() : "PUBLIC");
            ps.setInt(4, post.getId());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("posts:");
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/posts/" + id);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM posts WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("posts:");
    }

    @Override
    public List<Post> recuperer() throws SQLException {
        if (useApi) {
            return InMemoryCache.getOrLoad(CACHE_KEY, CACHE_TTL,
                    () -> jsonArrayToPosts(ApiClient.get("/posts")));
        }
        return InMemoryCache.getOrLoadChecked(CACHE_KEY, CACHE_TTL,
                () -> recupererFromDb());
    }

    private List<Post> recupererFromDb() throws SQLException {
        List<Post> posts = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT p.*, " +
                "(SELECT COUNT(*) FROM reactions WHERE post_id = p.id) AS likes_count, " +
                "(SELECT COUNT(*) FROM comments WHERE post_id = p.id) AS comments_count " +
                "FROM posts p ORDER BY p.created_at DESC")) {
            while (rs.next()) {
                Post p = new Post(
                        rs.getInt("id"),
                        rs.getInt("author_id"),
                        rs.getString("content"),
                        rs.getString("image_base64"),
                        rs.getInt("likes_count"),
                        rs.getInt("comments_count"),
                        rs.getTimestamp("created_at")
                );
                try { p.setSharesCount(rs.getInt("shares_count")); } catch (Exception ignored) {}
                try { p.setVisibility(rs.getString("visibility")); } catch (Exception ignored) {}
                try { p.setGroupId(rs.getObject("group_id") != null ? rs.getInt("group_id") : null); } catch (Exception ignored) {}
                posts.add(p);
            }
        }
        return posts;
    }
}
