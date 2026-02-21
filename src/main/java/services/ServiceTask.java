package services;

import com.google.gson.*;
import entities.Task;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceTask implements IService<Task> {

    private Connection connection;
    private final boolean useApi;

    public ServiceTask() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
    }

    // ==================== JSON helpers ====================

    private Task jsonToTask(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        java.sql.Date dueDate = null;
        if (obj.has("due_date") && !obj.get("due_date").isJsonNull()) {
            dueDate = java.sql.Date.valueOf(obj.get("due_date").getAsString());
        }
        int assigneeId = 0;
        if (obj.has("assigned_to") && !obj.get("assigned_to").isJsonNull()) {
            assigneeId = obj.get("assigned_to").getAsInt();
        }
        return new Task(
                obj.get("id").getAsInt(),
                obj.get("project_id").getAsInt(),
                assigneeId,
                obj.get("title").getAsString(),
                obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString() : "",
                obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "TODO",
                obj.has("priority") && !obj.get("priority").isJsonNull() ? obj.get("priority").getAsString() : "MEDIUM",
                dueDate,
                createdAt
        );
    }

    private List<Task> jsonArrayToTasks(JsonElement el) {
        List<Task> tasks = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                tasks.add(jsonToTask(item.getAsJsonObject()));
            }
        }
        return tasks;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(Task t) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("project_id", t.getProjectId());
            if (t.getAssigneeId() > 0) body.put("assigned_to", t.getAssigneeId());
            body.put("title", t.getTitle());
            body.put("description", t.getDescription());
            body.put("status", t.getStatus());
            body.put("priority", t.getPriority());
            body.put("due_date", t.getDueDate() != null ? t.getDueDate().toString() : null);
            JsonElement resp = ApiClient.post("/tasks", body);
            if (resp != null && resp.isJsonObject()) {
                t.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String sql = "INSERT INTO tasks (project_id, assigned_to, title, description, status, priority, due_date) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, t.getProjectId());
        if (t.getAssigneeId() > 0) ps.setInt(2, t.getAssigneeId()); else ps.setNull(2, Types.INTEGER);
        ps.setString(3, t.getTitle());
        ps.setString(4, t.getDescription());
        ps.setString(5, t.getStatus());
        ps.setString(6, t.getPriority());
        ps.setDate(7, t.getDueDate());
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) t.setId(keys.getInt(1));
        keys.close();
        ps.close();
    }

    @Override
    public void modifier(Task t) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("project_id", t.getProjectId());
            body.put("assigned_to", t.getAssigneeId() > 0 ? t.getAssigneeId() : null);
            body.put("title", t.getTitle());
            body.put("description", t.getDescription());
            body.put("status", t.getStatus());
            body.put("priority", t.getPriority());
            body.put("due_date", t.getDueDate() != null ? t.getDueDate().toString() : null);
            ApiClient.put("/tasks/" + t.getId(), body);
            return;
        }
        String sql = "UPDATE tasks SET project_id=?, assigned_to=?, title=?, description=?, status=?, priority=?, due_date=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, t.getProjectId());
        if (t.getAssigneeId() > 0) ps.setInt(2, t.getAssigneeId()); else ps.setNull(2, Types.INTEGER);
        ps.setString(3, t.getTitle());
        ps.setString(4, t.getDescription());
        ps.setString(5, t.getStatus());
        ps.setString(6, t.getPriority());
        ps.setDate(7, t.getDueDate());
        ps.setInt(8, t.getId());
        ps.executeUpdate();
        ps.close();
    }

    /** Quick status update for drag-and-drop Kanban. */
    public void updateStatus(int taskId, String newStatus) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", newStatus);
            ApiClient.put("/tasks/" + taskId, body);
            return;
        }
        PreparedStatement ps = connection.prepareStatement("UPDATE tasks SET status = ? WHERE id = ?");
        ps.setString(1, newStatus);
        ps.setInt(2, taskId);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/tasks/" + id);
            return;
        }
        PreparedStatement ps = connection.prepareStatement("DELETE FROM tasks WHERE id = ?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Task> recuperer() throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/tasks");
            return jsonArrayToTasks(el);
        }
        List<Task> tasks = new ArrayList<>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM tasks ORDER BY created_at DESC");
        while (rs.next()) tasks.add(rowToTask(rs));
        rs.close();
        st.close();
        return tasks;
    }

    /** Tasks for a specific project. */
    public List<Task> getByProject(int projectId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/tasks/project/" + projectId);
            return jsonArrayToTasks(el);
        }
        List<Task> tasks = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM tasks WHERE project_id = ? ORDER BY FIELD(priority,'HIGH','MEDIUM','LOW'), created_at DESC");
        ps.setInt(1, projectId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) tasks.add(rowToTask(rs));
        rs.close();
        ps.close();
        return tasks;
    }

    /** Tasks assigned to a specific user. */
    public List<Task> getByAssignee(int userId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/tasks/assignee/" + userId);
            return jsonArrayToTasks(el);
        }
        List<Task> tasks = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM tasks WHERE assigned_to = ? ORDER BY created_at DESC");
        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) tasks.add(rowToTask(rs));
        rs.close();
        ps.close();
        return tasks;
    }

    private Task rowToTask(ResultSet rs) throws SQLException {
        return new Task(
                rs.getInt("id"),
                rs.getInt("project_id"),
                rs.getInt("assigned_to"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("priority"),
                rs.getDate("due_date"),
                rs.getTimestamp("created_at")
        );
    }
}
