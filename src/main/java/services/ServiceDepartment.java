package services;

import com.google.gson.*;
import entities.Department;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServiceDepartment implements IService<Department> {

    private Connection connection;
    private final boolean useApi;

    public ServiceDepartment() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
    }

    // ==================== JSON helpers ====================

    private Department jsonToDepartment(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        Integer managerId = null;
        if (obj.has("manager_id") && !obj.get("manager_id").isJsonNull()) {
            managerId = obj.get("manager_id").getAsInt();
        }
        return new Department(
                obj.get("id").getAsInt(),
                obj.get("name").getAsString(),
                obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString() : "",
                managerId,
                obj.has("allocated_budget") && !obj.get("allocated_budget").isJsonNull() ? obj.get("allocated_budget").getAsDouble() : 0.0,
                createdAt
        );
    }

    private List<Department> jsonArrayToDepartments(JsonElement el) {
        List<Department> list = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                list.add(jsonToDepartment(item.getAsJsonObject()));
            }
        }
        return list;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(Department d) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", d.getName());
            body.put("description", d.getDescription());
            body.put("manager_id", d.getManagerId());
            body.put("allocated_budget", d.getAllocatedBudget());
            JsonElement resp = ApiClient.post("/departments", body);
            if (resp != null && resp.isJsonObject()) {
                d.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String sql = "INSERT INTO departments (name, description, manager_id, allocated_budget) VALUES (?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, d.getName());
        ps.setString(2, d.getDescription());
        if (d.getManagerId() != null) ps.setInt(3, d.getManagerId());
        else ps.setNull(3, Types.INTEGER);
        ps.setDouble(4, d.getAllocatedBudget());
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) d.setId(keys.getInt(1));
        keys.close();
        ps.close();
    }

    @Override
    public void modifier(Department d) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", d.getName());
            body.put("description", d.getDescription());
            body.put("manager_id", d.getManagerId());
            body.put("allocated_budget", d.getAllocatedBudget());
            ApiClient.put("/departments/" + d.getId(), body);
            return;
        }
        String sql = "UPDATE departments SET name=?, description=?, manager_id=?, allocated_budget=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, d.getName());
        ps.setString(2, d.getDescription());
        if (d.getManagerId() != null) ps.setInt(3, d.getManagerId());
        else ps.setNull(3, Types.INTEGER);
        ps.setDouble(4, d.getAllocatedBudget());
        ps.setInt(5, d.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/departments/" + id);
            return;
        }
        // Clear department_id from users first
        PreparedStatement clearUsers = connection.prepareStatement("UPDATE users SET department_id=NULL WHERE department_id=?");
        clearUsers.setInt(1, id);
        clearUsers.executeUpdate();
        clearUsers.close();

        PreparedStatement ps = connection.prepareStatement("DELETE FROM departments WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Department> recuperer() throws SQLException {
        if (useApi) {
            return jsonArrayToDepartments(ApiClient.get("/departments"));
        }
        List<Department> list = new ArrayList<>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM departments ORDER BY id");
        while (rs.next()) {
            list.add(rowToDepartment(rs));
        }
        rs.close();
        st.close();
        return list;
    }

    public Department getById(int id) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/departments/" + id);
            if (el != null && el.isJsonObject()) {
                return jsonToDepartment(el.getAsJsonObject());
            }
            return null;
        }
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM departments WHERE id=?");
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        Department d = null;
        if (rs.next()) d = rowToDepartment(rs);
        rs.close();
        ps.close();
        return d;
    }

    private Department rowToDepartment(ResultSet rs) throws SQLException {
        Integer managerId = rs.getInt("manager_id");
        if (rs.wasNull()) managerId = null;
        return new Department(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description"),
                managerId,
                rs.getDouble("allocated_budget"),
                rs.getTimestamp("created_at")
        );
    }
}
