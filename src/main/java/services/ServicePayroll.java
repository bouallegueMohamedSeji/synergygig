package services;

import com.google.gson.*;
import entities.Payroll;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

public class ServicePayroll implements IService<Payroll> {

    private Connection connection;
    private final boolean useApi;

    public ServicePayroll() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
    }

    // ==================== JSON helpers ====================

    private Payroll jsonToPayroll(JsonObject obj) {
        Timestamp generatedAt = null;
        if (obj.has("generated_at") && !obj.get("generated_at").isJsonNull()) {
            generatedAt = Timestamp.valueOf(obj.get("generated_at").getAsString().replace("T", " "));
        }
        java.sql.Date month = null;
        if (obj.has("month") && !obj.get("month").isJsonNull()) {
            month = java.sql.Date.valueOf(obj.get("month").getAsString());
        }
        Integer year = null;
        if (obj.has("year") && !obj.get("year").isJsonNull()) {
            year = obj.get("year").getAsInt();
        }
        return new Payroll(
                obj.get("id").getAsInt(),
                obj.get("user_id").getAsInt(),
                month,
                year,
                obj.has("amount") && !obj.get("amount").isJsonNull() ? obj.get("amount").getAsDouble() : 0.0,
                obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "PENDING",
                obj.has("base_salary") && !obj.get("base_salary").isJsonNull() ? obj.get("base_salary").getAsDouble() : 0.0,
                obj.has("bonus") && !obj.get("bonus").isJsonNull() ? obj.get("bonus").getAsDouble() : 0.0,
                obj.has("deductions") && !obj.get("deductions").isJsonNull() ? obj.get("deductions").getAsDouble() : 0.0,
                obj.has("net_salary") && !obj.get("net_salary").isJsonNull() ? obj.get("net_salary").getAsDouble() : 0.0,
                obj.has("total_hours_worked") && !obj.get("total_hours_worked").isJsonNull() ? obj.get("total_hours_worked").getAsDouble() : 0.0,
                obj.has("hourly_rate") && !obj.get("hourly_rate").isJsonNull() ? obj.get("hourly_rate").getAsDouble() : 0.0,
                generatedAt
        );
    }

    private List<Payroll> jsonArrayToList(JsonElement el) {
        List<Payroll> list = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                list.add(jsonToPayroll(item.getAsJsonObject()));
            }
        }
        return list;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(Payroll p) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", p.getUserId());
            body.put("month", p.getMonth() != null ? p.getMonth().toString() : null);
            body.put("year", p.getYear());
            body.put("amount", p.getAmount());
            body.put("base_salary", p.getBaseSalary());
            body.put("bonus", p.getBonus());
            body.put("deductions", p.getDeductions());
            body.put("net_salary", p.getNetSalary());
            body.put("total_hours_worked", p.getTotalHoursWorked());
            body.put("hourly_rate", p.getHourlyRate());
            body.put("status", p.getStatus());
            JsonElement resp = ApiClient.post("/payrolls", body);
            if (resp != null && resp.isJsonObject()) {
                p.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String sql = "INSERT INTO payrolls (user_id, month, year, amount, base_salary, bonus, deductions, net_salary, total_hours_worked, hourly_rate, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, p.getUserId());
        ps.setDate(2, p.getMonth());
        if (p.getYear() != null) ps.setInt(3, p.getYear());
        else ps.setNull(3, Types.INTEGER);
        ps.setDouble(4, p.getAmount());
        ps.setDouble(5, p.getBaseSalary());
        ps.setDouble(6, p.getBonus());
        ps.setDouble(7, p.getDeductions());
        ps.setDouble(8, p.getNetSalary());
        ps.setDouble(9, p.getTotalHoursWorked());
        ps.setDouble(10, p.getHourlyRate());
        ps.setString(11, p.getStatus());
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) p.setId(keys.getInt(1));
        keys.close();
        ps.close();
    }

    @Override
    public void modifier(Payroll p) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("user_id", p.getUserId());
            body.put("month", p.getMonth() != null ? p.getMonth().toString() : null);
            body.put("year", p.getYear());
            body.put("amount", p.getAmount());
            body.put("base_salary", p.getBaseSalary());
            body.put("bonus", p.getBonus());
            body.put("deductions", p.getDeductions());
            body.put("net_salary", p.getNetSalary());
            body.put("total_hours_worked", p.getTotalHoursWorked());
            body.put("hourly_rate", p.getHourlyRate());
            body.put("status", p.getStatus());
            ApiClient.put("/payrolls/" + p.getId(), body);
            return;
        }
        String sql = "UPDATE payrolls SET user_id=?, month=?, year=?, amount=?, base_salary=?, bonus=?, deductions=?, " +
                "net_salary=?, total_hours_worked=?, hourly_rate=?, status=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, p.getUserId());
        ps.setDate(2, p.getMonth());
        if (p.getYear() != null) ps.setInt(3, p.getYear());
        else ps.setNull(3, Types.INTEGER);
        ps.setDouble(4, p.getAmount());
        ps.setDouble(5, p.getBaseSalary());
        ps.setDouble(6, p.getBonus());
        ps.setDouble(7, p.getDeductions());
        ps.setDouble(8, p.getNetSalary());
        ps.setDouble(9, p.getTotalHoursWorked());
        ps.setDouble(10, p.getHourlyRate());
        ps.setString(11, p.getStatus());
        ps.setInt(12, p.getId());
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/payrolls/" + id);
            return;
        }
        PreparedStatement ps = connection.prepareStatement("DELETE FROM payrolls WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    @Override
    public List<Payroll> recuperer() throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/payrolls"));
        }
        List<Payroll> list = new ArrayList<>();
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM payrolls ORDER BY month DESC");
        while (rs.next()) list.add(rowToPayroll(rs));
        rs.close();
        st.close();
        return list;
    }

    public List<Payroll> getByUser(int userId) throws SQLException {
        if (useApi) {
            return jsonArrayToList(ApiClient.get("/payrolls/user/" + userId));
        }
        List<Payroll> list = new ArrayList<>();
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM payrolls WHERE user_id=? ORDER BY month DESC");
        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) list.add(rowToPayroll(rs));
        rs.close();
        ps.close();
        return list;
    }

    private Payroll rowToPayroll(ResultSet rs) throws SQLException {
        Integer year = rs.getInt("year");
        if (rs.wasNull()) year = null;
        return new Payroll(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getDate("month"),
                year,
                rs.getDouble("amount"),
                rs.getString("status"),
                rs.getDouble("base_salary"),
                rs.getDouble("bonus"),
                rs.getDouble("deductions"),
                rs.getDouble("net_salary"),
                rs.getDouble("total_hours_worked"),
                rs.getDouble("hourly_rate"),
                rs.getTimestamp("generated_at")
        );
    }
}
