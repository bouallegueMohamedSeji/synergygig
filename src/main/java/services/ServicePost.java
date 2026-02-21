package services;

import com.google.gson.*;
import entities.Post;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServicePost implements IService<Post> {

    private Connection connection;
    private final boolean useApi;

    public ServicePost() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
    }

    // ============ JSON helpers ============

    private Post jsonToPost(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        String imageBase64 = null;
        if (obj.has("image_base64") && !obj.get("image_base64").isJsonNull()) {
            imageBase64 = obj.get("image_base64").getAsString();
        }
        return new Post(
                obj.get("id").getAsInt(),
                obj.get("author_id").getAsInt(),
                obj.get("content").getAsString(),
                imageBase64,
                obj.has("likes_count") ? obj.get("likes_count").getAsInt() : 0,
                obj.has("comments_count") ? obj.get("comments_count").getAsInt() : 0,
                createdAt
        );
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
            ApiClient.post("/posts", body);
            return;
        }
        String sql = "INSERT INTO posts (author_id, content, image_base64) VALUES (?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, post.getAuthorId());
        ps.setString(2, post.getContent());
        ps.setString(3, post.getImageBase64());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void modifier(Post post) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("content", post.getContent());
            if (post.getImageBase64() != null) body.put("image_base64", post.getImageBase64());
            ApiClient.put("/posts/" + post.getId(), body);
            return;
        }
        String sql = "UPDATE posts SET content = ?, image_base64 = ? WHERE id = ?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, post.getContent());
        ps.setString(2, post.getImageBase64());
        ps.setInt(3, post.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/posts/" + id);
            return;
        }
        PreparedStatement ps = connection.prepareStatement("DELETE FROM posts WHERE id = ?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Post> recuperer() throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/posts");
            return jsonArrayToPosts(el);
        }
        List<Post> posts = new ArrayList<>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery(
                "SELECT p.*, " +
                "(SELECT COUNT(*) FROM reactions WHERE post_id = p.id) AS likes_count, " +
                "(SELECT COUNT(*) FROM comments WHERE post_id = p.id) AS comments_count " +
                "FROM posts p ORDER BY p.created_at DESC");
        while (rs.next()) {
            posts.add(new Post(
                    rs.getInt("id"),
                    rs.getInt("author_id"),
                    rs.getString("content"),
                    rs.getString("image_base64"),
                    rs.getInt("likes_count"),
                    rs.getInt("comments_count"),
                    rs.getTimestamp("created_at")
            ));
        }
        rs.close(); st.close();
        return posts;
    }
}
