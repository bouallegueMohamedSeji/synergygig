package services;

import entities.Question;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceQuestion implements IService<Question> {

    private Connection connection;

    public ServiceQuestion() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(Question q) throws SQLException {
        String req = "INSERT INTO questions (quiz_id, question_text, option_a, option_b, option_c, correct_option) VALUES (?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, q.getQuizId());
        ps.setString(2, q.getQuestionText());
        ps.setString(3, q.getOptionA());
        ps.setString(4, q.getOptionB());
        ps.setString(5, q.getOptionC());
        ps.setString(6, q.getCorrectOption());
        ps.executeUpdate();
    }

    @Override
    public void modifier(Question q) throws SQLException {
        String req = "UPDATE questions SET quiz_id=?, question_text=?, option_a=?, option_b=?, option_c=?, correct_option=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, q.getQuizId());
        ps.setString(2, q.getQuestionText());
        ps.setString(3, q.getOptionA());
        ps.setString(4, q.getOptionB());
        ps.setString(5, q.getOptionC());
        ps.setString(6, q.getCorrectOption());
        ps.setInt(7, q.getId());
        ps.executeUpdate();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM questions WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    @Override
    public List<Question> recuperer() throws SQLException {
        List<Question> questions = new ArrayList<>();
        String req = "SELECT * FROM questions";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            questions.add(new Question(
                    rs.getInt("id"),
                    rs.getInt("quiz_id"),
                    rs.getString("question_text"),
                    rs.getString("option_a"),
                    rs.getString("option_b"),
                    rs.getString("option_c"),
                    rs.getString("correct_option")));
        }
        return questions;
    }

    public List<Question> getByQuizId(int quizId) throws SQLException {
        List<Question> questions = new ArrayList<>();
        String req = "SELECT * FROM questions WHERE quiz_id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, quizId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            questions.add(new Question(
                    rs.getInt("id"),
                    rs.getInt("quiz_id"),
                    rs.getString("question_text"),
                    rs.getString("option_a"),
                    rs.getString("option_b"),
                    rs.getString("option_c"),
                    rs.getString("correct_option")));
        }
        return questions;
    }
}
