package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.Forum;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ForumDAO implements CRUD<Forum> {

    private Connection cnx;

    public ForumDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
    }

    @Override
    public void insertOne(Forum forum) throws SQLException {
        String sql = "INSERT INTO forums (title, content, created_by) VALUES (?, ?, ?)";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, forum.getTitle());
        ps.setString(2, forum.getContent());
        ps.setInt(3, forum.getCreatedBy());
        ps.executeUpdate();
    }

    @Override
    public void updateOne(Forum forum) throws SQLException {
        String sql = "UPDATE forums SET title=?, content=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, forum.getTitle());
        ps.setString(2, forum.getContent());
        ps.setInt(3, forum.getId());
        ps.executeUpdate();
    }

    @Override
    public void deleteOne(Forum forum) throws SQLException {
        String sql = "DELETE FROM forums WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, forum.getId());
        ps.executeUpdate();
    }

    @Override
    public List<Forum> selectAll() throws SQLException {
        List<Forum> forums = new ArrayList<>();
        String sql = "SELECT * FROM forums ORDER BY created_at DESC";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(sql);
        while (rs.next()) {
            Forum f = new Forum(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("content"),
                    rs.getInt("created_by"),
                    rs.getTimestamp("created_at"));
            forums.add(f);
        }
        return forums;
    }

    public boolean toggleLike(int forumId, int userId) throws SQLException {
        if (hasLiked(forumId, userId)) {
            removeLike(forumId, userId);
            return false; // Liked removed
        } else {
            addLike(forumId, userId);
            return true; // Like added
        }
    }

    public void addLike(int forumId, int userId) throws SQLException {
        String sql = "INSERT INTO forum_likes (forum_id, user_id) VALUES (?, ?)";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, forumId);
        ps.setInt(2, userId);
        ps.executeUpdate();
    }

    public void removeLike(int forumId, int userId) throws SQLException {
        String sql = "DELETE FROM forum_likes WHERE forum_id = ? AND user_id = ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, forumId);
        ps.setInt(2, userId);
        ps.executeUpdate();
    }

    public boolean hasLiked(int forumId, int userId) throws SQLException {
        String sql = "SELECT 1 FROM forum_likes WHERE forum_id = ? AND user_id = ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, forumId);
        ps.setInt(2, userId);
        ResultSet rs = ps.executeQuery();
        return rs.next();
    }

    public int getLikesCount(int forumId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM forum_likes WHERE forum_id = ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, forumId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return rs.getInt(1);
        }
        return 0;
    }
}
