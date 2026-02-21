package services;

import com.google.gson.*;
import entities.Comment;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceComment implements IService<Comment> {

    private Connection connection;
    private final boolean useApi;

    public ServiceComment() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
    }

    // ============ JSON ============

    private Comment jsonToComment(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        return new Comment(
                obj.get("id").getAsInt(),
                obj.get("post_id").getAsInt(),
                obj.get("author_id").getAsInt(),
                obj.get("content").getAsString(),
                createdAt
        );
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
            ApiClient.post("/comments", body);
            return;
        }
        String sql = "INSERT INTO comments (post_id, author_id, content) VALUES (?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, comment.getPostId());
        ps.setInt(2, comment.getAuthorId());
        ps.setString(3, comment.getContent());
        ps.executeUpdate();
        ps.close();
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
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, comment.getContent());
        ps.setInt(2, comment.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/comments/" + id);
            return;
        }
        PreparedStatement ps = connection.prepareStatement("DELETE FROM comments WHERE id = ?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Comment> recuperer() throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/comments");
            return jsonArrayToComments(el);
        }
        List<Comment> comments = new ArrayList<>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM comments ORDER BY created_at ASC");
        while (rs.next()) {
            comments.add(new Comment(
                    rs.getInt("id"),
                    rs.getInt("post_id"),
                    rs.getInt("author_id"),
                    rs.getString("content"),
                    rs.getTimestamp("created_at")
            ));
        }
        rs.close(); st.close();
        return comments;
    }

    /** Get comments for a specific post */
    public List<Comment> getByPostId(int postId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/comments/post/" + postId);
            return jsonArrayToComments(el);
        }
        List<Comment> comments = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM comments WHERE post_id = ? ORDER BY created_at ASC");
        ps.setInt(1, postId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            comments.add(new Comment(
                    rs.getInt("id"),
                    rs.getInt("post_id"),
                    rs.getInt("author_id"),
                    rs.getString("content"),
                    rs.getTimestamp("created_at")
            ));
        }
        rs.close(); ps.close();
        return comments;
    }
}
