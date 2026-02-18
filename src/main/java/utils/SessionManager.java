package utils;

import entities.User;

/**
 * Singleton to track the currently logged-in user across all controllers.
 * Set on login, cleared on logout.
 */
public class SessionManager {

    private static SessionManager instance;
    private User currentUser;
    private Runnable onAvatarChanged;
    private boolean darkTheme = true;

    private SessionManager() {
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void logout() {
        this.currentUser = null;
        this.onAvatarChanged = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public String getCurrentRole() {
        return currentUser != null ? currentUser.getRole() : null;
    }

    /** Register a callback that fires when the avatar is updated. */
    public void setOnAvatarChanged(Runnable callback) {
        this.onAvatarChanged = callback;
    }

    /** Notify listeners that the avatar has changed. */
    public void fireAvatarChanged() {
        if (onAvatarChanged != null) {
            onAvatarChanged.run();
        }
    }

    // ── Theme State ──

    public boolean isDarkTheme() {
        return darkTheme;
    }

    public void setDarkTheme(boolean dark) {
        this.darkTheme = dark;
    }
}
