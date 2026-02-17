package services;

import entities.Course;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceCourse implements IService<Course> {

    private Connection connection;

    public ServiceCourse() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(Course t) throws SQLException {
        String req = "INSERT INTO courses (title, description, instructor_id, skill_id) VALUES (?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, t.getTitle());
        ps.setString(2, t.getDescription());
        ps.setInt(3, t.getInstructorId());

        if (t.getSkillId() > 0) {
            ps.setInt(4, t.getSkillId());
        } else {
            ps.setNull(4, java.sql.Types.INTEGER);
        }

        ps.executeUpdate();
    }

    @Override
    public void modifier(Course t) throws SQLException {
        String req = "UPDATE courses SET title=?, description=?, instructor_id=?, skill_id=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, t.getTitle());
        ps.setString(2, t.getDescription());
        ps.setInt(3, t.getInstructorId());

        if (t.getSkillId() > 0) {
            ps.setInt(4, t.getSkillId());
        } else {
            ps.setNull(4, java.sql.Types.INTEGER);
        }

        ps.setInt(5, t.getId());
        ps.executeUpdate();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM courses WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    @Override
    public List<Course> recuperer() throws SQLException {
        List<Course> courses = new ArrayList<>();
        String req = "SELECT * FROM courses";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            courses.add(new Course(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getInt("instructor_id"),
                    rs.getInt("skill_id")));
        }
        return courses;
    }
}
