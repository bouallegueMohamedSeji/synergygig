package services;

import com.google.gson.*;
import entities.ExternalJob;

import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Fetches external job listings from 4 free, no-auth APIs:
 *   1. Remotive   – remote jobs worldwide
 *   2. Arbeitnow  – European / German jobs
 *   3. Jobicy     – remote-first jobs
 *   4. RemoteOK   – remote tech jobs
 *
 * Includes a 15-minute in-memory cache to avoid hammering API endpoints.
 */
public class ExternalJobService {

    // ── Endpoints ──
    private static final String REMOTIVE_URL  = "https://remotive.com/api/remote-jobs?limit=20";
    private static final String ARBEITNOW_URL = "https://www.arbeitnow.com/api/job-board-api";
    private static final String JOBICY_URL    = "https://jobicy.com/api/v2/remote-jobs?count=20";
    private static final String REMOTEOK_URL  = "https://remoteok.com/api";

    // ── Cache ──
    private static final long CACHE_TTL_MS = 15 * 60 * 1000; // 15 minutes
    private static List<ExternalJob> cachedJobs = null;
    private static long cacheTimestamp = 0;

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Gson gson = new Gson();

    /**
     * Returns a combined list of external jobs from all sources.
     * Uses cache if data is less than 15 minutes old.
     */
    public static List<ExternalJob> fetchAll() {
        if (cachedJobs != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) {
            return cachedJobs;
        }

        List<ExternalJob> all = new ArrayList<>();

        // Fetch from all sources in parallel
        ExecutorService exec = Executors.newFixedThreadPool(4);
        List<Future<List<ExternalJob>>> futures = new ArrayList<>();
        futures.add(exec.submit(ExternalJobService::fetchRemotive));
        futures.add(exec.submit(ExternalJobService::fetchArbeitnow));
        futures.add(exec.submit(ExternalJobService::fetchJobicy));
        futures.add(exec.submit(ExternalJobService::fetchRemoteOK));

        for (Future<List<ExternalJob>> f : futures) {
            try {
                all.addAll(f.get(15, TimeUnit.SECONDS));
            } catch (Exception e) {
                System.err.println("[ExternalJobService] Source failed: " + e.getMessage());
            }
        }
        exec.shutdown();

        // Shuffle to mix sources, then cache
        Collections.shuffle(all);
        cachedJobs = all;
        cacheTimestamp = System.currentTimeMillis();
        System.out.println("[ExternalJobService] Fetched " + all.size() + " external jobs total.");
        return all;
    }

    /** Clears the cache, forcing next call to re-fetch. */
    public static void clearCache() {
        cachedJobs = null;
        cacheTimestamp = 0;
    }

    // ════════════════════════════════════════════
    // SOURCE 1: Remotive
    // ════════════════════════════════════════════
    private static List<ExternalJob> fetchRemotive() {
        List<ExternalJob> jobs = new ArrayList<>();
        try {
            String json = httpGet(REMOTIVE_URL);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("jobs");
            if (arr == null) return jobs;

            for (JsonElement el : arr) {
                JsonObject j = el.getAsJsonObject();
                ExternalJob job = new ExternalJob();
                job.setSource("Remotive");
                job.setTitle(getStr(j, "title"));
                job.setCompany(getStr(j, "company_name"));
                job.setLocation(getStr(j, "candidate_required_location", "Remote"));
                job.setSalary(getStr(j, "salary", ""));
                job.setJobType(normalizeJobType(getStr(j, "job_type", "full_time")));
                job.setCategory(getStr(j, "category", ""));
                job.setUrl(getStr(j, "url"));
                job.setDescription(truncateHtml(getStr(j, "description", ""), 150));
                job.setPublishedAt(getStr(j, "publication_date", ""));
                jobs.add(job);
            }
            System.out.println("[Remotive] Fetched " + jobs.size() + " jobs");
        } catch (Exception e) {
            System.err.println("[Remotive] Error: " + e.getMessage());
        }
        return jobs;
    }

    // ════════════════════════════════════════════
    // SOURCE 2: Arbeitnow
    // ════════════════════════════════════════════
    private static List<ExternalJob> fetchArbeitnow() {
        List<ExternalJob> jobs = new ArrayList<>();
        try {
            String json = httpGet(ARBEITNOW_URL);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("data");
            if (arr == null) return jobs;

            int limit = Math.min(arr.size(), 20);
            for (int i = 0; i < limit; i++) {
                JsonObject j = arr.get(i).getAsJsonObject();
                ExternalJob job = new ExternalJob();
                job.setSource("Arbeitnow");
                job.setTitle(getStr(j, "title"));
                job.setCompany(getStr(j, "company_name"));
                job.setLocation(getStr(j, "location", "Europe"));
                job.setSalary(""); // Arbeitnow doesn't reliably provide salary
                job.setDescription(truncateHtml(getStr(j, "description", ""), 150));
                job.setUrl(getStr(j, "url"));
                job.setPublishedAt(getStr(j, "created_at", ""));

                // Determine job type from boolean flags
                boolean remote = getBool(j, "remote");
                if (remote) {
                    job.setJobType("REMOTE");
                } else {
                    job.setJobType("ON-SITE");
                }

                // Tags as category
                if (j.has("tags") && j.get("tags").isJsonArray()) {
                    JsonArray tags = j.getAsJsonArray("tags");
                    StringBuilder sb = new StringBuilder();
                    for (int t = 0; t < Math.min(tags.size(), 3); t++) {
                        if (t > 0) sb.append(", ");
                        sb.append(tags.get(t).getAsString());
                    }
                    job.setCategory(sb.toString());
                }
                jobs.add(job);
            }
            System.out.println("[Arbeitnow] Fetched " + jobs.size() + " jobs");
        } catch (Exception e) {
            System.err.println("[Arbeitnow] Error: " + e.getMessage());
        }
        return jobs;
    }

    // ════════════════════════════════════════════
    // SOURCE 3: Jobicy
    // ════════════════════════════════════════════
    private static List<ExternalJob> fetchJobicy() {
        List<ExternalJob> jobs = new ArrayList<>();
        try {
            String json = httpGet(JOBICY_URL);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("jobs");
            if (arr == null) return jobs;

            for (JsonElement el : arr) {
                JsonObject j = el.getAsJsonObject();
                ExternalJob job = new ExternalJob();
                job.setSource("Jobicy");
                job.setTitle(getStr(j, "jobTitle"));
                job.setCompany(getStr(j, "companyName"));
                job.setLocation(getStr(j, "jobGeo", "Remote"));
                job.setSalary(buildJobicySalary(j));
                job.setJobType(normalizeJobType(getStr(j, "jobType", "full_time")));
                job.setCategory(getStr(j, "jobIndustry", ""));
                job.setUrl(getStr(j, "url"));
                job.setDescription(truncateHtml(getStr(j, "jobDescription", ""), 150));
                job.setPublishedAt(getStr(j, "pubDate", ""));
                jobs.add(job);
            }
            System.out.println("[Jobicy] Fetched " + jobs.size() + " jobs");
        } catch (Exception e) {
            System.err.println("[Jobicy] Error: " + e.getMessage());
        }
        return jobs;
    }

    // ════════════════════════════════════════════
    // SOURCE 4: RemoteOK
    // ════════════════════════════════════════════
    private static List<ExternalJob> fetchRemoteOK() {
        List<ExternalJob> jobs = new ArrayList<>();
        try {
            String json = httpGet(REMOTEOK_URL);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            // first element is metadata/legal notice, skip it
            int limit = Math.min(arr.size(), 21);
            for (int i = 1; i < limit; i++) {
                JsonObject j = arr.get(i).getAsJsonObject();
                ExternalJob job = new ExternalJob();
                job.setSource("RemoteOK");
                job.setTitle(getStr(j, "position"));
                job.setCompany(getStr(j, "company"));
                job.setLocation(getStr(j, "location", "Remote"));
                job.setSalary(buildRemoteOKSalary(j));
                job.setJobType("REMOTE");
                job.setUrl(getStr(j, "url", "https://remoteok.com"));
                job.setDescription(truncateHtml(getStr(j, "description", ""), 150));
                job.setPublishedAt(getStr(j, "date", ""));

                // Tags as category
                if (j.has("tags") && j.get("tags").isJsonArray()) {
                    JsonArray tags = j.getAsJsonArray("tags");
                    StringBuilder sb = new StringBuilder();
                    for (int t = 0; t < Math.min(tags.size(), 3); t++) {
                        if (t > 0) sb.append(", ");
                        sb.append(tags.get(t).getAsString());
                    }
                    job.setCategory(sb.toString());
                }
                jobs.add(job);
            }
            System.out.println("[RemoteOK] Fetched " + jobs.size() + " jobs");
        } catch (Exception e) {
            System.err.println("[RemoteOK] Error: " + e.getMessage());
        }
        return jobs;
    }

    // ════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════

    private static String httpGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "SynergyGig/1.0")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " from " + url);
        }
        return resp.body();
    }

    private static String getStr(JsonObject o, String key) {
        return getStr(o, key, "");
    }

    private static String getStr(JsonObject o, String key, String def) {
        if (o.has(key) && !o.get(key).isJsonNull()) {
            return o.get(key).getAsString();
        }
        return def;
    }

    private static boolean getBool(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }

    /** Strips HTML tags and truncates text. */
    private static String truncateHtml(String raw, int maxLen) {
        if (raw == null || raw.isEmpty()) return "";
        String text = raw.replaceAll("<[^>]*>", " ")
                         .replaceAll("&nbsp;", " ")
                         .replaceAll("&amp;", "&")
                         .replaceAll("&lt;", "<")
                         .replaceAll("&gt;", ">")
                         .replaceAll("\\s+", " ")
                         .trim();
        if (text.length() > maxLen) {
            return text.substring(0, maxLen) + "...";
        }
        return text;
    }

    /** Normalize varied job type strings to a consistent format. */
    private static String normalizeJobType(String raw) {
        if (raw == null) return "FULL-TIME";
        String up = raw.toUpperCase().replace("_", "-").replace(" ", "-").trim();
        return switch (up) {
            case "FULL-TIME", "FULL_TIME", "FULLTIME" -> "FULL-TIME";
            case "PART-TIME", "PART_TIME", "PARTTIME" -> "PART-TIME";
            case "CONTRACT" -> "CONTRACT";
            case "FREELANCE", "FREELANCER" -> "FREELANCE";
            case "INTERNSHIP", "INTERN" -> "INTERNSHIP";
            case "REMOTE" -> "REMOTE";
            case "ON-SITE", "ONSITE", "ON_SITE" -> "ON-SITE";
            default -> up.isEmpty() ? "FULL-TIME" : up;
        };
    }

    private static String buildJobicySalary(JsonObject j) {
        String min = getStr(j, "annualSalaryMin", "");
        String max = getStr(j, "annualSalaryMax", "");
        String cur = getStr(j, "salaryCurrency", "USD");
        if (!min.isEmpty() && !max.isEmpty()) {
            return cur + " " + min + " – " + max;
        } else if (!min.isEmpty()) {
            return cur + " " + min + "+";
        }
        return "";
    }

    private static String buildRemoteOKSalary(JsonObject j) {
        String min = getStr(j, "salary_min", "");
        String max = getStr(j, "salary_max", "");
        if (!min.isEmpty() && !max.isEmpty() && !"0".equals(min)) {
            return "$" + min + " – $" + max;
        }
        return "";
    }
}
