package controllers;

/**
 * Interface for controllers that hold background resources (schedulers, thread pools, etc.)
 * and need cleanup when their view is unloaded.
 * <p>
 * DashboardController calls {@link #stop()} on the active controller before loading a new view.
 */
public interface Stoppable {
    void stop();
}
