package utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Lightweight in-memory cache with per-entry TTL.
 * Zero external dependencies — uses ConcurrentHashMap.
 * <p>
 * Works in both API and JDBC modes.
 * <p>
 * Usage:
 * <pre>
 *   List&lt;User&gt; users = InMemoryCache.getOrLoad("users:all", 120,
 *       () -&gt; serviceUser.fetchFromSource());
 *
 *   InMemoryCache.evict("users:all");
 *   InMemoryCache.evictByPrefix("users:");
 * </pre>
 */
public final class InMemoryCache {

    private static final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();

    private InMemoryCache() { /* utility */ }

    // ═══════════════════════════════════════════
    //  CORE
    // ═══════════════════════════════════════════

    /**
     * Return cached value if present and not expired, otherwise call
     * the loader, cache the result, and return it.
     *
     * @param key      cache key
     * @param ttlSecs  time-to-live in seconds (0 = no expiry)
     * @param loader   supplier that fetches the data (only called on miss)
     */
    @SuppressWarnings("unchecked")
    public static <T> T getOrLoad(String key, int ttlSecs, Supplier<T> loader) {
        CacheEntry<?> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.value;
        }
        T value = loader.get();
        // Cache even null results to prevent repeated failing API calls
        cache.put(key, new CacheEntry<>(value, ttlSecs));
        return value;
    }

    /**
     * Same as {@link #getOrLoad} but accepts a {@link CheckedSupplier}
     * that may throw (e.g. SQLException).
     */
    @SuppressWarnings("unchecked")
    public static <T, E extends Exception> T getOrLoadChecked(
            String key, int ttlSecs, CheckedSupplier<T, E> loader) throws E {
        CacheEntry<?> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.value;
        }
        T value = loader.get();
        // Cache even null results to prevent repeated failing API calls
        cache.put(key, new CacheEntry<>(value, ttlSecs));
        return value;
    }

    /** Evict a single key. */
    public static void evict(String key) {
        cache.remove(key);
    }

    /** Evict all keys that start with the given prefix. */
    public static void evictByPrefix(String prefix) {
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** Clear all cached data. */
    public static void clear() {
        cache.clear();
    }

    /** Number of entries currently in cache (including expired). */
    public static int size() {
        return cache.size();
    }

    // ═══════════════════════════════════════════
    //  INTERNALS
    // ═══════════════════════════════════════════

    private static class CacheEntry<T> {
        final T value;
        final long expiresAt; // System.currentTimeMillis epoch; 0 = never

        CacheEntry(T value, int ttlSecs) {
            this.value = value;
            this.expiresAt = ttlSecs > 0
                    ? System.currentTimeMillis() + (ttlSecs * 1000L)
                    : 0;
        }

        boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
        }
    }

    /** Supplier that can throw a checked exception. */
    @FunctionalInterface
    public interface CheckedSupplier<T, E extends Exception> {
        T get() throws E;
    }
}
