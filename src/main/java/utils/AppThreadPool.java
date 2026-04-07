package utils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application-wide thread pool for background tasks.
 * <p>
 * Replaces ad-hoc {@code new Thread(...)} calls throughout controllers
 * with a managed, daemon-threaded executor.
 * <p>
 * Usage:
 * <pre>{@code
 *     AppThreadPool.io(() -> { ... });             // fire-and-forget I/O task
 *     Future<?> f = AppThreadPool.submit(() -> {}); // task with future
 * }</pre>
 */
public final class AppThreadPool {

    /** Cached thread pool for short-lived I/O tasks (API calls, DB queries, AI requests). */
    private static final ExecutorService IO = Executors.newCachedThreadPool(new DaemonFactory("app-io"));

    private AppThreadPool() { /* utility */ }

    /** Submit a fire-and-forget I/O task. */
    public static void io(Runnable task) {
        IO.execute(task);
    }

    /** Submit a task and return a Future. */
    public static Future<?> submit(Runnable task) {
        return IO.submit(task);
    }

    /** Submit a callable and return a Future. */
    public static <T> Future<T> submit(Callable<T> task) {
        return IO.submit(task);
    }

    /** Shut down all pools. Call from application stop / shutdown hook. */
    public static void shutdownNow() {
        IO.shutdownNow();
    }

    // ── Daemon thread factory ──

    private static class DaemonFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        DaemonFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
