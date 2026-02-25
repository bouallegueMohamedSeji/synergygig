package controllers;

import entities.Comment;
import entities.Post;
import entities.Reaction;
import entities.User;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.stage.Window;
import utils.StyledAlert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.util.Duration;
import services.ServiceComment;
import services.ServicePost;
import services.ServiceReaction;
import services.ServiceUser;
import utils.AnimatedButton;
import utils.AppThreadPool;
import utils.BadWordsService;
import utils.SessionManager;
import utils.SoundManager;
import services.ZAIService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class CommunityController implements Stoppable {

    @FXML private VBox newPostForm;
    @FXML private TextArea postTextArea;
    @FXML private Button btnNewPost;
    @FXML private Button btnSubmitPost;
    @FXML private Button btnAttachPostImage;
    @FXML private Button btnAiImprove;
    @FXML private Button btnAiSentiment;
    @FXML private Label imageAttachLabel;
    @FXML private ScrollPane feedScroll;
    @FXML private VBox feedContainer;

    // Detail view elements
    @FXML private VBox detailView;
    @FXML private VBox detailContent;

    // Tab bar + tab buttons
    @FXML private HBox tabBar;
    @FXML private Button tabFeed;
    @FXML private Button tabWiki;
    @FXML private Button tabQuotes;

    // Wikipedia tab
    @FXML private VBox wikiPane;
    @FXML private TextField wikiSearchField;
    @FXML private Button btnWikiSearch;
    @FXML private ScrollPane wikiScroll;
    @FXML private VBox wikiResultBox;

    // Quotes tab
    @FXML private VBox quotesPane;
    @FXML private Button btnRefreshQuote;
    @FXML private VBox quoteBox;

    // Animated "+ New Post" button (replaces btnNewPost in init)
    private javafx.scene.layout.StackPane animNewPostBtn;

    // Bot author ID — posts from this user are hidden from the community feed
    private static final int BOT_AUTHOR_ID = 1;

    private final ServicePost servicePost = new ServicePost();
    private final ServiceComment serviceComment = new ServiceComment();
    private final ServiceReaction serviceReaction = new ServiceReaction();
    private final ServiceUser serviceUser = new ServiceUser();

    private Map<Integer, User> userCache = new HashMap<>();
    private String pendingImageBase64 = null;
    private ScheduledExecutorService scheduler;

    // Track which posts have their comments expanded (in feed view)
    private Set<Integer> expandedComments = new HashSet<>();

    // Cache of post IDs where comments endpoint returned errors (avoid spam)
    private final Map<Integer, Long> commentErrorCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long COMMENT_ERROR_COOLDOWN_MS = 60_000; // 1 min

    // Saved / bookmarked posts (persisted in Preferences)
    private final Set<Integer> savedPostIds = new LinkedHashSet<>();
    private static final String PREF_SAVED_POSTS = "community_saved_posts";

    // Currently open post detail (null = feed view)
    private Post detailPost = null;

    // Available emoji reactions — hex codepoints for Twemoji PNG lookup
    private static final String[] REACTION_HEX = {
            "2764",   // ❤️
            "1f44d",  // 👍
            "1f602",  // 😂
            "1f525",  // 🔥
            "1f44f",  // 👏
            "1f62e"   // 😮
    };
    private static final String[] REACTION_TYPES  = {"heart", "thumbsup", "laugh", "fire", "clap", "wow"};

    // Server → local time offset (server timestamps are UTC, we need to adjust)
    private long serverTimeOffsetMs = 0;

    // Bookmark/saved view mode
    private boolean showingBookmarks = false;

    // ── Performance: caches and pagination ──
    private final Map<Integer, List<Reaction>> reactionsCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, Image> postImageCache = new HashMap<>();
    private final Map<String, Image> avatarImageCache = new HashMap<>();
    private static final int PAGE_SIZE = 15;
    private List<Post> allPosts = new ArrayList<>();
    private int displayedPostCount = 0;
    private static final ExecutorService ioPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "community-io");
        t.setDaemon(true);
        return t;
    });

    @FXML
    public void initialize() {
        // Replace static FXML button with animated version
        animNewPostBtn = AnimatedButton.createPrimary(
                "+ New Post", "\u270F", e -> handleNewPost());
        animNewPostBtn.setMinWidth(155);
        animNewPostBtn.setMaxHeight(38);
        javafx.scene.Parent headerParent = btnNewPost.getParent();
        if (headerParent instanceof HBox) {
            HBox header = (HBox) headerParent;
            int idx = header.getChildren().indexOf(btnNewPost);
            header.getChildren().remove(btnNewPost);
            if (idx >= 0) header.getChildren().add(idx, animNewPostBtn);
            else header.getChildren().add(animNewPostBtn);
        }

        loadSavedPosts();
        computeServerTimeOffset();
        initTabs();

        // Load users in background, then load feed when ready
        AppThreadPool.io(() -> {
            loadUsers();
            Platform.runLater(() -> loadFeed());
        });

        // ── Twemoji icon on the image attach button ──
        Platform.runLater(() -> {
            btnAttachPostImage.setText(" Image");
            ImageView camIv = new ImageView();
            camIv.setFitWidth(16); camIv.setFitHeight(16); camIv.setSmooth(true);
            Image camImg = utils.EmojiPicker.getCachedEmojiImage("1f4f7", 16);
            if (camImg != null) camIv.setImage(camImg);
            else camIv.setImage(new Image(utils.EmojiPicker.getCdnBase() + "1f4f7.png", 16, 16, true, true, true));
            btnAttachPostImage.setGraphic(camIv);
        });

        // Auto-refresh every 30 seconds (was 10s – reduced to lower server load)
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "community-poll");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                loadUsers();
                List<Post> posts = filterHumanPosts(servicePost.recuperer());

                // Pre-fetch reactions in parallel on this background thread
                int limit = Math.min(posts.size(), Math.max(displayedPostCount, PAGE_SIZE));
                List<Post> page = posts.subList(0, limit);
                if (detailPost != null) {
                    // Only pre-fetch for the detail post
                    for (Post p : posts) {
                        if (p.getId() == detailPost.getId()) {
                            prefetchReactions(Collections.singletonList(p));
                            break;
                        }
                    }
                } else {
                    prefetchReactions(page);
                }

                Platform.runLater(() -> {
                    if (detailPost != null) {
                        for (Post p : posts) {
                            if (p.getId() == detailPost.getId()) {
                                detailPost = p;
                                renderDetailView(p);
                                break;
                            }
                        }
                    } else {
                        allPosts = posts;
                        displayedPostCount = limit;
                        renderFeed(page, posts.size() > limit);
                    }
                });
            } catch (SQLException ignored) {}
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void loadUsers() {
        try {
            List<User> users = serviceUser.recuperer();
            for (User u : users) userCache.put(u.getId(), u);
        } catch (SQLException e) { System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    /** Estimate server→local offset by comparing a known server timestamp */
    private void computeServerTimeOffset() {
        // The server returns timestamps in its own timezone.
        // We assume server time ≈ UTC. Adjust to local zone.
        try {
            ZonedDateTime utcNow = ZonedDateTime.now(ZoneId.of("UTC"));
            ZonedDateTime localNow = ZonedDateTime.now();
            serverTimeOffsetMs = java.time.Duration.between(utcNow, localNow).toMillis();
        } catch (Exception e) { serverTimeOffsetMs = 0; }
    }

    // ═══════════════════════════════════════════
    //  SAVED POSTS PERSISTENCE
    // ═══════════════════════════════════════════

    private void loadSavedPosts() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(CommunityController.class);
            String csv = prefs.get(PREF_SAVED_POSTS, "");
            savedPostIds.clear();
            if (!csv.isEmpty()) {
                for (String s : csv.split(",")) {
                    try { savedPostIds.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) { System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    private void saveSavedPosts() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(CommunityController.class);
            String csv = savedPostIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            prefs.put(PREF_SAVED_POSTS, csv);
            prefs.flush();
        } catch (Exception e) { System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    private void toggleSavePost(int postId) {
        if (savedPostIds.contains(postId)) {
            savedPostIds.remove(postId);
        } else {
            savedPostIds.add(postId);
        }
        saveSavedPosts();
        SoundManager.getInstance().play(SoundManager.EMOJI_POP);
    }

    // ═══════════════════════════════════════════
    //  NEW POST FORM
    // ═══════════════════════════════════════════

    @FXML
    private void handleNewPost() {
        newPostForm.setOpacity(0);
        newPostForm.setTranslateY(-20);
        newPostForm.setManaged(true);
        newPostForm.setVisible(true);

        FadeTransition fade = new FadeTransition(Duration.millis(300), newPostForm);
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(300), newPostForm);
        slide.setFromY(-20); slide.setToY(0);
        new ParallelTransition(fade, slide).play();

        postTextArea.requestFocus();
    }

    @FXML
    private void handleCancelPost() {
        FadeTransition fade = new FadeTransition(Duration.millis(200), newPostForm);
        fade.setFromValue(1); fade.setToValue(0);
        TranslateTransition slide = new TranslateTransition(Duration.millis(200), newPostForm);
        slide.setFromY(0); slide.setToY(-15);
        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setOnFinished(e -> {
            newPostForm.setManaged(false);
            newPostForm.setVisible(false);
            newPostForm.setOpacity(1);
            newPostForm.setTranslateY(0);
        });
        pt.play();
        postTextArea.clear();
        pendingImageBase64 = null;
        imageAttachLabel.setVisible(false);
        imageAttachLabel.setManaged(false);
    }

    @FXML
    private void handleCommunityMenu(javafx.event.ActionEvent event) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("community-emoji-menu");

        MenuItem bookmarkItem = new MenuItem(showingBookmarks ? "\uD83D\uDCDA All Posts" : "\uD83D\uDD16 My Bookmarks");
        bookmarkItem.setOnAction(e -> {
            showingBookmarks = !showingBookmarks;
            if (showingBookmarks) {
                loadBookmarkFeed();
            } else {
                loadFeed();
            }
        });
        menu.getItems().add(bookmarkItem);

        MenuItem refreshItem = new MenuItem("\uD83D\uDD04 Refresh");
        refreshItem.setOnAction(e -> {
            showingBookmarks = false;
            loadFeed();
        });
        menu.getItems().add(refreshItem);

        Button src = (Button) event.getSource();
        menu.show(src, Side.BOTTOM, 0, 4);
    }

    private void loadBookmarkFeed() {
        AppThreadPool.io(() -> {
            try {
                List<Post> all = filterHumanPosts(servicePost.recuperer());
                List<Post> saved = all.stream()
                        .filter(p -> savedPostIds.contains(p.getId()))
                        .collect(Collectors.toList());
                prefetchReactions(saved);
                Platform.runLater(() -> renderFeed(saved));
            } catch (SQLException e) {
                System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleAttachPostImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Attach Image");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = fc.showOpenDialog(feedContainer.getScene().getWindow());
        if (f != null) {
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                pendingImageBase64 = Base64.getEncoder().encodeToString(bytes);
                imageAttachLabel.setText("📷 " + f.getName());
                imageAttachLabel.setVisible(true);
                imageAttachLabel.setManaged(true);
            } catch (Exception ex) {
                pendingImageBase64 = null;
            }
        }
    }

    @FXML
    private void handleSubmitPost() {
        String text = postTextArea.getText();
        if ((text == null || text.trim().isEmpty()) && pendingImageBase64 == null) {
            showToast("Please write something or attach an image.", true);
            return;
        }

        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) return;

        // Bad words filter
        if (text != null && !text.trim().isEmpty()) {
            BadWordsService.CheckResult check = BadWordsService.check(text.trim());
            if (check.hasBadWords) {
                showToast("Your post contains inappropriate language. Please revise it.", true);
                postTextArea.setText(check.censoredContent);
                return;
            }
        }

        // ── Show loading spinner on Post button ──
        String origText = btnSubmitPost.getText();
        javafx.scene.Node origGraphic = btnSubmitPost.getGraphic();
        javafx.scene.control.ProgressIndicator postSpinner = new javafx.scene.control.ProgressIndicator();
        postSpinner.setMaxSize(16, 16);
        postSpinner.setPrefSize(16, 16);
        postSpinner.setStyle("-fx-progress-color: #F0EDEE;");
        btnSubmitPost.setGraphic(postSpinner);
        btnSubmitPost.setText("");
        btnSubmitPost.setDisable(true);
        btnSubmitPost.setOpacity(0.8);

        try {
            Post post = new Post(me.getId(), text != null ? text.trim() : "", pendingImageBase64);
            servicePost.ajouter(post);
            SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
            handleCancelPost();
            loadFeed();
        } catch (SQLException e) {
            showToast("Failed to submit post: " + e.getMessage(), true);
        } finally {
            btnSubmitPost.setGraphic(origGraphic);
            btnSubmitPost.setText(origText);
            btnSubmitPost.setDisable(false);
            btnSubmitPost.setOpacity(1.0);
        }
    }

    // ═══════════════════════════════════════════
    //  PERFORMANCE: PARALLEL REACTIONS PRE-FETCH
    // ═══════════════════════════════════════════

    /** Pre-fetch reactions for all given posts in parallel (call from background thread). */
    private void prefetchReactions(List<Post> posts) {
        if (posts.isEmpty()) return;
        CompletableFuture<?>[] futures = posts.stream()
                .map(p -> CompletableFuture.runAsync(() -> {
                    try {
                        reactionsCache.put(p.getId(), serviceReaction.getByPostId(p.getId()));
                    } catch (SQLException e) {
                        reactionsCache.put(p.getId(), Collections.emptyList());
                    }
                }, ioPool))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
    }

    /** Load the next page of posts (called from "Load more" button). */
    private void loadMorePosts() {
        AppThreadPool.io(() -> {
            int newEnd = Math.min(allPosts.size(), displayedPostCount + PAGE_SIZE);
            List<Post> newPage = allPosts.subList(displayedPostCount, newEnd);
            prefetchReactions(newPage);
            final int finalEnd = newEnd;
            Platform.runLater(() -> {
                // Remove "Load more" button
                if (!feedContainer.getChildren().isEmpty()) {
                    var last = feedContainer.getChildren().get(feedContainer.getChildren().size() - 1);
                    if (last instanceof HBox) feedContainer.getChildren().remove(last);
                }
                User me = SessionManager.getInstance().getCurrentUser();
                int myId = me != null ? me.getId() : 0;
                for (Post post : newPage) {
                    VBox card = buildPostCard(post, myId, false);
                    card.setMaxWidth(680);
                    feedContainer.getChildren().add(card);
                }
                displayedPostCount = finalEnd;
                if (finalEnd < allPosts.size()) {
                    Button loadMore = new Button("Load more posts...");
                    loadMore.getStyleClass().add("btn-secondary");
                    loadMore.setStyle("-fx-padding: 10 24; -fx-font-size: 13;");
                    loadMore.setOnAction(e -> loadMorePosts());
                    HBox wrapper = new HBox(loadMore);
                    wrapper.setAlignment(Pos.CENTER);
                    wrapper.setPadding(new Insets(10, 0, 10, 0));
                    feedContainer.getChildren().add(wrapper);
                }
            });
        });
    }

    // ═══════════════════════════════════════════
    //  NAVIGATION: FEED ↔ DETAIL
    // ═══════════════════════════════════════════

    @FXML
    private void handleBackToFeed() {
        detailPost = null;
        if (detailView != null) { detailView.setVisible(false); detailView.setManaged(false); }
        if (tabBar != null) { tabBar.setVisible(true); tabBar.setManaged(true); }
        switchToFeed();
    }

    private void openPostDetail(Post post) {
        detailPost = post;
        expandedComments.add(post.getId()); // always show comments in detail
        commentErrorCache.remove(post.getId()); // clear error cache so we retry fresh
        feedScroll.setVisible(false); feedScroll.setManaged(false);
        if (wikiPane != null) { wikiPane.setVisible(false); wikiPane.setManaged(false); }
        if (quotesPane != null) { quotesPane.setVisible(false); quotesPane.setManaged(false); }
        if (tabBar != null) { tabBar.setVisible(false); tabBar.setManaged(false); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(false); animNewPostBtn.setManaged(false); }
        handleCancelPost(); // hide new post form
        if (detailView != null) { detailView.setVisible(true); detailView.setManaged(true); }
        renderDetailView(post);
    }

    // ═══════════════════════════════════════════
    //  FEED VIEW
    // ═══════════════════════════════════════════

    /** Filter out bot-generated posts (Wikipedia, Weather, Quotes from SynergyBot). */
    private List<Post> filterHumanPosts(List<Post> posts) {
        List<Post> human = new ArrayList<>();
        for (Post p : posts) {
            String c = p.getContent();
            // Only skip posts that BOTH come from the bot account AND look like bot-generated content
            if (p.getAuthorId() == BOT_AUTHOR_ID && c != null
                    && (c.contains("SynergyBot") || c.startsWith("**Wikipedia:")
                        || c.startsWith("\uD83D\uDCDA") // 📚 emoji prefix used by wiki bot
                        || c.startsWith("**Weather in") || c.startsWith("**Quote of the Day**"))) {
                continue;
            }
            human.add(p);
        }
        return human;
    }

    private void loadFeed() {
        // Run API calls on background thread to avoid blocking FX thread
        AppThreadPool.io(() -> {
            try {
                List<Post> posts = filterHumanPosts(servicePost.recuperer());
                int limit = Math.min(posts.size(), PAGE_SIZE);
                List<Post> page = posts.subList(0, limit);
                prefetchReactions(page);
                Platform.runLater(() -> {
                    allPosts = posts;
                    displayedPostCount = limit;
                    renderFeed(page, posts.size() > limit);
                });
            } catch (SQLException e) {
                System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void renderFeed(List<Post> posts) {
        renderFeed(posts, false);
    }

    private void renderFeed(List<Post> posts, boolean hasMore) {
        feedContainer.getChildren().clear();

        if (posts.isEmpty()) {
            Label empty = new Label("No posts yet. Be the first to share something!");
            empty.getStyleClass().add("content-subtitle");
            empty.setStyle("-fx-padding: 40; -fx-font-size: 14;");
            feedContainer.getChildren().add(empty);
            return;
        }

        User me = SessionManager.getInstance().getCurrentUser();
        int myId = me != null ? me.getId() : 0;

        for (Post post : posts) {
            VBox card = buildPostCard(post, myId, false);
            card.setMaxWidth(680);
            feedContainer.getChildren().add(card);
        }

        // "Load more" button for pagination
        if (hasMore) {
            Button loadMore = new Button("Load more posts...");
            loadMore.getStyleClass().add("btn-secondary");
            loadMore.setStyle("-fx-padding: 10 24; -fx-font-size: 13;");
            loadMore.setOnAction(e -> loadMorePosts());
            HBox wrapper = new HBox(loadMore);
            wrapper.setAlignment(Pos.CENTER);
            wrapper.setPadding(new Insets(10, 0, 10, 0));
            feedContainer.getChildren().add(wrapper);
        }
    }

    // ═══════════════════════════════════════════
    //  DETAIL VIEW
    // ═══════════════════════════════════════════

    private void renderDetailView(Post post) {
        if (detailContent == null) return;
        detailContent.getChildren().clear();

        User me = SessionManager.getInstance().getCurrentUser();
        int myId = me != null ? me.getId() : 0;

        VBox card = buildPostCard(post, myId, true);
        card.setMaxWidth(720);
        detailContent.getChildren().add(card);
    }

    // ═══════════════════════════════════════════
    //  POST CARD BUILDER
    // ═══════════════════════════════════════════

    private VBox buildPostCard(Post post, int myId, boolean isDetailView) {
        User author = userCache.get(post.getAuthorId());
        String authorName = author != null
                ? author.getFirstName() + " " + author.getLastName()
                : "User #" + post.getAuthorId();

        VBox card = new VBox(0);
        card.getStyleClass().add("community-card");

        // ── Header: avatar + name + time + actions ──
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 10, 16));

        StackPane avatar = createAvatar(author, 42);

        VBox nameCol = new VBox(1);
        HBox.setHgrow(nameCol, Priority.ALWAYS);
        Label nameLabel = new Label(authorName);
        nameLabel.getStyleClass().add("community-author-name");

        String timeText = post.getCreatedAt() != null
                ? formatTimeAgo(post.getCreatedAt().getTime())
                : "";
        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("community-time-label");
        nameCol.getChildren().addAll(nameLabel, timeLabel);

        header.getChildren().addAll(avatar, nameCol);

        // ── 3-dot menu ──
        HBox actionBtns = new HBox(4);
        actionBtns.setAlignment(Pos.CENTER_RIGHT);

        // Bookmark / save button (always visible)
        boolean isSaved = savedPostIds.contains(post.getId());
        Button saveBtn = new Button();
        saveBtn.setGraphic(twemojiIcon(isSaved ? "1f516" : "1f3f7", 18));
        saveBtn.getStyleClass().add("community-action-btn");
        if (isSaved) saveBtn.getStyleClass().add("community-action-btn-active");
        saveBtn.setTooltip(new Tooltip(isSaved ? "Unsave post" : "Save post"));
        saveBtn.setOnAction(e -> {
            toggleSavePost(post.getId());
            if (detailPost != null) renderDetailView(post);
            else loadFeed();
        });
        actionBtns.getChildren().add(saveBtn);

        // Three-dot menu (only for own posts)
        if (post.getAuthorId() == myId) {
            Button menuBtn = new Button("⋮");
            menuBtn.getStyleClass().add("community-action-btn");
            menuBtn.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-padding: 0 6; -fx-min-width: 28; -fx-min-height: 28;");
            menuBtn.setTooltip(new Tooltip("More options"));

            ContextMenu postMenu = new ContextMenu();
            postMenu.setStyle("-fx-background-color: #1E1E2E; -fx-background-radius: 10; -fx-border-color: #2A2A3C; -fx-border-radius: 10; -fx-padding: 4;");

            MenuItem editItem = new MenuItem("✏️  Edit post");
            editItem.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 13;");
            editItem.setOnAction(e -> showEditPostDialog(post));

            MenuItem deleteItem = new MenuItem("🗑️  Delete post");
            deleteItem.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 13;");
            deleteItem.setOnAction(e -> deletePost(post));

            postMenu.getItems().addAll(editItem, deleteItem);
            menuBtn.setOnAction(e -> postMenu.show(menuBtn, Side.BOTTOM, 0, 0));
            actionBtns.getChildren().add(menuBtn);
        }
        header.getChildren().add(actionBtns);

        // ── Content body ──
        VBox body = new VBox(10);
        body.setPadding(new Insets(0, 16, 10, 16));

        if (post.getContent() != null && !post.getContent().trim().isEmpty()) {
            Label textLabel = new Label(post.getContent());
            textLabel.setWrapText(true);
            textLabel.setMaxWidth(isDetailView ? 680 : 640);
            textLabel.getStyleClass().add("community-post-text");
            body.getChildren().add(textLabel);
        }

        if (post.getImageBase64() != null && !post.getImageBase64().isEmpty()) {
            try {
                // Use cached decoded image to avoid re-decoding base64 every render
                int imgKey = post.getId();
                Image img = postImageCache.get(imgKey);
                if (img == null) {
                    byte[] imgBytes = Base64.getDecoder().decode(post.getImageBase64());
                    img = new Image(new ByteArrayInputStream(imgBytes));
                    postImageCache.put(imgKey, img);
                }
                ImageView iv = new ImageView(img);
                double maxW = isDetailView ? 680 : 640;
                double w = Math.min(img.getWidth(), maxW);
                iv.setFitWidth(w);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                double h = w * (img.getHeight() / img.getWidth());
                Rectangle clip = new Rectangle(w, h);
                clip.setArcWidth(12); clip.setArcHeight(12);
                iv.setClip(clip);
                body.getChildren().add(iv);
            } catch (Exception ignored) {}
        }

        // ── Emoji reactions bar ──
        HBox reactBar = buildReactionsBar(post, myId);
        body.getChildren().add(reactBar);

        // ── Separator ──
        Region sep = new Region();
        sep.setMinHeight(1); sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: #1C1B22;");
        VBox.setMargin(sep, new Insets(2, 16, 0, 16));

        // ── Footer: comment count + click to open ──
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 16, 12, 16));

        ImageView commentIcon = twemojiIcon("1f4ac", 14);
        Label commentLabel = new Label(" " + post.getCommentsCount() +
                (post.getCommentsCount() == 1 ? " comment" : " comments"));
        commentLabel.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 12;");
        HBox commentContent = new HBox(4, commentIcon, commentLabel);
        commentContent.setAlignment(Pos.CENTER);
        Button commentBtn = new Button();
        commentBtn.setGraphic(commentContent);
        commentBtn.getStyleClass().add("community-react-btn");

        if (!isDetailView) {
            // In feed: click opens detail
            commentBtn.setOnAction(e -> openPostDetail(post));

            // Make the card clickable too
            card.setOnMouseClicked(e -> {
                if (e.getTarget() instanceof Button) return; // skip if clicking a button
                openPostDetail(post);
            });
            card.setStyle("-fx-cursor: hand;");
        } else {
            commentBtn.setDisable(true);
            commentBtn.setOpacity(0.6);
        }

        footer.getChildren().add(commentBtn);

        card.getChildren().addAll(header, body, sep, footer);

        // ── Comments section (detail view always, feed view if expanded) ──
        if (isDetailView || expandedComments.contains(post.getId())) {
            VBox commentsSection = new VBox(6);
            commentsSection.getStyleClass().add("community-comments-section");
            commentsSection.setPadding(new Insets(6, 16, 14, 16));
            loadCommentsInto(commentsSection, post, myId);
            card.getChildren().add(commentsSection);
        }

        return card;
    }

    // ═══════════════════════════════════════════
    //  EMOJI REACTIONS BAR
    // ═══════════════════════════════════════════

    private HBox buildReactionsBar(Post post, int myId) {
        HBox bar = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 0, 0, 0));

        // Use pre-fetched reactions from cache (populated on background thread)
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        Set<String> myReactions = new HashSet<>();
        List<Reaction> reactions = reactionsCache.getOrDefault(post.getId(), Collections.emptyList());
        for (Reaction r : reactions) {
            typeCounts.merge(r.getType(), 1, Integer::sum);
            if (r.getUserId() == myId) myReactions.add(r.getType());
        }

        // Show active reactions (ones that have counts)
        for (int i = 0; i < REACTION_TYPES.length; i++) {
            String type = REACTION_TYPES[i];
            int count = typeCounts.getOrDefault(type, 0);
            if (count > 0) {
                boolean iMine = myReactions.contains(type);
                ImageView rIcon = twemojiIcon(REACTION_HEX[i], 16);
                Label countLabel = new Label(" " + count);
                countLabel.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 12;");
                HBox btnContent = new HBox(2, rIcon, countLabel);
                btnContent.setAlignment(Pos.CENTER);
                Button rb = new Button();
                rb.setGraphic(btnContent);
                rb.getStyleClass().add("community-react-btn");
                if (iMine) rb.getStyleClass().add("community-react-active");
                final String rType = type;
                rb.setOnAction(e -> toggleReaction(post, myId, rType));
                bar.getChildren().add(rb);
            }
        }

        // Add reaction picker button — pure JavaFX popup with Twemoji images
        Button addReactBtn = new Button("+");
        addReactBtn.getStyleClass().add("community-react-btn");
        addReactBtn.getStyleClass().add("community-react-add");
        addReactBtn.setTooltip(new Tooltip("React"));

        final Set<String> finalMyReactions = myReactions;
        Runnable showReactPopup = () -> {
            Popup reactPopup = new Popup();
            reactPopup.setAutoHide(true);

            HBox row = new HBox(4);
            row.setAlignment(Pos.CENTER);
            row.setPadding(new Insets(8, 12, 8, 12));
            row.setStyle("-fx-background-color: #1e1e30; -fx-border-color: #2a2a3e; "
                    + "-fx-border-width: 1; -fx-border-radius: 12; -fx-background-radius: 12; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 12, 0, 0, 3);");

            for (int j = 0; j < REACTION_HEX.length; j++) {
                String hex = REACTION_HEX[j];
                String tp = REACTION_TYPES[j];
                boolean mine = finalMyReactions.contains(tp);

                Button rb = new Button();
                rb.setGraphic(twemojiIcon(hex, 28));
                rb.setMinSize(40, 40);
                rb.setPrefSize(40, 40);
                rb.setStyle("-fx-background-color: " + (mine ? "rgba(44,102,110,0.2)" : "transparent")
                        + "; -fx-border-color: " + (mine ? "#2C666E" : "transparent")
                        + "; -fx-border-width: 2; -fx-border-radius: 10; -fx-background-radius: 10; -fx-cursor: hand;");
                rb.setOnMouseEntered(e -> rb.setStyle("-fx-background-color: #2a2a3e; -fx-border-color: transparent; "
                        + "-fx-border-width: 2; -fx-border-radius: 10; -fx-background-radius: 10; -fx-cursor: hand; "
                        + "-fx-scale-x: 1.25; -fx-scale-y: 1.25;"));
                rb.setOnMouseExited(e -> rb.setStyle("-fx-background-color: " + (mine ? "rgba(44,102,110,0.2)" : "transparent")
                        + "; -fx-border-color: " + (mine ? "#2C666E" : "transparent")
                        + "; -fx-border-width: 2; -fx-border-radius: 10; -fx-background-radius: 10; -fx-cursor: hand;"));
                rb.setOnAction(e -> {
                    reactPopup.hide();
                    toggleReaction(post, myId, tp);
                });
                row.getChildren().add(rb);
            }

            reactPopup.getContent().add(row);
            var bounds = addReactBtn.localToScreen(addReactBtn.getBoundsInLocal());
            reactPopup.show(addReactBtn.getScene().getWindow(), bounds.getMinX() - 80, bounds.getMinY() - 58);
        };

        addReactBtn.setOnMouseEntered(e -> showReactPopup.run());
        addReactBtn.setOnAction(e -> showReactPopup.run());

        bar.getChildren().add(addReactBtn);

        return bar;
    }

    // ═══════════════════════════════════════════
    //  TWEMOJI IMAGE HELPER
    // ═══════════════════════════════════════════

    /** Create an ImageView with a Twemoji PNG for the given hex codepoint. */
    private static ImageView twemojiIcon(String hex, double size) {
        ImageView iv = new ImageView();
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setSmooth(true);
        iv.setPreserveRatio(true);
        // Use the EmojiPicker cached image if available, else load from CDN
        Image cached = utils.EmojiPicker.getCachedEmojiImage(hex, size);
        if (cached != null) {
            iv.setImage(cached);
        } else {
            iv.setImage(new Image(utils.EmojiPicker.getCdnBase() + hex + ".png",
                    size, size, true, true, true));
        }
        return iv;
    }

    // ═══════════════════════════════════════════
    //  REACTIONS
    // ═══════════════════════════════════════════

    private void toggleReaction(Post post, int userId, String type) {
        // Run API calls on background thread
        AppThreadPool.io(() -> {
            try {
                serviceReaction.toggleReaction(post.getId(), userId, type);
                // Refresh only this post's reactions in cache
                List<Reaction> updated = serviceReaction.getByPostId(post.getId());
                reactionsCache.put(post.getId(), updated);
                Platform.runLater(() -> {
                    SoundManager.getInstance().play(SoundManager.EMOJI_POP);
                    if (detailPost != null) {
                        renderDetailView(detailPost);
                    } else {
                        // Re-render feed from existing data (reactions already updated in cache)
                        int limit = Math.min(allPosts.size(), displayedPostCount);
                        renderFeed(allPosts.subList(0, limit), allPosts.size() > limit);
                    }
                });
            } catch (SQLException e) {
                System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    // ═══════════════════════════════════════════
    //  COMMENTS
    // ═══════════════════════════════════════════

    private void toggleComments(Post post) {
        if (expandedComments.contains(post.getId())) {
            expandedComments.remove(post.getId());
        } else {
            expandedComments.add(post.getId());
        }
        loadFeed();
    }

    private void loadCommentsInto(VBox section, Post post, int myId) {
        section.getChildren().clear();

        // Section title
        Label title = new Label("Comments");
        title.getStyleClass().add("community-comments-title");
        section.getChildren().add(title);

        // Skip if this post's comments endpoint recently errored (avoid spam)
        Long lastErr = commentErrorCache.get(post.getId());
        if (lastErr != null && System.currentTimeMillis() - lastErr < COMMENT_ERROR_COOLDOWN_MS) {
            Label errLbl = new Label("Comments unavailable. Try later.");
            errLbl.getStyleClass().add("community-time-label");
            errLbl.setStyle("-fx-padding: 8 0; -fx-text-fill: #f87171;");
            section.getChildren().add(errLbl);
        } else {
            try {
                List<Comment> comments = serviceComment.getByPostId(post.getId());
                commentErrorCache.remove(post.getId()); // success — clear any cached error
                if (comments.isEmpty()) {
                    Label empty = new Label("No comments yet. Be the first!");
                    empty.getStyleClass().add("community-time-label");
                    empty.setStyle("-fx-padding: 8 0;");
                    section.getChildren().add(empty);
                } else {
                    for (Comment c : comments) {
                        HBox row = buildCommentRow(c, myId);
                        section.getChildren().add(row);
                    }
                }
            } catch (SQLException e) {
                commentErrorCache.put(post.getId(), System.currentTimeMillis());
                Label errLbl = new Label("Couldn't load comments.");
                errLbl.getStyleClass().add("community-time-label");
                errLbl.setStyle("-fx-padding: 8 0; -fx-text-fill: #f87171;");
                section.getChildren().add(errLbl);
            }
        }

        // Add comment input
        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.setPadding(new Insets(6, 0, 0, 0));

        User me = SessionManager.getInstance().getCurrentUser();
        StackPane myAvatar = createAvatar(me, 30);

        TextField commentField = new TextField();
        commentField.setPromptText("Write a comment...");
        commentField.getStyleClass().add("input-field");
        commentField.setStyle("-fx-font-size: 13; -fx-pref-height: 40; -fx-padding: 8 12;");
        HBox.setHgrow(commentField, Priority.ALWAYS);

        Button sendBtn = new Button("➤");
        sendBtn.getStyleClass().add("btn-primary");
        sendBtn.setStyle("-fx-font-size: 13; -fx-min-width: 40; -fx-min-height: 40; -fx-padding: 0;");
        sendBtn.setTooltip(new Tooltip("Send comment"));
        sendBtn.setOnAction(e -> {
            String text = commentField.getText();
            if (text != null && !text.trim().isEmpty()) {
                submitComment(post, text.trim());
            }
        });
        commentField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                String text = commentField.getText();
                if (text != null && !text.trim().isEmpty()) {
                    submitComment(post, text.trim());
                }
            }
        });

        inputRow.getChildren().addAll(myAvatar, commentField, sendBtn);
        section.getChildren().add(inputRow);
    }

    private HBox buildCommentRow(Comment comment, int myId) {
        User author = userCache.get(comment.getAuthorId());
        String name = author != null
                ? author.getFirstName() + " " + author.getLastName()
                : "User #" + comment.getAuthorId();

        HBox row = new HBox(8);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        row.getStyleClass().add("community-comment-row");

        StackPane avatar = createAvatar(author, 30);

        VBox col = new VBox(3);
        HBox.setHgrow(col, Priority.ALWAYS);

        // Name + time on same line
        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("community-comment-author");

        String timeAgo = comment.getCreatedAt() != null
                ? formatTimeAgo(comment.getCreatedAt().getTime())
                : "";
        Label timeLabel = new Label(timeAgo);
        timeLabel.getStyleClass().add("community-time-label");
        timeLabel.setStyle("-fx-font-size: 10;");
        nameRow.getChildren().addAll(nameLabel, timeLabel);

        Label contentLabel = new Label(comment.getContent());
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(560);
        contentLabel.getStyleClass().add("community-comment-text");

        col.getChildren().addAll(nameRow, contentLabel);
        row.getChildren().addAll(avatar, col);

        // 3-dot menu for own comments
        if (comment.getAuthorId() == myId) {
            Button menuBtn = new Button("⋮");
            menuBtn.getStyleClass().add("community-action-btn");
            menuBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 0 4; -fx-min-width: 22; -fx-min-height: 22;");
            menuBtn.setTooltip(new Tooltip("More options"));

            ContextMenu commentMenu = new ContextMenu();
            commentMenu.setStyle("-fx-background-color: #1E1E2E; -fx-background-radius: 10; -fx-border-color: #2A2A3C; -fx-border-radius: 10; -fx-padding: 4;");

            MenuItem editItem = new MenuItem("✏️  Edit");
            editItem.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 12;");
            editItem.setOnAction(e -> showEditCommentDialog(comment));

            MenuItem deleteItem = new MenuItem("🗑️  Delete");
            deleteItem.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12;");
            deleteItem.setOnAction(e -> {
                try {
                    serviceComment.supprimer(comment.getId());
                    SoundManager.getInstance().play(SoundManager.TASK_DELETED);
                    if (detailPost != null) {
                        renderDetailView(detailPost);
                    } else {
                        loadFeed();
                    }
                } catch (SQLException ex) { ex.printStackTrace(); }
            });

            commentMenu.getItems().addAll(editItem, deleteItem);
            menuBtn.setOnAction(e -> commentMenu.show(menuBtn, Side.BOTTOM, 0, 0));
            row.getChildren().add(menuBtn);
        }

        return row;
    }

    private void submitComment(Post post, String text) {
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) return;

        // Bad words filter
        BadWordsService.CheckResult check = BadWordsService.check(text);
        if (check.hasBadWords) {
            showToast("Your comment contains inappropriate language. Please revise it.", true);
            return;
        }

        try {
            serviceComment.ajouter(new Comment(post.getId(), me.getId(), text));
            SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
            expandedComments.add(post.getId());

            if (detailPost != null) {
                renderDetailView(detailPost);
            } else {
                loadFeed();
            }
        } catch (SQLException e) {
            showToast("Failed to comment: " + e.getMessage(), true);
        }
    }

    // ═══════════════════════════════════════════
    //  DELETE
    // ═══════════════════════════════════════════

    private Window ownerWindow() {
        return feedContainer != null && feedContainer.getScene() != null
                ? feedContainer.getScene().getWindow() : null;
    }

    private void deletePost(Post post) {
        if (StyledAlert.confirm(ownerWindow(), "Confirm Delete",
                "Delete this post and all its comments?")) {
            try {
                servicePost.supprimer(post.getId());
                if (detailPost != null && detailPost.getId() == post.getId()) {
                    handleBackToFeed();
                } else {
                    loadFeed();
                }
            } catch (SQLException e) { System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage()); }
        }
    }

    // ═══════════════════════════════════════════
    //  EDIT POST / COMMENT DIALOGS
    // ═══════════════════════════════════════════

    private void showEditPostDialog(Post post) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Edit Post");
        dialog.initOwner(ownerWindow());

        DialogPane pane = dialog.getDialogPane();
        pane.setStyle("-fx-background-color: #0D0D1A;");
        pane.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        pane.setPrefWidth(500);

        // Title
        Label title = new Label("✏️ Edit Post");
        title.setStyle("-fx-text-fill: #8B5CF6; -fx-font-size: 18; -fx-font-weight: bold;");

        // Text area with current content
        TextArea textArea = new TextArea(post.getContent());
        textArea.setWrapText(true);
        textArea.setPrefRowCount(6);
        textArea.setStyle("-fx-control-inner-background: #1A1A2E; -fx-text-fill: #E0E0E0; " +
                "-fx-font-size: 14; -fx-border-color: #2A2A3C; -fx-border-radius: 8; " +
                "-fx-background-radius: 8; -fx-prompt-text-fill: #666;");
        textArea.setPromptText("Edit your post...");

        // Character count
        Label charCount = new Label(textArea.getText().length() + " chars");
        charCount.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
        textArea.textProperty().addListener((ob, ov, nv) ->
                charCount.setText((nv != null ? nv.length() : 0) + " chars"));

        VBox content = new VBox(12, title, textArea, charCount);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: #0D0D1A;");
        pane.setContent(content);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveType, cancelType);

        // Style buttons
        Button saveButton = (Button) pane.lookupButton(saveType);
        saveButton.setStyle("-fx-background-color: #8B5CF6; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 8 24;");
        Button cancelButton = (Button) pane.lookupButton(cancelType);
        cancelButton.setStyle("-fx-background-color: #2A2A3C; -fx-text-fill: #b0b0b0; " +
                "-fx-background-radius: 8; -fx-padding: 8 24;");

        // Disable save if empty
        saveButton.disableProperty().bind(
                textArea.textProperty().isNull().or(textArea.textProperty().isEmpty()));

        dialog.setResultConverter(bt -> bt == saveType ? textArea.getText().trim() : null);

        dialog.showAndWait().ifPresent(newContent -> {
            if (!newContent.isEmpty() && !newContent.equals(post.getContent())) {
                // Bad words check
                BadWordsService.CheckResult check = BadWordsService.check(newContent);
                if (check.hasBadWords) {
                    showToast("Your post contains inappropriate language. Please revise.", true);
                    return;
                }
                try {
                    post.setContent(newContent);
                    servicePost.modifier(post);
                    SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
                    if (detailPost != null) renderDetailView(post);
                    else loadFeed();
                } catch (SQLException e) {
                    showToast("Failed to edit post: " + e.getMessage(), true);
                }
            }
        });
    }

    private void showEditCommentDialog(Comment comment) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Edit Comment");
        dialog.initOwner(ownerWindow());

        DialogPane pane = dialog.getDialogPane();
        pane.setStyle("-fx-background-color: #0D0D1A;");
        pane.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        pane.setPrefWidth(450);

        // Title
        Label title = new Label("✏️ Edit Comment");
        title.setStyle("-fx-text-fill: #8B5CF6; -fx-font-size: 16; -fx-font-weight: bold;");

        // Text field with current content
        TextArea textArea = new TextArea(comment.getContent());
        textArea.setWrapText(true);
        textArea.setPrefRowCount(3);
        textArea.setStyle("-fx-control-inner-background: #1A1A2E; -fx-text-fill: #E0E0E0; " +
                "-fx-font-size: 13; -fx-border-color: #2A2A3C; -fx-border-radius: 8; " +
                "-fx-background-radius: 8;");
        textArea.setPromptText("Edit your comment...");

        VBox content = new VBox(12, title, textArea);
        content.setPadding(new Insets(14));
        content.setStyle("-fx-background-color: #0D0D1A;");
        pane.setContent(content);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveType, cancelType);

        Button saveButton = (Button) pane.lookupButton(saveType);
        saveButton.setStyle("-fx-background-color: #8B5CF6; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 8 20;");
        Button cancelButton = (Button) pane.lookupButton(cancelType);
        cancelButton.setStyle("-fx-background-color: #2A2A3C; -fx-text-fill: #b0b0b0; " +
                "-fx-background-radius: 8; -fx-padding: 8 20;");

        saveButton.disableProperty().bind(
                textArea.textProperty().isNull().or(textArea.textProperty().isEmpty()));

        dialog.setResultConverter(bt -> bt == saveType ? textArea.getText().trim() : null);

        dialog.showAndWait().ifPresent(newContent -> {
            if (!newContent.isEmpty() && !newContent.equals(comment.getContent())) {
                BadWordsService.CheckResult check = BadWordsService.check(newContent);
                if (check.hasBadWords) {
                    showToast("Your comment contains inappropriate language. Please revise.", true);
                    return;
                }
                try {
                    comment.setContent(newContent);
                    serviceComment.modifier(comment);
                    SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
                    if (detailPost != null) renderDetailView(detailPost);
                    else loadFeed();
                } catch (SQLException e) {
                    showToast("Failed to edit comment: " + e.getMessage(), true);
                }
            }
        });
    }

    // ═══════════════════════════════════════════
    //  AVATAR & HELPERS
    // ═══════════════════════════════════════════

    private StackPane createAvatar(User user, double size) {
        StackPane wrapper = new StackPane();
        wrapper.setMinSize(size, size);
        wrapper.setMaxSize(size, size);
        Circle circle = new Circle(size / 2);
        boolean loaded = false;
        if (user != null && user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
            String avatarKey = user.getId() + "_" + (int) size;
            Image img = avatarImageCache.get(avatarKey);
            if (img == null) {
                File f = new File(user.getAvatarPath());
                if (f.exists()) {
                    try {
                        img = new Image(f.toURI().toString(), size, size, true, true);
                        avatarImageCache.put(avatarKey, img);
                    } catch (Exception ignored) {}
                }
            }
            if (img != null) {
                circle.setFill(new ImagePattern(img));
                loaded = true;
            }
        }
        if (!loaded) {
            String initials = "";
            if (user != null) {
                if (user.getFirstName() != null && !user.getFirstName().isEmpty())
                    initials += user.getFirstName().charAt(0);
                if (user.getLastName() != null && !user.getLastName().isEmpty())
                    initials += user.getLastName().charAt(0);
            }
            if (initials.isEmpty()) initials = "?";
            circle.getStyleClass().add("msg-avatar-circle");
            Label l = new Label(initials.toUpperCase());
            l.getStyleClass().add("msg-avatar-initials");
            l.setStyle("-fx-font-size: " + (int)(size * 0.38) + ";");
            wrapper.getChildren().addAll(circle, l);
            return wrapper;
        }
        wrapper.getChildren().add(circle);
        return wrapper;
    }

    /**
    /**
     * Format a server timestamp into a human-readable relative time.
     * Timestamps are already converted to UTC epoch millis during JSON parsing.
     */
    private String formatTimeAgo(long timestampMs) {
        long now = System.currentTimeMillis();
        long diff = now - timestampMs;

        // Guard against future timestamps (clock skew)
        if (diff < 0) diff = 0;

        long seconds = diff / 1000;
        if (seconds < 30) return "just now";
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days == 1) return "yesterday";
        if (days < 7) return days + " days ago";
        if (days < 30) {
            long weeks = days / 7;
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        }
        // Older: show date
        return new SimpleDateFormat("MMM d, yyyy 'at' h:mm a").format(new Date(timestampMs));
    }

    private void showToast(String message, boolean isError) {
        StyledAlert.show(ownerWindow(), isError ? "Error" : "Info", message, isError ? "error" : "info");
    }

    // ═══════════════════════════════════════════
    //  LINE TABS: FEED / WIKIPEDIA / QUOTES
    // ═══════════════════════════════════════════

    private static final HttpClient sidebarHttp = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private static final String[] LOCAL_QUOTES = {
        "The only way to do great work is to love what you do.|Steve Jobs",
        "Innovation distinguishes between a leader and a follower.|Steve Jobs",
        "Stay hungry, stay foolish.|Steve Jobs",
        "Life is what happens when you're busy making other plans.|John Lennon",
        "The future belongs to those who believe in the beauty of their dreams.|Eleanor Roosevelt",
        "It is during our darkest moments that we must focus to see the light.|Aristotle",
        "In the middle of every difficulty lies opportunity.|Albert Einstein",
        "Believe you can and you're halfway there.|Theodore Roosevelt",
        "Act as if what you do makes a difference. It does.|William James",
        "Success is not final, failure is not fatal: it is the courage to continue that counts.|Winston Churchill",
        "The best time to plant a tree was 20 years ago. The second best time is now.|Chinese Proverb",
        "Your time is limited, don't waste it living someone else's life.|Steve Jobs",
        "If you look at what you have in life, you'll always have more.|Oprah Winfrey",
        "The mind is everything. What you think you become.|Buddha",
        "An unexamined life is not worth living.|Socrates",
        "Strive not to be a success, but rather to be of value.|Albert Einstein",
        "The only impossible journey is the one you never begin.|Tony Robbins",
        "Everything you've ever wanted is on the other side of fear.|George Addair",
        "What we achieve inwardly will change outer reality.|Plutarch",
        "Happiness is not something readymade. It comes from your own actions.|Dalai Lama"
    };

    private String activeTab = "feed";

    private void initTabs() {
        if (wikiSearchField != null) {
            wikiSearchField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) handleWikiSearch();
            });
        }
        // Show welcome state in wiki pane
        if (wikiResultBox != null) {
            Label hint = new Label("Search for any topic to get Wikipedia information");
            hint.getStyleClass().add("wiki-hint-text");
            hint.setWrapText(true);
            wikiResultBox.getChildren().add(hint);
        }
        handleRefreshQuote();
    }

    @FXML
    private void switchToFeed() {
        activeTab = "feed";
        setTabActive(tabFeed);
        feedScroll.setVisible(true); feedScroll.setManaged(true);
        if (wikiPane != null) { wikiPane.setVisible(false); wikiPane.setManaged(false); }
        if (quotesPane != null) { quotesPane.setVisible(false); quotesPane.setManaged(false); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(true); animNewPostBtn.setManaged(true); }
        loadFeed();
    }

    @FXML
    private void switchToWiki() {
        activeTab = "wiki";
        setTabActive(tabWiki);
        feedScroll.setVisible(false); feedScroll.setManaged(false);
        if (wikiPane != null) { wikiPane.setVisible(true); wikiPane.setManaged(true); }
        if (quotesPane != null) { quotesPane.setVisible(false); quotesPane.setManaged(false); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(false); animNewPostBtn.setManaged(false); }
        handleCancelPost();
        Platform.runLater(() -> { if (wikiSearchField != null) wikiSearchField.requestFocus(); });
    }

    @FXML
    private void switchToQuotes() {
        activeTab = "quotes";
        setTabActive(tabQuotes);
        feedScroll.setVisible(false); feedScroll.setManaged(false);
        if (wikiPane != null) { wikiPane.setVisible(false); wikiPane.setManaged(false); }
        if (quotesPane != null) { quotesPane.setVisible(true); quotesPane.setManaged(true); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(false); animNewPostBtn.setManaged(false); }
        handleCancelPost();
    }

    private void setTabActive(Button active) {
        for (Button tab : new Button[]{tabFeed, tabWiki, tabQuotes}) {
            if (tab == null) continue;
            tab.getStyleClass().remove("community-tab-active");
        }
        if (active != null) active.getStyleClass().add("community-tab-active");
        SoundManager.getInstance().play(SoundManager.TAB_SWITCH);
    }

    // ═══════════════════════════════════════════
    //  WIKIPEDIA — rich content via MediaWiki Action API
    // ═══════════════════════════════════════════

    @FXML
    private void handleWikiSearch() {
        String query = wikiSearchField != null ? wikiSearchField.getText().trim() : "";
        if (query.isEmpty()) return;

        wikiResultBox.getChildren().clear();
        HBox loadingBox = new HBox(8);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPadding(new Insets(40, 0, 0, 0));
        Label spinner = new Label("⏳");
        spinner.setStyle("-fx-font-size: 24;");
        Label loadingText = new Label("Searching Wikipedia...");
        loadingText.getStyleClass().add("wiki-hint-text");
        loadingBox.getChildren().addAll(spinner, loadingText);
        wikiResultBox.getChildren().add(loadingBox);

        AppThreadPool.io(() -> {
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);

                // Single API call — MediaWiki Action API (no redirects, auto-normalizes titles)
                String apiUrl = "https://en.wikipedia.org/w/api.php?action=query"
                        + "&titles=" + encoded
                        + "&prop=extracts%7Cpageimages%7Cinfo%7Cdescription"
                        + "&inprop=url&pithumbsize=600"
                        + "&explaintext=1&exsectionformat=wiki"
                        + "&format=json&formatversion=2";

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Accept", "application/json")
                        .header("User-Agent", "SynergyGig/1.0 (JavaFX Desktop App)")
                        .GET().build();
                HttpResponse<String> resp = sidebarHttp.send(req, HttpResponse.BodyHandlers.ofString());
                String body = resp.body();

                // Parse from the "pages" array (formatversion=2 gives array)
                int pagesIdx = body.indexOf("\"pages\"");
                if (pagesIdx < 0) throw new RuntimeException("No pages in response");

                // Check for "missing" page
                int missingIdx = body.indexOf("\"missing\"", pagesIdx);
                int extractIdx = body.indexOf("\"extract\"", pagesIdx);
                if (missingIdx > 0 && (extractIdx < 0 || missingIdx < extractIdx)) {
                    Platform.runLater(() -> {
                        wikiResultBox.getChildren().clear();
                        Label noResult = new Label("No Wikipedia article found for \"" + query + "\".\nTry a different search term.");
                        noResult.getStyleClass().add("wiki-hint-text");
                        noResult.setWrapText(true);
                        wikiResultBox.getChildren().add(noResult);
                    });
                    return;
                }

                String title = jsonStringField(body, "title");
                String description = jsonStringField(body, "description");
                String fullExtract = jsonStringField(body, "extract");
                String fullUrl = jsonStringField(body, "fullurl");

                // Thumbnail — find "thumbnail" then "source" inside it
                String thumbnailUrl = null;
                int thumbIdx = body.indexOf("\"thumbnail\"");
                if (thumbIdx >= 0) {
                    int srcIdx = body.indexOf("\"source\"", thumbIdx);
                    if (srcIdx >= 0 && srcIdx < thumbIdx + 300) {
                        thumbnailUrl = jsonStringFieldAt(body, srcIdx);
                    }
                }

                // Split the extract into intro + sections using == markers
                String introText = "";
                List<String[]> sections = new ArrayList<>();
                if (fullExtract != null && !fullExtract.isEmpty()) {
                    // Find first section heading
                    int firstSection = fullExtract.indexOf("\n== ");
                    if (firstSection > 0) {
                        introText = fullExtract.substring(0, firstSection).trim();
                        parseSectionsFromExtract(fullExtract.substring(firstSection), sections);
                    } else {
                        introText = fullExtract.trim();
                    }
                }

                final String fTitle = title != null ? title : query;
                final String fDesc = description;
                final String fIntro = introText;
                final String fThumb = thumbnailUrl;
                final String fUrl = fullUrl;
                final List<String[]> fSections = sections;

                Platform.runLater(() -> {
                    wikiResultBox.getChildren().clear();
                    buildWikiArticle(fTitle, fDesc, fIntro, fThumb, fUrl, fSections);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    wikiResultBox.getChildren().clear();
                    Label err = new Label("❌ Failed to load article. Check your connection.");
                    err.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 13;");
                    err.setWrapText(true);
                    wikiResultBox.getChildren().add(err);
                });
            }
        });
    }

    /** Parse sections from plain-text extract with == Heading == markers. */
    private void parseSectionsFromExtract(String text, List<String[]> sections) {
        // Split by section headings: == Title == or === Subtitle ===
        String[] parts = text.split("(?m)^==+\\s*");
        for (String part : parts) {
            if (part.trim().isEmpty()) continue;
            // First line is the heading (ends with ==)
            int headEnd = part.indexOf("==");
            if (headEnd < 0) continue;
            String heading = part.substring(0, headEnd).trim();
            String content = part.substring(headEnd).replaceAll("^=+\\s*", "").trim();
            if (heading.isEmpty() || content.isEmpty()) continue;
            // Skip meta sections
            if (heading.equals("See also") || heading.equals("References")
                    || heading.equals("External links") || heading.equals("Notes")
                    || heading.equals("Further reading") || heading.equals("Sources")
                    || heading.equals("Bibliography")) continue;
            // Limit individual section text to reasonable length
            if (content.length() > 3000) content = content.substring(0, 3000) + "...";
            sections.add(new String[]{heading, content});
        }
    }

    private void buildWikiArticle(String title, String description, String extract,
                                   String thumbnailUrl, String pageUrl, List<String[]> sections) {
        // ── Article header ──
        VBox header = new VBox(4);
        header.getStyleClass().add("wiki-article-header");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("wiki-article-title");
        titleLbl.setWrapText(true);
        header.getChildren().add(titleLbl);

        if (description != null && !description.isEmpty()) {
            Label descLbl = new Label(description);
            descLbl.getStyleClass().add("wiki-article-description");
            descLbl.setWrapText(true);
            header.getChildren().add(descLbl);
        }

        wikiResultBox.getChildren().add(header);

        // ── Thumbnail image ──
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            try {
                ImageView thumb = new ImageView(new Image(thumbnailUrl, 600, 300, true, true, true));
                thumb.setFitWidth(Math.min(600, 580));
                thumb.setPreserveRatio(true);
                thumb.setSmooth(true);
                VBox imgBox = new VBox(thumb);
                imgBox.getStyleClass().add("wiki-article-image");
                imgBox.setAlignment(Pos.CENTER);
                wikiResultBox.getChildren().add(imgBox);
            } catch (Exception ignored) {}
        }

        // ── Lead extract (summary paragraph) ──
        if (extract != null && !extract.isEmpty()) {
            Label extractLbl = new Label(extract);
            extractLbl.getStyleClass().add("wiki-article-extract");
            extractLbl.setWrapText(true);
            extractLbl.setMaxWidth(Double.MAX_VALUE);
            wikiResultBox.getChildren().add(extractLbl);
        }

        // ── Sections ──
        for (String[] section : sections) {
            String secTitle = section[0];
            String secText = section[1];
            if (secText.trim().isEmpty()) continue;

            Label secHeader = new Label(secTitle);
            secHeader.getStyleClass().add("wiki-section-title");
            secHeader.setWrapText(true);

            Label secContent = new Label(secText);
            secContent.getStyleClass().add("wiki-section-text");
            secContent.setWrapText(true);
            secContent.setMaxWidth(Double.MAX_VALUE);

            VBox secBox = new VBox(4, secHeader, secContent);
            secBox.getStyleClass().add("wiki-section");
            wikiResultBox.getChildren().add(secBox);
        }

        // ── "Read on Wikipedia" link ──
        if (pageUrl != null && !pageUrl.isEmpty()) {
            Hyperlink link = new Hyperlink("🔗 Read full article on Wikipedia →");
            link.getStyleClass().add("wiki-article-link");
            link.setOnAction(ev -> {
                try { java.awt.Desktop.getDesktop().browse(URI.create(pageUrl)); } catch (Exception ignored) {}
            });
            VBox linkBox = new VBox(link);
            linkBox.setPadding(new Insets(10, 0, 20, 0));
            wikiResultBox.getChildren().add(linkBox);
        }
    }

    /** Extract value of a JSON string field at the given key position. */
    private static String jsonStringFieldAt(String json, int keyStart) {
        int colon = json.indexOf(':', keyStart);
        if (colon < 0) return null;
        // Find opening quote (skip whitespace)
        int valStart = json.indexOf('"', colon + 1);
        if (valStart < 0) return null;
        valStart++;
        StringBuilder sb = new StringBuilder();
        for (int i = valStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'u':
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            try { sb.append((char) Integer.parseInt(hex, 16)); } catch (Exception e) { sb.append(hex); }
                            i += 4;
                        }
                        break;
                    default: sb.append(next);
                }
                continue;
            }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════
    //  QUOTES TAB
    // ═══════════════════════════════════════════

    @FXML
    private void handleRefreshQuote() {
        if (quoteBox == null) return;
        quoteBox.getChildren().clear();

        String entry = LOCAL_QUOTES[new Random().nextInt(LOCAL_QUOTES.length)];
        String[] parts = entry.split("\\|", 2);
        String quote = parts[0];
        String author = parts.length > 1 ? parts[1] : "Unknown";

        // Big styled quote card
        VBox quoteCard = new VBox(12);
        quoteCard.getStyleClass().add("quote-card");
        quoteCard.setMaxWidth(500);
        quoteCard.setAlignment(Pos.CENTER);

        Label openQuote = new Label("\u201C");
        openQuote.getStyleClass().add("quote-big-mark");

        Label quoteLbl = new Label(quote);
        quoteLbl.getStyleClass().add("quote-text");
        quoteLbl.setWrapText(true);
        quoteLbl.setMaxWidth(440);

        Label authorLbl = new Label("— " + author);
        authorLbl.getStyleClass().add("quote-author");

        quoteCard.getChildren().addAll(openQuote, quoteLbl, authorLbl);
        quoteBox.getChildren().add(quoteCard);
    }

    /** Tiny helper: extract a top-level JSON string field by name (no external JSON library). */
    private static String jsonStringField(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        return jsonStringFieldAt(json, idx);
    }

    // ==================== AI WRITING ASSISTANT ====================

    @FXML
    private void handleAiImprove() {
        String text = postTextArea.getText();
        if (text == null || text.trim().isEmpty()) {
            showToast("Write something first, then AI will improve it.", true);
            return;
        }

        btnAiImprove.setDisable(true);
        btnAiImprove.setText("⏳ Improving...");

        AppThreadPool.io(() -> {
            try {
                ZAIService zai = new ZAIService();
                String improved = zai.improvePost(text.trim(), "professional, engaging, and concise");
                Platform.runLater(() -> {
                    postTextArea.setText(improved);
                    btnAiImprove.setDisable(false);
                    btnAiImprove.setText("✨ AI Improve");
                    SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                    showToast("✨ Post improved by AI!", false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnAiImprove.setDisable(false);
                    btnAiImprove.setText("✨ AI Improve");
                    showToast("AI improvement failed: " + ex.getMessage(), true);
                });
            }
        });
    }

    @FXML
    private void handleAiSentiment() {
        String text = postTextArea.getText();
        if (text == null || text.trim().isEmpty()) {
            showToast("Write something first to check its tone.", true);
            return;
        }

        btnAiSentiment.setDisable(true);
        btnAiSentiment.setText("⏳ Checking...");

        AppThreadPool.io(() -> {
            try {
                ZAIService zai = new ZAIService();
                String analysis = zai.analyzeSentiment(text.trim());

                // Try to parse JSON response
                String sentiment = "unknown", tone = "unknown", summary = "";
                boolean toxic = false;
                try {
                    String clean = analysis.trim();
                    if (clean.startsWith("```")) {
                        clean = clean.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
                    }
                    com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(clean).getAsJsonObject();
                    sentiment = obj.has("sentiment") ? obj.get("sentiment").getAsString() : sentiment;
                    tone = obj.has("tone") ? obj.get("tone").getAsString() : tone;
                    summary = obj.has("summary") ? obj.get("summary").getAsString() : "";
                    toxic = obj.has("toxic") && obj.get("toxic").getAsBoolean();
                } catch (Exception ignored) {
                    summary = analysis; // fallback: show raw text
                }

                String emoji = switch (sentiment.toLowerCase()) {
                    case "positive" -> "😊";
                    case "negative" -> "😟";
                    case "mixed" -> "🤔";
                    default -> "😐";
                };

                final String msg = emoji + " Sentiment: " + sentiment +
                        "\nTone: " + tone +
                        (toxic ? "\n⚠️ Warning: May be perceived as toxic" : "") +
                        (summary.isEmpty() ? "" : "\n" + summary);

                Platform.runLater(() -> {
                    btnAiSentiment.setDisable(false);
                    btnAiSentiment.setText("🔍 Check Tone");
                    SoundManager.getInstance().play(SoundManager.AI_COMPLETE);

                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Tone Analysis");
                    info.setHeaderText("🔍 Post Tone Analysis");
                    info.setContentText(msg);
                    info.showAndWait();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnAiSentiment.setDisable(false);
                    btnAiSentiment.setText("🔍 Check Tone");
                    showToast("Tone check failed: " + ex.getMessage(), true);
                });
            }
        });
    }

    // ==================== Lifecycle cleanup ====================

    @Override
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}
