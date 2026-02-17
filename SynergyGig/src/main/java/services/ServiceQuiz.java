package services;

import entities.Quiz;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceQuiz implements IService<Quiz> {

    private Connection connection;

    public ServiceQuiz() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(Quiz quiz) throws SQLException {
        String req = "INSERT INTO quizzes (course_id, title) VALUES (?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, quiz.getCourseId());
        ps.setString(2, quiz.getTitle());
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Quiz added: " + quiz.getTitle());
    }

    @Override
    public void modifier(Quiz quiz) throws SQLException {
        String req = "UPDATE quizzes SET course_id=?, title=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, quiz.getCourseId());
        ps.setString(2, quiz.getTitle());
        ps.setInt(3, quiz.getId());
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Quiz updated: " + quiz.getTitle());
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM quizzes WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Quiz deleted: id=" + id);
    }

    @Override
    public List<Quiz> recuperer() throws SQLException {
        List<Quiz> quizzes = new ArrayList<>();
        String req = "SELECT * FROM quizzes";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Quiz quiz = new Quiz(
                    rs.getInt("id"),
                    rs.getInt("course_id"),
                    rs.getString("title"));
            quizzes.add(quiz);
        }
        rs.close();
        ps.close();
        return quizzes;
    }
}
