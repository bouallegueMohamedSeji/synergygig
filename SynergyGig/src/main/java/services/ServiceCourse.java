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
    public void ajouter(Course course) throws SQLException {
        String req = "INSERT INTO courses (title, description, instructor_id) VALUES (?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, course.getTitle());
        ps.setString(2, course.getDescription());
        if (course.getInstructorId() != 0) {
            ps.setInt(3, course.getInstructorId());
        } else {
            ps.setNull(3, Types.INTEGER);
        }
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Course added: " + course.getTitle());
    }

    @Override
    public void modifier(Course course) throws SQLException {
        String req = "UPDATE courses SET title=?, description=?, instructor_id=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, course.getTitle());
        ps.setString(2, course.getDescription());
        if (course.getInstructorId() != 0) {
            ps.setInt(3, course.getInstructorId());
        } else {
            ps.setNull(3, Types.INTEGER);
        }
        ps.setInt(4, course.getId());
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Course updated: " + course.getTitle());
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM courses WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Course deleted: id=" + id);
    }

    @Override
    public List<Course> recuperer() throws SQLException {
        List<Course> courses = new ArrayList<>();
        String req = "SELECT * FROM courses";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Course course = new Course(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getInt("instructor_id"));
            courses.add(course);
        }
        rs.close();
        ps.close();
        return courses;
    }
}
