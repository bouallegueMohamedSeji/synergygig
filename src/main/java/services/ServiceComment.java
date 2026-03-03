package services;

import com.google.gson.*;
import entities.Comment;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceComment implements IService<Comment> {

    private final boolean useApi;

    public ServiceComment() {
        useApi = AppConfig.isApiMode();
    }

    // ============ JSON ============

    private Comment jsonToComment(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            // Parse as UTC — server stores timestamps in UTC
            String raw = obj.get("created_at").getAsString().replace("T", " ");
            if (raw.contains(".")) raw = raw.substring(0, raw.indexOf(".")); // trim fractional seconds
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            createdAt = Timestamp.from(ldt.atZone(java.time.ZoneOffset.UTC).toInstant());
        }
        Comment c = new Comment(
                obj.get("id").getAsInt(),
                obj.get("post_id").getAsInt(),
                obj.get("author_id").getAsInt(),
                obj.get("content").getAsString(),
                createdAt
        );
        if (obj.has("parent_id") && !obj.get("parent_id").isJsonNull())
            c.setParentId(obj.get("parent_id").getAsInt());
        return c;
    }

    private List<Comment> jsonArrayToComments(JsonElement el) {
        List<Comment> comments = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                comments.add(jsonToComment(item.getAsJsonObject()));
            }
        }
        return comments;
    }

    // ============ CRUD ============

    @Override
    public void ajouter(Comment comment) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("post_id", comment.getPostId());
            body.put("author_id", comment.getAuthorId());
            body.put("content", comment.getContent());
            if (comment.getParentId() != null) body.put("parent_id", comment.getParentId());
            ApiClient.post("/comments", body);
            return;
        }
        String sql = "INSERT INTO comments (post_id, author_id, content, parent_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, comment.getPostId());
            ps.setInt(2, comment.getAuthorId());
            ps.setString(3, comment.getContent());
            if (comment.getParentId() != null) ps.setInt(4, comment.getParentId());
            else ps.setNull(4, java.sql.Types.INTEGER);
            ps.executeUpdate();
        }
    }

    @Override
    public void modifier(Comment comment) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("content", comment.getContent());
            ApiClient.put("/comments/" + comment.getId(), body);
            return;
        }
        String sql = "UPDATE comments SET content = ? WHERE id = ?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, comment.getContent());
            ps.setInt(2, comment.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/comments/" + id);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM comments WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public List<Comment> recuperer() throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/comments");
            return jsonArrayToComments(el);
        }
        List<Comment> comments = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM comments ORDER BY created_at ASC")) {
            while (rs.next()) {
                Comment c = new Comment(
                        rs.getInt("id"),
                        rs.getInt("post_id"),
                        rs.getInt("author_id"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at")
                );
                int pid = rs.getInt("parent_id");
                if (!rs.wasNull()) c.setParentId(pid);
                comments.add(c);
            }
        }
        return comments;
    }

    /** Get comments for a specific post */
    public List<Comment> getByPostId(int postId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/comments/post/" + postId);
            return jsonArrayToComments(el);
        }
        List<Comment> comments = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM comments WHERE post_id = ? ORDER BY created_at ASC")) {
            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Comment c = new Comment(
                            rs.getInt("id"),
                            rs.getInt("post_id"),
                            rs.getInt("author_id"),
                            rs.getString("content"),
                            rs.getTimestamp("created_at")
                    );
                    int pid = rs.getInt("parent_id");
                    if (!rs.wasNull()) c.setParentId(pid);
                    comments.add(c);
                }
            }
        }
        return comments;
    }
}
