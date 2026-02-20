package utils;

import com.google.gson.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

/**
 * Lightweight HTTP client for the SynergyGig REST API.
 * Used when {@code app.mode=api} to replace direct JDBC calls.
 */
public class ApiClient {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String BASE_URL = AppConfig.getRestBaseUrl();
    private static final Gson gson = new GsonBuilder().create();

    /** Send a GET request, return the response body as a JsonElement. */
    public static JsonElement get(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.err.println("❌ API GET " + path + " → " + resp.statusCode() + ": " + resp.body());
                return null;
            }
            return JsonParser.parseString(resp.body());
        } catch (Exception e) {
            System.err.println("❌ API GET " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    /** Send a POST request with JSON body. */
    public static JsonElement post(String path, Object body) {
        try {
            String json = gson.toJson(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.err.println("❌ API POST " + path + " → " + resp.statusCode() + ": " + resp.body());
                return null;
            }
            return JsonParser.parseString(resp.body());
        } catch (Exception e) {
            System.err.println("❌ API POST " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    /** Send a PUT request with JSON body. */
    public static JsonElement put(String path, Object body) {
        try {
            String json = gson.toJson(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .PUT(BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.err.println("❌ API PUT " + path + " → " + resp.statusCode() + ": " + resp.body());
                return null;
            }
            return JsonParser.parseString(resp.body());
        } catch (Exception e) {
            System.err.println("❌ API PUT " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    /** Send a DELETE request. */
    public static JsonElement delete(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Accept", "application/json")
                    .DELETE()
                    .build();
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.err.println("❌ API DELETE " + path + " → " + resp.statusCode() + ": " + resp.body());
                return null;
            }
            return JsonParser.parseString(resp.body());
        } catch (Exception e) {
            System.err.println("❌ API DELETE " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════
    //  FILE UPLOAD / DOWNLOAD
    // ═══════════════════════════════════════════

    /**
     * Upload a file via multipart/form-data.
     * Returns the server response JSON (file_id, filename, size, content_type).
     */
    public static JsonElement uploadFile(String path, File file) {
        try {
            String boundary = "----SynergyGig" + System.currentTimeMillis();

            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) mimeType = "application/octet-stream";

            byte[] fileBytes = Files.readAllBytes(file.toPath());

            // Build multipart body manually
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n"
                    + "Content-Type: " + mimeType + "\r\n\r\n";
            body.write(header.getBytes(StandardCharsets.UTF_8));
            body.write(fileBytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            // Use a separate client with longer timeouts for large files
            HttpClient uploadClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(BodyPublishers.ofByteArray(body.toByteArray()))
                    .timeout(Duration.ofMinutes(10))
                    .build();

            HttpResponse<String> resp = uploadClient.send(req, BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.err.println("❌ API UPLOAD " + path + " → " + resp.statusCode() + ": " + resp.body());
                return null;
            }
            return JsonParser.parseString(resp.body());
        } catch (Exception e) {
            System.err.println("❌ API UPLOAD " + path + " failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Download a file and save it to the destination path.
     * Returns true on success.
     */
    public static boolean downloadFile(String path, File destination) {
        try {
            HttpClient downloadClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .GET()
                    .timeout(Duration.ofMinutes(10))
                    .build();

            HttpResponse<byte[]> resp = downloadClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 400) {
                System.err.println("❌ API DOWNLOAD " + path + " → " + resp.statusCode());
                return false;
            }

            Files.write(destination.toPath(), resp.body());
            return true;
        } catch (Exception e) {
            System.err.println("❌ API DOWNLOAD " + path + " failed: " + e.getMessage());
            return false;
        }
    }
}
