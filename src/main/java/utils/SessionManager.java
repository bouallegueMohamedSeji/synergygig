package utils;

import entities.User;
import services.ServiceUser;

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
        if (user != null) {
            try { new ServiceUser().setOnlineStatus(user.getId(), true); } catch (Exception ignored) {}
        }
    }

    public void logout() {
        if (currentUser != null) {
            try { new ServiceUser().setOnlineStatus(currentUser.getId(), false); } catch (Exception ignored) {}
        }
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
