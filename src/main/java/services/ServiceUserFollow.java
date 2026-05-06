package services;

import com.google.gson.*;
import entities.UserFollow;
import utils.ApiClient;
import utils.AppConfig;
import utils.InMemoryCache;
import utils.MyDatabase;

import java.sql.*;
import java.util.*;

/**
 * Service for managing user follow / friend-request relationships.
 * Supports API and JDBC dual mode. Auto-creates table if needed.
 *
 * status column: "ACCEPTED" (default, a follow), "PENDING" (friend request waiting).
 */
public class ServiceUserFollow {

    private boolean useApi() { return AppConfig.isApiMode(); }

    // ============ Table Auto-Create ============

    public void ensureTable() {
        Connection conn = MyDatabase.getInstance().getConnection();
        if (conn == null) return;
        try (conn; Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS user_follows (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    follower_id INT NOT NULL,
                    followed_id INT NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_follow (follower_id, followed_id),
                    INDEX idx_follower (follower_id),
                    INDEX idx_followed (followed_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        } catch (SQLException e) {
            System.err.println("ServiceUserFollow.ensureTable: " + e.getMessage());
        }
    }

    // ============ JSON helpers ============

    private UserFollow jsonToFollow(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            try {
                String raw = obj.get("created_at").getAsString().replace("T", " ");
                if (raw.contains(".")) raw = raw.substring(0, raw.indexOf("."));
                createdAt = Timestamp.valueOf(raw);
            } catch (Exception ignored) {}
        }
        UserFollow uf = new UserFollow(
                obj.get("id").getAsInt(),
                obj.get("follower_id").getAsInt(),
                obj.get("followed_id").getAsInt(),
                createdAt
        );
        if (obj.has("status") && !obj.get("status").isJsonNull())
            uf.setStatus(obj.get("status").getAsString());
        return uf;
    }

    private List<UserFollow> jsonArrayToList(JsonElement el) {
        List<UserFollow> list = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray())
                list.add(jsonToFollow(item.getAsJsonObject()));
        }
        return list;
    }

    // ============ Core Follow Operations ============

    /** Follow a user (status = ACCEPTED) and auto-create mutual friendship. */
    public void follow(int followerId, int followedId) throws SQLException {
        if (followerId == followedId) return;
        if (useApi()) {
            Map<String, Object> body = new HashMap<>();
            body.put("follower_id", followerId);
            body.put("followed_id", followedId);
            body.put("status", UserFollow.STATUS_ACCEPTED);
            try { ApiClient.post("/user_follows", body); } catch (Exception ignored) {}
            // Auto-friend: also create the reverse direction
            Map<String, Object> reverse = new HashMap<>();
            reverse.put("follower_id", followedId);
            reverse.put("followed_id", followerId);
            reverse.put("status", UserFollow.STATUS_ACCEPTED);
            try { ApiClient.post("/user_follows", reverse); } catch (Exception ignored) {}
            InMemoryCache.evictByPrefix("follows:");
            return;
        }
        String sql = "INSERT IGNORE INTO user_follows (follower_id, followed_id, status, created_at) VALUES (?, ?, ?, NOW())";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, followerId);
            ps.setInt(2, followedId);
            ps.setString(3, UserFollow.STATUS_FOLLOWING);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("follows:");
    }

    /** Unfollow / remove relationship. */
    public void unfollow(int followerId, int followedId) throws SQLException {
        if (useApi()) {
            try { ApiClient.delete("/user_follows?follower_id=" + followerId + "&followed_id=" + followedId); }
            catch (Exception ignored) {}
            InMemoryCache.evictByPrefix("follows:");
            return;
        }
        String sql = "DELETE FROM user_follows WHERE follower_id = ? AND followed_id = ?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, followerId);
            ps.setInt(2, followedId);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("follows:");
    }

    /** Check if followerId is following followedId (ACCEPTED only). */
    public boolean isFollowing(int followerId, int followedId) {
        return getFollowedIds(followerId).contains(followedId);
    }

    /** Get accepted followed IDs. Cached 60s. */
    public Set<Integer> getFollowedIds(int followerId) {
        String key = "follows:following:" + followerId;
        return InMemoryCache.getOrLoad(key, 60, () -> {
            Set<Integer> ids = new HashSet<>();
            if (useApi()) {
                try {
                    JsonElement el = ApiClient.get("/user_follows?follower_id=" + followerId + "&status=ACCEPTED");
                    if (el != null && el.isJsonArray())
                        for (JsonElement item : el.getAsJsonArray())
                            ids.add(item.getAsJsonObject().get("followed_id").getAsInt());
                } catch (Exception ignored) {}
            } else {
                try (Connection conn = MyDatabase.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT followed_id FROM user_follows WHERE follower_id = ? AND status IN ('following','friend_accepted')")) {
                    ps.setInt(1, followerId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) ids.add(rs.getInt(1));
                } catch (SQLException ignored) {}
            }
            return ids;
        });
    }

    /** Get accepted follower IDs. Cached 60s. */
    public Set<Integer> getFollowerIds(int followedId) {
        String key = "follows:followers:" + followedId;
        return InMemoryCache.getOrLoad(key, 60, () -> {
            Set<Integer> ids = new HashSet<>();
            if (useApi()) {
                try {
                    JsonElement el = ApiClient.get("/user_follows?followed_id=" + followedId + "&status=ACCEPTED");
                    if (el != null && el.isJsonArray())
                        for (JsonElement item : el.getAsJsonArray())
                            ids.add(item.getAsJsonObject().get("follower_id").getAsInt());
                } catch (Exception ignored) {}
            } else {
                try (Connection conn = MyDatabase.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT follower_id FROM user_follows WHERE followed_id = ? AND status IN ('following','friend_accepted')")) {
                    ps.setInt(1, followedId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) ids.add(rs.getInt(1));
                } catch (SQLException ignored) {}
            }
            return ids;
        });
    }

    /** Get follower count (accepted). */
    public int getFollowerCount(int userId) { return getFollowerIds(userId).size(); }

    /** Get following count (accepted). */
    public int getFollowingCount(int userId) { return getFollowedIds(userId).size(); }

    // ============ Friend-Request Operations ============

    /** Send a friend request (status = PENDING). */
    public void sendFriendRequest(int fromUserId, int toUserId) throws SQLException {
        if (fromUserId == toUserId) return;
        if (useApi()) {
            Map<String, Object> body = new HashMap<>();
            body.put("follower_id", fromUserId);
            body.put("followed_id", toUserId);
            body.put("status", UserFollow.STATUS_PENDING);
            try { ApiClient.post("/user_follows", body); } catch (Exception ignored) {}
            InMemoryCache.evictByPrefix("follows:");
            return;
        }
        String sql = "INSERT IGNORE INTO user_follows (follower_id, followed_id, status, created_at) VALUES (?, ?, ?, NOW())";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fromUserId);
            ps.setInt(2, toUserId);
            ps.setString(3, UserFollow.STATUS_PENDING);
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("follows:");
    }

    /** Accept a pending request → set status to ACCEPTED. */
    public void acceptFriendRequest(int fromUserId, int toUserId) throws SQLException {
        if (useApi()) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", UserFollow.STATUS_ACCEPTED);
            try { ApiClient.put("/user_follows/accept?follower_id=" + fromUserId + "&followed_id=" + toUserId, body); }
            catch (Exception ignored) {}
            InMemoryCache.evictByPrefix("follows:");
            return;
        }
        try (Connection conn = MyDatabase.getInstance().getConnection()) {
            // Mark the original request as ACCEPTED
            String update = "UPDATE user_follows SET status = ? WHERE follower_id = ? AND followed_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setString(1, UserFollow.STATUS_ACCEPTED);
                ps.setInt(2, fromUserId);
                ps.setInt(3, toUserId);
                ps.executeUpdate();
            }
            // Insert the reverse direction so getFriendIds() (intersection check) sees both sides
            String reverse = "INSERT IGNORE INTO user_follows (follower_id, followed_id, status, created_at) VALUES (?, ?, ?, NOW())";
            try (PreparedStatement ps2 = conn.prepareStatement(reverse)) {
                ps2.setInt(1, toUserId);
                ps2.setInt(2, fromUserId);
                ps2.setString(3, UserFollow.STATUS_ACCEPTED);
                ps2.executeUpdate();
            }
        }
        InMemoryCache.evictByPrefix("follows:");
    }

    /** Reject / cancel a pending request → delete the row. */
    public void rejectFriendRequest(int fromUserId, int toUserId) throws SQLException {
        unfollow(fromUserId, toUserId);
    }

    /** Get pending friend requests sent TO userId (others → userId, status=PENDING). */
    public List<UserFollow> getPendingRequests(int userId) {
        String key = "follows:pending:" + userId;
        return InMemoryCache.getOrLoad(key, 60, () -> {
            List<UserFollow> list = new ArrayList<>();
            if (useApi()) {
                try {
                    JsonElement el = ApiClient.get("/user_follows?followed_id=" + userId + "&status=PENDING");
                    list = jsonArrayToList(el);
                } catch (Exception ignored) {}
            } else {
                try (Connection conn = MyDatabase.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT * FROM user_follows WHERE followed_id = ? AND status = 'friend_pending' ORDER BY created_at DESC")) {
                    ps.setInt(1, userId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        UserFollow uf = new UserFollow(
                                rs.getInt("id"), rs.getInt("follower_id"),
                                rs.getInt("followed_id"), rs.getTimestamp("created_at"));
                        uf.setStatus(rs.getString("status"));
                        list.add(uf);
                    }
                } catch (SQLException ignored) {}
            }
            return list;
        });
    }

    /** True if both users mutually follow each other (ACCEPTED). */
    public boolean areFriends(int userA, int userB) {
        return isFollowing(userA, userB) && isFollowing(userB, userA);
    }

    /** Get the status of a follow row (follower→followed), or "NONE" if none. */
    public String getRelationshipStatus(int followerId, int followedId) {
        String key = "follows:rel:" + followerId + ":" + followedId;
        return InMemoryCache.getOrLoad(key, 60, () -> {
            if (useApi()) {
                try {
                    JsonElement el = ApiClient.get("/user_follows?follower_id=" + followerId + "&followed_id=" + followedId);
                    if (el != null && el.isJsonArray() && el.getAsJsonArray().size() > 0)
                        return el.getAsJsonArray().get(0).getAsJsonObject().get("status").getAsString();
                } catch (Exception ignored) {}
            } else {
                try (Connection conn = MyDatabase.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT status FROM user_follows WHERE follower_id = ? AND followed_id = ?")) {
                    ps.setInt(1, followerId);
                    ps.setInt(2, followedId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) return rs.getString(1);
                } catch (SQLException ignored) {}
            }
            return null;
        });
    }

    /** Get set of user IDs that userId is friends with (mutual friend_accepted rows). */
    public Set<Integer> getFriendIds(int userId) {
        String key = "follows:friends:" + userId;
        return InMemoryCache.getOrLoad(key, 60, () -> {
            Set<Integer> ids = new HashSet<>();
            if (useApi()) {
                // Fall back to intersection approach for API mode
                Set<Integer> following = getFollowedIds(userId);
                Set<Integer> followers = getFollowerIds(userId);
                ids.addAll(following);
                ids.retainAll(followers);
            } else {
                try (Connection conn = MyDatabase.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                         "SELECT a.followed_id FROM user_follows a " +
                         "JOIN user_follows b ON a.followed_id = b.follower_id AND a.follower_id = b.followed_id " +
                         "WHERE a.follower_id = ? AND a.status = 'friend_accepted' AND b.status = 'friend_accepted'")) {
                    ps.setInt(1, userId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) ids.add(rs.getInt(1));
                } catch (SQLException ignored) {}
            }
            return ids;
        });
    }

    /** Get friend count. */
    public int getFriendCount(int userId) { return getFriendIds(userId).size(); }
}
