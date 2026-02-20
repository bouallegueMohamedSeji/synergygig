package services;

import com.google.gson.*;
import entities.Call;
import utils.ApiClient;
import utils.AppConfig;

import java.sql.Timestamp;
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
                startedAt, endedAt, createdAt
        );
    }

    // ==================== API Methods ====================

    /** Initiate a new call. Returns the created Call with id. */
    public Call createCall(int callerId, int calleeId, int roomId) {
        Map<String, Object> body = new HashMap<>();
        body.put("caller_id", callerId);
        body.put("callee_id", calleeId);
        body.put("room_id", roomId);
        JsonElement resp = ApiClient.post("/calls", body);
        if (resp != null && resp.isJsonObject()) {
            JsonObject obj = resp.getAsJsonObject();
            Call call = new Call(callerId, calleeId, roomId);
            call.setId(obj.get("id").getAsInt());
            call.setStatus(obj.get("status").getAsString());
            return call;
        }
        return null;
    }

    /** Get a call by ID. */
    public Call getCall(int callId) {
        JsonElement resp = ApiClient.get("/calls/" + callId);
        if (resp != null && resp.isJsonObject()) {
            return jsonToCall(resp.getAsJsonObject());
        }
        return null;
    }

    /** Check for incoming ringing calls for a user. Returns null if none. */
    public Call getIncomingCall(int userId) {
        JsonElement resp = ApiClient.get("/calls/incoming/" + userId);
        if (resp != null && resp.isJsonObject()) {
            return jsonToCall(resp.getAsJsonObject());
        }
        return null;
    }

    /** Get active call for a user. Returns null if none. */
    public Call getActiveCall(int userId) {
        JsonElement resp = ApiClient.get("/calls/active/" + userId);
        if (resp != null && resp.isJsonObject()) {
            return jsonToCall(resp.getAsJsonObject());
        }
        return null;
    }

    /** Accept a ringing call. */
    public void acceptCall(int callId) {
        ApiClient.put("/calls/" + callId + "/accept", new HashMap<>());
    }

    /** Reject a ringing call. */
    public void rejectCall(int callId) {
        ApiClient.put("/calls/" + callId + "/reject", new HashMap<>());
    }

    /** End an active or ringing call. */
    public void endCall(int callId) {
        ApiClient.put("/calls/" + callId + "/end", new HashMap<>());
    }
}
