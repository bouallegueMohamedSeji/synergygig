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

    public void addSkillToUser(int userId, int skillId) throws SQLException {
        // First check if user already has the skill to avoid duplicates
        if (hasSkill(userId, skillId)) {
            System.out.println("User " + userId + " already has skill " + skillId);
            return;
        }

        String req = "INSERT INTO user_skills (user_id, skill_id) VALUES (?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, userId);
        ps.setInt(2, skillId);
        ps.executeUpdate();
        System.out.println("Skill " + skillId + " added to user " + userId);
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
}
