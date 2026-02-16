package tn.esprit.synergygig.services;

import tn.esprit.synergygig.dao.ForumDAO;
import tn.esprit.synergygig.entities.Forum;

import java.sql.SQLException;
import java.util.List;

public class ForumService {

    private ForumDAO dao;

    public ForumService() {
        dao = new ForumDAO();
    }

    public void addForum(Forum f) throws SQLException {
        if (f.getTitle() == null || f.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }
        if (f.getContent() == null || f.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }
        dao.insertOne(f);
    }

    public void updateForum(Forum f) throws SQLException {
        if (f.getId() == 0) {
            throw new IllegalArgumentException("Forum ID is required for update");
        }
        dao.updateOne(f);
    }

    public void deleteForum(Forum f) throws SQLException {
        if (f.getId() == 0) {
            throw new IllegalArgumentException("Forum ID is required for deletion");
        }
        dao.deleteOne(f);
    }

    public List<Forum> getAllForums() throws SQLException {
        return dao.selectAll();
    }

    public boolean toggleLike(int forumId, int userId) throws SQLException {
        return dao.toggleLike(forumId, userId);
    }

    public boolean hasLiked(int forumId, int userId) throws SQLException {
        return dao.hasLiked(forumId, userId);
    }

    public int getLikesCount(int forumId) throws SQLException {
        return dao.getLikesCount(forumId);
    }
}
