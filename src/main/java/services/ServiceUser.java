package services;

import com.google.gson.*;
import entities.User;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceUser implements IService<User> {

    private Connection connection;
    private final boolean useApi;

    public ServiceUser() {
        useApi = AppConfig.isApiMode();
        if (!useApi) {
            connection = MyDatabase.getInstance().getConnection();
        }
    }

    // ==================== JSON → Entity helpers ====================

    private User jsonToUser(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        User user = new User(
                obj.get("id").getAsInt(),
                obj.get("email").getAsString(),
                obj.has("password") && !obj.get("password").isJsonNull() ? obj.get("password").getAsString() : "",
                obj.get("first_name").getAsString(),
                obj.get("last_name").getAsString(),
                obj.get("role").getAsString(),
                createdAt,
                obj.has("avatar_path") && !obj.get("avatar_path").isJsonNull() ? obj.get("avatar_path").getAsString() : null,
                obj.has("face_encoding") && !obj.get("face_encoding").isJsonNull() ? obj.get("face_encoding").getAsString() : null
        );
        if (obj.has("is_online") && !obj.get("is_online").isJsonNull()) {
            user.setOnline(obj.get("is_online").getAsBoolean());
        }
        if (obj.has("is_verified") && !obj.get("is_verified").isJsonNull()) {
            user.setVerified(obj.get("is_verified").getAsBoolean());
        }
        if (obj.has("is_active") && !obj.get("is_active").isJsonNull()) {
            user.setActive(obj.get("is_active").getAsBoolean());
        }
        // HR fields
        if (obj.has("department_id") && !obj.get("department_id").isJsonNull()) {
            user.setDepartmentId(obj.get("department_id").getAsInt());
        }
        if (obj.has("hourly_rate") && !obj.get("hourly_rate").isJsonNull()) {
            user.setHourlyRate(obj.get("hourly_rate").getAsDouble());
        }
        if (obj.has("monthly_salary") && !obj.get("monthly_salary").isJsonNull()) {
            user.setMonthlySalary(obj.get("monthly_salary").getAsDouble());
        }
        return user;
    }

    /** Populate HR fields from JDBC ResultSet. */
    private void setHrFields(User user, ResultSet rs) {
        try {
            int deptId = rs.getInt("department_id");
            if (!rs.wasNull()) user.setDepartmentId(deptId);
            user.setHourlyRate(rs.getDouble("hourly_rate"));
            user.setMonthlySalary(rs.getDouble("monthly_salary"));
        } catch (SQLException ignored) {
            // columns might not exist in some queries
        }
    }

    private List<User> jsonArrayToUsers(JsonElement el) {
        List<User> users = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                users.add(jsonToUser(item.getAsJsonObject()));
            }
        }
        return users;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(User user) throws SQLException {
        String hashed = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("email", user.getEmail());
            body.put("password", hashed);
            body.put("first_name", user.getFirstName());
            body.put("last_name", user.getLastName());
            body.put("role", user.getRole());
            JsonElement resp = ApiClient.post("/auth/signup", body);
            if (resp != null) {
                System.out.println("\u2705 User added via API: " + user.getEmail());
            }
            return;
        }
        String req = "INSERT INTO users (email, password, first_name, last_name, role) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, user.getEmail());
        ps.setString(2, hashed);
        ps.setString(3, user.getFirstName());
        ps.setString(4, user.getLastName());
        ps.setString(5, user.getRole());
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ User added: " + user.getEmail());
    }

    @Override
    public void modifier(User user) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("email", user.getEmail());
            body.put("password", user.getPassword());
            body.put("first_name", user.getFirstName());
            body.put("last_name", user.getLastName());
            body.put("role", user.getRole());
            body.put("avatar_path", user.getAvatarPath());
            ApiClient.put("/users/" + user.getId(), body);
            System.out.println("✅ User updated via API: " + user.getEmail());
            return;
        }
        String req = "UPDATE users SET email=?, password=?, first_name=?, last_name=?, role=?, avatar_path=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, user.getEmail());
        ps.setString(2, user.getPassword());
        ps.setString(3, user.getFirstName());
        ps.setString(4, user.getLastName());
        ps.setString(5, user.getRole());
        ps.setString(6, user.getAvatarPath());
        ps.setInt(7, user.getId());
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ User updated: " + user.getEmail());
    }

    public void updateAvatar(int userId, String avatarPath) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("avatar_path", avatarPath);
            ApiClient.put("/users/" + userId + "/avatar", body);
            System.out.println("✅ Avatar updated via API for user id=" + userId);
            return;
        }
        String req = "UPDATE users SET avatar_path=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, avatarPath);
        ps.setInt(2, userId);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Avatar updated for user id=" + userId);
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/users/" + id);
            System.out.println("✅ User deleted via API: id=" + id);
            return;
        }
        String req = "DELETE FROM users WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ User deleted: id=" + id);
    }

    @Override
    public List<User> recuperer() throws SQLException {
        if (useApi) {
            return jsonArrayToUsers(ApiClient.get("/users"));
        }
        List<User> users = new ArrayList<>();
        String req = "SELECT * FROM users";
        PreparedStatement ps = connection.prepareStatement(req);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            User user = new User(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("password"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("role"),
                    rs.getTimestamp("created_at"),
                    rs.getString("avatar_path"),
                    rs.getString("face_encoding"));
            setHrFields(user, rs);
            users.add(user);
        }
        rs.close();
        ps.close();
        return users;
    }

    public User login(String email, String password) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("email", email);
            body.put("password", password);
            JsonElement resp = ApiClient.post("/auth/login", body);
            if (resp != null && resp.isJsonObject()) {
                return jsonToUser(resp.getAsJsonObject());
            }
            return null;
        }
        String req = "SELECT * FROM users WHERE email=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, email);
        ResultSet rs = ps.executeQuery();
        User user = null;
        if (rs.next()) {
            String storedHash = rs.getString("password");
            boolean match = storedHash.startsWith("$2")
                    ? BCrypt.checkpw(password, storedHash)
                    : password.equals(storedHash); // fallback for plain
            if (match) {
                user = new User(
                        rs.getInt("id"),
                        rs.getString("email"),
                        storedHash,
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("role"),
                        rs.getTimestamp("created_at"),
                        rs.getString("avatar_path"),
                        rs.getString("face_encoding"));
                setHrFields(user, rs);
            }
        }
        rs.close();
        ps.close();
        return user;
    }

    public boolean emailExists(String email) throws SQLException {
        if (useApi) {
            JsonElement resp = ApiClient.get("/users/email-exists/" + email);
            if (resp != null && resp.isJsonObject()) {
                return resp.getAsJsonObject().get("exists").getAsBoolean();
            }
            return false;
        }
        String req = "SELECT COUNT(*) FROM users WHERE email=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, email);
        ResultSet rs = ps.executeQuery();
        boolean exists = false;
        if (rs.next()) {
            exists = rs.getInt(1) > 0;
        }
        rs.close();
        ps.close();
        return exists;
    }

    public void updateRole(int userId, String newRole) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("role", newRole);
            ApiClient.put("/users/" + userId + "/role", body);
            System.out.println("✅ Role updated via API for user id=" + userId + " → " + newRole);
            return;
        }
        String req = "UPDATE users SET role=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, newRole);
        ps.setInt(2, userId);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ Role updated for user id=" + userId + " → " + newRole);
    }

    /**
     * Toggle user active status (freeze / unfreeze).
     */
    public void toggleActive(int userId, boolean active) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("is_active", active);
            ApiClient.put("/users/" + userId + "/active", body);
            System.out.println("✅ User " + (active ? "activated" : "frozen") + " via API: id=" + userId);
            return;
        }
        String req = "UPDATE users SET is_active=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setBoolean(1, active);
        ps.setInt(2, userId);
        ps.executeUpdate();
        ps.close();
        System.out.println("✅ User " + (active ? "activated" : "frozen") + ": id=" + userId);
    }

    /**
     * Set user online/offline status.
     */
    public void setOnlineStatus(int userId, boolean online) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("is_online", online);
            ApiClient.put("/users/" + userId + "/online", body);
            return;
        }
        String req = "UPDATE users SET is_online=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setBoolean(1, online);
        ps.setInt(2, userId);
        ps.executeUpdate();
        ps.close();
    }

    public List<User> getByRole(String role) throws SQLException {
        if (useApi) {
            return jsonArrayToUsers(ApiClient.get("/users/role/" + role));
        }
        List<User> users = new ArrayList<>();
        String req = "SELECT * FROM users WHERE role=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, role);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            User user = new User(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("password"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("role"),
                    rs.getTimestamp("created_at"),
                    rs.getString("avatar_path"),
                    rs.getString("face_encoding"));
            setHrFields(user, rs);
            users.add(user);
        }
        rs.close();
        ps.close();
        return users;
    }

    public void updateFaceEncoding(int userId, String faceEncoding) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("face_encoding", faceEncoding);
            ApiClient.put("/users/" + userId + "/face-encoding", body);
            System.out.println("✅ Face encoding updated via API for user id=" + userId);
            return;
        }
        String req = "UPDATE users SET face_encoding=? WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, faceEncoding);
        ps.setInt(2, userId);
        ps.executeUpdate();
        ps.close();
        System.out.println("\u2705 Face encoding updated for user id=" + userId);
    }

    public User getById(int userId) throws SQLException {
        if (useApi) {
            JsonElement resp = ApiClient.get("/users/" + userId);
            if (resp != null && resp.isJsonObject()) {
                return jsonToUser(resp.getAsJsonObject());
            }
            return null;
        }
        String req = "SELECT * FROM users WHERE id=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
        User user = null;
        if (rs.next()) {
            user = new User(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("password"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("role"),
                    rs.getTimestamp("created_at"),
                    rs.getString("avatar_path"),
                    rs.getString("face_encoding"));
        }
        rs.close();
        ps.close();
        return user;
    }

    // ==================== Email Verification ====================

    /**
     * Request a verification token for the given email.
     * Server generates + stores the token, returns it so the client can send the email.
     */
    public JsonObject requestVerification(String email) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("email", email);
            JsonElement resp = ApiClient.post("/auth/send-verification", body);
            if (resp != null && resp.isJsonObject()) {
                return resp.getAsJsonObject();
            }
            return null;
        }
        // JDBC mode: no verification needed, users are auto-verified
        return null;
    }

    // ==================== OTP Password Reset ====================

    /**
     * Request an OTP for the given email.
     * In API mode: calls server which generates + stores OTP, returns it.
     * In JDBC mode: generates OTP locally (no server store — simpler flow).
     *
     * @return JsonObject with "otp" and "first_name", or null on error
     */
    public JsonObject requestOtp(String email) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("email", email);
            JsonElement resp = ApiClient.post("/auth/forgot-password", body);
            if (resp != null && resp.isJsonObject()) {
                return resp.getAsJsonObject();
            }
            return null;
        }
        // JDBC mode: check email exists, generate OTP locally
        String req = "SELECT first_name FROM users WHERE email=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, email);
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            ps.close();
            return null;
        }
        String firstName = rs.getString("first_name");
        rs.close();
        ps.close();

        String otp = String.valueOf((int) (Math.random() * 900000) + 100000);
        JsonObject result = new JsonObject();
        result.addProperty("otp", otp);
        result.addProperty("first_name", firstName);
        return result;
    }

    /**
     * Verify an OTP code (API mode only — server holds the store).
     * Returns true if valid.
     */
    public boolean verifyOtp(String email, String otp) {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("email", email);
            body.put("otp", otp);
            JsonElement resp = ApiClient.post("/auth/verify-otp", body);
            return resp != null && resp.isJsonObject()
                    && resp.getAsJsonObject().has("valid")
                    && resp.getAsJsonObject().get("valid").getAsBoolean();
        }
        // In JDBC mode, OTP is verified locally in the controller
        return true;
    }

    /**
     * Reset password after OTP verification.
     */
    public boolean resetPassword(String email, String otp, String newPassword) throws SQLException {
        String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("email", email);
            body.put("otp", otp);
            body.put("new_password", hashed);
            JsonElement resp = ApiClient.post("/auth/reset-password", body);
            return resp != null && resp.isJsonObject();
        }
        // JDBC mode: update password
        String req = "UPDATE users SET password=? WHERE email=?";
        PreparedStatement ps = connection.prepareStatement(req);
        ps.setString(1, hashed);
        ps.setString(2, email);
        int rows = ps.executeUpdate();
        ps.close();
        return rows > 0;
    }
}
