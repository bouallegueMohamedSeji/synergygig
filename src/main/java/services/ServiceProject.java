package services;

import com.google.gson.*;
import entities.Project;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceProject implements IService<Project> {

    private Connection connection;
    private final boolean useApi;

    public ServiceProject() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
    }

    // ==================== JSON helpers ====================

    private Project jsonToProject(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        java.sql.Date startDate = null;
        if (obj.has("start_date") && !obj.get("start_date").isJsonNull()) {
            startDate = java.sql.Date.valueOf(obj.get("start_date").getAsString());
        }
        java.sql.Date deadline = null;
        if (obj.has("deadline") && !obj.get("deadline").isJsonNull()) {
            deadline = java.sql.Date.valueOf(obj.get("deadline").getAsString());
        }
        String status = "PLANNING";
        if (obj.has("status") && !obj.get("status").isJsonNull()) {
            status = obj.get("status").getAsString();
        }
        return new Project(
                obj.get("id").getAsInt(),
                obj.get("name").getAsString(),
                obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString() : "",
                obj.get("owner_id").getAsInt(),
                startDate,
                deadline,
                status,
                createdAt
        );
    }

    private Project parseProject(JsonObject obj) {
        Project p = jsonToProject(obj);
        if (obj.has("department_id") && !obj.get("department_id").isJsonNull()) {
            p.setDepartmentId(obj.get("department_id").getAsInt());
        }
        return p;
    }

    private List<Project> jsonArrayToProjects(JsonElement el) {
        List<Project> projects = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                projects.add(parseProject(item.getAsJsonObject()));
            }
        }
        return projects;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(Project p) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", p.getName());
            body.put("description", p.getDescription());
            body.put("owner_id", p.getManagerId());
            body.put("start_date", p.getStartDate() != null ? p.getStartDate().toString() : null);
            body.put("deadline", p.getDeadline() != null ? p.getDeadline().toString() : null);
            body.put("status", p.getStatus());
            body.put("department_id", p.getDepartmentId());
            JsonElement resp = ApiClient.post("/projects", body);
            if (resp != null && resp.isJsonObject()) {
                p.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String sql = "INSERT INTO projects (name, description, owner_id, start_date, deadline, status, department_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, p.getName());
        ps.setString(2, p.getDescription());
        ps.setInt(3, p.getManagerId());
        ps.setDate(4, p.getStartDate());
        ps.setDate(5, p.getDeadline());
        ps.setString(6, p.getStatus());
        if (p.getDepartmentId() != null) ps.setInt(7, p.getDepartmentId());
        else ps.setNull(7, java.sql.Types.INTEGER);
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) p.setId(keys.getInt(1));
        keys.close();
        ps.close();
    }

    @Override
    public void modifier(Project p) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", p.getName());
            body.put("description", p.getDescription());
            body.put("owner_id", p.getManagerId());
            body.put("start_date", p.getStartDate() != null ? p.getStartDate().toString() : null);
            body.put("deadline", p.getDeadline() != null ? p.getDeadline().toString() : null);
            body.put("status", p.getStatus());
            body.put("department_id", p.getDepartmentId());
            ApiClient.put("/projects/" + p.getId(), body);
            return;
        }
        String sql = "UPDATE projects SET name=?, description=?, owner_id=?, start_date=?, deadline=?, status=?, department_id=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, p.getName());
        ps.setString(2, p.getDescription());
        ps.setInt(3, p.getManagerId());
        ps.setDate(4, p.getStartDate());
        ps.setDate(5, p.getDeadline());
        ps.setString(6, p.getStatus());
        if (p.getDepartmentId() != null) ps.setInt(7, p.getDepartmentId());
        else ps.setNull(7, java.sql.Types.INTEGER);
        ps.setInt(8, p.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/projects/" + id);
            return;
        }
        // Delete tasks first (cascade)
        PreparedStatement delTasks = connection.prepareStatement("DELETE FROM tasks WHERE project_id = ?");
        delTasks.setInt(1, id);
        delTasks.executeUpdate();
        delTasks.close();

        PreparedStatement ps = connection.prepareStatement("DELETE FROM projects WHERE id = ?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Project> recuperer() throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/projects");
            return jsonArrayToProjects(el);
        }
        List<Project> projects = new ArrayList<>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM projects ORDER BY created_at DESC");
        while (rs.next()) {
            projects.add(rowToProject(rs));
        }
        rs.close();
        st.close();
        return projects;
    }

    /** Get projects where the given user is the manager. */
    public List<Project> getByManager(int managerId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/projects/owner/" + managerId);
            return jsonArrayToProjects(el);
        }
        List<Project> projects = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM projects WHERE owner_id = ? ORDER BY created_at DESC");
        ps.setInt(1, managerId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            projects.add(rowToProject(rs));
        }
        rs.close();
        ps.close();
        return projects;
    }

    private Project rowToProject(ResultSet rs) throws SQLException {
        Project p = new Project(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("owner_id"),
                rs.getDate("start_date"),
                rs.getDate("deadline"),
                rs.getString("status"),
                rs.getTimestamp("created_at")
        );
        int deptId = rs.getInt("department_id");
        if (!rs.wasNull()) p.setDepartmentId(deptId);
        return p;
    }
}
