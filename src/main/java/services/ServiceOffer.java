package services;

import com.google.gson.*;
import entities.Offer;
import utils.ApiClient;
import utils.AppConfig;
import utils.MyDatabase;
import utils.InMemoryCache;

import java.sql.*;
import java.util.*;

public class ServiceOffer implements IService<Offer> {

    private final boolean useApi;

    private static final String CACHE_KEY = "offers:all";
    private static final int CACHE_TTL = 120;

    public ServiceOffer() {
        useApi = AppConfig.isApiMode();
    }

    // ==================== JSON helpers ====================

    private Offer jsonToOffer(JsonObject obj) {
        Timestamp createdAt = null;
        if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
            String raw = obj.get("created_at").getAsString().replace("T", " ");
            createdAt = Timestamp.valueOf(raw);
        }
        java.sql.Date startDate = null;
        if (obj.has("start_date") && !obj.get("start_date").isJsonNull()) {
            startDate = java.sql.Date.valueOf(obj.get("start_date").getAsString());
        }
        java.sql.Date endDate = null;
        if (obj.has("end_date") && !obj.get("end_date").isJsonNull()) {
            endDate = java.sql.Date.valueOf(obj.get("end_date").getAsString());
        }
        Integer departmentId = null;
        if (obj.has("department_id") && !obj.get("department_id").isJsonNull()) {
            departmentId = obj.get("department_id").getAsInt();
        }

        Offer o = new Offer(
            obj.get("id").getAsInt(),
            obj.has("title") && !obj.get("title").isJsonNull() ? obj.get("title").getAsString() : "",
            obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString() : "",
            obj.has("offer_type") && !obj.get("offer_type").isJsonNull() ? obj.get("offer_type").getAsString() : "FREELANCE",
            obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "DRAFT",
            obj.has("required_skills") && !obj.get("required_skills").isJsonNull() ? obj.get("required_skills").getAsString() : "",
            obj.has("location") && !obj.get("location").isJsonNull() ? obj.get("location").getAsString() : "",
            obj.has("amount") && !obj.get("amount").isJsonNull() ? obj.get("amount").getAsDouble() : 0,
            obj.has("currency") && !obj.get("currency").isJsonNull() ? obj.get("currency").getAsString() : "USD",
            obj.has("owner_id") && !obj.get("owner_id").isJsonNull() ? obj.get("owner_id").getAsInt() : 0,
            departmentId,
            startDate,
            endDate,
            createdAt
        );
        return o;
    }

    private List<Offer> jsonArrayToOffers(JsonElement el) {
        List<Offer> offers = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                offers.add(jsonToOffer(item.getAsJsonObject()));
            }
        }
        return offers;
    }

    // ==================== CRUD ====================

    @Override
    public void ajouter(Offer o) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("title", o.getTitle());
            body.put("description", o.getDescription());
            body.put("offer_type", o.getOfferType());
            body.put("status", o.getStatus());
            body.put("required_skills", o.getRequiredSkills());
            body.put("location", o.getLocation());
            body.put("amount", o.getAmount());
            body.put("currency", o.getCurrency());
            body.put("owner_id", o.getOwnerId());
            body.put("department_id", o.getDepartmentId());
            body.put("start_date", o.getStartDate() != null ? o.getStartDate().toString() : null);
            body.put("end_date", o.getEndDate() != null ? o.getEndDate().toString() : null);
            JsonElement resp = ApiClient.post("/offers", body);
            if (resp != null && resp.isJsonObject()) {
                o.setId(resp.getAsJsonObject().get("id").getAsInt());
            }
            return;
        }
        String sql = "INSERT INTO offers (title, description, offer_type, status, required_skills, location, amount, currency, owner_id, department_id, start_date, end_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, o.getTitle());
            ps.setString(2, o.getDescription());
            ps.setString(3, o.getOfferType());
            ps.setString(4, o.getStatus());
            ps.setString(5, o.getRequiredSkills());
            ps.setString(6, o.getLocation());
            ps.setDouble(7, o.getAmount());
            ps.setString(8, o.getCurrency());
            ps.setInt(9, o.getOwnerId());
            if (o.getDepartmentId() != null) ps.setInt(10, o.getDepartmentId());
            else ps.setNull(10, Types.INTEGER);
            ps.setDate(11, o.getStartDate());
            ps.setDate(12, o.getEndDate());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) o.setId(keys.getInt(1));
            }
        }
        InMemoryCache.evictByPrefix("offers:");
    }

    @Override
    public void modifier(Offer o) throws SQLException {
        if (useApi) {
            Map<String, Object> body = new HashMap<>();
            body.put("title", o.getTitle());
            body.put("description", o.getDescription());
            body.put("offer_type", o.getOfferType());
            body.put("status", o.getStatus());
            body.put("required_skills", o.getRequiredSkills());
            body.put("location", o.getLocation());
            body.put("amount", o.getAmount());
            body.put("currency", o.getCurrency());
            body.put("owner_id", o.getOwnerId());
            body.put("department_id", o.getDepartmentId());
            body.put("start_date", o.getStartDate() != null ? o.getStartDate().toString() : null);
            body.put("end_date", o.getEndDate() != null ? o.getEndDate().toString() : null);
            ApiClient.put("/offers/" + o.getId(), body);
            return;
        }
        String sql = "UPDATE offers SET title=?, description=?, offer_type=?, status=?, required_skills=?, location=?, amount=?, currency=?, owner_id=?, department_id=?, start_date=?, end_date=? WHERE id=?";
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, o.getTitle());
            ps.setString(2, o.getDescription());
            ps.setString(3, o.getOfferType());
            ps.setString(4, o.getStatus());
            ps.setString(5, o.getRequiredSkills());
            ps.setString(6, o.getLocation());
            ps.setDouble(7, o.getAmount());
            ps.setString(8, o.getCurrency());
            ps.setInt(9, o.getOwnerId());
            if (o.getDepartmentId() != null) ps.setInt(10, o.getDepartmentId());
            else ps.setNull(10, Types.INTEGER);
            ps.setDate(11, o.getStartDate());
            ps.setDate(12, o.getEndDate());
            ps.setInt(13, o.getId());
            ps.executeUpdate();
        }
        InMemoryCache.evictByPrefix("offers:");
    }

    @Override
    public void supprimer(int id) throws SQLException {
        if (useApi) {
            ApiClient.delete("/offers/" + id);
            return;
        }
        // Cascade: delete applications and contracts referencing this offer
        try (Connection conn = MyDatabase.getInstance().getConnection()) {
            try (PreparedStatement delApps = conn.prepareStatement("DELETE FROM job_applications WHERE offer_id = ?")) {
                delApps.setInt(1, id);
                delApps.executeUpdate();
            }
            try (PreparedStatement delContracts = conn.prepareStatement("DELETE FROM contracts WHERE offer_id = ?")) {
                delContracts.setInt(1, id);
                delContracts.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM offers WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        }
        InMemoryCache.evictByPrefix("offers:");
    }

    @Override
    public List<Offer> recuperer() throws SQLException {
        if (useApi) {
            return InMemoryCache.getOrLoad(CACHE_KEY, CACHE_TTL,
                    () -> jsonArrayToOffers(ApiClient.get("/offers")));
        }
        return InMemoryCache.getOrLoadChecked(CACHE_KEY, CACHE_TTL,
                () -> recupererFromDb());
    }

    private List<Offer> recupererFromDb() throws SQLException {
        List<Offer> offers = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM offers ORDER BY created_at DESC")) {
            while (rs.next()) offers.add(rowToOffer(rs));
        }
        return offers;
    }

    /** Get offers owned by a specific user. */
    public List<Offer> getByOwner(int ownerId) throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/offers/owner/" + ownerId);
            return jsonArrayToOffers(el);
        }
        List<Offer> offers = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM offers WHERE owner_id = ? ORDER BY created_at DESC")) {
            ps.setInt(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) offers.add(rowToOffer(rs));
            }
        }
        return offers;
    }

    /** Get all open offers (marketplace). */
    public List<Offer> getOpen() throws SQLException {
        if (useApi) {
            JsonElement el = ApiClient.get("/offers/open");
            return jsonArrayToOffers(el);
        }
        List<Offer> offers = new ArrayList<>();
        try (Connection conn = MyDatabase.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM offers WHERE status='OPEN' ORDER BY created_at DESC")) {
            while (rs.next()) offers.add(rowToOffer(rs));
        }
        return offers;
    }

    private Offer rowToOffer(ResultSet rs) throws SQLException {
        Integer departmentId = rs.getInt("department_id");
        if (rs.wasNull()) departmentId = null;
        return new Offer(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("offer_type"),
            rs.getString("status"),
            rs.getString("required_skills"),
            rs.getString("location"),
            rs.getDouble("amount"),
            rs.getString("currency"),
            rs.getInt("owner_id"),
            departmentId,
            rs.getDate("start_date"),
            rs.getDate("end_date"),
            rs.getTimestamp("created_at")
        );
    }
}
