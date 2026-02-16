package tn.esprit.synergygig.dao;

import tn.esprit.synergygig.entities.ForumComment;
import tn.esprit.synergygig.utils.MyDBConnexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ForumCommentDAO implements CRUD<ForumComment> {

    private Connection cnx;

    public ForumCommentDAO() {
        cnx = MyDBConnexion.getInstance().getCnx();
    }

    @Override
    public void insertOne(ForumComment comment) throws SQLException {
        String sql = "INSERT INTO forum_comments (forum_id, content, created_by) VALUES (?, ?, ?)";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, comment.getForumId());
        ps.setString(2, comment.getContent());
        ps.setInt(3, comment.getCreatedBy());
        ps.executeUpdate();
    }

    @Override
    public void updateOne(ForumComment comment) throws SQLException {
        String sql = "UPDATE forum_comments SET content=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, comment.getContent());
        ps.setInt(2, comment.getId());
        ps.executeUpdate();
    }

    @Override
    public void deleteOne(ForumComment comment) throws SQLException {
        String sql = "DELETE FROM forum_comments WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, comment.getId());
        ps.executeUpdate();
    }

    @Override
    public List<ForumComment> selectAll() throws SQLException {
        // Usually we select by forum_id, but the interface mandates selectAll.
        // We will implement selectByForumId as a separate method.
        List<ForumComment> comments = new ArrayList<>();
        String sql = "SELECT * FROM forum_comments";
        Statement st = cnx.createStatement();
        ResultSet rs = st.executeQuery(sql);
        while (rs.next()) {
            ForumComment c = new ForumComment(
                    rs.getInt("id"),
                    rs.getInt("forum_id"),
                    rs.getString("content"),
                    rs.getInt("created_by"),
                    rs.getTimestamp("created_at"));
            comments.add(c);
        }
        return comments;
    }

    public List<ForumComment> selectByForumId(int forumId) throws SQLException {
        List<ForumComment> comments = new ArrayList<>();
        String sql = "SELECT * FROM forum_comments WHERE forum_id = ? ORDER BY created_at ASC";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, forumId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            ForumComment c = new ForumComment(
                    rs.getInt("id"),
                    rs.getInt("forum_id"),
                    rs.getString("content"),
                    rs.getInt("created_by"),
                    rs.getTimestamp("created_at"));
            comments.add(c);
        }
        return comments;
    }
}
