package services;

import entities.Resource;
import utils.MyDatabase;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceResource implements IService<Resource> {

    private Connection connection;

    public ServiceResource() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(Resource resource) throws SQLException {
        String req = "INSERT INTO resources (course_id, type, url) VALUES (?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, resource.getCourseId());
        ps.setString(2, resource.getType());
        ps.setString(3, resource.getUrl());
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Resource added: " + resource.getType());
    }

    @Override
    public void modifier(Resource resource) throws SQLException {
        String req = "UPDATE resources SET course_id=?, type=?, url=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, resource.getCourseId());
        ps.setString(2, resource.getType());
        ps.setString(3, resource.getUrl());
        ps.setInt(4, resource.getId());
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Resource updated: " + resource.getUrl());
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM resources WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Resource deleted: id=" + id);
    }

    @Override
    public List<Resource> recuperer() throws SQLException {
        List<Resource> resources = new ArrayList<>();
        String req = "SELECT * FROM resources";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Resource resource = new Resource(
                    rs.getInt("id"),
                    rs.getInt("course_id"),
                    rs.getString("type"),
                    rs.getString("url"));
            resources.add(resource);
        }
        rs.close();
        ps.close();
        return resources;
    }
}
