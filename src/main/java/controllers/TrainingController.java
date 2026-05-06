package controllers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import entities.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import services.*;
import utils.AppConfig;
import utils.AppThreadPool;
import utils.CardEffects;
import utils.DialogHelper;
import utils.SessionManager;
import utils.SoundManager;
import utils.TrainingCertificatePdf;
import services.ZAIService;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.SnapshotParameters;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class TrainingController {

    @FXML private BorderPane rootPane;
    @FXML private HBox tabBar;
    @FXML private StackPane contentArea;
    @FXML private Label headerTitle;
    @FXML private Label headerRole;

    private final ServiceTrainingCourse serviceCourse = new ServiceTrainingCourse();
    private final ServiceTrainingEnrollment serviceEnrollment = new ServiceTrainingEnrollment();
    private final ServiceTrainingCertificate serviceCertificate = new ServiceTrainingCertificate();
    private final ServiceUser serviceUser = new ServiceUser();

    private User currentUser;
    private boolean isHrOrAdmin;
    private Button activeTab;
    private Region trAmbienceIndicator;

    private Map<Integer, TrainingCourse> courseMap = new HashMap<>();

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private static final Gson gson = new Gson();

    @FXML
    public void initialize() {
        currentUser = SessionManager.getInstance().getCurrentUser();
        String role = currentUser != null ? currentUser.getRole() : "";
        isHrOrAdmin = "ADMIN".equals(role) || "HR_MANAGER".equals(role);

        headerRole.setText(isHrOrAdmin ? "Manager View" : "Learner View");

        loadUserNames();

        List<String[]> tabs = new ArrayList<>();
        tabs.add(new String[]{"📊", "Dashboard"});
        tabs.add(new String[]{"📚", "Catalog"});
        tabs.add(new String[]{"📖", "My Learning"});
        tabs.add(new String[]{"🏆", "Certificates"});
        if (isHrOrAdmin) {
            tabs.add(new String[]{"⚙", "Manage"});
        }

        for (String[] tab : tabs) {
            Button btn = new Button(tab[0] + "  " + tab[1]);
            btn.getStyleClass().add("tr-tab-btn");
            btn.setUserData(tab[1]);
            btn.setOnAction(e -> {
                SoundManager.getInstance().play(SoundManager.TAB_SWITCH);
                switchTab(btn, tab[1]);
            });
            tabBar.getChildren().add(btn);
        }

        // Spotlight Pill Navbar
        setupSpotlightPillNavbar();

        // Load course map in background, then trigger first tab
        AppThreadPool.io(() -> {
            loadCourseMap();
            Platform.runLater(() -> {
                if (!tabBar.getChildren().isEmpty()) {
                    Button first = (Button) tabBar.getChildren().get(0);
                    switchTab(first, "Dashboard");
                }
            });
        });
    }

    private void loadUserNames() {
        utils.UserNameCache.refresh();
    }

    private void loadCourseMap() {
        try {
            for (TrainingCourse c : serviceCourse.recuperer()) {
                courseMap.put(c.getId(), c);
            }
        } catch (SQLException e) { System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    private String getUserName(int userId) {
        return utils.UserNameCache.getName(userId);
    }

    private void switchTab(Button btn, String tabName) {
        if (activeTab != null) activeTab.getStyleClass().remove("tr-tab-active");
        btn.getStyleClass().add("tr-tab-active");
        activeTab = btn;
        animateTrAmbience(btn);

        switch (tabName) {
            case "Dashboard":    showDashboardTab(); break;
            case "Catalog":      showCatalogTab(); break;
            case "My Learning":  showMyLearningTab(); break;
            case "Certificates": showCertificatesTab(); break;
            case "Manage":       showManageTab(); break;
        }
    }

    // ==================== Spotlight Pill Navbar ====================

    private void setupSpotlightPillNavbar() {
        javafx.scene.layout.VBox topBar = (javafx.scene.layout.VBox) tabBar.getParent();
        int idx = topBar.getChildren().indexOf(tabBar);
        topBar.getChildren().remove(tabBar);

        StackPane navContainer = new StackPane();
        navContainer.getStyleClass().add("tr-nav-container");

        StackPane pill = new StackPane();
        pill.getStyleClass().add("tr-nav-pill");

        tabBar.getStyleClass().remove("tr-tab-bar");
        tabBar.getStyleClass().add("tr-pill-tabs");
        tabBar.setAlignment(Pos.CENTER);

        Region spotlightGlow = new Region();
        spotlightGlow.setMouseTransparent(true);
        spotlightGlow.setOpacity(0);
        spotlightGlow.getStyleClass().add("tr-spotlight-glow");

        trAmbienceIndicator = new Region();
        trAmbienceIndicator.setManaged(false);
        trAmbienceIndicator.setPrefHeight(2);
        trAmbienceIndicator.setMaxHeight(2);
        trAmbienceIndicator.setPrefWidth(50);
        trAmbienceIndicator.setMaxWidth(50);
        trAmbienceIndicator.getStyleClass().add("tr-ambience-line");

        Region trackLine = new Region();
        trackLine.setManaged(false);
        trackLine.setPrefHeight(1);
        trackLine.setMaxHeight(1);
        trackLine.getStyleClass().add("tr-track-line");

        pill.getChildren().addAll(tabBar, spotlightGlow, trackLine, trAmbienceIndicator);
        navContainer.getChildren().add(pill);
        topBar.getChildren().add(idx, navContainer);

        pill.layoutBoundsProperty().addListener((obs, ov, nv) -> {
            double h = nv.getHeight();
            trackLine.setLayoutY(h - 1);
            trackLine.setPrefWidth(nv.getWidth() - 16);
            trackLine.setLayoutX(8);
            trAmbienceIndicator.setLayoutY(h - 2);
        });

        tabBar.setOnMouseMoved(e -> {
            double xInPill = e.getX() + tabBar.getLayoutX();
            double pillW = pill.getWidth();
            if (pillW <= 0) return;
            double pct = Math.max(0, Math.min(100, (xInPill / pillW) * 100.0));
            spotlightGlow.setOpacity(1.0);
            spotlightGlow.setStyle(String.format(
                    "-fx-background-color: radial-gradient(center %.1f%% 100%%, radius 25%%, rgba(138,138,255,0.12), transparent);",
                    pct));
        });

        tabBar.setOnMouseExited(e -> {
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(400), spotlightGlow);
            ft.setToValue(0);
            ft.play();
        });
    }

    private void animateTrAmbience(Button btn) {
        if (trAmbienceIndicator == null) return;
        Platform.runLater(() -> {
            javafx.geometry.Bounds b = btn.getBoundsInParent();
            if (b.getWidth() == 0) return;
            double offset = tabBar.getLayoutX();
            double targetX = offset + b.getMinX() + b.getWidth() / 2 - trAmbienceIndicator.getPrefWidth() / 2;
            javafx.animation.KeyValue kv = new javafx.animation.KeyValue(
                    trAmbienceIndicator.layoutXProperty(), targetX,
                    javafx.animation.Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0));
            javafx.animation.KeyFrame kf = new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(350), kv);
            javafx.animation.Timeline tl = new javafx.animation.Timeline(kf);
            tl.play();
        });
    }

    // ================================================================
    // TAB 1: DASHBOARD — stats + 2 API cards
    // ================================================================

    private void showDashboardTab() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("tr-scroll");

        VBox view = new VBox(20);
        view.getStyleClass().add("tr-view");
        view.setPadding(new Insets(24));

        Label title = new Label("Training Dashboard");
        title.getStyleClass().add("tr-view-title");

        // Stats row
        HBox statsRow = new HBox(16);
        statsRow.setAlignment(Pos.CENTER_LEFT);

        try {
            List<TrainingCourse> allCourses = serviceCourse.recuperer();
            List<TrainingEnrollment> myEnrolls = serviceEnrollment.getByUser(currentUser.getId());
            List<TrainingCertificate> myCerts = serviceCertificate.getByUser(currentUser.getId());

            long activeCourses = allCourses.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
            long inProgress = myEnrolls.stream().filter(e -> "IN_PROGRESS".equals(e.getStatus()) || "ENROLLED".equals(e.getStatus())).count();
            long completed = myEnrolls.stream().filter(e -> "COMPLETED".equals(e.getStatus())).count();
            int avgProgress = myEnrolls.isEmpty() ? 0 :
                    (int) myEnrolls.stream().mapToInt(TrainingEnrollment::getProgress).average().orElse(0);

            statsRow.getChildren().addAll(
                    createStatCard("📚", "Active Courses", String.valueOf(activeCourses), "tr-stat-blue"),
                    createStatCard("📖", "My Enrollments", String.valueOf(inProgress), "tr-stat-green"),
                    createStatCard("✅", "Completed", String.valueOf(completed), "tr-stat-purple"),
                    createStatCard("🏆", "Certificates", String.valueOf(myCerts.size()), "tr-stat-orange"),
                    createStatCard("📈", "Avg Progress", avgProgress + "%", "tr-stat-cyan")
            );
        } catch (SQLException e) {
            statsRow.getChildren().add(new Label("Error loading stats: " + e.getMessage()));
        }

        // API sections
        HBox apiRow = new HBox(16);
        apiRow.setAlignment(Pos.TOP_LEFT);

        VBox triviaSection = createTriviaQuizSection();
        HBox.setHgrow(triviaSection, Priority.ALWAYS);

        VBox bookSection = createRecommendedReadingSection();
        HBox.setHgrow(bookSection, Priority.ALWAYS);

        apiRow.getChildren().addAll(triviaSection, bookSection);

        // Recent enrollments
        VBox recentSection = createRecentEnrollmentsSection();

        // AI Recommendations section
        VBox aiRecsSection = createAIRecommendationsSection();

        view.getChildren().addAll(title, statsRow, apiRow, recentSection, aiRecsSection);
        scroll.setContent(view);
        contentArea.getChildren().setAll(scroll);
    }

    private VBox createStatCard(String icon, String label, String value, String colorClass) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("tr-stat-card", colorClass);
        card.setPadding(new Insets(16));
        card.setPrefWidth(160);
        card.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 24px;");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("tr-stat-value");

        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("tr-stat-label");

        card.getChildren().addAll(iconLabel, valueLabel, nameLabel);
        return card;
    }

    // ── API #1: Daily Trivia Quiz (Open Trivia DB) ──

    private VBox createTriviaQuizSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("tr-section-card");
        section.setPadding(new Insets(16));

        Label title = new Label("🧠 Daily Knowledge Quiz");
        title.getStyleClass().add("tr-section-title");

        VBox content = new VBox(10);
        Label loading = new Label("Loading trivia...");
        loading.getStyleClass().add("tr-section-subtitle");
        content.getChildren().add(loading);

        Button newQuizBtn = new Button("🔄 New Question");
        newQuizBtn.getStyleClass().add("tr-action-btn");
        newQuizBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            fetchTriviaQuestion(content);
        });

        section.getChildren().addAll(title, content, newQuizBtn);
        fetchTriviaQuestion(content);

        return section;
    }

    private void fetchTriviaQuestion(VBox container) {
        container.getChildren().setAll(new Label("Loading..."));

        AppThreadPool.io(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://opentdb.com/api.php?amount=1&type=multiple"))
                        .timeout(Duration.ofSeconds(8))
                        .GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject root = gson.fromJson(resp.body(), JsonObject.class);
                JsonArray results = root.getAsJsonArray("results");

                if (results == null || results.isEmpty()) {
                    Platform.runLater(() -> {
                        container.getChildren().clear();
                        container.getChildren().add(new Label("No trivia available right now"));
                    });
                    return;
                }

                JsonObject q = results.get(0).getAsJsonObject();
                String category = decodeHtml(q.get("category").getAsString());
                String difficulty = q.get("difficulty").getAsString();
                String question = decodeHtml(q.get("question").getAsString());
                String correctAnswer = decodeHtml(q.get("correct_answer").getAsString());

                List<String> answers = new ArrayList<>();
                answers.add(correctAnswer);
                for (JsonElement inc : q.getAsJsonArray("incorrect_answers")) {
                    answers.add(decodeHtml(inc.getAsString()));
                }
                Collections.shuffle(answers);

                Platform.runLater(() -> {
                    container.getChildren().clear();

                    // Category + difficulty badge
                    HBox meta = new HBox(8);
                    meta.setAlignment(Pos.CENTER_LEFT);

                    Label catLabel = new Label(category);
                    catLabel.getStyleClass().add("tr-trivia-category");

                    Label diffLabel = new Label(difficulty.toUpperCase());
                    diffLabel.getStyleClass().addAll("tr-trivia-diff", "tr-trivia-diff-" + difficulty.toLowerCase());

                    meta.getChildren().addAll(catLabel, diffLabel);

                    // Question
                    Label qLabel = new Label(question);
                    qLabel.getStyleClass().add("tr-trivia-question");
                    qLabel.setWrapText(true);

                    VBox answersBox = new VBox(6);

                    // Answer feedback label (hidden initially)
                    Label feedback = new Label();
                    feedback.getStyleClass().add("tr-trivia-feedback");
                    feedback.setWrapText(true);
                    feedback.setVisible(false);
                    feedback.setManaged(false);

                    for (String ans : answers) {
                        Button ansBtn = new Button(ans);
                        ansBtn.getStyleClass().add("tr-trivia-answer-btn");
                        ansBtn.setMaxWidth(Double.MAX_VALUE);
                        ansBtn.setWrapText(true);
                        ansBtn.setOnAction(e -> {
                            // Disable all buttons
                            for (Node n : answersBox.getChildren()) {
                                if (n instanceof Button) {
                                    ((Button) n).setDisable(true);
                                    if (((Button) n).getText().equals(correctAnswer)) {
                                        n.getStyleClass().add("tr-trivia-correct");
                                    }
                                }
                            }
                            if (ans.equals(correctAnswer)) {
                                SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
                                feedback.setText("✅ Correct! Well done!");
                                feedback.getStyleClass().add("tr-trivia-feedback-correct");
                            } else {
                                ansBtn.getStyleClass().add("tr-trivia-wrong");
                                feedback.setText("❌ Wrong! The answer is: " + correctAnswer);
                                feedback.getStyleClass().add("tr-trivia-feedback-wrong");
                            }
                            feedback.setVisible(true);
                            feedback.setManaged(true);
                        });
                        answersBox.getChildren().add(ansBtn);
                    }

                    container.getChildren().addAll(meta, qLabel, answersBox, feedback);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    container.getChildren().clear();
                    container.getChildren().add(new Label("Could not load trivia — check your connection"));
                });
            }
        });
    }

    private String decodeHtml(String text) {
        return text.replace("&quot;", "\"").replace("&#039;", "'")
                .replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&eacute;", "é")
                .replace("&ntilde;", "ñ").replace("&uuml;", "ü");
    }

    // ── API #2: Recommended Reading (Open Library) ──

    private VBox createRecommendedReadingSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("tr-section-card");
        section.setPadding(new Insets(16));

        Label title = new Label("📖 Recommended Reading");
        title.getStyleClass().add("tr-section-title");

        VBox content = new VBox(10);
        Label loading = new Label("Finding a book for you...");
        loading.getStyleClass().add("tr-section-subtitle");
        content.getChildren().add(loading);

        Button refreshBtn = new Button("🔄 Another Book");
        refreshBtn.getStyleClass().add("tr-action-btn");
        refreshBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            fetchRandomBook(content);
        });

        section.getChildren().addAll(title, content, refreshBtn);
        fetchRandomBook(content);

        return section;
    }

    private void fetchRandomBook(VBox container) {
        container.getChildren().setAll(new Label("Loading..."));

        String[] subjects = {"programming", "leadership", "management", "science", "technology",
                "machine_learning", "software_engineering", "data_science", "communication", "productivity"};
        String subject = subjects[new Random().nextInt(subjects.length)];

        AppThreadPool.io(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://openlibrary.org/subjects/" + subject + ".json?limit=20"))
                        .timeout(Duration.ofSeconds(10))
                        .GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject root = gson.fromJson(resp.body(), JsonObject.class);
                JsonArray works = root.getAsJsonArray("works");

                if (works == null || works.isEmpty()) {
                    Platform.runLater(() -> {
                        container.getChildren().clear();
                        container.getChildren().add(new Label("No books found — try again!"));
                    });
                    return;
                }

                // Pick random book from results
                JsonObject book = works.get(new Random().nextInt(works.size())).getAsJsonObject();
                String bookTitle = book.has("title") ? book.get("title").getAsString() : "Unknown Title";

                String authorName = "Unknown Author";
                if (book.has("authors") && book.getAsJsonArray("authors").size() > 0) {
                    JsonObject author = book.getAsJsonArray("authors").get(0).getAsJsonObject();
                    if (author.has("name")) authorName = author.get("name").getAsString();
                }

                String subjectDisplay = subject.replace("_", " ");
                subjectDisplay = subjectDisplay.substring(0, 1).toUpperCase() + subjectDisplay.substring(1);

                int coverId = book.has("cover_id") ? book.get("cover_id").getAsInt() : -1;
                String editionCount = book.has("edition_count") ? book.get("edition_count").getAsString() : "—";
                String firstPublish = book.has("first_publish_year") ? book.get("first_publish_year").getAsString() : "";

                String finalAuthor = authorName;
                String finalSubject = subjectDisplay;

                Platform.runLater(() -> {
                    container.getChildren().clear();

                    // Book title
                    Label titleLabel = new Label("📕  " + bookTitle);
                    titleLabel.getStyleClass().add("tr-book-title");
                    titleLabel.setWrapText(true);

                    // Author
                    Label authorLabel = new Label("by " + finalAuthor);
                    authorLabel.getStyleClass().add("tr-book-author");

                    // Meta row
                    HBox metaRow = new HBox(10);
                    metaRow.setAlignment(Pos.CENTER_LEFT);

                    Label subLabel = new Label(finalSubject);
                    subLabel.getStyleClass().add("tr-book-subject");

                    if (!firstPublish.isEmpty()) {
                        Label yearLabel = new Label("📅 " + firstPublish);
                        yearLabel.getStyleClass().add("tr-book-year");
                        metaRow.getChildren().add(yearLabel);
                    }

                    Label edLabel = new Label("📚 " + editionCount + " editions");
                    edLabel.getStyleClass().add("tr-book-editions");

                    metaRow.getChildren().addAll(subLabel, edLabel);

                    container.getChildren().addAll(titleLabel, authorLabel, metaRow);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    container.getChildren().clear();
                    container.getChildren().add(new Label("Could not fetch book — check your connection"));
                });
            }
        });
    }

    private VBox createRecentEnrollmentsSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("tr-section-card");
        section.setPadding(new Insets(16));

        Label title = new Label("📋 Recent Enrollments");
        title.getStyleClass().add("tr-section-title");

        VBox list = new VBox(6);

        try {
            List<TrainingEnrollment> enrolls = serviceEnrollment.getByUser(currentUser.getId());
            if (enrolls.isEmpty()) {
                list.getChildren().add(new Label("No enrollments yet. Explore the Catalog!"));
            } else {
                for (TrainingEnrollment en : enrolls.subList(0, Math.min(5, enrolls.size()))) {
                    TrainingCourse course = courseMap.get(en.getCourseId());
                    String courseName = course != null ? course.getTitle() : "Course #" + en.getCourseId();

                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("tr-list-row");
                    row.setPadding(new Insets(8, 12, 8, 12));

                    Label nameLabel = new Label(courseName);
                    nameLabel.getStyleClass().add("tr-list-name");
                    HBox.setHgrow(nameLabel, Priority.ALWAYS);
                    nameLabel.setMaxWidth(Double.MAX_VALUE);

                    ProgressBar pb = new ProgressBar(en.getProgress() / 100.0);
                    pb.setPrefWidth(100);
                    pb.getStyleClass().add("tr-progress-bar");

                    Label pctLabel = new Label(en.getProgress() + "%");
                    pctLabel.getStyleClass().add("tr-list-pct");
                    pctLabel.setMinWidth(40);

                    Label statusLabel = new Label(en.getStatus());
                    statusLabel.getStyleClass().add("tr-badge-" + en.getStatus().toLowerCase().replace("_", "-"));

                    row.getChildren().addAll(nameLabel, pb, pctLabel, statusLabel);
                    list.getChildren().add(row);
                }
            }
        } catch (SQLException e) {
            list.getChildren().add(new Label("Error: " + e.getMessage()));
        }

        section.getChildren().addAll(title, list);
        return section;
    }

    // ================================================================
    // TAB 2: COURSE CATALOG — browse & enroll
    // ================================================================

    private void showCatalogTab() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("tr-scroll");

        VBox view = new VBox(16);
        view.getStyleClass().add("tr-view");
        view.setPadding(new Insets(24));

        Label title = new Label("Course Catalog");
        title.getStyleClass().add("tr-view-title");

        // Filters row
        HBox filters = new HBox(12);
        filters.setAlignment(Pos.CENTER_LEFT);

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search courses...");
        searchField.getStyleClass().add("tr-search-field");
        searchField.setPrefWidth(250);

        ComboBox<String> categoryFilter = new ComboBox<>();
        categoryFilter.getItems().addAll("All Categories", "TECHNICAL", "SOFT_SKILLS", "COMPLIANCE", "ONBOARDING", "LEADERSHIP");
        categoryFilter.setValue("All Categories");
        categoryFilter.getStyleClass().add("tr-filter-combo");

        ComboBox<String> difficultyFilter = new ComboBox<>();
        difficultyFilter.getItems().addAll("All Levels", "BEGINNER", "INTERMEDIATE", "ADVANCED");
        difficultyFilter.setValue("All Levels");
        difficultyFilter.getStyleClass().add("tr-filter-combo");

        filters.getChildren().addAll(searchField, categoryFilter, difficultyFilter);

        // Course grid
        FlowPane courseGrid = new FlowPane(16, 16);
        courseGrid.getStyleClass().add("tr-course-grid");

        Runnable refreshGrid = () -> {
            courseGrid.getChildren().clear();
            try {
                List<TrainingCourse> courses = serviceCourse.getActive();
                String searchText = searchField.getText().toLowerCase().trim();
                String catVal = categoryFilter.getValue();
                String diffVal = difficultyFilter.getValue();

                // Get user's enrollments for enroll/unenroll state
                Set<Integer> enrolledCourseIds = new HashSet<>();
                try {
                    for (TrainingEnrollment en : serviceEnrollment.getByUser(currentUser.getId())) {
                        if (!"DROPPED".equals(en.getStatus())) enrolledCourseIds.add(en.getCourseId());
                    }
                } catch (SQLException ignored) {}

                // Sort: enrolled courses first, then by title
                List<TrainingCourse> filtered = new ArrayList<>();
                for (TrainingCourse c : courses) {
                    if (!searchText.isEmpty() && !c.getTitle().toLowerCase().contains(searchText)
                            && !c.getDescription().toLowerCase().contains(searchText)) continue;
                    if (!"All Categories".equals(catVal) && !catVal.equals(c.getCategory())) continue;
                    if (!"All Levels".equals(diffVal) && !diffVal.equals(c.getDifficulty())) continue;
                    filtered.add(c);
                }
                filtered.sort((a, b) -> {
                    boolean aEnrolled = enrolledCourseIds.contains(a.getId());
                    boolean bEnrolled = enrolledCourseIds.contains(b.getId());
                    if (aEnrolled != bEnrolled) return aEnrolled ? -1 : 1;
                    return a.getTitle().compareToIgnoreCase(b.getTitle());
                });

                for (TrainingCourse c : filtered) {
                    VBox card = createCourseCard(c, enrolledCourseIds.contains(c.getId()));
                    StackPane cometCard = CardEffects.applyCometEffect(card);
                    courseGrid.getChildren().add(cometCard);
                }
                if (courseGrid.getChildren().isEmpty()) {
                    Label noResults = new Label("No courses match your filters");
                    noResults.getStyleClass().add("tr-no-results");
                    courseGrid.getChildren().add(noResults);
                }
            } catch (SQLException e) {
                courseGrid.getChildren().add(new Label("Error loading courses"));
            }
        };

        searchField.textProperty().addListener((obs, o, n) -> refreshGrid.run());
        categoryFilter.setOnAction(e -> refreshGrid.run());
        difficultyFilter.setOnAction(e -> refreshGrid.run());

        refreshGrid.run();

        view.getChildren().addAll(title, filters, courseGrid);
        scroll.setContent(view);
        contentArea.getChildren().setAll(scroll);
    }

    private VBox createCourseCard(TrainingCourse course, boolean isEnrolled) {
        VBox card = new VBox(0);
        card.getStyleClass().add("tr-course-card");
        if (isEnrolled) card.getStyleClass().add("tr-course-card-enrolled");
        card.setPrefWidth(290);
        card.setMaxWidth(290);

        // ── Top color banner (category-based gradient) ──
        StackPane banner = new StackPane();
        banner.setPrefHeight(8);
        banner.setMinHeight(8);
        String bannerColor = switch (course.getCategory() != null ? course.getCategory() : "") {
            case "TECHNICAL" -> "#4A9EFF";
            case "SOFT_SKILLS" -> "#F472B6";
            case "COMPLIANCE" -> "#FBBF24";
            case "ONBOARDING" -> "#4ADE80";
            case "LEADERSHIP" -> "#C084FC";
            default -> "#8A8AFF";
        };
        banner.setStyle("-fx-background-color: " + bannerColor + "; -fx-background-radius: 14 14 0 0;");

        // ── Content area ──
        VBox content = new VBox(10);
        content.setPadding(new Insets(14, 16, 16, 16));

        // Badges row
        HBox badges = new HBox(6);
        badges.setAlignment(Pos.CENTER_LEFT);

        Label catBadge = new Label(formatCategory(course.getCategory()));
        catBadge.getStyleClass().addAll("tr-badge", "tr-badge-" + course.getCategory().toLowerCase().replace("_", "-"));

        Label diffBadge = new Label(course.getDifficulty());
        diffBadge.getStyleClass().addAll("tr-badge", "tr-badge-" + course.getDifficulty().toLowerCase());

        badges.getChildren().addAll(catBadge, diffBadge);

        if (isEnrolled) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label enrolledBadge = new Label("✅ ENROLLED");
            enrolledBadge.setStyle("-fx-text-fill: #4ADE80; -fx-font-size: 10px; -fx-font-weight: bold; " +
                    "-fx-background-color: rgba(74,222,128,0.12); -fx-padding: 3 8; -fx-background-radius: 8;");
            badges.getChildren().addAll(spacer, enrolledBadge);
        }

        // Title
        Label titleLabel = new Label(course.getTitle());
        titleLabel.getStyleClass().add("tr-card-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxHeight(44);

        // Description (2 lines max)
        Label desc = new Label(truncate(course.getDescription(), 80));
        desc.getStyleClass().add("tr-card-desc");
        desc.setWrapText(true);
        desc.setMaxHeight(36);

        // ── Stats row ──
        HBox statsRow = new HBox(10);
        statsRow.setAlignment(Pos.CENTER_LEFT);
        statsRow.setPadding(new Insets(4, 0, 0, 0));

        Label durationLabel = new Label("⏱ " + course.getDurationHours() + "h");
        durationLabel.getStyleClass().add("tr-card-info");

        Label instructorLabel = new Label("👤 " + (course.getInstructorName() != null && !course.getInstructorName().isEmpty()
                ? course.getInstructorName() : "—"));
        instructorLabel.getStyleClass().add("tr-card-info");
        instructorLabel.setMaxWidth(120);

        Label timerLabel = new Label("⏰ " + course.getEffectiveQuizTimer() + "s/q");
        timerLabel.setStyle("-fx-text-fill: #8A8AFF; -fx-font-size: 11px;");

        statsRow.getChildren().addAll(durationLabel, instructorLabel, timerLabel);

        // Dates
        HBox dateRow = new HBox(8);
        dateRow.setAlignment(Pos.CENTER_LEFT);
        if (course.getStartDate() != null) {
            Label dateLabel = new Label("📅 " + course.getStartDate().toLocalDate().format(DateTimeFormatter.ofPattern("MMM dd")) +
                    (course.getEndDate() != null ? " → " + course.getEndDate().toLocalDate().format(DateTimeFormatter.ofPattern("MMM dd")) : ""));
            dateLabel.getStyleClass().add("tr-card-info");
            dateRow.getChildren().add(dateLabel);
        }

        // ── Separator ──
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color: #2A2A3E;");

        // ── Action row ──
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);
        actions.setPadding(new Insets(4, 0, 0, 0));

        if (isEnrolled) {
            Button viewBtn = new Button("📖 Continue Learning");
            viewBtn.setStyle("-fx-background-color: rgba(74,222,128,0.15); -fx-text-fill: #4ADE80; " +
                    "-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8 20; " +
                    "-fx-background-radius: 8; -fx-cursor: hand;");
            viewBtn.setOnAction(e -> {
                // Switch to My Learning tab
                for (javafx.scene.Node node : tabBar.getChildren()) {
                    if (node instanceof Button btn && btn.getText().contains("My Learning")) {
                        switchTab(btn, "My Learning");
                        break;
                    }
                }
            });
            actions.getChildren().add(viewBtn);

            if (course.getMegaLink() != null && !course.getMegaLink().isEmpty()) {
                Button megaBtn = new Button("☁");
                megaBtn.getStyleClass().add("tr-mega-btn");
                megaBtn.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-font-size: 12px; " +
                        "-fx-padding: 8 12; -fx-background-radius: 8; -fx-cursor: hand;");
                megaBtn.setOnAction(e -> openMegaLink(course.getMegaLink()));
                actions.getChildren().add(megaBtn);
            }
        } else {
            Button enrollBtn = new Button("📝 Enroll Now");
            enrollBtn.getStyleClass().add("tr-enroll-btn");
            enrollBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(enrollBtn, Priority.ALWAYS);
            enrollBtn.setOnAction(e -> enrollInCourse(course));
            actions.getChildren().add(enrollBtn);
        }

        content.getChildren().addAll(badges, titleLabel, desc, statsRow, dateRow, sep, actions);
        card.getChildren().addAll(banner, content);
        return card;
    }

    private void enrollInCourse(TrainingCourse course) {
        try {
            // ── Max participants check ──
            List<TrainingEnrollment> courseEnrolls = serviceEnrollment.getByCourse(course.getId());
            long activeCount = courseEnrolls.stream()
                    .filter(e -> !"DROPPED".equals(e.getStatus()))
                    .count();
            if (course.getMaxParticipants() > 0 && activeCount >= course.getMaxParticipants()) {
                showAlert(Alert.AlertType.WARNING, "Course Full",
                        "\"" + course.getTitle() + "\" has reached its maximum capacity (" + course.getMaxParticipants() + " participants).\nPlease try again later or contact HR.");
                return;
            }

            // ── Difficulty progression warning ──
            String diff = course.getDifficulty();
            if ("INTERMEDIATE".equals(diff) || "ADVANCED".equals(diff)) {
                String prereqDiff = "INTERMEDIATE".equals(diff) ? "BEGINNER" : "INTERMEDIATE";
                List<TrainingEnrollment> myEnrolls = serviceEnrollment.getByUser(currentUser.getId());
                List<TrainingCourse> allCourses = serviceCourse.recuperer();
                Map<Integer, String> courseDiffMap = new HashMap<>();
                for (TrainingCourse c : allCourses) courseDiffMap.put(c.getId(), c.getDifficulty());
                // Check if user completed any course at the prerequisite difficulty in same category
                boolean hasPrereq = myEnrolls.stream()
                        .filter(e -> "COMPLETED".equals(e.getStatus()))
                        .anyMatch(e -> {
                            String d = courseDiffMap.get(e.getCourseId());
                            return prereqDiff.equals(d);
                        });
                if (!hasPrereq) {
                    Alert warn = new Alert(Alert.AlertType.CONFIRMATION);
                    warn.setTitle("Difficulty Warning");
                    warn.setHeaderText("This is a" + ("ADVANCED".equals(diff) ? "n ADVANCED" : "n INTERMEDIATE") + " course");
                    warn.setContentText("You haven't completed any " + prereqDiff + " course yet.\nIt's recommended to complete a " + prereqDiff + " course first.\n\nEnroll anyway?");
                    DialogHelper.theme(warn);
                    Optional<ButtonType> result = warn.showAndWait();
                    if (result.isEmpty() || result.get() != ButtonType.OK) return;
                }
            }

            TrainingEnrollment enrollment = new TrainingEnrollment(course.getId(), currentUser.getId());
            serviceEnrollment.ajouter(enrollment);

            // Try n8n webhook (fire-and-forget)
            triggerN8nWebhook("enroll", Map.of(
                    "user_id", currentUser.getId(),
                    "user_name", getUserName(currentUser.getId()),
                    "user_email", currentUser.getEmail() != null ? currentUser.getEmail() : "",
                    "course_title", course.getTitle(),
                    "course_id", course.getId(),
                    "enrolled_at", new Timestamp(System.currentTimeMillis()).toString()
            ));

            showAlert(Alert.AlertType.INFORMATION, "Enrolled!", "You are now enrolled in: " + course.getTitle());
            showCatalogTab(); // Refresh
        } catch (SQLException ex) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not enroll: " + ex.getMessage());
        }
    }

    // ================================================================
    // TAB 3: MY LEARNING — enrolled courses with progress
    // ================================================================

    private void showMyLearningTab() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("tr-scroll");

        VBox view = new VBox(16);
        view.getStyleClass().add("tr-view");
        view.setPadding(new Insets(24));

        Label title = new Label("My Learning");
        title.getStyleClass().add("tr-view-title");

        VBox courseList = new VBox(14);

        try {
            List<TrainingEnrollment> enrollments = serviceEnrollment.getByUser(currentUser.getId());
            // Remove dropped, sort: in-progress first, then enrolled, then completed
            enrollments.removeIf(en -> "DROPPED".equals(en.getStatus()));
            enrollments.sort((a, b) -> {
                int order = statusOrder(a.getStatus()) - statusOrder(b.getStatus());
                if (order != 0) return order;
                return b.getEnrolledAt() != null && a.getEnrolledAt() != null
                        ? b.getEnrolledAt().compareTo(a.getEnrolledAt()) : 0;
            });

            if (enrollments.isEmpty()) {
                VBox emptyState = new VBox(12);
                emptyState.setAlignment(Pos.CENTER);
                emptyState.setPadding(new Insets(60, 0, 0, 0));
                Label emptyIcon = new Label("📚");
                emptyIcon.setStyle("-fx-font-size: 48px;");
                Label emptyText = new Label("You haven't enrolled in any courses yet.");
                emptyText.setStyle("-fx-text-fill: #8B8BA0; -fx-font-size: 16px;");
                Button browseBtn = new Button("Browse Catalog →");
                browseBtn.getStyleClass().add("tr-enroll-btn");
                browseBtn.setOnAction(e -> {
                    for (javafx.scene.Node node : tabBar.getChildren()) {
                        if (node instanceof Button btn && btn.getText().contains("Catalog")) {
                            switchTab(btn, "Catalog");
                            break;
                        }
                    }
                });
                emptyState.getChildren().addAll(emptyIcon, emptyText, browseBtn);
                courseList.getChildren().add(emptyState);
            } else {
                for (TrainingEnrollment en : enrollments) {
                    TrainingCourse course = courseMap.get(en.getCourseId());
                    if (course == null) continue;

                    boolean isCompleted = "COMPLETED".equals(en.getStatus());

                    // ── Card root ──
                    HBox card = new HBox(0);
                    card.getStyleClass().add("tr-learning-card");
                    if (isCompleted) card.getStyleClass().add("tr-learning-card-completed");

                    // ── Left color accent bar ──
                    Region accentBar = new Region();
                    accentBar.setPrefWidth(5);
                    accentBar.setMinWidth(5);
                    String accentColor = isCompleted ? "#4ADE80" : (en.getProgress() > 0 ? "#FBBF24" : "#4A9EFF");
                    accentBar.setStyle("-fx-background-color: " + accentColor + "; -fx-background-radius: 12 0 0 12;");

                    // ── Content section ──
                    VBox content = new VBox(8);
                    content.setPadding(new Insets(14, 16, 14, 14));
                    HBox.setHgrow(content, Priority.ALWAYS);

                    // Row 1: Title + Status badge
                    HBox topRow = new HBox(10);
                    topRow.setAlignment(Pos.CENTER_LEFT);

                    Label courseName = new Label(course.getTitle());
                    courseName.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #E0E0E8;");
                    courseName.setWrapText(true);
                    HBox.setHgrow(courseName, Priority.ALWAYS);
                    courseName.setMaxWidth(Double.MAX_VALUE);

                    Label statusLabel = new Label(isCompleted ? "✅ COMPLETED" : en.getStatus().replace("_", " "));
                    statusLabel.getStyleClass().add("tr-badge-" + en.getStatus().toLowerCase().replace("_", "-"));
                    statusLabel.setStyle(statusLabel.getStyle() + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 10;");

                    topRow.getChildren().addAll(courseName, statusLabel);

                    // Row 2: Category + Difficulty + Duration + Timer
                    HBox metaRow = new HBox(8);
                    metaRow.setAlignment(Pos.CENTER_LEFT);

                    Label catBadge = new Label(formatCategory(course.getCategory()));
                    catBadge.getStyleClass().addAll("tr-badge", "tr-badge-" + course.getCategory().toLowerCase().replace("_", "-"));

                    Label diffBadge = new Label(course.getDifficulty());
                    diffBadge.getStyleClass().addAll("tr-badge", "tr-badge-" + course.getDifficulty().toLowerCase());

                    Label durLabel = new Label("⏱ " + course.getDurationHours() + "h");
                    durLabel.setStyle("-fx-text-fill: #6B6B80; -fx-font-size: 11px;");

                    Label timerLabel = new Label("⏰ " + course.getEffectiveQuizTimer() + "s/q");
                    timerLabel.setStyle("-fx-text-fill: #8A8AFF; -fx-font-size: 11px;");

                    metaRow.getChildren().addAll(catBadge, diffBadge, durLabel, timerLabel);

                    // Row 3: Progress bar
                    HBox progressRow = new HBox(10);
                    progressRow.setAlignment(Pos.CENTER_LEFT);

                    ProgressBar pb = new ProgressBar(en.getProgress() / 100.0);
                    pb.setPrefHeight(10);
                    pb.setMaxHeight(10);
                    pb.getStyleClass().add("tr-progress-bar");
                    HBox.setHgrow(pb, Priority.ALWAYS);

                    Label pctLabel = new Label(en.getProgress() + "%");
                    pctLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " +
                            (isCompleted ? "#4ADE80" : en.getProgress() > 0 ? "#FBBF24" : "#8B8BA0") + ";");
                    pctLabel.setMinWidth(40);

                    if (en.getScore() != null && en.getScore() > 0) {
                        Label scoreLabel = new Label("🏆 " + String.format("%.0f%%", en.getScore()));
                        scoreLabel.setStyle("-fx-text-fill: #FBBF24; -fx-font-size: 12px; -fx-font-weight: bold;");
                        progressRow.getChildren().addAll(pb, pctLabel, scoreLabel);
                    } else {
                        progressRow.getChildren().addAll(pb, pctLabel);
                    }

                    // Row 4: Actions
                    HBox actions = new HBox(8);
                    actions.setAlignment(Pos.CENTER_LEFT);
                    actions.setPadding(new Insets(4, 0, 0, 0));

                    if (!isCompleted) {
                        Button quizBtn = new Button("📝 Take Quiz");
                        quizBtn.setStyle("-fx-background-color: #2C666E; -fx-text-fill: white; " +
                                "-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 7 18; " +
                                "-fx-background-radius: 8; -fx-cursor: hand;");
                        quizBtn.setOnAction(e -> showQuizDialog(en, course));
                        actions.getChildren().add(quizBtn);
                    }

                    if (course.getMegaLink() != null && !course.getMegaLink().isEmpty()) {
                        Button megaBtn = new Button("☁ Open Course");
                        megaBtn.getStyleClass().add("tr-mega-btn");
                        megaBtn.setOnAction(e -> openMegaLink(course.getMegaLink()));
                        actions.getChildren().add(megaBtn);
                    }

                    if (!isCompleted) {
                        Region actionSpacer = new Region();
                        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
                        Button dropBtn = new Button("Drop");
                        dropBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #6B6B80; " +
                                "-fx-font-size: 11px; -fx-padding: 5 10; -fx-cursor: hand; " +
                                "-fx-border-color: #3A3A5E; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;");
                        dropBtn.setOnAction(e -> dropEnrollment(en));
                        actions.getChildren().addAll(actionSpacer, dropBtn);
                    }

                    content.getChildren().addAll(topRow, metaRow, progressRow, actions);
                    card.getChildren().addAll(accentBar, content);
                    courseList.getChildren().add(card);
                }
            }
        } catch (SQLException e) {
            courseList.getChildren().add(new Label("Error loading enrollments: " + e.getMessage()));
        }

        view.getChildren().addAll(title, courseList);
        scroll.setContent(view);
        contentArea.getChildren().setAll(scroll);
    }

    private void updateEnrollmentProgress(TrainingEnrollment enrollment, int newProgress, TrainingCourse course) {
        try {
            serviceEnrollment.updateProgress(enrollment.getId(), newProgress);

            // If completed, auto-generate certificate
            if (newProgress >= 100) {
                generateCertificate(enrollment, course);

                triggerN8nWebhook("complete", Map.of(
                        "user_id", currentUser.getId(),
                        "user_name", getUserName(currentUser.getId()),
                        "user_email", currentUser.getEmail() != null ? currentUser.getEmail() : "",
                        "course_title", course.getTitle(),
                        "course_id", course.getId(),
                        "completed_at", new Timestamp(System.currentTimeMillis()).toString()
                ));

                showAlert(Alert.AlertType.INFORMATION, "Congratulations! 🎉",
                        "You completed: " + course.getTitle() + "\nA certificate has been generated!");
            }

            loadCourseMap();
            showMyLearningTab(); // Refresh
        } catch (SQLException ex) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not update progress: " + ex.getMessage());
        }
    }

    // ================================================================
    // AI QUIZ DIALOG
    // ================================================================

    private void showQuizDialog(TrainingEnrollment enrollment, TrainingCourse course) {
        // Show loading dialog while AI generates quiz
        javafx.stage.Stage loadingStage = new javafx.stage.Stage();
        loadingStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        loadingStage.initOwner(rootPane.getScene().getWindow());
        loadingStage.setTitle("Generating Quiz...");

        VBox loadingBox = new VBox(16);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPadding(new Insets(40));
        loadingBox.setStyle("-fx-background-color: #1a1a2e;");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(60, 60);
        Label loadingLabel = new Label("🤖 AI is generating your quiz for:\n" + course.getTitle());
        loadingLabel.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 14px; -fx-text-alignment: center;");
        loadingLabel.setWrapText(true);
        Label tipLabel = new Label("Questions are based on the course content and difficulty level (" + course.getDifficulty() + ")");
        tipLabel.setStyle("-fx-text-fill: #7a7a9a; -fx-font-size: 11px;");
        loadingBox.getChildren().addAll(spinner, loadingLabel, tipLabel);
        javafx.scene.Scene loadingScene = new javafx.scene.Scene(loadingBox, 420, 200);
        loadingStage.setScene(loadingScene);
        loadingStage.setResizable(false);
        DialogHelper.themeStage(loadingStage);
        loadingStage.show();

        // Generate quiz on background thread
        AppThreadPool.io(() -> {
            try {
                ZAIService zai = new ZAIService();
                String rawResponse = zai.generateCourseQuiz(
                        course.getTitle(),
                        course.getDescription(),
                        course.getDifficulty()
                );

                // Clean response — strip markdown code fences, find JSON object
                String jsonStr = rawResponse.trim();
                if (jsonStr.startsWith("```")) {
                    jsonStr = jsonStr.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
                }
                // Extract JSON object between first { and last }
                int firstBrace = jsonStr.indexOf('{');
                int lastBrace = jsonStr.lastIndexOf('}');
                if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
                    throw new RuntimeException("AI did not return valid JSON. Response: " +
                            (jsonStr.length() > 120 ? jsonStr.substring(0, 120) + "..." : jsonStr));
                }
                jsonStr = jsonStr.substring(firstBrace, lastBrace + 1);

                JsonObject quizJson = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                JsonArray questions = quizJson.getAsJsonArray("questions");

                if (questions == null || questions.isEmpty()) {
                    throw new RuntimeException("AI returned no questions. Please try again.");
                }

                // If AI suggested a recommended timer and admin hasn't set a custom one, use AI's suggestion
                if (course.getQuizTimerSeconds() <= 0 && quizJson.has("recommended_timer_seconds")) {
                    try {
                        int aiTimer = quizJson.get("recommended_timer_seconds").getAsInt();
                        if (aiTimer >= 5 && aiTimer <= 60) {
                            course.setQuizTimerSeconds(aiTimer);
                        }
                    } catch (Exception ignored) {}
                }

                Platform.runLater(() -> {
                    loadingStage.close();
                    displayQuizDialog(enrollment, course, questions);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loadingStage.close();
                    showAlert(Alert.AlertType.ERROR, "Quiz Generation Failed",
                            "Could not generate quiz: " + ex.getMessage() + "\nPlease try again.");
                });
            }
        });
    }

    private void displayQuizDialog(TrainingEnrollment enrollment, TrainingCourse course, JsonArray questions) {
        javafx.stage.Stage quizStage = new javafx.stage.Stage();
        quizStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        quizStage.initOwner(rootPane.getScene().getWindow());
        quizStage.setTitle("📝 Quiz: " + course.getTitle());

        // ── Build the question queue ──
        List<Integer> questionQueue = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) questionQueue.add(i);
        int originalTotal = questions.size();
        int timerSec = course.getEffectiveQuizTimer();

        // Track answers: questionIndex → selectedOptionIndex (-1 = timeout / unanswered)
        Map<Integer, Integer> answerMap = new LinkedHashMap<>();
        // Track per-question correct/wrong
        List<Boolean> resultLog = new ArrayList<>(); // true = correct, false = wrong/timeout

        // Stats
        int[] stats = {0, 0, 0}; // [correct, wrong, streak]

        // ── Root layout ──
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0f0f1e;");
        root.setPrefSize(760, 620);

        // ══ TOP BAR: progress + question counter + streak + quit ══
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(16, 24, 12, 24));
        topBar.setStyle("-fx-background-color: #161630;");

        // Progress bar
        ProgressBar quizProgress = new ProgressBar(0);
        quizProgress.setPrefHeight(8);
        quizProgress.setPrefWidth(200);
        quizProgress.setStyle("-fx-accent: #8A8AFF;");
        HBox.setHgrow(quizProgress, Priority.ALWAYS);

        Label questionCounter = new Label("1/" + originalTotal);
        questionCounter.setStyle("-fx-text-fill: #8B8BA0; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label streakLabel = new Label("🔥 0");
        streakLabel.setStyle("-fx-text-fill: #FBBF24; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label scoreDisplay = new Label("⭐ 0");
        scoreDisplay.setStyle("-fx-text-fill: #4ADE80; -fx-font-size: 14px; -fx-font-weight: bold;");

        Button quitBtn = new Button("✕");
        quitBtn.setStyle("-fx-background-color: rgba(248,113,113,0.15); -fx-text-fill: #F87171; " +
                "-fx-font-size: 16px; -fx-background-radius: 20; -fx-padding: 4 10; -fx-cursor: hand;");

        topBar.getChildren().addAll(quizProgress, questionCounter, streakLabel, scoreDisplay, quitBtn);

        // ══ CENTER: Timer ring + Question + Options ══
        StackPane centerPane = new StackPane();
        centerPane.setStyle("-fx-background-color: #0f0f1e;");
        VBox.setVgrow(centerPane, Priority.ALWAYS);

        VBox questionContainer = new VBox(14);
        questionContainer.setAlignment(Pos.CENTER);
        questionContainer.setPadding(new Insets(12, 32, 12, 32));
        questionContainer.setMaxWidth(700);

        // ── Circular Timer ──
        StackPane timerRing = new StackPane();
        timerRing.setPrefSize(70, 70);
        timerRing.setMaxSize(70, 70);

        Circle bgCircle = new Circle(30);
        bgCircle.setFill(Color.TRANSPARENT);
        bgCircle.setStroke(Color.web("#2A2A3E"));
        bgCircle.setStrokeWidth(4);

        Arc timerArc = new Arc(0, 0, 30, 30, 90, 360);
        timerArc.setType(ArcType.OPEN);
        timerArc.setFill(Color.TRANSPARENT);
        timerArc.setStroke(Color.web("#8A8AFF"));
        timerArc.setStrokeWidth(4);
        timerArc.setStrokeLineCap(StrokeLineCap.ROUND);

        Label timerText = new Label(String.valueOf(timerSec));
        timerText.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #E0E0E8;");

        timerRing.getChildren().addAll(bgCircle, timerArc, timerText);

        // ── Question text ──
        Label questionLabel = new Label();
        questionLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #E0E0FF; " +
                "-fx-text-alignment: center; -fx-line-spacing: 4;");
        questionLabel.setWrapText(true);
        questionLabel.setMaxWidth(620);
        questionLabel.setAlignment(Pos.CENTER);
        questionLabel.setMinHeight(Region.USE_PREF_SIZE);

        // ── Difficulty badge ──
        Label diffBadge = new Label(course.getDifficulty());
        diffBadge.getStyleClass().addAll("tr-badge", "tr-badge-" + course.getDifficulty().toLowerCase());

        // ── Options grid (2x2 like Quizizz) ──
        GridPane optionsGrid = new GridPane();
        optionsGrid.setHgap(12);
        optionsGrid.setVgap(12);
        optionsGrid.setAlignment(Pos.CENTER);
        optionsGrid.setMaxWidth(620);

        // Option colors (Quizizz style)
        String[] optColors = {"#E14B4B", "#2D70AE", "#D89E00", "#298F47"};
        String[] optIcons = {"◆", "●", "▲", "■"};

        Button[] optionBtns = new Button[4];
        for (int i = 0; i < 4; i++) {
            Button btn = new Button();
            btn.setWrapText(true);
            btn.setPrefSize(295, 80);
            btn.setMaxWidth(295);
            btn.setMinHeight(70);
            btn.setStyle("-fx-background-color: " + optColors[i] + "; -fx-text-fill: white; " +
                    "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 10; " +
                    "-fx-cursor: hand; -fx-padding: 12 16; -fx-alignment: CENTER;");
            optionBtns[i] = btn;
            int col = i % 2;
            int row = i / 2;
            optionsGrid.add(btn, col, row);
            // Make columns equal width
            if (i < 2) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(50);
                cc.setHgrow(Priority.ALWAYS);
                optionsGrid.getColumnConstraints().add(cc);
            }
        }

        // ── Feedback overlay ──
        Label feedbackLabel = new Label();
        feedbackLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-padding: 8 20; " +
                "-fx-background-radius: 12;");
        feedbackLabel.setVisible(false);

        questionContainer.getChildren().addAll(timerRing, diffBadge, questionLabel, optionsGrid, feedbackLabel);
        centerPane.getChildren().add(questionContainer);

        // ── Bottom bar: timer info ──
        HBox bottomBar = new HBox(16);
        bottomBar.setAlignment(Pos.CENTER);
        bottomBar.setPadding(new Insets(12, 24, 16, 24));
        bottomBar.setStyle("-fx-background-color: #161630;");

        Label timerInfo = new Label("⏱ " + timerSec + "s per question");
        timerInfo.setStyle("-fx-text-fill: #6B6B80; -fx-font-size: 12px;");

        Label wrongPenalty = new Label("🎯 Pass: 70%");
        wrongPenalty.setStyle("-fx-text-fill: #FBBF24; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label courseLabel = new Label("📚 " + course.getTitle());
        courseLabel.setStyle("-fx-text-fill: #8B8BA0; -fx-font-size: 12px;");
        courseLabel.setMaxWidth(200);

        bottomBar.getChildren().addAll(timerInfo, wrongPenalty, courseLabel);

        root.getChildren().addAll(topBar, centerPane, bottomBar);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 760, 620);
        // Apply main stylesheet if available
        try {
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {}
        quizStage.setScene(scene);
        quizStage.setResizable(false);
        DialogHelper.themeStage(quizStage);
        quizStage.show();

        // ══════════════════════════════════════════════
        // QUIZ ENGINE — one question at a time with timer
        // ══════════════════════════════════════════════

        Timeline[] currentTimer = {null};
        int[] queueIndex = {0};
        int[] answeredCorrectly = {0};      // unique correct answers
        Set<Integer> correctlyAnswered = new HashSet<>();  // track which original questions were answered correctly

        Runnable[] showQuestion = new Runnable[1];
        showQuestion[0] = () -> {
            if (queueIndex[0] >= questionQueue.size()) {
                // Quiz complete!
                if (currentTimer[0] != null) currentTimer[0].stop();
                quizStage.close();

                // Build ToggleGroups for results display (compatibility with showQuizResults)
                List<ToggleGroup> fakeGroups = new ArrayList<>();
                for (int i = 0; i < originalTotal; i++) {
                    ToggleGroup tg = new ToggleGroup();
                    RadioButton rb = new RadioButton();
                    rb.setUserData(answerMap.getOrDefault(i, -1));
                    rb.setToggleGroup(tg);
                    tg.selectToggle(rb);
                    fakeGroups.add(tg);
                }

                int finalCorrect = correctlyAnswered.size();
                double scorePercent = (finalCorrect * 100.0) / originalTotal;
                boolean passed = scorePercent >= 70.0;
                showQuizResults(enrollment, course, questions, fakeGroups, finalCorrect, originalTotal, scorePercent, passed);
                return;
            }

            int qIdx = questionQueue.get(queueIndex[0]);
            JsonObject q = questions.get(qIdx).getAsJsonObject();
            String qText = q.get("q").getAsString();
            JsonArray opts = q.getAsJsonArray("options");
            int correctIdx = q.get("answer").getAsInt();

            // Update UI
            int displayNum = Math.min(queueIndex[0] + 1, questionQueue.size());
            questionCounter.setText(displayNum + "/" + questionQueue.size());
            double progress = (double) queueIndex[0] / questionQueue.size();
            quizProgress.setProgress(progress);
            questionLabel.setText(qText);
            feedbackLabel.setVisible(false);

            // Set option texts
            for (int i = 0; i < 4 && i < opts.size(); i++) {
                optionBtns[i].setText(optIcons[i] + "  " + opts.get(i).getAsString());
                optionBtns[i].setDisable(false);
                optionBtns[i].setOpacity(1.0);
                optionBtns[i].setStyle("-fx-background-color: " + optColors[i] + "; -fx-text-fill: white; " +
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 10; " +
                        "-fx-cursor: hand; -fx-padding: 12 16; -fx-alignment: CENTER;");
            }

            // Reset and start timer
            if (currentTimer[0] != null) currentTimer[0].stop();

            IntegerProperty timeLeft = new SimpleIntegerProperty(timerSec);
            timerText.setText(String.valueOf(timerSec));
            timerArc.setLength(360);

            // Color transitions: green > yellow > red
            Timeline timer = new Timeline();
            for (int sec = timerSec; sec >= 0; sec--) {
                final int s = sec;
                KeyFrame kf = new KeyFrame(javafx.util.Duration.seconds(timerSec - sec), e -> {
                    timerText.setText(String.valueOf(s));
                    double fraction = (double) s / timerSec;
                    timerArc.setLength(360 * fraction);

                    // Color the arc and text based on time remaining
                    if (fraction > 0.5) {
                        timerArc.setStroke(Color.web("#4ADE80")); // green
                        timerText.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #4ADE80;");
                    } else if (fraction > 0.25) {
                        timerArc.setStroke(Color.web("#FBBF24")); // yellow
                        timerText.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #FBBF24;");
                    } else {
                        timerArc.setStroke(Color.web("#F87171")); // red
                        timerText.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #F87171;");
                    }
                });
                timer.getKeyFrames().add(kf);
            }

            // On timeout
            timer.setOnFinished(e -> {
                // Time's up = wrong
                stats[1]++;  // wrong count
                stats[2] = 0; // reset streak
                streakLabel.setText("🔥 " + stats[2]);
                resultLog.add(false);

                for (Button btn : optionBtns) btn.setDisable(true);

                // Flash correct answer
                optionBtns[correctIdx].setStyle("-fx-background-color: #4ADE80; -fx-text-fill: white; " +
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 10; " +
                        "-fx-padding: 12 16; -fx-alignment: CENTER; " +
                        "-fx-border-color: white; -fx-border-width: 3; -fx-border-radius: 10;");

                feedbackLabel.setText("⏰ Time's up!");
                feedbackLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-padding: 8 20; " +
                        "-fx-background-radius: 12; -fx-text-fill: #F87171; -fx-background-color: rgba(248,113,113,0.12);");
                feedbackLabel.setVisible(true);

                SoundManager.getInstance().play(SoundManager.ERROR);

                // Proceed to next after 1.5s delay
                Timeline delay = new Timeline(new KeyFrame(javafx.util.Duration.millis(1500), ev -> {
                    queueIndex[0]++;
                    showQuestion[0].run();
                }));
                delay.play();
            });

            currentTimer[0] = timer;
            timer.play();

            // ── Option click handlers ──
            for (int i = 0; i < 4; i++) {
                final int optIdx = i;
                optionBtns[i].setOnAction(ev -> {
                    timer.stop();
                    for (Button btn : optionBtns) btn.setDisable(true);

                    boolean isCorrect = optIdx == correctIdx;
                    answerMap.put(qIdx, optIdx);

                    if (isCorrect) {
                        stats[0]++;  // correct
                        stats[2]++; // streak
                        correctlyAnswered.add(qIdx);
                        resultLog.add(true);

                        // Green flash on selected button
                        optionBtns[optIdx].setStyle("-fx-background-color: #4ADE80; -fx-text-fill: white; " +
                                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 10; " +
                                "-fx-padding: 12 16; -fx-alignment: CENTER; " +
                                "-fx-border-color: white; -fx-border-width: 3; -fx-border-radius: 10;");

                        feedbackLabel.setText("✅ Correct! +" + (100 * stats[2]) + " pts");
                        feedbackLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-padding: 8 20; " +
                                "-fx-background-radius: 12; -fx-text-fill: #4ADE80; -fx-background-color: rgba(74,222,128,0.12);");

                        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
                    } else {
                        stats[1]++;  // wrong
                        stats[2] = 0; // reset streak
                        resultLog.add(false);

                        // Red flash on selected, green on correct
                        optionBtns[optIdx].setStyle("-fx-background-color: #F87171; -fx-text-fill: white; " +
                                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 10; " +
                                "-fx-padding: 12 16; -fx-alignment: CENTER; " +
                                "-fx-border-color: white; -fx-border-width: 3; -fx-border-radius: 10;");
                        optionBtns[correctIdx].setStyle("-fx-background-color: #4ADE80; -fx-text-fill: white; " +
                                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 10; " +
                                "-fx-padding: 12 16; -fx-alignment: CENTER; " +
                                "-fx-border-color: white; -fx-border-width: 3; -fx-border-radius: 10;");

                        // Dim incorrect other options
                        for (int k = 0; k < 4; k++) {
                            if (k != optIdx && k != correctIdx) {
                                optionBtns[k].setOpacity(0.3);
                            }
                        }

                        feedbackLabel.setText("❌ Wrong!");
                        feedbackLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-padding: 8 20; " +
                                "-fx-background-radius: 12; -fx-text-fill: #F87171; -fx-background-color: rgba(248,113,113,0.12);");

                        SoundManager.getInstance().play(SoundManager.ERROR);
                    }

                    feedbackLabel.setVisible(true);
                    streakLabel.setText("🔥 " + stats[2]);
                    scoreDisplay.setText("⭐ " + correctlyAnswered.size() + "/" + originalTotal);

                    // Advance after 1.2s
                    Timeline delay = new Timeline(new KeyFrame(javafx.util.Duration.millis(1200), e -> {
                        queueIndex[0]++;
                        showQuestion[0].run();
                    }));
                    delay.play();
                });
            }
        };

        // Start quiz
        showQuestion[0].run();

        // Quit button
        quitBtn.setOnAction(e -> {
            if (currentTimer[0] != null) currentTimer[0].stop();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to quit the quiz? Your progress will be lost.",
                    ButtonType.YES, ButtonType.NO);
            DialogHelper.theme(confirm);
            confirm.setHeaderText("Quit Quiz?");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) quizStage.close();
            });
        });

        quizStage.setOnCloseRequest(e -> {
            if (currentTimer[0] != null) currentTimer[0].stop();
        });
    }

    private void showQuizResults(TrainingEnrollment enrollment, TrainingCourse course,
                                  JsonArray questions, List<ToggleGroup> toggleGroups,
                                  int correct, int total, double scorePercent, boolean passed) {
        javafx.stage.Stage resultStage = new javafx.stage.Stage();
        resultStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        resultStage.initOwner(rootPane.getScene().getWindow());
        resultStage.setTitle(passed ? "🎉 Quiz Passed!" : "❌ Quiz Failed");

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #1a1a2e; -fx-background-color: #1a1a2e;");

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: #1a1a2e;");

        // Result header
        Label resultIcon = new Label(passed ? "🎉" : "😔");
        resultIcon.setStyle("-fx-font-size: 48px;");

        Label resultTitle = new Label(passed ? "Congratulations! You Passed!" : "Quiz Not Passed");
        resultTitle.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: "
                + (passed ? "#4CAF50;" : "#FF6B6B;"));

        Label scoreLabel = new Label(String.format("Score: %d/%d (%.0f%%)", correct, total, scorePercent));
        scoreLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #e0e0ff;");

        // Progress bar for score
        ProgressBar scorePb = new ProgressBar(scorePercent / 100.0);
        scorePb.setPrefWidth(400);
        scorePb.setPrefHeight(16);
        scorePb.setStyle(passed
                ? "-fx-accent: #4CAF50;"
                : "-fx-accent: #FF6B6B;");

        Label passThreshold = new Label("Pass threshold: 70%  |  Your score: " + String.format("%.0f%%", scorePercent));
        passThreshold.setStyle("-fx-font-size: 12px; -fx-text-fill: #7a7a9a;");

        root.getChildren().addAll(resultIcon, resultTitle, scoreLabel, scorePb, passThreshold);

        // Show answer review
        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: #333366;");
        Label reviewTitle = new Label("📋 Answer Review");
        reviewTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #90DDF0;");
        root.getChildren().addAll(divider, reviewTitle);

        String[] letters = {"A", "B", "C", "D"};
        for (int i = 0; i < total; i++) {
            JsonObject q = questions.get(i).getAsJsonObject();
            int correctIdx = q.get("answer").getAsInt();
            int selectedIdx = (int) toggleGroups.get(i).getSelectedToggle().getUserData();
            JsonArray options = q.getAsJsonArray("options");

            // Handle unanswered questions (selectedIdx == -1 when time expired)
            boolean answered = selectedIdx >= 0 && selectedIdx < options.size();
            boolean isCorrect = answered && selectedIdx == correctIdx;

            VBox reviewCard = new VBox(6);
            reviewCard.setPadding(new Insets(12));
            reviewCard.setStyle("-fx-background-color: " + (isCorrect ? "#1a3a1a" : "#3a1a1a")
                    + "; -fx-background-radius: 8; -fx-border-color: "
                    + (isCorrect ? "#4CAF50" : "#FF6B6B") + "; -fx-border-radius: 8; -fx-border-width: 1;");

            Label qText = new Label((isCorrect ? "✅ " : "❌ ") + "Q" + (i + 1) + ". " + q.get("q").getAsString());
            qText.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e0e0ff;");
            qText.setWrapText(true);

            Label yourAnswer;
            if (answered) {
                yourAnswer = new Label("Your answer: " + letters[selectedIdx] + ") " + options.get(selectedIdx).getAsString());
            } else {
                yourAnswer = new Label("Your answer: ⏰ Not answered (time expired)");
            }
            yourAnswer.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isCorrect ? "#4CAF50;" : "#FF6B6B;"));

            reviewCard.getChildren().addAll(qText, yourAnswer);

            if (!isCorrect) {
                Label correctAnswer = new Label("Correct answer: " + letters[correctIdx] + ") " + options.get(correctIdx).getAsString());
                correctAnswer.setStyle("-fx-font-size: 12px; -fx-text-fill: #4CAF50;");
                reviewCard.getChildren().add(correctAnswer);
            }

            root.getChildren().add(reviewCard);
        }

        // Action buttons
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);
        btnRow.setPadding(new Insets(16, 0, 8, 0));

        if (passed) {
            // Auto-complete the course and generate certificate
            try {
                enrollment.setScore(scorePercent);
                enrollment.setProgress(100);
                enrollment.setStatus("COMPLETED");
                enrollment.setCompletedAt(new Timestamp(System.currentTimeMillis()));
                serviceEnrollment.modifier(enrollment);

                generateCertificate(enrollment, course);

                triggerN8nWebhook("complete", Map.of(
                        "user_id", currentUser.getId(),
                        "user_name", getUserName(currentUser.getId()),
                        "user_email", currentUser.getEmail() != null ? currentUser.getEmail() : "",
                        "course_title", course.getTitle(),
                        "course_id", course.getId(),
                        "quiz_score", String.format("%.0f%%", scorePercent),
                        "completed_at", new Timestamp(System.currentTimeMillis()).toString()
                ));

                SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
            } catch (SQLException ex) {
                System.err.println("Failed to complete enrollment: " + ex.getMessage());
            }

            Label certMsg = new Label("🏆 A certificate has been generated! Check the Certificates tab.");
            certMsg.setStyle("-fx-font-size: 14px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            certMsg.setWrapText(true);
            root.getChildren().add(certMsg);

            Button closeBtn = new Button("🎉 Close & View Certificates");
            closeBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px; "
                    + "-fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-weight: bold;");
            closeBtn.setOnAction(ev -> {
                resultStage.close();
                loadCourseMap();
                showMyLearningTab();
            });
            btnRow.getChildren().add(closeBtn);
        } else {
            // Save the score but don't complete
            try {
                enrollment.setScore(scorePercent);
                serviceEnrollment.modifier(enrollment);
            } catch (SQLException ex) {
                System.err.println("Failed to save score: " + ex.getMessage());
            }

            Label failMsg = new Label("You need at least 70% to pass. Study the course material and try again!");
            failMsg.setStyle("-fx-font-size: 13px; -fx-text-fill: #FF6B6B;");
            failMsg.setWrapText(true);
            root.getChildren().add(failMsg);

            Button retryBtn = new Button("🔄 Retry Quiz");
            retryBtn.setStyle("-fx-background-color: #2C666E; -fx-text-fill: white; -fx-font-size: 14px; "
                    + "-fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand;");
            retryBtn.setOnAction(ev -> {
                resultStage.close();
                showQuizDialog(enrollment, course);
            });

            Button closeBtn = new Button("Close");
            closeBtn.setStyle("-fx-background-color: #444; -fx-text-fill: #ccc; -fx-font-size: 13px; "
                    + "-fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand;");
            closeBtn.setOnAction(ev -> {
                resultStage.close();
                loadCourseMap();
                showMyLearningTab();
            });

            btnRow.getChildren().addAll(retryBtn, closeBtn);
        }

        root.getChildren().add(btnRow);
        scroll.setContent(root);

        javafx.scene.Scene scene = new javafx.scene.Scene(scroll, 680, 700);
        resultStage.setScene(scene);
        resultStage.setResizable(true);
        DialogHelper.themeStage(resultStage);
        resultStage.show();
    }

    private void generateCertificate(TrainingEnrollment enrollment, TrainingCourse course) {
        try {
            // Generate a readable cert number: SG-YYYY-COURSEID-USERID-XXXXX
            String year = String.valueOf(java.time.Year.now().getValue());
            String uid = String.format("%04X", (int)(Math.random() * 0xFFFF));
            String certNumber = "SG-" + year + "-C" + course.getId() + "-U" + currentUser.getId() + "-" + uid;

            TrainingCertificate cert = new TrainingCertificate(
                    enrollment.getId(), currentUser.getId(), course.getId(),
                    certNumber
            );
            serviceCertificate.ajouter(cert);
        } catch (SQLException e) {
            System.err.println("Could not generate certificate: " + e.getMessage());
        }
    }

    private void dropEnrollment(TrainingEnrollment enrollment) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Drop this course?", ButtonType.YES, ButtonType.NO);
        DialogHelper.theme(confirm);
        confirm.setHeaderText("Are you sure you want to drop this course?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    enrollment.setStatus("DROPPED");
                    serviceEnrollment.modifier(enrollment);
                    showMyLearningTab();
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
                }
            }
        });
    }

    // ================================================================
    // TAB 4: CERTIFICATES
    // ================================================================

    private void showCertificatesTab() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("tr-scroll");

        VBox view = new VBox(16);
        view.getStyleClass().add("tr-view");
        view.setPadding(new Insets(24));

        Label title = new Label(isHrOrAdmin ? "All Certificates" : "My Certificates");
        title.getStyleClass().add("tr-view-title");

        VBox certList = new VBox(12);

        try {
            // HR/Admin see ALL certificates so they can sign them; others see only their own
            List<TrainingCertificate> certs = isHrOrAdmin
                    ? serviceCertificate.recuperer()
                    : serviceCertificate.getByUser(currentUser.getId());

            if (certs.isEmpty()) {
                Label empty = new Label("No certificates yet.\nComplete a course to earn your first certificate!");
                empty.getStyleClass().add("tr-empty-text");
                empty.setWrapText(true);
                certList.getChildren().add(empty);
            } else {
                for (TrainingCertificate cert : certs) {
                    TrainingCourse course = courseMap.get(cert.getCourseId());
                    String courseName = course != null ? course.getTitle() : "Course #" + cert.getCourseId();
                    double hours = course != null ? course.getDurationHours() : 0;
                    String userName = getUserName(cert.getUserId());
                    String dateStr = cert.getIssuedAt() != null
                            ? cert.getIssuedAt().toLocalDateTime().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                            : "N/A";

                    HBox card = new HBox(16);
                    card.getStyleClass().add("tr-cert-card");
                    card.setPadding(new Insets(16));
                    card.setAlignment(Pos.CENTER_LEFT);

                    VBox info = new VBox(4);
                    HBox.setHgrow(info, Priority.ALWAYS);

                    Label certIcon = new Label("🏆");
                    certIcon.setStyle("-fx-font-size: 32px;");

                    Label courseLabel = new Label(courseName);
                    courseLabel.getStyleClass().add("tr-cert-course");

                    Label recipientLabel = new Label("Awarded to: " + userName);
                    recipientLabel.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 12px;");

                    Label certNum = new Label("ID: " + cert.getCertificateNumber().substring(0, Math.min(18, cert.getCertificateNumber().length())) + "...");
                    certNum.getStyleClass().add("tr-cert-number");

                    Label dateLabel = new Label("Issued: " + dateStr);
                    dateLabel.getStyleClass().add("tr-cert-date");

                    // ── Signature badge ──
                    if (cert.isSigned()) {
                        String signerName = getUserName(cert.getSignedByUserId());
                        Label signedBadge = new Label("✅ Signed by " + signerName);
                        signedBadge.getStyleClass().add("tr-signed-badge");
                        info.getChildren().addAll(courseLabel, recipientLabel, certNum, dateLabel, signedBadge);
                    } else {
                        Label unsignedBadge = new Label("⏳ Pending signature");
                        unsignedBadge.getStyleClass().add("tr-unsigned-badge");
                        info.getChildren().addAll(courseLabel, recipientLabel, certNum, dateLabel, unsignedBadge);
                    }

                    VBox buttons = new VBox(6);
                    buttons.setAlignment(Pos.CENTER);

                    Button viewBtn = new Button("👁 View");
                    viewBtn.getStyleClass().add("tr-action-btn");
                    viewBtn.setOnAction(e -> showCertificatePreview(cert, courseName, hours, userName, dateStr));

                    Button pdfBtn = new Button("📄 Save PDF");
                    pdfBtn.getStyleClass().add("tr-action-btn");
                    pdfBtn.setOnAction(e -> exportCertificatePdf(cert, courseName, hours));

                    buttons.getChildren().addAll(viewBtn, pdfBtn);

                    // ── Sign button (HR / Admin only) ──
                    if (isHrOrAdmin && !cert.isSigned()) {
                        Button signBtn = new Button("✍ Sign");
                        signBtn.getStyleClass().addAll("tr-action-btn", "tr-sign-btn");
                        final TrainingCertificate fCert = cert;
                        final String fCourseName = courseName;
                        final double fHours = hours;
                        final String fUserName = userName;
                        final String fDateStr = dateStr;
                        signBtn.setOnAction(e -> showSignatureDialog(fCert, fCourseName, fHours, fUserName, fDateStr));
                        buttons.getChildren().add(signBtn);
                    }

                    card.getChildren().addAll(certIcon, info, buttons);
                    certList.getChildren().add(card);
                }
            }
        } catch (SQLException e) {
            certList.getChildren().add(new Label("Error: " + e.getMessage()));
        }

        view.getChildren().addAll(title, certList);
        scroll.setContent(view);
        contentArea.getChildren().setAll(scroll);
    }

    /** Shows a rich in-app certificate preview dialog. */
    private void showCertificatePreview(TrainingCertificate cert, String courseName,
                                         double hours, String userName, String dateStr) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.setTitle("Certificate of Completion");

        // Landscape-ish aspect ratio — taller when signed to fit signature image
        boolean hasSig = cert.isSigned() && cert.getSignatureData() != null;
        double cw = 780, ch = hasSig ? 570 : 520;

        StackPane canvas = new StackPane();
        canvas.setPrefSize(cw, ch);
        canvas.setMaxSize(cw, ch);
        canvas.setStyle("-fx-background-color: #FCF9F2; -fx-background-radius: 0;");

        // ── Outer border ──
        StackPane outerBorder = new StackPane();
        outerBorder.setMaxSize(cw - 20, ch - 20);
        outerBorder.setStyle("-fx-border-color: #2C666E; -fx-border-width: 3; -fx-border-radius: 0;");

        StackPane innerBorder = new StackPane();
        innerBorder.setMaxSize(cw - 36, ch - 36);
        innerBorder.setStyle("-fx-border-color: #B49932; -fx-border-width: 1.5; -fx-border-radius: 0;");

        // ── Content ──
        VBox content = new VBox();
        content.setAlignment(Pos.TOP_CENTER);
        content.setMaxSize(cw - 60, ch - 60);
        content.setSpacing(0);
        content.setPadding(new Insets(28, 40, 20, 40));

        // Company name
        Label company = new Label("S Y N E R G Y G I G");
        company.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2C666E; -fx-padding: 0 0 4 0;");

        // Gold line
        Region goldLine1 = new Region();
        goldLine1.setPrefSize(120, 1);
        goldLine1.setMaxSize(120, 1);
        goldLine1.setStyle("-fx-background-color: #B49932;");

        Region sp1 = new Region();
        sp1.setPrefHeight(16);

        // Title
        Label certTitle = new Label("Certificate of Completion");
        certTitle.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #19193C;");

        // Divider with dot
        HBox divider = new HBox(8);
        divider.setAlignment(Pos.CENTER);
        divider.setPadding(new Insets(6, 0, 6, 0));
        Region lineL = new Region();
        lineL.setPrefSize(110, 1.5);
        lineL.setMaxSize(110, 1.5);
        lineL.setStyle("-fx-background-color: #2C666E;");
        Label diamond = new Label("◆");
        diamond.setStyle("-fx-text-fill: #B49932; -fx-font-size: 10px;");
        Region lineR = new Region();
        lineR.setPrefSize(110, 1.5);
        lineR.setMaxSize(110, 1.5);
        lineR.setStyle("-fx-background-color: #2C666E;");
        divider.getChildren().addAll(lineL, diamond, lineR);

        Region sp2 = new Region();
        sp2.setPrefHeight(10);

        // "This is to certify that"
        Label certifyText = new Label("This is to certify that");
        certifyText.setStyle("-fx-font-size: 12px; -fx-text-fill: #646478;");

        Region sp3 = new Region();
        sp3.setPrefHeight(12);

        // Recipient name
        Label nameLabel = new Label(userName);
        nameLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #19193C;");

        // Gold underline for name
        Region nameUnderline = new Region();
        nameUnderline.setPrefSize(Math.min(userName.length() * 14, 400), 1);
        nameUnderline.setMaxSize(Math.min(userName.length() * 14, 400), 1);
        nameUnderline.setStyle("-fx-background-color: #B49932;");

        Region sp4 = new Region();
        sp4.setPrefHeight(10);

        // "has successfully completed"
        Label completedText = new Label("has successfully completed the training course");
        completedText.setStyle("-fx-font-size: 12px; -fx-text-fill: #646478;");

        Region sp5 = new Region();
        sp5.setPrefHeight(14);

        // Course name
        int cFontSize = courseName.length() > 40 ? 16 : (courseName.length() > 30 ? 18 : 20);
        Label courseLabel = new Label(courseName);
        courseLabel.setStyle("-fx-font-size: " + cFontSize + "px; -fx-font-weight: bold; -fx-text-fill: #2C666E;");
        courseLabel.setWrapText(true);
        courseLabel.setAlignment(Pos.CENTER);

        Region sp6 = new Region();
        sp6.setPrefHeight(6);

        // Duration
        String durationStr = "Duration: " + (hours == (int) hours ? String.valueOf((int) hours) : String.valueOf(hours)) + " hours";
        Label durationLabel = new Label(durationStr);
        durationLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9696A5;");

        Region sp7 = new Region();
        sp7.setPrefHeight(20);
        VBox.setVgrow(sp7, Priority.ALWAYS);

        // Bottom: Date + Signature
        HBox bottom = new HBox();
        bottom.setAlignment(Pos.CENTER);
        bottom.setSpacing(80);

        VBox dateCol = new VBox(4);
        dateCol.setAlignment(Pos.CENTER);
        Label dateVal = new Label(dateStr);
        dateVal.setStyle("-fx-font-size: 11px; -fx-text-fill: #19193C;");
        Region dateLine = new Region();
        dateLine.setPrefSize(120, 0.5);
        dateLine.setMaxSize(120, 0.5);
        dateLine.setStyle("-fx-background-color: #646478;");
        Label dateCaption = new Label("Date of Issue");
        dateCaption.setStyle("-fx-font-size: 9px; -fx-text-fill: #9696A5;");
        dateCol.getChildren().addAll(dateVal, dateLine, dateCaption);

        VBox sigCol = new VBox(2);
        sigCol.setAlignment(Pos.CENTER);

        // If the certificate is signed, render the drawn signature image
        if (cert.isSigned() && cert.getSignatureData() != null) {
            try {
                byte[] sigBytes = Base64.getDecoder().decode(cert.getSignatureData());
                Image sigImg = new Image(new ByteArrayInputStream(sigBytes));
                ImageView sigView = new ImageView(sigImg);
                sigView.setFitWidth(160);
                sigView.setFitHeight(55);
                sigView.setPreserveRatio(true);
                sigView.setSmooth(true);
                // Clip to keep sharp edges
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(160, 55);
                clip.setArcWidth(4);
                clip.setArcHeight(4);
                sigView.setClip(clip);
                sigCol.getChildren().add(sigView);
            } catch (Exception ex) {
                Label sigVal = new Label("SynergyGig HR");
                sigVal.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #19193C;");
                sigCol.getChildren().add(sigVal);
            }
        } else {
            Label sigVal = new Label("SynergyGig HR");
            sigVal.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #19193C;");
            sigCol.getChildren().add(sigVal);
        }

        Region sigLine = new Region();
        sigLine.setPrefSize(140, 0.5);
        sigLine.setMaxSize(140, 0.5);
        sigLine.setStyle("-fx-background-color: #646478;");
        String signerLabel = cert.isSigned() ? getUserName(cert.getSignedByUserId()) : "Training Director";
        Label sigCaption = new Label(signerLabel);
        sigCaption.setStyle("-fx-font-size: 9px; -fx-text-fill: #9696A5;");
        sigCol.getChildren().addAll(sigLine, sigCaption);

        bottom.getChildren().addAll(dateCol, sigCol);

        Region sp8 = new Region();
        sp8.setPrefHeight(8);

        // Cert ID
        Label certIdLabel = new Label("Certificate ID: " + cert.getCertificateNumber());
        certIdLabel.setStyle("-fx-font-size: 7px; -fx-text-fill: #9696A5; -fx-font-family: 'Courier New';");

        content.getChildren().addAll(company, goldLine1, sp1, certTitle, divider, sp2,
                certifyText, sp3, nameLabel, nameUnderline, sp4, completedText, sp5,
                courseLabel, sp6, durationLabel, sp7, bottom, sp8, certIdLabel);

        canvas.getChildren().addAll(outerBorder, innerBorder, content);

        // Wrap in a dark background with buttons
        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #1a1a2e;");

        HBox btnBar = new HBox(12);
        btnBar.setAlignment(Pos.CENTER);

        Button saveBtn = new Button("📄 Save as PDF");
        saveBtn.setStyle("-fx-background-color: #2C666E; -fx-text-fill: white; -fx-font-size: 13px; "
                + "-fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> {
            dialog.close();
            exportCertificatePdf(cert, courseName, hours);
        });

        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: #333; -fx-text-fill: #ccc; -fx-font-size: 13px; "
                + "-fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> dialog.close());

        btnBar.getChildren().addAll(saveBtn, closeBtn);
        root.getChildren().addAll(canvas, btnBar);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, cw + 48, ch + 90);
        dialog.setScene(scene);
        dialog.setResizable(false);
        DialogHelper.themeStage(dialog);
        dialog.show();
    }

    private void exportCertificatePdf(TrainingCertificate cert, String courseName, double hours) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Certificate PDF");
        fc.setInitialFileName("Certificate_" + cert.getCertificateNumber() + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = fc.showSaveDialog(rootPane.getScene().getWindow());
        if (file != null) {
            try {
                String signerName = cert.isSigned() ? getUserName(cert.getSignedByUserId()) : null;
                TrainingCertificatePdf.export(file.getAbsolutePath(),
                        getUserName(cert.getUserId()), courseName, hours,
                        cert.getCertificateNumber(),
                        cert.getIssuedAt() != null ? cert.getIssuedAt() : new Timestamp(System.currentTimeMillis()),
                        cert.getSignatureData(), signerName);
                showAlert(Alert.AlertType.INFORMATION, "Exported!", "Certificate saved to: " + file.getName());
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Error", "Could not export PDF: " + ex.getMessage());
            }
        }
    }

    // ================================================================
    // SIGNATURE DRAWING DIALOG (HR / Admin)
    // ================================================================

    /**
     * Opens a modal dialog with a drawable canvas where the HR/Admin can draw
     * their signature with the mouse. The signature is saved as a base64 PNG
     * and stored on the certificate so that other members can see it on the
     * certificate preview and PDF export.
     */
    private void showSignatureDialog(TrainingCertificate cert, String courseName,
                                      double hours, String userName, String dateStr) {

        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initOwner(rootPane.getScene().getWindow());
        dialog.setTitle("Sign Certificate — " + courseName);

        // ── Dark root wrapper ──
        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("tr-sig-dialog-root");
        root.setStyle("-fx-background-color: #1a1a2e;");

        // ── Header ──
        Label header = new Label("✍  Draw Your Signature");
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #F0EDEE;");

        Label subtitle = new Label("Sign as " + currentUser.getFullName() + "  •  " + courseName);
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #90DDF0;");
        subtitle.setWrapText(true);

        // ── Canvas container ──
        double canvasW = 480, canvasH = 180;
        StackPane canvasWrapper = new StackPane();
        canvasWrapper.setMaxSize(canvasW + 4, canvasH + 4);
        canvasWrapper.setStyle("-fx-background-color: #2C666E; -fx-background-radius: 10; -fx-padding: 2;");

        Canvas sigCanvas = new Canvas(canvasW, canvasH);
        GraphicsContext gc = sigCanvas.getGraphicsContext2D();

        // White background for drawing
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.fillRect(0, 0, canvasW, canvasH);

        // Subtle guide line (where to sign)
        gc.setStroke(javafx.scene.paint.Color.rgb(200, 200, 210));
        gc.setLineWidth(0.8);
        gc.strokeLine(40, canvasH * 0.72, canvasW - 40, canvasH * 0.72);

        // "Sign here" hint
        gc.setFill(javafx.scene.paint.Color.rgb(180, 180, 195));
        gc.setFont(Font.font("Arial", 10));
        gc.fillText("Sign here", canvasW / 2 - 22, canvasH * 0.72 + 14);

        // Drawing state
        final boolean[] drawing = {false};
        final double[] lastX = {0}, lastY = {0};

        gc.setStroke(javafx.scene.paint.Color.rgb(25, 25, 60));
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineWidth(2.5);

        sigCanvas.setOnMousePressed(e -> {
            drawing[0] = true;
            lastX[0] = e.getX();
            lastY[0] = e.getY();
        });

        sigCanvas.setOnMouseDragged(e -> {
            if (!drawing[0]) return;
            gc.strokeLine(lastX[0], lastY[0], e.getX(), e.getY());
            lastX[0] = e.getX();
            lastY[0] = e.getY();
        });

        sigCanvas.setOnMouseReleased(e -> drawing[0] = false);

        canvasWrapper.getChildren().add(sigCanvas);

        // ── Pen thickness controls ──
        HBox penControls = new HBox(12);
        penControls.setAlignment(Pos.CENTER);

        Label penLabel = new Label("Pen:");
        penLabel.setStyle("-fx-text-fill: #9696A5; -fx-font-size: 11px;");

        String btnStyle = "-fx-background-color: #333; -fx-text-fill: #ccc; -fx-font-size: 11px; "
                + "-fx-padding: 4 10; -fx-background-radius: 5; -fx-cursor: hand;";

        Button thinBtn = new Button("Thin");
        thinBtn.setStyle(btnStyle);
        thinBtn.setOnAction(e -> gc.setLineWidth(1.5));

        Button medBtn = new Button("Medium");
        medBtn.setStyle(btnStyle);
        medBtn.setOnAction(e -> gc.setLineWidth(2.5));

        Button boldBtn = new Button("Bold");
        boldBtn.setStyle(btnStyle);
        boldBtn.setOnAction(e -> gc.setLineWidth(4.0));

        // Pen colour
        Label colorLabel = new Label("Color:");
        colorLabel.setStyle("-fx-text-fill: #9696A5; -fx-font-size: 11px;");

        Button blackPen = new Button("⬛");
        blackPen.setStyle(btnStyle);
        blackPen.setOnAction(e -> gc.setStroke(javafx.scene.paint.Color.rgb(25, 25, 60)));

        Button bluePen = new Button("🔵");
        bluePen.setStyle(btnStyle);
        bluePen.setOnAction(e -> gc.setStroke(javafx.scene.paint.Color.rgb(20, 50, 140)));

        penControls.getChildren().addAll(penLabel, thinBtn, medBtn, boldBtn,
                new Region() {{ setPrefWidth(12); }},
                colorLabel, blackPen, bluePen);

        // ── Buttons ──
        HBox btnBar = new HBox(12);
        btnBar.setAlignment(Pos.CENTER);

        Button clearBtn = new Button("🗑 Clear");
        clearBtn.setStyle("-fx-background-color: #444; -fx-text-fill: #F0EDEE; -fx-font-size: 13px; "
                + "-fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> {
            gc.setFill(javafx.scene.paint.Color.WHITE);
            gc.fillRect(0, 0, canvasW, canvasH);
            gc.setStroke(javafx.scene.paint.Color.rgb(200, 200, 210));
            gc.setLineWidth(0.8);
            gc.strokeLine(40, canvasH * 0.72, canvasW - 40, canvasH * 0.72);
            gc.setFill(javafx.scene.paint.Color.rgb(180, 180, 195));
            gc.setFont(Font.font("Arial", 10));
            gc.fillText("Sign here", canvasW / 2 - 22, canvasH * 0.72 + 14);
            gc.setStroke(javafx.scene.paint.Color.rgb(25, 25, 60));
            gc.setLineWidth(2.5);
        });

        Button confirmBtn = new Button("✅ Confirm & Sign");
        confirmBtn.setStyle("-fx-background-color: #2C666E; -fx-text-fill: white; -fx-font-size: 13px; "
                + "-fx-padding: 8 24; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");
        confirmBtn.setOnAction(e -> {
            // Snapshot the canvas to base64 PNG
            try {
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(javafx.scene.paint.Color.WHITE);
                WritableImage snapshot = sigCanvas.snapshot(params, null);

                java.awt.image.BufferedImage bImg = SwingFXUtils.fromFXImage(snapshot, null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bImg, "png", baos);
                String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

                // Persist to DB
                serviceCertificate.signCertificate(cert.getId(), currentUser.getId(), base64);

                // Update in-memory object
                cert.setSignedByUserId(currentUser.getId());
                cert.setSignatureData(base64);
                cert.setSignedAt(new Timestamp(System.currentTimeMillis()));

                SoundManager.getInstance().play(SoundManager.TASK_COMPLETED);
                dialog.close();

                // Refresh certificates tab
                showCertificatesTab();

                showAlert(Alert.AlertType.INFORMATION, "Signed!",
                        "Certificate for \"" + courseName + "\" has been signed by " + currentUser.getFullName() + ".");

            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Signature Error",
                        "Could not save signature: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #333; -fx-text-fill: #ccc; -fx-font-size: 13px; "
                + "-fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> dialog.close());

        btnBar.getChildren().addAll(clearBtn, confirmBtn, cancelBtn);

        root.getChildren().addAll(header, subtitle, canvasWrapper, penControls, btnBar);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, canvasW + 96, canvasH + 220);
        dialog.setScene(scene);
        dialog.setResizable(false);
        DialogHelper.themeStage(dialog);
        dialog.show();
    }

    // ================================================================
    // TAB 5: MANAGE COURSES (HR/Admin only)
    // ================================================================

    private void showManageTab() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("tr-scroll");

        VBox view = new VBox(16);
        view.getStyleClass().add("tr-view");
        view.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Manage Courses");
        title.getStyleClass().add("tr-view-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        Button addBtn = new Button("➕ Add Course");
        addBtn.getStyleClass().add("tr-action-btn");
        addBtn.setOnAction(e -> showCourseDialog(null));

        header.getChildren().addAll(title, addBtn);

        // ── Filter & Column Toggle Row ──
        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.getStyleClass().add("tr-filter-row");
        filterRow.setPadding(new Insets(8, 0, 4, 0));

        TextField titleFilter = new TextField();
        titleFilter.setPromptText("🔍 Search by title...");
        titleFilter.getStyleClass().add("tr-filter-search");
        titleFilter.setPrefWidth(200);

        ComboBox<String> catFilter = new ComboBox<>();
        catFilter.getItems().addAll("All", "TECHNICAL", "SOFT_SKILLS", "COMPLIANCE", "ONBOARDING", "LEADERSHIP");
        catFilter.setValue("All");
        catFilter.getStyleClass().add("tr-filter-combo");
        catFilter.setPromptText("Category");

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All", "DRAFT", "ACTIVE", "ARCHIVED");
        statusFilter.setValue("All");
        statusFilter.getStyleClass().add("tr-filter-combo");
        statusFilter.setPromptText("Status");

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        // Column toggle
        String[] colNames = {"Title", "Category", "Status", "Duration", "Enrollments", "Actions"};
        boolean[] colVis = {true, true, true, true, true, true};
        MenuButton colToggle = new MenuButton("⚙ Columns");
        colToggle.getStyleClass().add("tr-column-toggle");

        VBox courseList = new VBox(10);

        for (int i = 0; i < colNames.length; i++) {
            CheckMenuItem item = new CheckMenuItem(colNames[i]);
            item.setSelected(true);
            final int idx = i;
            item.selectedProperty().addListener((obs, ov, nv) -> {
                colVis[idx] = nv;
                refreshCourseManageList(courseList, titleFilter.getText(), catFilter.getValue(), statusFilter.getValue(), colVis);
            });
            colToggle.getItems().add(item);
        }

        filterRow.getChildren().addAll(titleFilter, catFilter, statusFilter, filterSpacer, colToggle);

        // Listeners
        titleFilter.textProperty().addListener((obs, ov, nv) ->
                refreshCourseManageList(courseList, nv, catFilter.getValue(), statusFilter.getValue(), colVis));
        catFilter.valueProperty().addListener((obs, ov, nv) ->
                refreshCourseManageList(courseList, titleFilter.getText(), nv, statusFilter.getValue(), colVis));
        statusFilter.valueProperty().addListener((obs, ov, nv) ->
                refreshCourseManageList(courseList, titleFilter.getText(), catFilter.getValue(), nv, colVis));

        view.getChildren().addAll(header, filterRow, courseList);
        scroll.setContent(view);
        contentArea.getChildren().setAll(scroll);

        refreshCourseManageList(courseList, "", "All", "All", colVis);
    }

    private void refreshCourseManageList(VBox courseList, String titleFilter, String catFilter,
                                          String statusFilter, boolean[] colVis) {
        courseList.getChildren().clear();
        try {
            List<TrainingCourse> courses = serviceCourse.recuperer();

            // Apply filters
            if (titleFilter != null && !titleFilter.isEmpty()) {
                String q = titleFilter.toLowerCase();
                courses = courses.stream()
                        .filter(c -> c.getTitle().toLowerCase().contains(q))
                        .collect(Collectors.toList());
            }
            if (catFilter != null && !"All".equals(catFilter)) {
                courses = courses.stream()
                        .filter(c -> catFilter.equals(c.getCategory()))
                        .collect(Collectors.toList());
            }
            if (statusFilter != null && !"All".equals(statusFilter)) {
                courses = courses.stream()
                        .filter(c -> statusFilter.equals(c.getStatus()))
                        .collect(Collectors.toList());
            }

            for (TrainingCourse c : courses) {
                HBox row = new HBox(12);
                row.getStyleClass().add("tr-manage-row");
                row.setPadding(new Insets(12));
                row.setAlignment(Pos.CENTER_LEFT);

                VBox info = new VBox(4);
                HBox.setHgrow(info, Priority.ALWAYS);

                HBox meta = new HBox(8);

                // Col 0: Title
                if (colVis[0]) {
                    Label nameLabel = new Label(c.getTitle());
                    nameLabel.getStyleClass().add("tr-list-name");
                    info.getChildren().add(nameLabel);
                }

                // Col 1: Category badge
                if (colVis[1]) {
                    Label catLabel = new Label(formatCategory(c.getCategory()));
                    catLabel.getStyleClass().addAll("tr-badge", "tr-badge-" + c.getCategory().toLowerCase().replace("_", "-"));
                    meta.getChildren().add(catLabel);
                }

                // Col 2: Status badge
                if (colVis[2]) {
                    Label statusLabel = new Label(c.getStatus());
                    statusLabel.getStyleClass().add("tr-badge-" + c.getStatus().toLowerCase());
                    meta.getChildren().add(statusLabel);
                }

                // Col 3: Duration
                if (colVis[3]) {
                    Label durLabel = new Label("⏱ " + c.getDurationHours() + "h");
                    durLabel.getStyleClass().add("tr-card-info");
                    meta.getChildren().add(durLabel);
                }

                // Col 4: Enrollment count vs capacity
                if (colVis[4]) {
                    long enrollCount = 0;
                    try {
                        enrollCount = serviceEnrollment.getByCourse(c.getId()).stream()
                                .filter(en -> !"DROPPED".equals(en.getStatus())).count();
                    } catch (SQLException ignored) {}
                    String capStr = c.getMaxParticipants() > 0 ? (" / " + c.getMaxParticipants()) : "";
                    Label enrollLabel = new Label("👥 " + enrollCount + capStr + " enrolled");
                    if (c.getMaxParticipants() > 0 && enrollCount >= c.getMaxParticipants()) {
                        enrollLabel.setStyle("-fx-text-fill: #FF4444;");
                    }
                    enrollLabel.getStyleClass().add("tr-card-info");
                    meta.getChildren().add(enrollLabel);
                }

                if (!meta.getChildren().isEmpty()) info.getChildren().add(meta);
                row.getChildren().add(info);

                // Col 5: Actions
                if (colVis[5]) {
                    Button editBtn = new Button("✏");
                    editBtn.getStyleClass().add("tr-icon-btn");
                    editBtn.setOnAction(e -> showCourseDialog(c));

                    Button deleteBtn = new Button("🗑");
                    deleteBtn.getStyleClass().add("tr-icon-btn-danger");
                    deleteBtn.setOnAction(e -> deleteCourse(c));

                    row.getChildren().addAll(editBtn, deleteBtn);
                }

                courseList.getChildren().add(row);
            }

            if (courses.isEmpty()) {
                courseList.getChildren().add(new Label("No courses found matching filters."));
            }
        } catch (SQLException e) {
            courseList.getChildren().add(new Label("Error: " + e.getMessage()));
        }
    }

    private void showCourseDialog(TrainingCourse existing) {
        Dialog<TrainingCourse> dialog = new Dialog<>();
        DialogHelper.theme(dialog);
        dialog.setTitle(existing == null ? "Add Course" : "Edit Course");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStyleClass().add("tr-dialog");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField titleField = new TextField(existing != null ? existing.getTitle() : "");
        titleField.setPromptText("Course Title");

        TextArea descField = new TextArea(existing != null ? existing.getDescription() : "");
        descField.setPromptText("Course Description");
        descField.setPrefRowCount(3);

        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll("TECHNICAL", "SOFT_SKILLS", "COMPLIANCE", "ONBOARDING", "LEADERSHIP");
        categoryBox.setValue(existing != null ? existing.getCategory() : "TECHNICAL");

        ComboBox<String> difficultyBox = new ComboBox<>();
        difficultyBox.getItems().addAll("BEGINNER", "INTERMEDIATE", "ADVANCED");
        difficultyBox.setValue(existing != null ? existing.getDifficulty() : "BEGINNER");

        Spinner<Double> durationSpinner = new Spinner<>(0.5, 200, existing != null ? existing.getDurationHours() : 2.0, 0.5);
        durationSpinner.setEditable(true);

        TextField instructorField = new TextField(existing != null ? existing.getInstructorName() : "");
        instructorField.setPromptText("Instructor Name");

        TextField megaField = new TextField(existing != null ? existing.getMegaLink() : "");
        megaField.setPromptText("Mega.nz Link (optional)");

        Spinner<Integer> maxPart = new Spinner<>(1, 500, existing != null ? existing.getMaxParticipants() : 50);

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("DRAFT", "ACTIVE", "ARCHIVED");
        statusBox.setValue(existing != null ? existing.getStatus() : "ACTIVE");

        DatePicker startPicker = new DatePicker(existing != null && existing.getStartDate() != null ?
                existing.getStartDate().toLocalDate() : LocalDate.now());
        DatePicker endPicker = new DatePicker(existing != null && existing.getEndDate() != null ?
                existing.getEndDate().toLocalDate() : LocalDate.now().plusMonths(1));

        // Quiz timer per question (0 = AI decides based on difficulty)
        Spinner<Integer> quizTimerSpinner = new Spinner<>(0, 120,
                existing != null ? existing.getQuizTimerSeconds() : 0);
        quizTimerSpinner.setEditable(true);
        Label timerHint = new Label("0 = AI auto (BEGINNER: 10s, INTERMEDIATE: 12s, ADVANCED: 15s)");
        timerHint.setStyle("-fx-text-fill: #6B6B80; -fx-font-size: 10px; -fx-font-style: italic;");
        VBox timerBox = new VBox(2, quizTimerSpinner, timerHint);

        int row = 0;
        grid.add(new Label("Title:"), 0, row); grid.add(titleField, 1, row++);
        grid.add(new Label("Description:"), 0, row); grid.add(descField, 1, row++);
        grid.add(new Label("Category:"), 0, row); grid.add(categoryBox, 1, row++);
        grid.add(new Label("Difficulty:"), 0, row); grid.add(difficultyBox, 1, row++);
        grid.add(new Label("Duration (h):"), 0, row); grid.add(durationSpinner, 1, row++);
        grid.add(new Label("Instructor:"), 0, row); grid.add(instructorField, 1, row++);
        grid.add(new Label("Mega Link:"), 0, row); grid.add(megaField, 1, row++);
        grid.add(new Label("Max Participants:"), 0, row); grid.add(maxPart, 1, row++);
        grid.add(new Label("Status:"), 0, row); grid.add(statusBox, 1, row++);
        grid.add(new Label("Start Date:"), 0, row); grid.add(startPicker, 1, row++);
        grid.add(new Label("End Date:"), 0, row); grid.add(endPicker, 1, row++);
        grid.add(new Label("⏱ Quiz Timer (s):"), 0, row); grid.add(timerBox, 1, row++);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                TrainingCourse c = existing != null ? existing : new TrainingCourse();
                c.setTitle(titleField.getText().trim());
                c.setDescription(descField.getText().trim());
                c.setCategory(categoryBox.getValue());
                c.setDifficulty(difficultyBox.getValue());
                c.setDurationHours(durationSpinner.getValue());
                c.setInstructorName(instructorField.getText().trim());
                c.setMegaLink(megaField.getText().trim());
                c.setMaxParticipants(maxPart.getValue());
                c.setStatus(statusBox.getValue());
                c.setStartDate(startPicker.getValue() != null ? Date.valueOf(startPicker.getValue()) : null);
                c.setEndDate(endPicker.getValue() != null ? Date.valueOf(endPicker.getValue()) : null);
                c.setQuizTimerSeconds(quizTimerSpinner.getValue());
                if (existing == null) c.setCreatedBy(currentUser.getId());
                return c;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(course -> {
            try {
                if (existing == null) {
                    serviceCourse.ajouter(course);
                    showAlert(Alert.AlertType.INFORMATION, "Created", "Course created: " + course.getTitle());
                } else {
                    serviceCourse.modifier(course);
                    showAlert(Alert.AlertType.INFORMATION, "Updated", "Course updated: " + course.getTitle());
                }
                loadCourseMap();
                showManageTab();
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });
    }

    private void deleteCourse(TrainingCourse course) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete course: " + course.getTitle() + "?", ButtonType.YES, ButtonType.NO);
        DialogHelper.theme(confirm);
        confirm.setHeaderText("This will also remove all enrollments for this course.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    serviceCourse.supprimer(course.getId());
                    loadCourseMap();
                    showManageTab();
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
                }
            }
        });
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private void openMegaLink(String link) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(link));
            }
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open browser: " + ex.getMessage());
        }
    }

    private void triggerN8nWebhook(String event, Map<String, Object> payload) {
        AppThreadPool.io(() -> {
            try {
                String n8nBase = AppConfig.get("n8n.base_url", "");
                if (n8nBase.isEmpty()) return;

                String url = n8nBase + "/webhook/training-" + event;
                String json = gson.toJson(payload);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {
                // n8n optional — don't break the app
            }
        });
    }

    private String formatCategory(String cat) {
        if (cat == null) return "";
        return switch (cat) {
            case "TECHNICAL" -> "💻 Technical";
            case "SOFT_SKILLS" -> "🤝 Soft Skills";
            case "COMPLIANCE" -> "📋 Compliance";
            case "ONBOARDING" -> "🚀 Onboarding";
            case "LEADERSHIP" -> "👑 Leadership";
            default -> cat;
        };
    }

    private int statusOrder(String status) {
        return switch (status == null ? "" : status) {
            case "IN_PROGRESS" -> 0;
            case "ENROLLED" -> 1;
            case "COMPLETED" -> 2;
            default -> 3;
        };
    }

    // ==================== AI RECOMMENDATIONS SECTION ====================

    private VBox createAIRecommendationsSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("tr-section-card");
        section.setPadding(new Insets(16));

        Label title = new Label("🤖 AI Course Recommendations");
        title.getStyleClass().add("tr-section-title");

        Label subtitle = new Label("Get personalized course suggestions based on your role and learning history");
        subtitle.getStyleClass().add("tr-section-subtitle");

        TextArea recArea = new TextArea();
        recArea.setEditable(false);
        recArea.setWrapText(true);
        recArea.setPrefHeight(250);
        recArea.setPromptText("Click the button to get AI recommendations...");
        recArea.getStyleClass().add("tr-ai-result");

        Label statusLabel = new Label("");
        statusLabel.getStyleClass().add("tr-section-subtitle");

        Button btnRecommend = new Button("🧠 Get Recommendations");
        btnRecommend.getStyleClass().add("tr-action-btn");

        Button btnLearningPath = new Button("📍 Learning Path");
        btnLearningPath.getStyleClass().add("tr-action-btn");

        btnRecommend.setOnAction(e -> {
            btnRecommend.setDisable(true);
            btnRecommend.setText("⏳ Analyzing...");
            statusLabel.setText("AI is analyzing your learning profile...");

            AppThreadPool.io(() -> {
                try {
                    // Gather user data
                    List<TrainingEnrollment> myEnrolls = serviceEnrollment.getByUser(currentUser.getId());
                    List<TrainingCourse> allCourses = serviceCourse.recuperer();

                    // Build completed courses list
                    Set<Integer> enrolledIds = new HashSet<>();
                    StringBuilder completed = new StringBuilder();
                    for (TrainingEnrollment en : myEnrolls) {
                        enrolledIds.add(en.getCourseId());
                        TrainingCourse c = courseMap.get(en.getCourseId());
                        if (c != null) {
                            completed.append(c.getTitle()).append(" (").append(c.getCategory()).append(", ").append(en.getStatus()).append(")\n");
                        }
                    }

                    // Build available courses list (not yet enrolled)
                    StringBuilder available = new StringBuilder();
                    for (TrainingCourse c : allCourses) {
                        if ("ACTIVE".equals(c.getStatus()) && !enrolledIds.contains(c.getId())) {
                            available.append(c.getTitle()).append(" - ").append(c.getCategory())
                                    .append(" (").append(c.getDifficulty()).append(", ").append(c.getDurationHours()).append("h)\n");
                        }
                    }

                    ZAIService zai = new ZAIService();
                    String result = zai.recommendCourses(
                            currentUser.getRole(),
                            completed.toString(),
                            available.length() > 0 ? available.toString() : "No additional courses available");

                    Platform.runLater(() -> {
                        recArea.setText(result);
                        statusLabel.setText("✅ Recommendations ready");
                        btnRecommend.setDisable(false);
                        btnRecommend.setText("🧠 Get Recommendations");
                        SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        recArea.setText("Failed: " + ex.getMessage());
                        statusLabel.setText("❌ Failed");
                        btnRecommend.setDisable(false);
                        btnRecommend.setText("🧠 Get Recommendations");
                    });
                }
            });
        });

        btnLearningPath.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("Improve my skills");
            DialogHelper.theme(dialog);
            dialog.setTitle("Learning Path");
            dialog.setHeaderText("📍 What are your learning goals?");
            dialog.setContentText("Goals:");
            dialog.showAndWait().ifPresent(goals -> {
                btnLearningPath.setDisable(true);
                btnLearningPath.setText("⏳ Generating...");
                statusLabel.setText("Generating personalized learning path...");

                AppThreadPool.io(() -> {
                    try {
                        List<TrainingEnrollment> myEnrolls = serviceEnrollment.getByUser(currentUser.getId());
                        StringBuilder skills = new StringBuilder();
                        for (TrainingEnrollment en : myEnrolls) {
                            if ("COMPLETED".equals(en.getStatus())) {
                                TrainingCourse c = courseMap.get(en.getCourseId());
                                if (c != null) skills.append(c.getTitle()).append(", ");
                            }
                        }

                        ZAIService zai = new ZAIService();
                        String path = zai.generateLearningPath(
                                currentUser.getRole(),
                                skills.length() > 0 ? skills.toString() : "Beginner",
                                goals);

                        Platform.runLater(() -> {
                            recArea.setText(path);
                            statusLabel.setText("✅ Learning path ready");
                            btnLearningPath.setDisable(false);
                            btnLearningPath.setText("📍 Learning Path");
                            SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            recArea.setText("Failed: " + ex.getMessage());
                            btnLearningPath.setDisable(false);
                            btnLearningPath.setText("📍 Learning Path");
                        });
                    }
                });
            });
        });

        HBox buttons = new HBox(12, btnRecommend, btnLearningPath);
        buttons.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().addAll(title, subtitle, buttons, statusLabel, recArea);
        return section;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            DialogHelper.theme(alert);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}
