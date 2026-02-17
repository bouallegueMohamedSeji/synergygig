package services;

import entities.Skill;
import utils.MyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceSkill implements IService<Skill> {

    private Connection connection;

    public ServiceSkill() {
        connection = MyDatabase.getInstance().getConnection();
    }

    @Override
    public void ajouter(Skill skill) throws SQLException {
        String req = "INSERT INTO skills (name) VALUES (?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, skill.getName());
        ps.executeUpdate();
    }

    @Override
    public void modifier(Skill skill) throws SQLException {
        String req = "UPDATE skills SET name=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, skill.getName());
        ps.setInt(2, skill.getId());
        ps.executeUpdate();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        String req = "DELETE FROM skills WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    @Override
    public List<Skill> recuperer() throws SQLException {
        List<Skill> skills = new ArrayList<>();
        String req = "SELECT * FROM skills";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            skills.add(new Skill(
                    rs.getInt("id"),
                    rs.getString("name")));
        }
        return skills;
    }
}
