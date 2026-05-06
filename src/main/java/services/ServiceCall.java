package services;

import com.google.gson.*;
import entities.Call;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing audio call signaling via REST API.
 */
public class ServiceCall {

    // ==================== JSON → Entity ====================

    private Call jsonToCall(JsonObject obj) {
        Timestamp startedAt = null, endedAt = null, createdAt = null;
        if (obj.has("started_at") && !obj.get("started_at").isJsonNull()) {
            startedAt = Timestamp.valueOf(obj.get("started_at").getAsString().replace("T", " "));
        }
        if (obj.has("ended_at") && !obj.get("ended_at").isJsonNull()) {
            endedAt = Timestamp.valueOf(obj.get("ended_at").getAsString().replace("T", " "));
        }
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            createdAt = Timestamp.valueOf(obj.get("created_at").getAsString().replace("T", " "));
        }
        return new Call(
                obj.get("id").getAsInt(),
                obj.get("caller_id").getAsInt(),
                obj.get("callee_id").getAsInt(),
                obj.has("room_id") && !obj.get("room_id").isJsonNull() ? obj.get("room_id").getAsInt() : 0,
                obj.get("status").getAsString(),
                obj.has("call_type") && !obj.get("call_type").isJsonNull() ? obj.get("call_type").getAsString() : "audio",
                startedAt, endedAt, createdAt
        );
    }

    // ==================== JDBC → Entity ====================

    /**
     * Map a DB row to a Call.
     * DB stores uppercase statuses (RINGING, CONNECTED, ENDED…) while the
     * Java entity uses lowercase (ringing, active, ended…).
     */
    private Call rsToCall(ResultSet rs) throws SQLException {
        String dbStatus = rs.getString("status");
        String status = dbStatus == null ? "ended" : switch (dbStatus.toUpperCase()) {
            case "RINGING"   -> "ringing";
            case "CONNECTED" -> "active";
            case "ENDED"     -> "ended";
            case "REJECTED"  -> "rejected";
            case "MISSED"    -> "missed";
            default          -> dbStatus.toLowerCase();
        };
        return new Call(
                rs.getInt("id"),
                rs.getInt("caller_id"),
                rs.getInt("callee_id"),
                rs.getInt("room_id"),
                status,
                rs.getString("call_type") != null ? rs.getString("call_type").toLowerCase() : "audio",
                rs.getTimestamp("started_at"),
                rs.getTimestamp("ended_at"),
                rs.getTimestamp("created_at")
        );
    }

    // ==================== API Methods ====================

    /** Initiate a new call. Returns the created Call with id. */
    public Call createCall(int callerId, int calleeId, int roomId) {
        return createCall(callerId, calleeId, roomId, "audio");
    }

    /** Initiate a new call with type ("audio" or "video"). Returns the created Call with id. */
    public Call createCall(int callerId, int calleeId, int roomId, String callType) {
        String type = (callType != null ? callType : "audio").toUpperCase();
        if (!AppConfig.isApiMode()) {
            // JDBC: end any existing ringing/active call for caller first
            String sql = "INSERT INTO calls (caller_id, callee_id, room_id, call_type, status, created_at) VALUES (?,?,?,?,'RINGING',NOW())";
            try (Connection c = MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, callerId);
                ps.setInt(2, calleeId);
                ps.setInt(3, roomId > 0 ? roomId : 0);
                ps.setString(4, type);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                Call call = new Call(callerId, calleeId, roomId);
                if (keys.next()) call.setId(keys.getInt(1));
                call.setStatus("ringing");
                call.setCallType(callType != null ? callType : "audio");
                return call;
            } catch (SQLException e) {
                System.err.println("[ServiceCall] createCall JDBC error: " + e.getMessage());
                return null;
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("caller_id", callerId);
        body.put("callee_id", calleeId);
        body.put("room_id", roomId);
        body.put("call_type", callType != null ? callType : "audio");
        JsonElement resp = ApiClient.post("/calls", body);
        if (resp != null && resp.isJsonObject()) {
            JsonObject obj = resp.getAsJsonObject();
            Call call = new Call(callerId, calleeId, roomId);
            call.setId(obj.get("id").getAsInt());
            call.setStatus(obj.get("status").getAsString());
            call.setCallType(callType != null ? callType : "audio");
            return call;
        }
        return null;
    }

    /** Get a call by ID. */
    public Call getCall(int callId) {
        if (!AppConfig.isApiMode()) {
            String sql = "SELECT * FROM calls WHERE id=? LIMIT 1";
            try (Connection c = MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, callId);
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rsToCall(rs) : null;
            } catch (SQLException e) {
                System.err.println("[ServiceCall] getCall JDBC error: " + e.getMessage());
                return null;
            }
        }
        JsonElement resp = ApiClient.get("/calls/" + callId);
        if (resp != null && resp.isJsonObject()) {
            return jsonToCall(resp.getAsJsonObject());
        }
        return null;
    }

    /** Check for incoming ringing calls for a user. Returns null if none. */
    public Call getIncomingCall(int userId) {
        if (!AppConfig.isApiMode()) {
            String sql = "SELECT * FROM calls WHERE callee_id=? AND status='RINGING' ORDER BY created_at DESC LIMIT 1";
            try (Connection c = MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rsToCall(rs) : null;
            } catch (SQLException e) {
                System.err.println("[ServiceCall] getIncomingCall JDBC error: " + e.getMessage());
                return null;
            }
        }
        JsonElement resp = ApiClient.get("/calls/incoming/" + userId);
        if (resp != null && resp.isJsonObject()) {
            return jsonToCall(resp.getAsJsonObject());
        }
        return null;
    }

    /** Get active call for a user. Returns null if none. */
    public Call getActiveCall(int userId) {
        if (!AppConfig.isApiMode()) {
            String sql = "SELECT * FROM calls WHERE (caller_id=? OR callee_id=?) AND status IN ('RINGING','CONNECTED') ORDER BY created_at DESC LIMIT 1";
            try (Connection c = MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rsToCall(rs) : null;
            } catch (SQLException e) {
                System.err.println("[ServiceCall] getActiveCall JDBC error: " + e.getMessage());
                return null;
            }
        }
        JsonElement resp = ApiClient.get("/calls/active/" + userId);
        if (resp != null && resp.isJsonObject()) {
            return jsonToCall(resp.getAsJsonObject());
        }
        return null;
    }

    /** Accept a ringing call. */
    public void acceptCall(int callId) {
        if (!AppConfig.isApiMode()) {
            String sql = "UPDATE calls SET status='CONNECTED', started_at=NOW() WHERE id=?";
            try (Connection c = MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, callId);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[ServiceCall] acceptCall JDBC error: " + e.getMessage());
            }
            return;
        }
        ApiClient.put("/calls/" + callId + "/accept", new HashMap<>());
    }

    /** Reject a ringing call. */
    public void rejectCall(int callId) {
        if (!AppConfig.isApiMode()) {
            String sql = "UPDATE calls SET status='REJECTED', ended_at=NOW() WHERE id=?";
            try (Connection c = MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, callId);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[ServiceCall] rejectCall JDBC error: " + e.getMessage());
            }
            return;
        }
        ApiClient.put("/calls/" + callId + "/reject", new HashMap<>());
    }

    /** End an active or ringing call. */
    public void endCall(int callId) {
        if (!AppConfig.isApiMode()) {
            String sql = "UPDATE calls SET status='ENDED', ended_at=NOW() WHERE id=?";
            try (Connection c = MyDatabase.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, callId);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[ServiceCall] endCall JDBC error: " + e.getMessage());
            }
            return;
        }
        ApiClient.put("/calls/" + callId + "/end", new HashMap<>());
    }
}
