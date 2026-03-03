package utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import utils.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Content moderation using the APILayer Bad Words API with local fallback.
 * <ul>
 *   <li>Primary: APILayer Bad Words API</li>
 *   <li>Fallback: local word list when API is unreachable</li>
 *   <li>Leetspeak normalization: converts @→a, 0→o, 1/!→i, $→s, 3→e, etc.</li>
 *   <li>Response caching: avoids repeated API calls for the same text (60s TTL)</li>
 * </ul>
 */
public class BadWordsService {

    private static final String API_URL = "https://api.apilayer.com/bad_words";
    private static final String API_KEY = AppConfig.get("badwords.api_key",
            "YgbeLs1Let62752r83R0gWVaXdhZMBPj");

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ── Cache ──────────────────────────────────────────────
    private static final long CACHE_TTL_MS = 60_000; // 60 seconds
    private static final ConcurrentHashMap<String, CachedResult> cache = new ConcurrentHashMap<>();

    private static class CachedResult {
        final CheckResult result;
        final long timestamp;
        CachedResult(CheckResult r) { this.result = r; this.timestamp = System.currentTimeMillis(); }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_TTL_MS; }
    }

    // ── Leetspeak map ──────────────────────────────────────
    private static final Map<Character, Character> LEET_MAP = Map.ofEntries(
            Map.entry('@', 'a'), Map.entry('4', 'a'),
            Map.entry('0', 'o'),
            Map.entry('1', 'i'), Map.entry('!', 'i'),
            Map.entry('$', 's'), Map.entry('5', 's'),
            Map.entry('3', 'e'),
            Map.entry('7', 't'),
            Map.entry('+', 't'),
            Map.entry('8', 'b'),
            Map.entry('9', 'g'),
            Map.entry('(', 'c'),
            Map.entry('|', 'l'),
            Map.entry('2', 'z')
    );

    // ── Local word list (fallback) ─────────────────────────
    private static final Set<String> LOCAL_BAD_WORDS = new HashSet<>(Arrays.asList(
            // Severe
            "fuck", "fucker", "fucking", "fucked", "fucks", "motherfucker", "motherfucking",
            "shit", "shitty", "shitting", "bullshit", "horseshit", "dipshit",
            "ass", "asshole", "assholes", "arse", "arsehole",
            "bitch", "bitches", "bitchy", "bitching",
            "damn", "damned", "dammit", "goddamn", "goddammit",
            "bastard", "bastards",
            "dick", "dicks", "dickhead",
            "cock", "cocks", "cocksucker",
            "cunt", "cunts",
            "piss", "pissed", "pissing",
            "crap", "crappy",
            "whore", "whores",
            "slut", "sluts", "slutty",
            "wanker", "wankers", "wank",
            "twat", "twats",
            "prick", "pricks",
            "douche", "douchebag", "douchebags",
            // Moderate
            "hell", "bloody", "bugger", "sod", "bollocks",
            "tits", "boobs", "boob",
            "retard", "retarded", "retards",
            "idiot", "idiots", "idiotic",
            "moron", "morons", "moronic",
            "stupid", "dumb", "dumbass",
            "loser", "losers",
            "sucker", "suckers",
            "screw", "screwed", "screwing",
            "jerk", "jerks",
            "freak", "freaking",
            "nigger", "niggers", "nigga", "niggas",
            "fag", "fags", "faggot", "faggots",
            "dyke", "dykes",
            "spic", "spics", "spick",
            "kike", "kikes",
            "chink", "chinks",
            "wetback", "wetbacks",
            "cracker", "crackers",
            "nazi", "nazis",
            "porn", "porno", "pornography",
            "sex", "sexy", "sexual",
            "rape", "rapist", "raping",
            "molest", "molester", "molestation",
            "pervert", "perverts", "perverted",
            "prostitute", "prostitution",
            "hentai", "xxx", "nude", "nudity",
            "kill", "murder", "suicide",
            "terrorist", "terrorism"
    ));

    // Compiled patterns for local matching (word-boundary, case-insensitive)
    private static final Map<String, Pattern> LOCAL_PATTERNS = new HashMap<>();
    static {
        for (String word : LOCAL_BAD_WORDS) {
            LOCAL_PATTERNS.put(word, Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE));
        }
    }

    /**
     * Result of a bad words check.
     */
    public static class CheckResult {
        public final boolean hasBadWords;
        public final int badWordsCount;
        public final String censoredContent;
        public final String originalContent;
        public final String source; // "api", "local", or "cache"

        public CheckResult(boolean hasBadWords, int badWordsCount, String censoredContent, String originalContent) {
            this(hasBadWords, badWordsCount, censoredContent, originalContent, "api");
        }

        public CheckResult(boolean hasBadWords, int badWordsCount, String censoredContent, String originalContent, String source) {
            this.hasBadWords = hasBadWords;
            this.badWordsCount = badWordsCount;
            this.censoredContent = censoredContent;
            this.originalContent = originalContent;
            this.source = source;
        }
    }

    /**
     * Check the given text for bad words.
     * Flow: cache → API → local fallback.
     * Leetspeak is normalized before local checks.
     */
    public static CheckResult check(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new CheckResult(false, 0, text, text, "local");
        }

        // 1. Check cache
        String cacheKey = text.trim().toLowerCase();
        CachedResult cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            CheckResult cr = cached.result;
            return new CheckResult(cr.hasBadWords, cr.badWordsCount, cr.censoredContent, text, "cache");
        }

        // 2. Try API
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "?censor_character=*"))
                    .header("apikey", API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(8))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                int badWordsTotal = json.has("bad_words_total") ? json.get("bad_words_total").getAsInt() : 0;
                String censored = json.has("censored_content") ? json.get("censored_content").getAsString() : text;

                CheckResult result = new CheckResult(badWordsTotal > 0, badWordsTotal, censored, text, "api");
                cache.put(cacheKey, new CachedResult(result));
                return result;
            } else {
                System.err.println("Bad Words API error: " + resp.statusCode() + " — falling back to local check");
            }
        } catch (Exception e) {
            System.err.println("Bad Words API unreachable: " + e.getMessage() + " — falling back to local check");
        }

        // 3. Local fallback with leetspeak normalization
        CheckResult localResult = checkLocal(text);
        cache.put(cacheKey, new CachedResult(localResult));
        return localResult;
    }

    /**
     * Local word-list check with leetspeak normalization.
     */
    private static CheckResult checkLocal(String text) {
        // Normalize leetspeak
        String normalized = normalizeLeetspeak(text);

        String censored = text;
        int count = 0;

        for (Map.Entry<String, Pattern> entry : LOCAL_PATTERNS.entrySet()) {
            // Check both original text and leetspeak-normalized text
            Matcher origMatcher = entry.getValue().matcher(text);
            Matcher leetMatcher = entry.getValue().matcher(normalized);

            boolean foundInOrig = origMatcher.find();
            boolean foundInLeet = leetMatcher.find();

            if (foundInOrig) {
                // Censor in original text
                origMatcher.reset();
                StringBuilder sb = new StringBuilder();
                while (origMatcher.find()) {
                    count++;
                    String stars = "*".repeat(origMatcher.group().length());
                    origMatcher.appendReplacement(sb, stars);
                }
                origMatcher.appendTail(sb);
                censored = sb.toString();
            } else if (foundInLeet) {
                // Found via leetspeak but not plain — count it but harder to censor precisely
                count++;
                // Try to censor the approximate region in original text
                leetMatcher.reset();
                while (leetMatcher.find()) {
                    int start = leetMatcher.start();
                    int end = leetMatcher.end();
                    if (start < censored.length() && end <= censored.length()) {
                        String stars = "*".repeat(end - start);
                        censored = censored.substring(0, start) + stars + censored.substring(end);
                    }
                }
            }
        }

        return new CheckResult(count > 0, count, censored, text, "local");
    }

    /**
     * Normalize leetspeak characters to their letter equivalents.
     * Example: "f@ck" → "fack", "$h1t" → "shit"
     */
    public static String normalizeLeetspeak(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            Character replacement = LEET_MAP.get(c);
            sb.append(replacement != null ? replacement : Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /**
     * Quick convenience: returns true if the text contains bad words.
     */
    public static boolean containsBadWords(String text) {
        return check(text).hasBadWords;
    }

    /**
     * Clear the result cache (useful for testing).
     */
    public static void clearCache() {
        cache.clear();
    }
}
