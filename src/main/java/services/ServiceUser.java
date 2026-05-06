package services;

import com.google.gson.*;
import entities.User;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;
import utils.InMemoryCache;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceUser implements IService<User> {

    private final boolean useApi;

    /** Redis cache key for the full user list. */
    private static final String CACHE_KEY = "users:all";
    /** Cache TTL in seconds (2 minutes). */
    private static final int CACHE_TTL = 120;

    public ServiceUser() {
        useApi = AppConfig.isApiMode();
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
        // Social profile fields
        if (obj.has("bio") && !obj.get("bio").isJsonNull()) {
            user.setBio(obj.get("bio").getAsString());
        }
        if (obj.has("cover_base64") && !obj.get("cover_base64").isJsonNull()) {
            user.setCoverBase64(obj.get("cover_base64").getAsString());
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
        try {
            user.setBio(rs.getString("bio"));
            user.setCoverBase64(rs.getString("cover_base64"));
        } catch (SQLException ignored) {}
        try {
            user.setCvPath(rs.getString("cv_path"));
            user.setCvOriginalName(rs.getString("cv_original_name"));
            user.setCvUploadedAt(rs.getTimestamp("cv_uploaded_at"));
            user.setCvSkillsText(rs.getString("cv_skills_text"));
        } catch (SQLException ignored) {}
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
        String req = "INSERT INTO user (email, password, first_name, last_name, role, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, user.getEmail());
            ps.setString(2, hashed);
            ps.setString(3, user.getFirstName());
            ps.setString(4, user.getLastName());
            ps.setString(5, user.getRole());
            ps.setTimestamp(6, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("users:");
        System.out.println("✅ User added: " + user.getEmail());
    }

    @Override
    public void modifier(User user) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("email", user.getEmail());
            // Only send password if it looks like a new plaintext value (not already hashed)
            String pw = user.getPassword();
            if (pw != null && !pw.isEmpty() && !pw.startsWith("$2")) {
                // Hash plaintext password before sending
                pw = BCrypt.hashpw(pw, BCrypt.gensalt());
                user.setPassword(pw);
            }
            body.put("password", pw);
            body.put("first_name", user.getFirstName());
            body.put("last_name", user.getLastName());
            body.put("role", user.getRole());
            body.put("avatar_path", user.getAvatarPath());
            if (user.getBio() != null) body.put("bio", user.getBio());
            if (user.getCoverBase64() != null) body.put("cover_base64", user.getCoverBase64());
            ApiClient.put("/users/" + user.getId(), body);
            System.out.println("✅ User updated via API: " + user.getEmail());
            return;
        }
        // JDBC: hash password if it's plaintext
        String pw = user.getPassword();
        if (pw != null && !pw.isEmpty() && !pw.startsWith("$2")) {
            pw = BCrypt.hashpw(pw, BCrypt.gensalt());
            user.setPassword(pw);
        }
        String req = "UPDATE user SET email=?, password=?, first_name=?, last_name=?, role=?, avatar_path=?, bio=?, cover_base64=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, user.getEmail());
            ps.setString(2, pw);
            ps.setString(3, user.getFirstName());
            ps.setString(4, user.getLastName());
            ps.setString(5, user.getRole());
            ps.setString(6, user.getAvatarPath());
            ps.setString(7, user.getBio());
            ps.setString(8, user.getCoverBase64());
            ps.setInt(9, user.getId());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("users:");
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
        String req = "UPDATE user SET avatar_path=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, avatarPath);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("users:");
        System.out.println("✅ Avatar updated for user id=" + userId);
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/users/" + id);
            System.out.println("✅ User deleted via API: id=" + id);
            return;
        }
        String req = "DELETE FROM user WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("users:");
        System.out.println("✅ User deleted: id=" + id);
    }

    @Override
    public List<User> recuperer() throws SQLException {
        if (useApi) {
            return InMemoryCache.getOrLoad(CACHE_KEY, CACHE_TTL,
                    () -> jsonArrayToUsers(ApiClient.get("/users")));
        }
        return InMemoryCache.getOrLoadChecked(CACHE_KEY, CACHE_TTL,
                () -> recupererFromDb());
    }

    /** Direct DB fetch (bypasses cache). */
    private List<User> recupererFromDb() throws SQLException {
        List<User> users = new ArrayList<>();
        String req = "SELECT * FROM user";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req);
             ResultSet rs = ps.executeQuery()) {
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
        }
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
        String req = "SELECT * FROM user WHERE email=?";
        User user = null;
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password");
                    boolean match;
                    if (storedHash.startsWith("$2")) {
                        match = BCrypt.checkpw(password, storedHash);
                    } else {
                        // Legacy plaintext — verify then auto-upgrade to BCrypt
                        match = password.equals(storedHash);
                        if (match) {
                            String upgraded = BCrypt.hashpw(password, BCrypt.gensalt());
                            try (PreparedStatement up = conn.prepareStatement(
                                    "UPDATE user SET password=? WHERE email=?")) {
                                up.setString(1, upgraded);
                                up.setString(2, email);
                                up.executeUpdate();
                            }
                            storedHash = upgraded;
                        }
                    }
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
            }
        }
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
        String req = "SELECT COUNT(*) FROM user WHERE email=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public void updateRole(int userId, String newRole) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("role", newRole);
            ApiClient.put("/users/" + userId + "/role", body);
            InMemoryCache.evict(CACHE_KEY);
            System.out.println("✅ Role updated via API for user id=" + userId + " → " + newRole);
            return;
        }
        String req = "UPDATE user SET role=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, newRole);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        InMemoryCache.evict(CACHE_KEY);
        System.out.println("✅ Role updated for user id=" + userId + " → " + newRole);
    }

    /**
     * Update a user's department assignment. Pass null to unassign.
     * In API mode, fetches the full user first to send a complete PUT body
     * (the server's PUT /users/{id} requires all fields).
     */
    public void updateDepartmentId(int userId, Integer departmentId) throws SQLException {
        if (useApi) {
            // Fetch the full user to build a complete PUT body (API requires all fields)
            JsonElement resp = ApiClient.get("/users/" + userId);
            if (resp == null || !resp.isJsonObject()) {
                throw new SQLException("Failed to fetch user " + userId + " from API for department update.");
            }
            JsonObject userObj = resp.getAsJsonObject();
            Map<String, Object> body = new HashMap<>();
            body.put("email", userObj.get("email").getAsString());
            body.put("password", userObj.has("password") && !userObj.get("password").isJsonNull()
                    ? userObj.get("password").getAsString() : "");
            body.put("first_name", userObj.get("first_name").getAsString());
            body.put("last_name", userObj.get("last_name").getAsString());
            body.put("role", userObj.get("role").getAsString());
            if (userObj.has("avatar_path") && !userObj.get("avatar_path").isJsonNull())
                body.put("avatar_path", userObj.get("avatar_path").getAsString());
            if (userObj.has("bio") && !userObj.get("bio").isJsonNull())
                body.put("bio", userObj.get("bio").getAsString());
            if (userObj.has("cover_base64") && !userObj.get("cover_base64").isJsonNull())
                body.put("cover_base64", userObj.get("cover_base64").getAsString());
            body.put("department_id", departmentId);
            JsonElement putResp = ApiClient.put("/users/" + userId, body);
            if (putResp == null) {
                throw new SQLException("API failed to update department for user " + userId + ". Check server logs.");
            }
            System.out.println("✅ Department updated via API for user id=" + userId + " → " + departmentId);
            InMemoryCache.evictByPrefix("users:");
            return;
        }
        String req = "UPDATE user SET department_id=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            if (departmentId != null) {
                ps.setInt(1, departmentId);
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("users:");
        System.out.println("✅ Department updated for user id=" + userId + " → " + departmentId);
    }

    /**
     * Toggle user active status (freeze / unfreeze).
     */
    public void toggleActive(int userId, boolean active) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("is_active", active);
            ApiClient.put("/users/" + userId + "/active", body);
            InMemoryCache.evict(CACHE_KEY);
            System.out.println("✅ User " + (active ? "activated" : "frozen") + " via API: id=" + userId);
            return;
        }
        String req = "UPDATE user SET is_active=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setBoolean(1, active);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        InMemoryCache.evict(CACHE_KEY);
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
        String req = "UPDATE user SET is_online=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setBoolean(1, online);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public List<User> getByRole(String role) throws SQLException {
        if (useApi) {
            return jsonArrayToUsers(ApiClient.get("/users/role/" + role));
        }
        List<User> users = new ArrayList<>();
        String req = "SELECT * FROM user WHERE role=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
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
            }
        }
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
        String req = "UPDATE user SET face_encoding=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, faceEncoding);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
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
        String req = "SELECT * FROM user WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
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
            }
        }
        return null;
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
        String req = "SELECT first_name FROM user WHERE email=?";
        String firstName;
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                firstName = rs.getString("first_name");
            }
        }

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
    /**
     * Verify an OTP code.
     * API mode: server validates. JDBC mode: caller must pass the expected OTP for comparison.
     */
    public boolean verifyOtp(String email, String otp) {
        return verifyOtp(email, otp, null);
    }

    /**
     * Verify an OTP code against an expected value.
     * @param expectedOtp The OTP that was generated locally (JDBC mode). Ignored in API mode.
     */
    public boolean verifyOtp(String email, String otp, String expectedOtp) {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("email", email);
            body.put("otp", otp);
            JsonElement resp = ApiClient.post("/auth/verify-otp", body);
            return resp != null && resp.isJsonObject()
                    && resp.getAsJsonObject().has("valid")
                    && resp.getAsJsonObject().get("valid").getAsBoolean();
        }
        // JDBC mode: compare against locally generated OTP
        if (expectedOtp == null || expectedOtp.isEmpty()) {
            System.err.println("[OTP] JDBC mode requires expectedOtp — rejecting");
            return false;
        }
        return otp != null && otp.equals(expectedOtp);
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
        String req = "UPDATE user SET password=? WHERE email=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, hashed);
            ps.setString(2, email);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Persist CV fields for a user. Works in both API and JDBC mode.
     * Pass null for any field to leave it unchanged.
     */
    public void updateCv(int userId, String cvPath, String cvOriginalName, String cvSkillsText) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("cv_path", cvPath);
            body.put("cv_original_name", cvOriginalName);
            body.put("cv_uploaded_at", new java.sql.Timestamp(System.currentTimeMillis()).toString());
            body.put("cv_skills_text", cvSkillsText);
            ApiClient.put("/users/" + userId + "/cv", body);
            InMemoryCache.evictByPrefix("users:");
            return;
        }
        String req = "UPDATE user SET cv_path=?, cv_original_name=?, cv_uploaded_at=?, cv_skills_text=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setString(1, cvPath);
            ps.setString(2, cvOriginalName);
            ps.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setString(4, cvSkillsText);
            ps.setInt(5, userId);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("users:");
    }

    /** Remove CV for a user. */
    public void clearCv(int userId) throws SQLException {
        if (useApi) {
            ApiClient.delete("/users/" + userId + "/cv");
            InMemoryCache.evictByPrefix("users:");
            return;
        }
        String req = "UPDATE user SET cv_path=NULL, cv_original_name=NULL, cv_uploaded_at=NULL, cv_skills_text=NULL WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(req)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("users:");
    }
}
