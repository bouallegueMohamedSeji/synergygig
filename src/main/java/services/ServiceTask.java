package services;

import com.google.gson.*;
import entities.Task;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceTask implements IService<Task> {

    private final boolean useApi;

    public ServiceTask() {
        useApi = AppConfig.isApiMode();
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
        Task t = new Task(
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
        // Submission fields
        if (obj.has("submission_text") && !obj.get("submission_text").isJsonNull())
            t.setSubmissionText(obj.get("submission_text").getAsString());
        if (obj.has("submission_file") && !obj.get("submission_file").isJsonNull())
            t.setSubmissionFile(obj.get("submission_file").getAsString());
        // Review fields
        if (obj.has("review_status") && !obj.get("review_status").isJsonNull())
            t.setReviewStatus(obj.get("review_status").getAsString());
        if (obj.has("review_rating") && !obj.get("review_rating").isJsonNull())
            t.setReviewRating(obj.get("review_rating").getAsInt());
        if (obj.has("review_feedback") && !obj.get("review_feedback").isJsonNull())
            t.setReviewFeedback(obj.get("review_feedback").getAsString());
        if (obj.has("review_date") && !obj.get("review_date").isJsonNull()) {
            try { t.setReviewDate(Timestamp.valueOf(obj.get("review_date").getAsString().replace("T", " "))); } catch (Exception ignored) {}
        }
        return t;
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
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getProjectId());
            if (t.getAssigneeId() > 0) ps.setInt(2, t.getAssigneeId()); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, t.getTitle());
            ps.setString(4, t.getDescription());
            ps.setString(5, t.getStatus());
            ps.setString(6, t.getPriority());
            ps.setDate(7, t.getDueDate());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) t.setId(keys.getInt(1));
            }
        }
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
            if (t.getSubmissionText() != null) body.put("submission_text", t.getSubmissionText());
            if (t.getSubmissionFile() != null) body.put("submission_file", t.getSubmissionFile());
            ApiClient.put("/tasks/" + t.getId(), body);
            return;
        }
        String sql = "UPDATE tasks SET project_id=?, assigned_to=?, title=?, description=?, status=?, priority=?, due_date=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, t.getProjectId());
            if (t.getAssigneeId() > 0) ps.setInt(2, t.getAssigneeId()); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, t.getTitle());
            ps.setString(4, t.getDescription());
            ps.setString(5, t.getStatus());
            ps.setString(6, t.getPriority());
            ps.setDate(7, t.getDueDate());
            ps.setInt(8, t.getId());
            ps.executeUpdate();
        }
    }

    /** Quick status update for drag-and-drop Kanban. */
    public void updateStatus(int taskId, String newStatus) throws SQLException {
        if (useApi) {
            // GET the full task first, then PUT with updated status (backend expects all fields)
            JsonElement el = ApiClient.get("/tasks/" + taskId);
            if (el == null) {
                System.err.println("⚠ updateStatus: GET /tasks/" + taskId + " returned null, trying PATCH-style PUT");
                // Fallback: send just the status field
                Map<String, Object> body = new HashMap<>();
                body.put("status", newStatus);
                ApiClient.put("/tasks/" + taskId, body);
                return;
            }
            JsonObject t = el.isJsonObject() ? el.getAsJsonObject() : null;
            if (t == null) {
                System.err.println("⚠ updateStatus: response not a JSON object for task " + taskId);
                return;
            }
            Map<String, Object> body = new HashMap<>();
            // Copy all existing fields
            for (var entry : t.entrySet()) {
                String key = entry.getKey();
                if (key.equals("id") || key.equals("created_at") || key.equals("updated_at")) continue;
                JsonElement v = entry.getValue();
                if (v == null || v.isJsonNull()) {
                    body.put(key, null);
                } else if (v.isJsonPrimitive()) {
                    JsonPrimitive p = v.getAsJsonPrimitive();
                    if (p.isNumber()) body.put(key, p.getAsNumber());
                    else if (p.isBoolean()) body.put(key, p.getAsBoolean());
                    else body.put(key, p.getAsString());
                }
            }
            body.put("status", newStatus);
            ApiClient.put("/tasks/" + taskId, body);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE tasks SET status = ? WHERE id = ?")) {
            ps.setString(1, newStatus);
            ps.setInt(2, taskId);
            ps.executeUpdate();
        }
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/tasks/" + id);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public List<Task> recuperer() throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/tasks");
            return jsonArrayToTasks(el);
        }
        List<Task> tasks = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM tasks ORDER BY created_at DESC")) {
            while (rs.next()) tasks.add(rowToTask(rs));
        }
        return tasks;
    }

    /** Tasks for a specific project. */
    public List<Task> getByProject(int projectId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/tasks/project/" + projectId);
            return jsonArrayToTasks(el);
        }
        List<Task> tasks = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM tasks WHERE project_id = ? ORDER BY FIELD(priority,'HIGH','MEDIUM','LOW'), created_at DESC")) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tasks.add(rowToTask(rs));
            }
        }
        return tasks;
    }

    /** Tasks assigned to a specific user. */
    public List<Task> getByAssignee(int userId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/tasks/assignee/" + userId);
            return jsonArrayToTasks(el);
        }
        List<Task> tasks = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM tasks WHERE assigned_to = ? ORDER BY created_at DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tasks.add(rowToTask(rs));
            }
        }
        return tasks;
    }

    private Task rowToTask(ResultSet rs) throws SQLException {
        Task t = new Task(
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
        t.setSubmissionText(rs.getString("submission_text"));
        t.setSubmissionFile(rs.getString("submission_file"));
        t.setReviewStatus(rs.getString("review_status"));
        t.setReviewRating(rs.getObject("review_rating") != null ? rs.getInt("review_rating") : null);
        t.setReviewFeedback(rs.getString("review_feedback"));
        t.setReviewDate(rs.getTimestamp("review_date"));
        return t;
    }

    /** Submit a task for review — sends submission text via API. */
    public void submitForReview(int taskId, String submissionText) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", "IN_REVIEW");
            body.put("submission_text", submissionText);
            ApiClient.put("/tasks/" + taskId, body);
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE tasks SET status='IN_REVIEW', submission_text=? WHERE id=?")) {
            ps.setString(1, submissionText);
            ps.setInt(2, taskId);
            ps.executeUpdate();
        }
    }

    /** Get active task count for a user across all projects. */
    public int getActiveTaskCount(int userId) throws SQLException {
        if (useApi) {
            try {
                JsonElement el = ApiClient.get("/tasks/workload/" + userId);
                if (el != null && el.isJsonObject()) {
                    return el.getAsJsonObject().get("active_count").getAsInt();
                }
            } catch (Exception e) {
                System.err.println("Workload API error: " + e.getMessage());
            }
            return 0;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM tasks WHERE assigned_to=? AND status IN ('TODO','IN_PROGRESS','IN_REVIEW')")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
