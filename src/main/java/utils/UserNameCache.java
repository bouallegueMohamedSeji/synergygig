package utils;

import entities.User;
import services.ServiceUser;
import utils.InMemoryCache;

import java.sql.SQLException;
import java.util.*;

/**
 * Shared, thread-safe cache for user-name lookups used by multiple controllers.
 * <p>
 * Call {@link #refresh()} once per controller initialisation; the data is then
 * available via the static getters until the next refresh.
 */
public final class UserNameCache {

    private static final Map<Integer, String> nameMap = new LinkedHashMap<>();
    private static List<User> allUsers = Collections.emptyList();

    /** How long (ms) the cached data is considered fresh. */
    private static final long CACHE_TTL_MS = 60_000; // 60 seconds

    /** Timestamp (epoch ms) of the last successful refresh. */
    private static volatile long lastRefreshTime = 0;

    private UserNameCache() { /* utility class */ }

    /**
     * Reloads users from the service and rebuilds the name map.
     * <p>
     * If the cache was refreshed within the last {@value #CACHE_TTL_MS} ms,
     * the call returns immediately with the cached data — no DB query is made.
     *
     * @return the freshly loaded (or cached) user list
     */
    public static List<User> refresh() {
        // Fast path: return cached data if still fresh
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime < CACHE_TTL_MS && !allUsers.isEmpty()) {
            return allUsers;
        }
        return doRefresh(false);
    }

    /**
     * Forces a reload regardless of cache age.
     */
    public static List<User> forceRefresh() {
        return doRefresh(true);
    }

    private static synchronized List<User> doRefresh(boolean force) {
        // Double-check inside sync in case another thread refreshed while we waited
        long now = System.currentTimeMillis();
        if (!force && now - lastRefreshTime < CACHE_TTL_MS && !allUsers.isEmpty()) {
            return allUsers;
        }
        InMemoryCache.evictByPrefix("users:");
        try {
            ServiceUser svc = new ServiceUser();
            List<User> users = svc.recuperer();
            nameMap.clear();
            for (User u : users) {
                nameMap.put(u.getId(), u.getFirstName() + " " + u.getLastName());
            }
            allUsers = users;
            lastRefreshTime = System.currentTimeMillis();
        } catch (SQLException e) {
            System.err.println("UserNameCache: failed to load users — " + e.getMessage());
        }
        return allUsers;
    }

    /** Display name for a user id, or {@code "User #<id>"} if unknown. */
    public static String getName(int userId) {
        return nameMap.getOrDefault(userId, "User #" + userId);
    }

    /** The full user list from the last {@link #refresh()} call. */
    public static List<User> getAllUsers() {
        return allUsers;
    }
}
