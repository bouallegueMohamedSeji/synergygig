package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.ForumCommentDAO;
import tn.esprit.synergygig.entities.ForumComment;

import java.sql.SQLException;
import java.util.List;

public class ForumCommentService {

    private ForumCommentDAO dao;

    public ForumCommentService() {
        dao = new ForumCommentDAO();
    }

    public void addComment(ForumComment c) throws SQLException {
        if (c.getContent() == null || c.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Comment content cannot be empty");
        }
        dao.insertOne(c);
    }

    public void deleteComment(ForumComment c) throws SQLException {
        dao.deleteOne(c);
    }

    public void updateComment(ForumComment c) throws SQLException {
        if (c.getContent() == null || c.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Comment content cannot be empty");
        }
        dao.updateOne(c);
    }

    public List<ForumComment> getCommentsByForumId(int forumId) throws SQLException {
        return dao.selectByForumId(forumId);
    }

    public List<ForumComment> getAllComments() throws SQLException {
        return dao.selectAll();
    }
}
