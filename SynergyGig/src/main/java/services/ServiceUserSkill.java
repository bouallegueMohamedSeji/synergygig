package services;

import utils.MyDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ServiceUserSkill {

    private Connection connection;

    public ServiceUserSkill() {
        connection = MyDatabase.getInstance().getConnection();
    }

    public void addSkillToUser(int userId, int skillId, String skillLevel) throws SQLException {
        // First check if user already has the skill to avoid duplicates
        // We allow updating level if they already have it
        if (hasSkill(userId, skillId)) {
            System.out.println("User " + userId + " already has skill " + skillId + ". Updating level...");
            updateSkillLevel(userId, skillId, skillLevel);
            return;
        }

        String req = "INSERT INTO user_skills (user_id, skill_id, skill_level) VALUES (?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, userId);
        ps.setInt(2, skillId);
        ps.setString(3, skillLevel);
        ps.executeUpdate();
        System.out.println("Skill " + skillId + " (" + skillLevel + ") added to user " + userId);
    }

    public void updateSkillLevel(int userId, int skillId, String skillLevel) throws SQLException {
        String req = "UPDATE user_skills SET skill_level = ? WHERE user_id = ? AND skill_id = ?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, skillLevel);
        ps.setInt(2, userId);
        ps.setInt(3, skillId);
        ps.executeUpdate();
    }

    public boolean hasSkill(int userId, int skillId) throws SQLException {
        String req = "SELECT COUNT(*) FROM user_skills WHERE user_id = ? AND skill_id = ?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, userId);
        ps.setInt(2, skillId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return rs.getInt(1) > 0;
        }
        return false;
    }

    public java.util.List<entities.UserSkillView> getUserSkills(int userId) throws SQLException {
        java.util.List<entities.UserSkillView> skills = new java.util.ArrayList<>();
        String req = "SELECT s.name, us.skill_level FROM user_skills us JOIN skills s ON us.skill_id = s.id WHERE us.user_id = ?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            skills.add(new entities.UserSkillView(rs.getString("name"), rs.getString("skill_level")));
        }
        return skills;
    }
}
