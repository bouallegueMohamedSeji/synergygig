package controllers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
        loadCourseMap();

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
            btn.setOnAction(e -> {
                SoundManager.getInstance().play(SoundManager.TAB_SWITCH);
                switchTab(btn, tab[1]);
            });
            tabBar.getChildren().add(btn);
        }

        if (!tabBar.getChildren().isEmpty()) {
            Button first = (Button) tabBar.getChildren().get(0);
            switchTab(first, "Dashboard");
        }
    }

    private void loadUserNames() {
        utils.UserNameCache.refresh();
    }

    private void loadCourseMap() {
        try {
            for (TrainingCourse c : serviceCourse.recuperer()) {
                courseMap.put(c.getId(), c);
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private String getUserName(int userId) {
        return utils.UserNameCache.getName(userId);
    }

    private void switchTab(Button btn, String tabName) {
        if (activeTab != null) activeTab.getStyleClass().remove("tr-tab-active");
        btn.getStyleClass().add("tr-tab-active");
        activeTab = btn;

        switch (tabName) {
            case "Dashboard":    showDashboardTab(); break;
            case "Catalog":      showCatalogTab(); break;
            case "My Learning":  showMyLearningTab(); break;
            case "Certificates": showCertificatesTab(); break;
            case "Manage":       showManageTab(); break;
        }
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

        Thread t = new Thread(() -> {
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
        t.setDaemon(true);
        t.start();
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

        Thread t = new Thread(() -> {
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
        t.setDaemon(true);
        t.start();
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

                for (TrainingCourse c : courses) {
                    if (!searchText.isEmpty() && !c.getTitle().toLowerCase().contains(searchText)
                            && !c.getDescription().toLowerCase().contains(searchText)) continue;
                    if (!"All Categories".equals(catVal) && !catVal.equals(c.getCategory())) continue;
                    if (!"All Levels".equals(diffVal) && !diffVal.equals(c.getDifficulty())) continue;

                    VBox card = createCourseCard(c, enrolledCourseIds.contains(c.getId()));
                    courseGrid.getChildren().add(card);
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
        VBox card = new VBox(10);
        card.getStyleClass().add("tr-course-card");
        card.setPadding(new Insets(16));
        card.setPrefWidth(280);

        // Category + Difficulty badges
        HBox badges = new HBox(8);
        badges.setAlignment(Pos.CENTER_LEFT);

        Label catBadge = new Label(formatCategory(course.getCategory()));
        catBadge.getStyleClass().addAll("tr-badge", "tr-badge-" + course.getCategory().toLowerCase().replace("_", "-"));

        Label diffBadge = new Label(course.getDifficulty());
        diffBadge.getStyleClass().addAll("tr-badge", "tr-badge-" + course.getDifficulty().toLowerCase());

        badges.getChildren().addAll(catBadge, diffBadge);

        // Title
        Label titleLabel = new Label(course.getTitle());
        titleLabel.getStyleClass().add("tr-card-title");
        titleLabel.setWrapText(true);

        // Description
        Label desc = new Label(truncate(course.getDescription(), 100));
        desc.getStyleClass().add("tr-card-desc");
        desc.setWrapText(true);

        // Info row
        HBox infoRow = new HBox(12);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        Label durationLabel = new Label("⏱ " + course.getDurationHours() + "h");
        durationLabel.getStyleClass().add("tr-card-info");

        Label instructorLabel = new Label("👤 " + course.getInstructorName());
        instructorLabel.getStyleClass().add("tr-card-info");

        infoRow.getChildren().addAll(durationLabel, instructorLabel);

        // Dates
        HBox dateRow = new HBox(8);
        dateRow.setAlignment(Pos.CENTER_LEFT);
        if (course.getStartDate() != null) {
            Label dateLabel = new Label("📅 " + course.getStartDate().toLocalDate().format(DateTimeFormatter.ofPattern("MMM dd")) +
                    (course.getEndDate() != null ? " → " + course.getEndDate().toLocalDate().format(DateTimeFormatter.ofPattern("MMM dd")) : ""));
            dateLabel.getStyleClass().add("tr-card-info");
            dateRow.getChildren().add(dateLabel);
        }

        // Action buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        if (isEnrolled) {
            Label enrolledLabel = new Label("✅ Enrolled");
            enrolledLabel.getStyleClass().add("tr-enrolled-label");
            actions.getChildren().add(enrolledLabel);
        } else {
            Button enrollBtn = new Button("📝 Enroll");
            enrollBtn.getStyleClass().add("tr-enroll-btn");
            enrollBtn.setOnAction(e -> enrollInCourse(course));
            actions.getChildren().add(enrollBtn);
        }

        if (course.getMegaLink() != null && !course.getMegaLink().isEmpty()) {
            Button megaBtn = new Button("☁ Open Course");
            megaBtn.getStyleClass().add("tr-mega-btn");
            megaBtn.setOnAction(e -> openMegaLink(course.getMegaLink()));
            actions.getChildren().add(megaBtn);
        }

        card.getChildren().addAll(badges, titleLabel, desc, infoRow, dateRow, actions);
        return card;
    }

    private void enrollInCourse(TrainingCourse course) {
        try {
            TrainingEnrollment enrollment = new TrainingEnrollment(course.getId(), currentUser.getId());
            serviceEnrollment.ajouter(enrollment);

            // Try n8n webhook (fire-and-forget)
            triggerN8nWebhook("enroll", Map.of(
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

        VBox courseList = new VBox(12);

        try {
            List<TrainingEnrollment> enrollments = serviceEnrollment.getByUser(currentUser.getId());

            if (enrollments.isEmpty()) {
                Label empty = new Label("You haven't enrolled in any courses yet.\nGo to the Catalog to browse and enroll!");
                empty.getStyleClass().add("tr-empty-text");
                empty.setWrapText(true);
                courseList.getChildren().add(empty);
            } else {
                for (TrainingEnrollment en : enrollments) {
                    if ("DROPPED".equals(en.getStatus())) continue;
                    TrainingCourse course = courseMap.get(en.getCourseId());
                    if (course == null) continue;

                    VBox card = new VBox(10);
                    card.getStyleClass().add("tr-learning-card");
                    card.setPadding(new Insets(16));

                    HBox topRow = new HBox(12);
                    topRow.setAlignment(Pos.CENTER_LEFT);

                    Label courseName = new Label(course.getTitle());
                    courseName.getStyleClass().add("tr-card-title");
                    HBox.setHgrow(courseName, Priority.ALWAYS);
                    courseName.setMaxWidth(Double.MAX_VALUE);

                    Label statusLabel = new Label(en.getStatus().replace("_", " "));
                    statusLabel.getStyleClass().add("tr-badge-" + en.getStatus().toLowerCase().replace("_", "-"));

                    topRow.getChildren().addAll(courseName, statusLabel);

                    // Progress
                    HBox progressRow = new HBox(12);
                    progressRow.setAlignment(Pos.CENTER_LEFT);

                    ProgressBar pb = new ProgressBar(en.getProgress() / 100.0);
                    pb.setPrefWidth(300);
                    pb.setPrefHeight(12);
                    pb.getStyleClass().add("tr-progress-bar");
                    HBox.setHgrow(pb, Priority.ALWAYS);

                    Label pctLabel = new Label(en.getProgress() + "%");
                    pctLabel.getStyleClass().add("tr-progress-pct");

                    progressRow.getChildren().addAll(pb, pctLabel);

                    // Actions
                    HBox actions = new HBox(8);
                    actions.setAlignment(Pos.CENTER_LEFT);

                    if (!"COMPLETED".equals(en.getStatus())) {
                        Spinner<Integer> progressSpinner = new Spinner<>(0, 100, en.getProgress(), 10);
                        progressSpinner.setPrefWidth(90);
                        progressSpinner.setEditable(true);
                        progressSpinner.getStyleClass().add("tr-progress-spinner");

                        Button updateBtn = new Button("📊 Update Progress");
                        updateBtn.getStyleClass().add("tr-action-btn");
                        updateBtn.setOnAction(e -> {
                            int newProg = progressSpinner.getValue();
                            updateEnrollmentProgress(en, newProg, course);
                        });

                        actions.getChildren().addAll(progressSpinner, updateBtn);
                    }

                    if (course.getMegaLink() != null && !course.getMegaLink().isEmpty()) {
                        Button megaBtn = new Button("☁ Open Course");
                        megaBtn.getStyleClass().add("tr-mega-btn");
                        megaBtn.setOnAction(e -> openMegaLink(course.getMegaLink()));
                        actions.getChildren().add(megaBtn);
                    }

                    if (!"COMPLETED".equals(en.getStatus())) {
                        Button dropBtn = new Button("🗑 Drop");
                        dropBtn.getStyleClass().add("tr-drop-btn");
                        dropBtn.setOnAction(e -> dropEnrollment(en));
                        actions.getChildren().add(dropBtn);
                    }

                    // Course info
                    HBox info = new HBox(16);
                    info.setAlignment(Pos.CENTER_LEFT);
                    Label catLabel = new Label(formatCategory(course.getCategory()));
                    catLabel.getStyleClass().addAll("tr-badge", "tr-badge-" + course.getCategory().toLowerCase().replace("_", "-"));
                    Label durLabel = new Label("⏱ " + course.getDurationHours() + "h");
                    durLabel.getStyleClass().add("tr-card-info");
                    info.getChildren().addAll(catLabel, durLabel);

                    card.getChildren().addAll(topRow, progressRow, info, actions);
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

        Label title = new Label("My Certificates");
        title.getStyleClass().add("tr-view-title");

        VBox certList = new VBox(12);

        try {
            List<TrainingCertificate> certs = serviceCertificate.getByUser(currentUser.getId());

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

                    info.getChildren().addAll(courseLabel, recipientLabel, certNum, dateLabel);

                    VBox buttons = new VBox(6);
                    buttons.setAlignment(Pos.CENTER);

                    Button viewBtn = new Button("👁 View");
                    viewBtn.getStyleClass().add("tr-action-btn");
                    viewBtn.setOnAction(e -> showCertificatePreview(cert, courseName, hours, userName, dateStr));

                    Button pdfBtn = new Button("📄 Save PDF");
                    pdfBtn.getStyleClass().add("tr-action-btn");
                    pdfBtn.setOnAction(e -> exportCertificatePdf(cert, courseName, hours));

                    buttons.getChildren().addAll(viewBtn, pdfBtn);

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

        // Landscape-ish aspect ratio
        double cw = 780, ch = 520;

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

        VBox sigCol = new VBox(4);
        sigCol.setAlignment(Pos.CENTER);
        Label sigVal = new Label("SynergyGig HR");
        sigVal.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #19193C;");
        Region sigLine = new Region();
        sigLine.setPrefSize(120, 0.5);
        sigLine.setMaxSize(120, 0.5);
        sigLine.setStyle("-fx-background-color: #646478;");
        Label sigCaption = new Label("Training Director");
        sigCaption.setStyle("-fx-font-size: 9px; -fx-text-fill: #9696A5;");
        sigCol.getChildren().addAll(sigVal, sigLine, sigCaption);

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
                TrainingCertificatePdf.export(file.getAbsolutePath(),
                        getUserName(cert.getUserId()), courseName, hours,
                        cert.getCertificateNumber(),
                        cert.getIssuedAt() != null ? cert.getIssuedAt() : new Timestamp(System.currentTimeMillis()));
                showAlert(Alert.AlertType.INFORMATION, "Exported!", "Certificate saved to: " + file.getName());
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Error", "Could not export PDF: " + ex.getMessage());
            }
        }
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

                // Col 4: Enrollment count
                if (colVis[4]) {
                    long enrollCount = 0;
                    try { enrollCount = serviceEnrollment.getByCourse(c.getId()).size(); } catch (SQLException ignored) {}
                    Label enrollLabel = new Label("👥 " + enrollCount + " enrolled");
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
        Thread t = new Thread(() -> {
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
        t.setDaemon(true);
        t.start();
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

            new Thread(() -> {
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
            }).start();
        });

        btnLearningPath.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("Improve my skills");
            dialog.setTitle("Learning Path");
            dialog.setHeaderText("📍 What are your learning goals?");
            dialog.setContentText("Goals:");
            dialog.showAndWait().ifPresent(goals -> {
                btnLearningPath.setDisable(true);
                btnLearningPath.setText("⏳ Generating...");
                statusLabel.setText("Generating personalized learning path...");

                new Thread(() -> {
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
                }).start();
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
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}
