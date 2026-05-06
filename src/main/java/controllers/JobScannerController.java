package controllers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeType;
import javafx.scene.transform.Rotate;
import utils.AppConfig;
import utils.AppThreadPool;
import utils.SessionManager;
import utils.SoundManager;
import services.CvJobMatchService;
import entities.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for the Job Scanner module.
 * Calls the n8n "job-search" webhook to scrape LinkedIn (and optionally Reddit)
 * for job postings matching the user's query, then displays results as styled cards.
 */
public class JobScannerController implements Stoppable {

    @FXML private BorderPane rootPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> sourceCombo;
    @FXML private Button btnSearch;
    @FXML private Label headerStatus;

    // Filters
    @FXML private ComboBox<String> jobTypeCombo;
    @FXML private ComboBox<String> datePostedCombo;
    @FXML private TextField locationField;
    @FXML private Button btnReset;

    // Skills bar (created programmatically)
    private HBox skillsBar;
    private FlowPane skillsPane;

    // Collected training/cert skill labels used as CV fallback for matching
    private volatile String loadedSkillsText = null;

    // States
    @FXML private VBox emptyState;
    @FXML private VBox loadingState;
    @FXML private Label loadingLabel;
    @FXML private ScrollPane resultsScroll;
    @FXML private VBox resultsContainer;
    @FXML private StackPane contentArea;
    @FXML private StackPane radarContainer;

    private Timeline radarSpin;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Gson gson = new Gson();

    private volatile boolean searching = false;

    @FXML
    public void initialize() {
        sourceCombo.setItems(FXCollections.observableArrayList("All", "LinkedIn", "Reddit", "RSS"));
        sourceCombo.setValue("All");

        jobTypeCombo.setItems(FXCollections.observableArrayList(
                "All Types", "Full-time", "Part-time", "Contract", "Internship", "Freelance", "Temporary", "Volunteer"));
        jobTypeCombo.setValue("All Types");

        datePostedCombo.setItems(FXCollections.observableArrayList(
                "Any time", "Past 24 hours", "Past week", "Past month"));
        datePostedCombo.setValue("Any time");

        // Build skills bar programmatically and insert after filter bar
        skillsPane = new FlowPane(6, 6);
        skillsPane.getStyleClass().add("js-skills-pane");
        HBox.setHgrow(skillsPane, Priority.ALWAYS);

        Label skillsLabel = new Label("\uD83C\uDF93 My Skills:");
        skillsLabel.getStyleClass().add("js-skills-label");

        skillsBar = new HBox(8, skillsLabel, skillsPane);
        skillsBar.setAlignment(Pos.CENTER_LEFT);
        skillsBar.getStyleClass().add("js-skills-bar");
        skillsBar.setVisible(false);
        skillsBar.setManaged(false);

        // Insert into the top VBox (after filter bar)
        VBox topBar = (VBox) rootPane.getTop();
        topBar.getChildren().add(skillsBar);

        // Focus the search field
        Platform.runLater(() -> searchField.requestFocus());

        // Load user skills from certificates & completed courses
        loadUserSkills();
    }

    // ════════════════════════════════════════════════════════
    //  SKILLS FROM CERTIFICATES & COURSES
    // ════════════════════════════════════════════════════════

    /**
     * Loads the current user's completed course titles + categories as
     * clickable skill pills in the skills bar. Each pill pre-fills the
     * search field and fires a search when clicked.
     */
    private void loadUserSkills() {
        AppThreadPool.io(() -> {
            try {
                int userId = SessionManager.getInstance().getCurrentUser().getId();

                // 1. Get all enrollments for this user
                var enrollmentSvc = new services.ServiceTrainingEnrollment();
                var enrollments = enrollmentSvc.getByUser(userId);

                // 2. Get all courses (cached)
                var courseSvc = new services.ServiceTrainingCourse();
                var allCourses = courseSvc.recuperer();
                Map<Integer, entities.TrainingCourse> courseMap = new HashMap<>();
                for (var c : allCourses) courseMap.put(c.getId(), c);

                // 3. Get certificates for this user
                var certSvc = new services.ServiceTrainingCertificate();
                var certs = certSvc.getByUser(userId);
                Set<Integer> certifiedCourseIds = new HashSet<>();
                for (var cert : certs) certifiedCourseIds.add(cert.getCourseId());

                // 4. Build skill keywords from completed/certified courses
                //    Prioritize certified courses, then completed enrollments
                List<SkillPill> pills = new ArrayList<>();
                Set<String> seen = new LinkedHashSet<>();

                // Certified courses first (with 🎓 prefix)
                for (int courseId : certifiedCourseIds) {
                    entities.TrainingCourse course = courseMap.get(courseId);
                    if (course != null && seen.add(course.getTitle().toLowerCase())) {
                        pills.add(new SkillPill(course.getTitle(), true, course.getCategory()));
                    }
                }

                // Completed (but not yet certified) enrollments
                for (var enr : enrollments) {
                    if ("COMPLETED".equalsIgnoreCase(enr.getStatus()) || enr.getProgress() >= 100) {
                        entities.TrainingCourse course = courseMap.get(enr.getCourseId());
                        if (course != null && seen.add(course.getTitle().toLowerCase())) {
                            pills.add(new SkillPill(course.getTitle(), false, course.getCategory()));
                        }
                    }
                }

                // Also add unique categories as generic skill pills
                for (var enr : enrollments) {
                    if ("COMPLETED".equalsIgnoreCase(enr.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(enr.getStatus())) {
                        entities.TrainingCourse course = courseMap.get(enr.getCourseId());
                        if (course != null && course.getCategory() != null) {
                            String cat = course.getCategory().replace("_", " ");
                            if (seen.add(cat.toLowerCase())) {
                                pills.add(new SkillPill(cat, false, course.getCategory()));
                            }
                        }
                    }
                }

                // Store skill text for CV matching fallback
                if (!pills.isEmpty()) {
                    loadedSkillsText = pills.stream()
                            .map(p -> p.label)
                            .collect(java.util.stream.Collectors.joining(" "));
                }

                Platform.runLater(() -> {
                    if (pills.isEmpty()) {
                        skillsBar.setVisible(false);
                        skillsBar.setManaged(false);
                    } else {
                        skillsPane.getChildren().clear();
                        for (SkillPill pill : pills) {
                            Button btn = new Button((pill.certified ? "\uD83C\uDF93 " : "") + pill.label);
                            btn.getStyleClass().add("js-skill-pill");
                            if (pill.certified) btn.getStyleClass().add("js-skill-certified");
                            btn.setCursor(Cursor.HAND);
                            btn.setOnAction(e -> {
                                SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
                                searchField.setText(pill.label);
                                handleSearch();
                            });

                            // Tooltip with category
                            if (pill.category != null && !pill.category.isEmpty()) {
                                Tooltip tip = new Tooltip((pill.certified ? "Certified" : "Completed")
                                        + " — " + pill.category.replace("_", " "));
                                tip.setShowDelay(javafx.util.Duration.millis(300));
                                btn.setTooltip(tip);
                            }
                            skillsPane.getChildren().add(btn);
                        }
                        skillsBar.setVisible(true);
                        skillsBar.setManaged(true);
                    }
                });

            } catch (Exception e) {
                // Skills bar is optional — silently hide on error
                Platform.runLater(() -> {
                    skillsBar.setVisible(false);
                    skillsBar.setManaged(false);
                });
            }
        });
    }

    private static class SkillPill {
        final String label;
        final boolean certified;
        final String category;
        SkillPill(String label, boolean certified, String category) {
            this.label = label;
            this.certified = certified;
            this.category = category;
        }
    }

    @FXML
    private void handleSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            searchField.setStyle("-fx-border-color: #ff4444;");
            return;
        }
        searchField.setStyle("");

        if (searching) return;
        searching = true;

        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);

        String source = sourceCombo.getValue().toLowerCase();
        String jobType = jobTypeCombo.getValue();
        String datePosted = datePostedCombo.getValue();
        String location = locationField.getText().trim();

        // Show loading
        showState("loading");
        loadingLabel.setText("Searching " + sourceCombo.getValue() + " for \"" + query + "\"...");
        btnSearch.setDisable(true);
        headerStatus.setText("Searching...");

        AppThreadPool.io(() -> {
            try {
                String webhookUrl = AppConfig.get("n8n.webhook_url", "https://n8n.benzaitsue.work.gd");
                String url = webhookUrl + "/webhook/job-search";

                JsonObject body = new JsonObject();
                body.addProperty("query", query);
                body.addProperty("source", source);
                body.addProperty("jobType", jobType.equals("All Types") ? "all" : jobType.toLowerCase());
                body.addProperty("datePosted", datePosted.equals("Any time") ? "any" : datePosted.toLowerCase());
                body.addProperty("location", location);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    JsonObject result = gson.fromJson(resp.body(), JsonObject.class);
                    JsonArray results = result.has("results") ? result.getAsJsonArray("results") : new JsonArray();
                    int count = result.has("count") ? result.get("count").getAsInt() : results.size();

                    Platform.runLater(() -> {
                        searching = false;
                        btnSearch.setDisable(false);
                        if (count == 0) {
                            headerStatus.setText("No results found");
                            showState("empty");
                            ((Label) emptyState.getChildren().get(1)).setText("No jobs found for \"" + query + "\"");
                            ((Label) emptyState.getChildren().get(2)).setText("Try a different keyword or source");
                        } else {
                            headerStatus.setText(count + " job" + (count > 1 ? "s" : "") + " found");
                            buildResultCards(results);
                            showState("results");
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        searching = false;
                        btnSearch.setDisable(false);
                        headerStatus.setText("Error: HTTP " + resp.statusCode());
                        showState("empty");
                        ((Label) emptyState.getChildren().get(1)).setText("Search failed");
                        ((Label) emptyState.getChildren().get(2)).setText("n8n returned HTTP " + resp.statusCode());
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    searching = false;
                    btnSearch.setDisable(false);
                    headerStatus.setText("Error");
                    showState("empty");
                    ((Label) emptyState.getChildren().get(1)).setText("Connection failed");
                    ((Label) emptyState.getChildren().get(2)).setText(e.getMessage() != null ? e.getMessage() : "Network error");
                });
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  BUILD RESULT CARDS
    // ════════════════════════════════════════════════════════

    private void buildResultCards(JsonArray results) {
        resultsContainer.getChildren().clear();

        // CV scoring context
        CvJobMatchService cvMatcher = new CvJobMatchService();
        User currentUser = SessionManager.getInstance().getCurrentUser();
        String cvText = (currentUser != null && currentUser.getCvSkillsText() != null)
                ? currentUser.getCvSkillsText() : null;
        // Reject hash-like references (e.g. "abc123 pdf") — not actual skill text
        if (cvText != null && cvText.trim().split("\\s+").length < 5) {
            cvText = null;
        }
        // Fallback: use training/cert skill labels collected from loadUserSkills()
        if ((cvText == null || cvText.isBlank()) && loadedSkillsText != null) {
            cvText = loadedSkillsText;
        }

        for (JsonElement el : results) {
            JsonObject job = el.getAsJsonObject();
            String source = getStr(job, "source");
            String title = getStr(job, "title");
            String company = getStr(job, "company");
            String location = getStr(job, "location");
            String url = getStr(job, "url");
            String postedAt = getStr(job, "postedAt");
            String snippet = getStr(job, "snippet");
            String jobType = getStr(job, "jobType");

            // Card container
            VBox card = new VBox(6);
            card.getStyleClass().add("js-job-card");
            card.setPadding(new Insets(14, 18, 14, 18));

            // Row 1: Source badge + Job type badge + Title
            HBox titleRow = new HBox(8);
            titleRow.setAlignment(Pos.CENTER_LEFT);

            String badgeText, badgeClass;
            switch (source) {
                case "LinkedIn": badgeText = "in"; badgeClass = "js-badge-linkedin"; break;
                case "Reddit":   badgeText = "r/"; badgeClass = "js-badge-reddit"; break;
                case "RSS":      badgeText = "RSS"; badgeClass = "js-badge-rss"; break;
                default:         badgeText = source; badgeClass = "js-badge-rss"; break;
            }
            Label sourceBadge = new Label(badgeText);
            sourceBadge.getStyleClass().add(badgeClass);
            sourceBadge.setMinWidth(28);
            sourceBadge.setAlignment(Pos.CENTER);
            titleRow.getChildren().add(sourceBadge);

            // Job type badge
            if (!jobType.isEmpty()) {
                Label jtBadge = new Label(jobType);
                jtBadge.getStyleClass().addAll("js-badge-jobtype", "js-type-" + jobType.toLowerCase().replace(" ", "-"));
                titleRow.getChildren().add(jtBadge);
            }

            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("js-job-title");
            titleLabel.setWrapText(true);
            HBox.setHgrow(titleLabel, Priority.ALWAYS);
            titleRow.getChildren().add(titleLabel);

            // Row 2: Company + Location + Date
            HBox metaRow = new HBox(16);
            metaRow.setAlignment(Pos.CENTER_LEFT);
            metaRow.getStyleClass().add("js-meta-row");

            if (!company.isEmpty()) {
                Label compLabel = new Label("\uD83C\uDFE2 " + company);
                compLabel.getStyleClass().add("js-meta-text");
                metaRow.getChildren().add(compLabel);
            }
            // Always show location — default to "Not specified"
            String locDisplay = location.isEmpty() || location.equalsIgnoreCase("not specified") ? "Not specified" : location;
            Label locLabel = new Label("\uD83D\uDCCD " + locDisplay);
            locLabel.getStyleClass().add(location.isEmpty() || location.equalsIgnoreCase("not specified") ? "js-meta-muted" : "js-meta-text");
            metaRow.getChildren().add(locLabel);

            if (!postedAt.isEmpty()) {
                String timeAgo = formatTimeAgo(postedAt);
                Label dateLabel = new Label("\uD83D\uDD50 " + timeAgo);
                dateLabel.getStyleClass().add("js-meta-text");
                metaRow.getChildren().add(dateLabel);
            }

            card.getChildren().addAll(titleRow, metaRow);

            // CV Match score badge (only when user has uploaded a CV)
            if (cvText != null && !cvText.isBlank()) {
                Map<String, Object> matchResult = cvMatcher.score(cvText, title, snippet);
                int score = (int) matchResult.get("score");
                String scoreLabel = (String) matchResult.get("label");
                @SuppressWarnings("unchecked")
                List<String> matched = (List<String>) matchResult.get("matched");

                String scoreColor;
                if (score >= 70)      scoreColor = "#8b5cf6"; // purple - Excellent
                else if (score >= 50) scoreColor = "#22c55e"; // green  - Good
                else if (score >= 30) scoreColor = "#f97316"; // orange - Fair
                else                  scoreColor = "#ef4444"; // red    - Low

                HBox matchRow = new HBox(8);
                matchRow.setAlignment(Pos.CENTER_LEFT);

                Label scoreBadge = new Label(score + "% Match — " + scoreLabel);
                scoreBadge.setStyle("-fx-background-color:" + scoreColor + "20; -fx-text-fill:" + scoreColor
                        + "; -fx-background-radius:4; -fx-padding:2 8 2 8; -fx-font-size:11; -fx-font-weight:bold;");

                matchRow.getChildren().add(scoreBadge);

                if (!matched.isEmpty()) {
                    String skillList = String.join(", ", matched.subList(0, Math.min(5, matched.size())));
                    Label skillsHint = new Label("Skills: " + skillList);
                    skillsHint.setStyle("-fx-font-size:11; -fx-text-fill:#888;");
                    skillsHint.setWrapText(true);
                    matchRow.getChildren().add(skillsHint);
                }

                card.getChildren().add(matchRow);
            }

            // Row 3: Snippet (if available)
            if (!snippet.isEmpty()) {
                Label snippetLabel = new Label(snippet);
                snippetLabel.getStyleClass().add("js-snippet");
                snippetLabel.setWrapText(true);
                snippetLabel.setMaxHeight(40);
                card.getChildren().add(snippetLabel);
            }

            // Row 4: Open link button
            if (!url.isEmpty()) {
                HBox actionRow = new HBox();
                actionRow.setAlignment(Pos.CENTER_RIGHT);
                Button openBtn = new Button("Open \u2192");
                openBtn.getStyleClass().add("js-open-btn");
                openBtn.setCursor(Cursor.HAND);
                openBtn.setOnAction(e -> openUrl(url));
                actionRow.getChildren().add(openBtn);
                card.getChildren().add(actionRow);
            }

            // Click entire card to open
            if (!url.isEmpty()) {
                card.setCursor(Cursor.HAND);
                card.setOnMouseClicked(e -> openUrl(url));
            }

            resultsContainer.getChildren().add(card);
        }
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    @FXML
    private void handleReset() {
        jobTypeCombo.setValue("All Types");
        datePostedCombo.setValue("Any time");
        locationField.clear();
        sourceCombo.setValue("All");
        searchField.clear();
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
    }

    private void showState(String state) {
        emptyState.setVisible("empty".equals(state));
        emptyState.setManaged("empty".equals(state));
        loadingState.setVisible("loading".equals(state));
        loadingState.setManaged("loading".equals(state));
        resultsScroll.setVisible("results".equals(state));
        resultsScroll.setManaged("results".equals(state));

        // Rebuild & start radar on loading; stop otherwise
        if ("loading".equals(state)) {
            buildRadarLoader();
            if (radarSpin != null) radarSpin.play();
        } else if (radarSpin != null) {
            radarSpin.stop();
        }
    }

    /**
     * Builds a radar-style animated loader with theme-aware colors.
     * Uses a fixed-size Pane so StackPane centering stays rock-solid
     * even while the blurred sweep rotates.
     */
    private void buildRadarLoader() {
        if (radarContainer == null) return;

        boolean dark = SessionManager.getInstance().isDarkTheme();
        double size = 150;
        double cx = size / 2, cy = size / 2;

        // ── Theme colors ──
        Color glowColor, borderColor, dashedColor, sweepColor, bgColor, crossColor;
        if (dark) {
            bgColor     = Color.web("#0D0E12");
            borderColor = Color.web("#2C666E");
            dashedColor = Color.web("#1E4A50");
            crossColor  = Color.web("#1A3D42");
            sweepColor  = Color.web("#2C666E");
            glowColor   = Color.web("#90DDF0");
        } else {
            bgColor     = Color.web("#1A1215");
            borderColor = Color.web("#613039");
            dashedColor = Color.web("#4A252E");
            crossColor  = Color.web("#3D1E26");
            sweepColor  = Color.web("#8B3A4A");
            glowColor   = Color.web("#DE95A2");
        }

        // ── Background fill ──
        Circle background = new Circle(cx, cy, cx);
        background.setFill(bgColor);

        // ── Outer ring (accent-colored border with subtle glow) ──
        Circle outerRing = new Circle(cx, cy, cx - 1);
        outerRing.setFill(Color.TRANSPARENT);
        outerRing.setStroke(borderColor);
        outerRing.setStrokeWidth(1.8);
        outerRing.setEffect(new DropShadow(12, 0, 0, borderColor.deriveColor(0, 1, 1, 0.4)));

        // ── Middle dashed ring ──
        Circle middleRing = new Circle(cx, cy, cx * 0.66);
        middleRing.setFill(Color.TRANSPARENT);
        middleRing.setStroke(dashedColor);
        middleRing.setStrokeWidth(0.8);
        middleRing.getStrokeDashArray().addAll(6.0, 4.0);

        // ── Inner dashed ring ──
        Circle innerRing = new Circle(cx, cy, cx * 0.33);
        innerRing.setFill(Color.TRANSPARENT);
        innerRing.setStroke(dashedColor);
        innerRing.setStrokeWidth(0.8);
        innerRing.getStrokeDashArray().addAll(4.0, 3.0);

        // ── Cross-hair lines ──
        Line hLine = new Line(1, cy, size - 1, cy);
        hLine.setStroke(crossColor);
        hLine.setStrokeWidth(0.6);

        Line vLine = new Line(cx, 1, cx, size - 1);
        vLine.setStroke(crossColor);
        vLine.setStrokeWidth(0.6);

        // ── Center dot with glow ──
        Circle centerDot = new Circle(cx, cy, 4);
        centerDot.setFill(glowColor);
        centerDot.setEffect(new DropShadow(10, 0, 0, glowColor));

        // ── Sweep arm (the rotating element) ──
        Arc sweepArc = new Arc(cx, cy, cx - 3, cx - 3, 0, 55);
        sweepArc.setType(ArcType.ROUND);
        sweepArc.setFill(sweepColor.deriveColor(0, 1, 1, 0.35));
        sweepArc.setStroke(Color.TRANSPARENT);
        sweepArc.setEffect(new GaussianBlur(20));

        Arc innerCone = new Arc(cx, cy, (cx - 3) * 0.5, (cx - 3) * 0.5, 0, 40);
        innerCone.setType(ArcType.ROUND);
        innerCone.setFill(sweepColor.deriveColor(0, 1, 1, 0.28));
        innerCone.setStroke(Color.TRANSPARENT);
        innerCone.setEffect(new GaussianBlur(8));

        Line sweepEdge = new Line(cx, cy, cx + (cx - 3), cy);
        sweepEdge.setStroke(glowColor);
        sweepEdge.setStrokeWidth(1.5);
        sweepEdge.setStrokeLineCap(StrokeLineCap.ROUND);

        javafx.scene.Group sweepGroup = new javafx.scene.Group(sweepArc, innerCone, sweepEdge);
        Rotate sweepRotate = new Rotate(0, cx, cy);
        sweepGroup.getTransforms().add(sweepRotate);

        // ── Use a fixed-size Pane (not Group) so bounds never shift ──
        Pane radarPane = new Pane(
                background, hLine, vLine, outerRing, middleRing, innerRing,
                sweepGroup, centerDot
        );
        radarPane.setMinSize(size, size);
        radarPane.setPrefSize(size, size);
        radarPane.setMaxSize(size, size);
        // Circular clip on the Pane itself
        Circle clip = new Circle(cx, cy, cx);
        radarPane.setClip(clip);
        radarPane.setMouseTransparent(true);

        radarContainer.getChildren().setAll(radarPane);

        // ── Timeline drives the Rotate transform (explicit pivot, rock-solid) ──
        radarSpin = new Timeline(
                new KeyFrame(javafx.util.Duration.ZERO,
                        new KeyValue(sweepRotate.angleProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(javafx.util.Duration.seconds(3),
                        new KeyValue(sweepRotate.angleProperty(), 360, Interpolator.LINEAR))
        );
        radarSpin.setCycleCount(Animation.INDEFINITE);
    }

    private void openUrl(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
            // Fallback: copy to clipboard
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(url);
            clipboard.setContent(cc);
            headerStatus.setText("Link copied to clipboard");
        }
    }

    private String formatTimeAgo(String dateStr) {
        try {
            LocalDate posted = LocalDate.parse(dateStr.substring(0, 10));
            long days = ChronoUnit.DAYS.between(posted, LocalDate.now());
            if (days == 0) return "Today";
            if (days == 1) return "Yesterday";
            if (days < 7) return days + " days ago";
            if (days < 30) return (days / 7) + " week" + (days / 7 > 1 ? "s" : "") + " ago";
            return posted.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
        } catch (Exception e) {
            return dateStr;
        }
    }

    private static String getStr(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString().trim() : "";
    }

    @Override
    public void stop() {
        searching = false;
        if (radarSpin != null) radarSpin.stop();
    }
}
