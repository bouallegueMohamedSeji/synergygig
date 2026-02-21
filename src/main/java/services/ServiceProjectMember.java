package services;

import com.google.gson.*;
import entities.User;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

/**
 * Manages project team members (project_members table).
 */
public class ServiceProjectMember {

    private Connection connection;
    private final boolean useApi;

    public ServiceProjectMember() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
    }

    // ── Lightweight member info ──
    public static class ProjectMember {
        public int id, projectId, userId;
        public String role, firstName, lastName, email, userRole;
        public Integer departmentId;

        @Override
        public String toString() {
            return firstName + " " + lastName + " (" + email + ")";
        }
    }

    // ==================== API helpers ====================

    private ProjectMember jsonToMember(JsonObject obj) {
        ProjectMember m = new ProjectMember();
        m.id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsInt() : 0;
        m.projectId = obj.has("project_id") && !obj.get("project_id").isJsonNull() ? obj.get("project_id").getAsInt() : 0;
        m.userId = obj.has("user_id") && !obj.get("user_id").isJsonNull() ? obj.get("user_id").getAsInt() : 0;
        m.role = obj.has("role") && !obj.get("role").isJsonNull() ? obj.get("role").getAsString() : "MEMBER";
        m.firstName = obj.has("first_name") && !obj.get("first_name").isJsonNull() ? obj.get("first_name").getAsString() : "";
        m.lastName = obj.has("last_name") && !obj.get("last_name").isJsonNull() ? obj.get("last_name").getAsString() : "";
        m.email = obj.has("email") && !obj.get("email").isJsonNull() ? obj.get("email").getAsString() : "";
        m.userRole = obj.has("user_role") && !obj.get("user_role").isJsonNull() ? obj.get("user_role").getAsString() : "";
        m.departmentId = obj.has("department_id") && !obj.get("department_id").isJsonNull()
                ? obj.get("department_id").getAsInt() : null;
        return m;
    }

    // ==================== Operations ====================

    /** Get all members of a project. */
    public List<ProjectMember> getMembers(int projectId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/projects/" + projectId + "/members");
            List<ProjectMember> list = new ArrayList<>();
            if (el != null && el.isJsonArray()) {
                for (JsonElement item : el.getAsJsonArray()) {
                    list.add(jsonToMember(item.getAsJsonObject()));
                }
            }
            return list;
        }
        // JDBC fallback
        List<ProjectMember> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement(
                "SELECT pm.id, pm.project_id, pm.user_id, pm.role, " +
                "u.first_name, u.last_name, u.email, u.role as user_role, u.department_id " +
                "FROM project_members pm JOIN users u ON pm.user_id = u.id " +
                "WHERE pm.project_id = ? ORDER BY pm.joined_at");
        ps.setInt(1, projectId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            ProjectMember m = new ProjectMember();
            m.id = rs.getInt("id");
            m.projectId = rs.getInt("project_id");
            m.userId = rs.getInt("user_id");
            m.role = rs.getString("role");
            m.firstName = rs.getString("first_name");
            m.lastName = rs.getString("last_name");
            m.email = rs.getString("email");
            m.userRole = rs.getString("user_role");
            m.departmentId = rs.getObject("department_id") != null ? rs.getInt("department_id") : null;
            list.add(m);
        }
        rs.close();
        ps.close();
        return list;
    }

    /** Add a single member to a project. */
    public void addMember(int projectId, int userId) throws SQLException {
        addMember(projectId, userId, "MEMBER");
    }

    public void addMember(int projectId, int userId, String role) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", userId);
            body.put("role", role);
            ApiClient.post("/projects/" + projectId + "/members", body);
            return;
        }
        PreparedStatement ps = connection.prepareStatement(
                "INSERT IGNORE INTO project_members (project_id, user_id, role) VALUES (?, ?, ?)");
        ps.setInt(1, projectId);
        ps.setInt(2, userId);
        ps.setString(3, role);
        ps.executeUpdate();
        ps.close();
    }

    /** Remove a member from a project. */
    public void removeMember(int projectId, int userId) throws SQLException {
        if (useApi) {
            ApiClient.delete("/projects/" + projectId + "/members/" + userId);
            return;
        }
        PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM project_members WHERE project_id = ? AND user_id = ?");
        ps.setInt(1, projectId);
        ps.setInt(2, userId);
        ps.executeUpdate();
        ps.close();
    }

    /** Add all users from a department as members. Returns number added. */
    public int addDepartment(int projectId, int departmentId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.post("/projects/" + projectId + "/members/department/" + departmentId, new HashMap<>());
            if (el != null && el.isJsonObject()) {
                return el.getAsJsonObject().get("added").getAsInt();
            }
            return 0;
        }
        // JDBC fallback
        PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM users WHERE department_id = ?");
        ps.setInt(1, departmentId);
        ResultSet rs = ps.executeQuery();
        int added = 0;
        while (rs.next()) {
            try {
                addMember(projectId, rs.getInt("id"));
                added++;
            } catch (SQLException ignored) {}
        }
        rs.close();
        ps.close();
        return added;
    }

    /** Update project's department_id. */
    public void setProjectDepartment(int projectId, Integer departmentId) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("department_id", departmentId);
            ApiClient.put("/projects/" + projectId + "/department", body);
            return;
        }
        PreparedStatement ps = connection.prepareStatement(
                "UPDATE projects SET department_id = ? WHERE id = ?");
        if (departmentId != null) ps.setInt(1, departmentId);
        else ps.setNull(1, java.sql.Types.INTEGER);
        ps.setInt(2, projectId);
        ps.executeUpdate();
        ps.close();
    }
}
