package services;

import entities.QuizAttempt;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceQuizAttempt implements IService<QuizAttempt> {

    private Connection connection;

    public ServiceQuizAttempt() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(QuizAttempt qa) throws SQLException {
        String req = "INSERT INTO quiz_attempts (quiz_id, user_id, score) VALUES (?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, qa.getQuizId());
        ps.setInt(2, qa.getUserId());
        ps.setInt(3, qa.getScore());
        ps.executeUpdate();
    }

    @Override
    public void modifier(QuizAttempt qa) throws SQLException {
        String req = "UPDATE quiz_attempts SET quiz_id=?, user_id=?, score=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, qa.getQuizId());
        ps.setInt(2, qa.getUserId());
        ps.setInt(3, qa.getScore());
        ps.setInt(4, qa.getId());
        ps.executeUpdate();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM quiz_attempts WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    @Override
    public List<QuizAttempt> recuperer() throws SQLException {
        List<QuizAttempt> attempts = new ArrayList<>();
        String req = "SELECT * FROM quiz_attempts";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            attempts.add(new QuizAttempt(
                    rs.getInt("id"),
                    rs.getInt("quiz_id"),
                    rs.getInt("user_id"),
                    rs.getInt("score"),
                    rs.getTimestamp("attempt_at")));
        }
        return attempts;
    }
}
