package utils;

import entities.User;
import services.ServiceUser;

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

    private UserNameCache() { /* utility class */ }

    /**
     * Reloads users from the service and rebuilds the name map.
     * @return the freshly loaded user list (same reference as {@link #getAllUsers()})
     */
    public static List<User> refresh() {
        try {
            ServiceUser svc = new ServiceUser();
            List<User> users = svc.recuperer();
            synchronized (UserNameCache.class) {
                nameMap.clear();
                for (User u : users) {
                    nameMap.put(u.getId(), u.getFirstName() + " " + u.getLastName());
                }
                allUsers = users;
            }
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
