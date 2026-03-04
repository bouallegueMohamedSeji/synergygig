package controllers;

import entities.Comment;
import entities.CommunityGroup;
import entities.GroupMember;
import entities.Post;
import entities.Reaction;
import entities.User;
import entities.UserFollow;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
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
import services.ServiceCommunityGroup;
import services.ServiceGroupMember;
import services.ServicePost;
import services.ServiceReaction;
import services.ServiceUser;
import services.ServiceUserFollow;
import services.ServiceNotification;
import services.ServiceChatRoom;
import services.ServiceChatRoomMember;
import entities.ChatRoom;
import utils.AnimatedButton;
import utils.ApiClient;
import utils.AppThreadPool;
import utils.BadWordsService;
import utils.DialogHelper;
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

    // Navigation buttons (sidebar)
    @FXML private Button tabHome;
    @FXML private Button tabFeed;
    // Profile accessed via header avatar (no sidebar button)
    @FXML private Button tabWiki;
    @FXML private Button tabQuotes;

    // Header + Sidebar
    @FXML private TextField globalSearchField;
    @FXML private StackPane headerProfileAvatar;
    @FXML private VBox sidebarPane;
    @FXML private VBox sidebarGroupsList;

    // Group Detail
    @FXML private VBox groupDetailPane;
    @FXML private Label groupDetailName;
    @FXML private Label groupDetailMeta;
    @FXML private VBox groupDetailContent;

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

    // Groups
    @FXML private VBox groupsPane;
    @FXML private Button btnCreateGroup;
    @FXML private VBox groupsContainer;
    private CommunityGroup currentViewingGroup;

    // Profile tab
    @FXML private VBox profilePane;
    @FXML private VBox profileContainer;

    // Animated "+ New Post" button (replaces btnNewPost in init)
    private javafx.scene.layout.StackPane animNewPostBtn;

    // Post visibility ComboBox (built programmatically in the post form)
    private ComboBox<String> visibilityCombo;

    // People/Search pane (built programmatically)
    @FXML private VBox peoplePane;
    @FXML private VBox peopleContainer;
    @FXML private TextField peopleSearchField;
    @FXML private Button tabPeople;

    // Bot author ID — posts from this user are hidden from the community feed
    private static final int BOT_AUTHOR_ID = 1;

    private final ServicePost servicePost = new ServicePost();
    private final ServiceComment serviceComment = new ServiceComment();
    private final ServiceReaction serviceReaction = new ServiceReaction();
    private final ServiceUser serviceUser = new ServiceUser();
    private final ServiceUserFollow serviceFollow = new ServiceUserFollow();
    private final ServiceCommunityGroup serviceGroup = new ServiceCommunityGroup();
    private final ServiceGroupMember serviceGroupMember = new ServiceGroupMember();
    private final ServiceNotification serviceNotif = new ServiceNotification();
    private final ServiceChatRoom serviceChatRoom = new ServiceChatRoom();
    private final ServiceChatRoomMember serviceChatMember = new ServiceChatRoomMember();

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

    // Profile view: which user's profile we're viewing (null = own)
    private User viewingProfileUser = null;

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

        // Add visibility selector to the new post form
        visibilityCombo = new ComboBox<>();
        visibilityCombo.getItems().addAll("🌍 Public", "👥 Friends", "🔒 Only Me");
        visibilityCombo.setValue("🌍 Public");
        visibilityCombo.setStyle("-fx-font-size: 11; -fx-pref-height: 28; -fx-background-color: #1A1A2E; -fx-text-fill: #ccc; -fx-background-radius: 6; -fx-border-color: #2A2A3C; -fx-border-radius: 6;");
        visibilityCombo.setMaxWidth(130);
        // Insert into the bottom HBox of newPostForm (before Cancel)
        Platform.runLater(() -> {
            if (newPostForm.getChildren().size() > 0) {
                javafx.scene.Node formContent = newPostForm.getChildren().get(0);
                if (formContent instanceof VBox) {
                    VBox formVBox = (VBox) formContent;
                    for (javafx.scene.Node child : formVBox.getChildren()) {
                        if (child instanceof HBox) {
                            HBox row = (HBox) child;
                            if (row.getChildren().contains(btnSubmitPost)) {
                                // Insert visibility combo before the Region spacer
                                for (int i = 0; i < row.getChildren().size(); i++) {
                                    if (row.getChildren().get(i) instanceof Region) {
                                        row.getChildren().add(i, visibilityCombo);
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        });

        // Load users in background, then load feed when ready
        AppThreadPool.io(() -> {
            serviceFollow.ensureTable();
            serviceGroup.ensureTable();
            serviceGroupMember.ensureTable();
            loadUsers();
            Platform.runLater(() -> {
                loadFeed();
                loadSidebarGroups();
                setupHeaderAvatar();

                // Check if we should navigate to a specific user's profile (e.g. from notification click)
                Integer pendingProfileId = SessionManager.getInstance().consumePendingCommunityProfile();
                if (pendingProfileId != null) {
                    showUserProfile(pendingProfileId);
                }
            });
        });

        // Global search handler
        if (globalSearchField != null) {
            globalSearchField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) handleGlobalSearch();
            });
        }

        // Click header avatar to go to profile
        if (headerProfileAvatar != null) {
            headerProfileAvatar.setOnMouseClicked(e -> switchToProfile());
        }

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
            // Set visibility from combo
            if (visibilityCombo != null) {
                String sel = visibilityCombo.getValue();
                if (sel != null && sel.contains("Friends")) post.setVisibility(Post.VISIBILITY_FRIENDS);
                else if (sel != null && sel.contains("Only Me")) post.setVisibility(Post.VISIBILITY_ONLY_ME);
                else post.setVisibility(Post.VISIBILITY_PUBLIC);
            }
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
        switchToFeed();
    }

    private void openPostDetail(Post post) {
        detailPost = post;
        expandedComments.add(post.getId()); // always show comments in detail
        commentErrorCache.remove(post.getId()); // clear error cache so we retry fresh
        feedScroll.setVisible(false); feedScroll.setManaged(false);
        if (wikiPane != null) { wikiPane.setVisible(false); wikiPane.setManaged(false); }
        if (quotesPane != null) { quotesPane.setVisible(false); quotesPane.setManaged(false); }
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
        User me = SessionManager.getInstance().getCurrentUser();
        int myId = me != null ? me.getId() : 0;
        Set<Integer> myFriends = myId > 0 ? serviceFollow.getFriendIds(myId) : Collections.emptySet();

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
            // Visibility filtering
            String vis = p.getVisibility();
            if (Post.VISIBILITY_ONLY_ME.equals(vis) && p.getAuthorId() != myId) continue;
            if (Post.VISIBILITY_FRIENDS.equals(vis) && p.getAuthorId() != myId && !myFriends.contains(p.getAuthorId())) continue;
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
        // Click on name → view user's profile
        nameLabel.setCursor(javafx.scene.Cursor.HAND);
        nameLabel.setOnMouseClicked(e -> showUserProfile(post.getAuthorId()));

        String timeText = post.getCreatedAt() != null
                ? formatTimeAgo(post.getCreatedAt().getTime())
                : "";
        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("community-time-label");

        // Visibility indicator
        String visIcon = "";
        if (Post.VISIBILITY_FRIENDS.equals(post.getVisibility())) visIcon = " · 👥";
        else if (Post.VISIBILITY_ONLY_ME.equals(post.getVisibility())) visIcon = " · 🔒";
        else visIcon = " · 🌍";
        Label visLabel = new Label(visIcon);
        visLabel.setStyle("-fx-text-fill: #8888AA; -fx-font-size: 11;");
        HBox timeLine = new HBox(2, timeLabel, visLabel);
        timeLine.setAlignment(Pos.CENTER_LEFT);

        nameCol.getChildren().addAll(nameLabel, timeLine);

        header.getChildren().addAll(avatar, nameCol);

        // ── Follow button (for other users' posts) ──
        if (post.getAuthorId() != myId && myId > 0) {
            boolean following = serviceFollow.isFollowing(myId, post.getAuthorId());
            Button followBtn = new Button(following ? "Following" : "Follow");
            followBtn.getStyleClass().addAll("community-follow-btn");
            if (following) followBtn.getStyleClass().add("community-follow-btn-active");
            followBtn.setOnAction(e -> {
                AppThreadPool.io(() -> {
                    try {
                        if (serviceFollow.isFollowing(myId, post.getAuthorId())) {
                            serviceFollow.unfollow(myId, post.getAuthorId());
                        } else {
                            serviceFollow.follow(myId, post.getAuthorId());
                        }
                        utils.InMemoryCache.evictByPrefix("follows:");
                        Platform.runLater(() -> {
                            if (detailPost != null) renderDetailView(detailPost);
                            else if ("home".equals(activeTab)) loadHomeFeed();
                            else loadFeed();
                        });
                    } catch (SQLException ex) {
                        Platform.runLater(() -> showToast("Follow action failed: " + ex.getMessage(), true));
                    }
                });
            });
            header.getChildren().add(followBtn);
        }

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

        // ── Share button ──
        ImageView shareIcon = twemojiIcon("1f4e4", 14); // 📤 outbox tray
        Label shareLabel = new Label(" Share");
        shareLabel.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 12;");
        HBox shareContent = new HBox(4, shareIcon, shareLabel);
        shareContent.setAlignment(Pos.CENTER);
        Button shareBtn = new Button();
        shareBtn.setGraphic(shareContent);
        shareBtn.getStyleClass().add("community-react-btn");
        shareBtn.setOnAction(e -> showSharePopup(post, shareBtn));

        footer.getChildren().addAll(commentBtn, shareBtn);

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
    //  SOCIAL SHARING (Facebook / Instagram)
    // ═══════════════════════════════════════════

    private void showSharePopup(Post post, Node anchor) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        VBox card = new VBox(0);
        card.getStyleClass().add("share-popup-card");
        card.setPrefWidth(310);
        card.setMaxWidth(310);

        // ── Header ──
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 10, 16));
        header.setStyle("-fx-border-color: transparent transparent #1C1B22 transparent; -fx-border-width: 0 0 1 0;");

        ImageView headerIcon = twemojiIcon("1f4e4", 18);
        Label title = new Label("Share Post");
        title.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 15; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("\u2715");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #6B6B78; -fx-font-size: 14; -fx-cursor: hand; -fx-padding: 2 6;");
        closeBtn.setOnAction(e -> popup.hide());

        header.getChildren().addAll(headerIcon, title, spacer, closeBtn);

        // ── Post preview ──
        VBox previewBox = new VBox(6);
        previewBox.setPadding(new Insets(10, 16, 10, 16));
        previewBox.setStyle("-fx-background-color: #0E0D14; -fx-background-radius: 8;");
        VBox.setMargin(previewBox, new Insets(10, 12, 6, 12));

        if (post.getContent() != null && !post.getContent().trim().isEmpty()) {
            String preview = post.getContent().length() > 120
                    ? post.getContent().substring(0, 120) + "..."
                    : post.getContent();
            Label previewText = new Label(preview);
            previewText.setWrapText(true);
            previewText.setMaxWidth(270);
            previewText.setStyle("-fx-text-fill: #9E9EA8; -fx-font-size: 12;");
            previewBox.getChildren().add(previewText);
        }

        if (post.getImageBase64() != null && !post.getImageBase64().isEmpty()) {
            try {
                Image img = postImageCache.get(post.getId());
                if (img == null) {
                    byte[] imgBytes = Base64.getDecoder().decode(post.getImageBase64());
                    img = new Image(new ByteArrayInputStream(imgBytes));
                }
                ImageView thumb = new ImageView(img);
                thumb.setFitWidth(120);
                thumb.setPreserveRatio(true);
                thumb.setSmooth(true);
                Rectangle clip = new Rectangle(120, 80);
                clip.setArcWidth(8);
                clip.setArcHeight(8);
                thumb.setClip(clip);
                previewBox.getChildren().add(thumb);
            } catch (Exception ignored) {}
        }

        // ── Share buttons ──
        VBox buttonsBox = new VBox(6);
        buttonsBox.setPadding(new Insets(8, 12, 14, 12));

        // Facebook
        Button fbBtn = createShareButton("📘", "Share to Facebook",
                "Share post content on your Facebook timeline",
                "#1877F2", "#1565C0");
        fbBtn.setOnAction(e -> {
            shareToFacebook(post);
            popup.hide();
        });

        // Instagram
        Button igBtn = createShareButton("📷", "Share to Instagram",
                "Save image & copy text for Instagram",
                "#E1306C", "#C13584");
        igBtn.setOnAction(e -> {
            shareToInstagram(post);
            popup.hide();
        });

        // Copy to Clipboard
        Button copyBtn = createShareButton("📋", "Copy to Clipboard",
                "Copy post text to paste anywhere",
                "#2C666E", "#1E4D54");
        copyBtn.setOnAction(e -> {
            copyPostToClipboard(post);
            popup.hide();
        });

        // Save Image (only if post has image)
        if (post.getImageBase64() != null && !post.getImageBase64().isEmpty()) {
            Button saveImgBtn = createShareButton("💾", "Save Image",
                    "Download the post image to your computer",
                    "#7C3AED", "#6D28D9");
            saveImgBtn.setOnAction(e -> {
                savePostImage(post);
                popup.hide();
            });
            buttonsBox.getChildren().addAll(fbBtn, igBtn, copyBtn, saveImgBtn);
        } else {
            buttonsBox.getChildren().addAll(fbBtn, igBtn, copyBtn);
        }

        card.getChildren().addAll(header, previewBox, buttonsBox);

        // Drop shadow effect
        card.setEffect(new javafx.scene.effect.DropShadow(20, 0, 4,
                Color.rgb(0, 0, 0, 0.55)));

        popup.getContent().add(card);

        // Position near the share button
        Window win = anchor.getScene().getWindow();
        javafx.geometry.Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds != null) {
            popup.show(win, bounds.getMinX() - 100, bounds.getMaxY() + 6);
        } else {
            popup.show(win);
        }
    }

    private Button createShareButton(String emoji, String label, String description,
                                      String color1, String color2) {
        HBox content = new HBox(12);
        content.setAlignment(Pos.CENTER_LEFT);

        Label emojiLabel = new Label(emoji);
        emojiLabel.setStyle("-fx-font-size: 22;");

        VBox textCol = new VBox(1);
        Label nameLabel = new Label(label);
        nameLabel.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 13; -fx-font-weight: bold;");
        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 10.5;");
        textCol.getChildren().addAll(nameLabel, descLabel);

        content.getChildren().addAll(emojiLabel, textCol);

        Button btn = new Button();
        btn.setGraphic(content);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("share-option-btn");
        btn.setStyle("-fx-background-color: linear-gradient(to right, " + color1 + "15, " + color2 + "08);"
                + "-fx-border-color: " + color1 + "30; -fx-border-width: 1; -fx-border-radius: 10;"
                + "-fx-background-radius: 10; -fx-padding: 10 14; -fx-cursor: hand;");

        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: linear-gradient(to right, " + color1 + "30, " + color2 + "18);"
                + "-fx-border-color: " + color1 + "60; -fx-border-width: 1; -fx-border-radius: 10;"
                + "-fx-background-radius: 10; -fx-padding: 10 14; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: linear-gradient(to right, " + color1 + "15, " + color2 + "08);"
                + "-fx-border-color: " + color1 + "30; -fx-border-width: 1; -fx-border-radius: 10;"
                + "-fx-background-radius: 10; -fx-padding: 10 14; -fx-cursor: hand;"));

        return btn;
    }

    private void shareToFacebook(Post post) {
        try {
            String text = post.getContent() != null ? post.getContent() : "";
            String appName = "SynergyGig";
            String shareText = text + "\n\n— Shared from " + appName;

            // Copy content (text + image) to clipboard so user can paste
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(shareText);
            if (post.getImageBase64() != null && !post.getImageBase64().isEmpty()) {
                Image img = postImageCache.get(post.getId());
                if (img == null) {
                    byte[] imgBytes = Base64.getDecoder().decode(post.getImageBase64());
                    img = new Image(new ByteArrayInputStream(imgBytes));
                }
                cc.putImage(img);
                saveImageToTemp(post); // Save for reference
            }
            clipboard.setContent(cc);

            // Open Facebook — go straight to the main feed where user can "Create Post"
            java.awt.Desktop.getDesktop().browse(new URI("https://www.facebook.com/"));

            incrementShareCount(post);
            showToast("\u2705 Content copied! Click \"What's on your mind?\" on Facebook and paste (Ctrl+V)", false);
        } catch (Exception ex) {
            showToast("Could not open Facebook: " + ex.getMessage(), true);
        }
    }

    private void shareToInstagram(Post post) {
        try {
            String text = post.getContent() != null ? post.getContent() : "";
            String appName = "SynergyGig";
            String shareText = text + "\n\n— Shared from " + appName;

            // Copy caption text to clipboard
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(shareText);

            if (post.getImageBase64() != null && !post.getImageBase64().isEmpty()) {
                // Save image to temp + put on clipboard
                File tempImg = saveImageToTemp(post);
                Image img = postImageCache.get(post.getId());
                if (img == null) {
                    byte[] imgBytes = Base64.getDecoder().decode(post.getImageBase64());
                    img = new Image(new ByteArrayInputStream(imgBytes));
                }
                cc.putImage(img);
                clipboard.setContent(cc);

                // Open the saved image so user can drag/upload it
                if (tempImg != null) {
                    java.awt.Desktop.getDesktop().open(tempImg);
                }

                // Open Instagram's create post page
                java.awt.Desktop.getDesktop().browse(new URI("https://www.instagram.com/create/select/"));

                showToast("\u2705 Image opened & caption copied! Upload the image on Instagram, then paste caption (Ctrl+V)", false);
            } else {
                clipboard.setContent(cc);
                // Open Instagram's create page for text-only
                java.awt.Desktop.getDesktop().browse(new URI("https://www.instagram.com/create/select/"));
                showToast("\u2705 Caption copied! Create a post on Instagram and paste (Ctrl+V)", false);
            }

            incrementShareCount(post);
        } catch (Exception ex) {
            showToast("Could not open Instagram: " + ex.getMessage(), true);
        }
    }

    private void copyPostToClipboard(Post post) {
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();

            String text = post.getContent() != null ? post.getContent() : "";
            cc.putString(text);

            // Also copy image if present
            if (post.getImageBase64() != null && !post.getImageBase64().isEmpty()) {
                Image img = postImageCache.get(post.getId());
                if (img == null) {
                    byte[] imgBytes = Base64.getDecoder().decode(post.getImageBase64());
                    img = new Image(new ByteArrayInputStream(imgBytes));
                }
                cc.putImage(img);
            }

            clipboard.setContent(cc);
            SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
            showToast("Post copied to clipboard!", false);
            incrementShareCount(post);
        } catch (Exception ex) {
            showToast("Could not copy to clipboard: " + ex.getMessage(), true);
        }
    }

    private void savePostImage(Post post) {
        if (post.getImageBase64() == null || post.getImageBase64().isEmpty()) return;
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Post Image");
            fc.setInitialFileName("synergygig_post_" + post.getId() + ".png");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("PNG Image", "*.png"),
                    new FileChooser.ExtensionFilter("JPEG Image", "*.jpg", "*.jpeg"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File file = fc.showSaveDialog(ownerWindow());
            if (file != null) {
                byte[] imgBytes = Base64.getDecoder().decode(post.getImageBase64());

                // Convert to BufferedImage for proper format saving
                java.awt.image.BufferedImage bImg = javax.imageio.ImageIO.read(new ByteArrayInputStream(imgBytes));
                String ext = file.getName().contains(".") ?
                        file.getName().substring(file.getName().lastIndexOf('.') + 1).toLowerCase() : "png";
                if ("jpg".equals(ext)) ext = "jpeg";
                javax.imageio.ImageIO.write(bImg, ext, file);

                showToast("Image saved to " + file.getName(), false);
                incrementShareCount(post);
            }
        } catch (Exception ex) {
            showToast("Could not save image: " + ex.getMessage(), true);
        }
    }

    private File saveImageToTemp(Post post) {
        try {
            byte[] imgBytes = Base64.getDecoder().decode(post.getImageBase64());
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "synergygig_shares");
            tempDir.mkdirs();
            File tempFile = new File(tempDir, "post_" + post.getId() + ".png");

            java.awt.image.BufferedImage bImg = javax.imageio.ImageIO.read(new ByteArrayInputStream(imgBytes));
            javax.imageio.ImageIO.write(bImg, "png", tempFile);
            return tempFile;
        } catch (Exception ex) {
            return null;
        }
    }

    private void incrementShareCount(Post post) {
        AppThreadPool.io(() -> {
            try {
                post.setSharesCount(post.getSharesCount() + 1);
                // Update via dedicated share endpoint or direct DB
                if (utils.AppConfig.isApiMode()) {
                    ApiClient.post("/posts/" + post.getId() + "/share", new HashMap<>());
                } else {
                    try (java.sql.Connection conn = utils.MyDatabase.getInstance().getConnection();
                         java.sql.PreparedStatement ps = conn.prepareStatement(
                                 "UPDATE posts SET shares_count = shares_count + 1 WHERE id = ?")) {
                        ps.setInt(1, post.getId());
                        ps.executeUpdate();
                    }
                }
                utils.InMemoryCache.evictByPrefix("posts:");
            } catch (Exception ignored) {}
        });
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
                commentErrorCache.remove(post.getId());
                if (comments.isEmpty()) {
                    Label empty = new Label("No comments yet. Be the first!");
                    empty.getStyleClass().add("community-time-label");
                    empty.setStyle("-fx-padding: 8 0;");
                    section.getChildren().add(empty);
                } else {
                    // Build threaded comments: top-level + replies
                    Map<Integer, List<Comment>> repliesMap = new LinkedHashMap<>();
                    List<Comment> topLevel = new ArrayList<>();
                    for (Comment c : comments) {
                        if (c.getParentId() != null) {
                            repliesMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
                        } else {
                            topLevel.add(c);
                        }
                    }
                    for (Comment c : topLevel) {
                        HBox row = buildCommentRow(c, myId, post);
                        section.getChildren().add(row);
                        // Render replies indented
                        List<Comment> replies = repliesMap.get(c.getId());
                        if (replies != null) {
                            for (Comment reply : replies) {
                                HBox replyRow = buildCommentRow(reply, myId, post);
                                replyRow.setPadding(new Insets(0, 0, 0, 40));
                                replyRow.setStyle("-fx-border-color: transparent transparent transparent #2A2A3C; -fx-border-width: 0 0 0 2;");
                                section.getChildren().add(replyRow);
                            }
                        }
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

    private HBox buildCommentRow(Comment comment, int myId, Post post) {
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

        // Reply button (only for top-level comments)
        HBox actionsRow = new HBox(12);
        actionsRow.setAlignment(Pos.CENTER_LEFT);
        if (comment.getParentId() == null && post != null) {
            Button replyBtn = new Button("↩ Reply");
            replyBtn.setStyle("-fx-text-fill: #2C666E; -fx-font-size: 10; -fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 1 4;");
            replyBtn.setOnAction(e -> {
                // Show an inline reply field
                TextField replyField = new TextField();
                replyField.setPromptText("Reply to " + name + "...");
                replyField.getStyleClass().add("input-field");
                replyField.setStyle("-fx-font-size: 11; -fx-pref-height: 30; -fx-padding: 4 8;");
                Button sendReply = new Button("➤");
                sendReply.getStyleClass().add("btn-primary");
                sendReply.setStyle("-fx-font-size: 11; -fx-min-width: 30; -fx-min-height: 30; -fx-padding: 0;");
                HBox replyRow = new HBox(6, replyField, sendReply);
                HBox.setHgrow(replyField, Priority.ALWAYS);
                replyRow.setAlignment(Pos.CENTER_LEFT);
                replyRow.setPadding(new Insets(4, 0, 0, 0));

                Runnable doReply = () -> {
                    String txt = replyField.getText();
                    if (txt != null && !txt.trim().isEmpty()) {
                        submitReply(post, comment.getId(), txt.trim());
                    }
                };
                sendReply.setOnAction(ev -> doReply.run());
                replyField.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ENTER) doReply.run(); });

                // Add below content, remove after send
                int idx = col.getChildren().indexOf(actionsRow);
                if (idx >= 0 && idx + 1 < col.getChildren().size()
                        && col.getChildren().get(idx + 1) instanceof HBox
                        && ((HBox) col.getChildren().get(idx + 1)).getChildren().stream().anyMatch(n -> n instanceof TextField)) {
                    // Already showing reply field — toggle off
                    col.getChildren().remove(idx + 1);
                } else {
                    col.getChildren().add(idx + 1, replyRow);
                    replyField.requestFocus();
                }
            });
            actionsRow.getChildren().add(replyBtn);
        }

        col.getChildren().addAll(nameRow, contentLabel, actionsRow);
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

    /** Submit a reply to a comment (nested comment with parentId). */
    private void submitReply(Post post, int parentCommentId, String text) {
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) return;
        BadWordsService.CheckResult check = BadWordsService.check(text);
        if (check.hasBadWords) {
            showToast("Your reply contains inappropriate language.", true);
            return;
        }
        try {
            Comment reply = new Comment(post.getId(), me.getId(), text, parentCommentId);
            serviceComment.ajouter(reply);
            SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
            expandedComments.add(post.getId());
            if (detailPost != null) renderDetailView(detailPost);
            else loadFeed();
        } catch (SQLException e) {
            showToast("Failed to reply: " + e.getMessage(), true);
        }
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
        DialogHelper.theme(dialog);
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
        DialogHelper.theme(dialog);
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

    private String activeTab = "home";

    private void initTabs() {
        if (wikiSearchField != null) {
            wikiSearchField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) handleWikiSearch();
            });
        }
        if (peopleSearchField != null) {
            peopleSearchField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) loadPeople(peopleSearchField.getText());
            });
            peopleSearchField.textProperty().addListener((obs, oldv, newv) -> {
                // Debounced live search as user types
                loadPeople(newv);
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
    private void switchToHome() {
        activeTab = "home";
        setTabActive(tabHome);
        hideAllPanes();
        feedScroll.setVisible(true); feedScroll.setManaged(true);
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(true); animNewPostBtn.setManaged(true); }
        loadHomeFeed();
    }

    @FXML
    private void switchToFeed() {
        activeTab = "feed";
        setTabActive(tabFeed);
        hideAllPanes();
        feedScroll.setVisible(true); feedScroll.setManaged(true);
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(true); animNewPostBtn.setManaged(true); }
        loadFeed();
    }

    @FXML
    private void switchToGroups() {
        activeTab = "groups";
        // No dedicated tab button in sidebar — clear all nav highlights
        for (Button tab : new Button[]{tabHome, tabFeed, tabWiki, tabQuotes, tabPeople}) {
            if (tab != null) tab.getStyleClass().remove("community-nav-link-active");
        }
        hideAllPanes();
        if (groupsPane != null) { groupsPane.setVisible(true); groupsPane.setManaged(true); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(false); animNewPostBtn.setManaged(false); }
        handleCancelPost();
        loadGroups();
    }

    @FXML
    private void switchToProfile() {
        activeTab = "profile";
        setTabActive(null); // no sidebar button — clear all
        hideAllPanes();
        if (profilePane != null) { profilePane.setVisible(true); profilePane.setManaged(true); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(false); animNewPostBtn.setManaged(false); }
        handleCancelPost();
        viewingProfileUser = SessionManager.getInstance().getCurrentUser();
        loadProfile(viewingProfileUser);
    }

    @FXML
    private void switchToWiki() {
        activeTab = "wiki";
        setTabActive(tabWiki);
        hideAllPanes();
        if (wikiPane != null) { wikiPane.setVisible(true); wikiPane.setManaged(true); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(false); animNewPostBtn.setManaged(false); }
        handleCancelPost();
        Platform.runLater(() -> { if (wikiSearchField != null) wikiSearchField.requestFocus(); });
    }

    @FXML
    private void switchToQuotes() {
        activeTab = "quotes";
        setTabActive(tabQuotes);
        hideAllPanes();
        if (quotesPane != null) { quotesPane.setVisible(true); quotesPane.setManaged(true); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(false); animNewPostBtn.setManaged(false); }
        handleCancelPost();
    }

    @FXML
    private void switchToPeople() {
        activeTab = "people";
        setTabActive(tabPeople);
        hideAllPanes();
        if (peoplePane != null) { peoplePane.setVisible(true); peoplePane.setManaged(true); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(false); animNewPostBtn.setManaged(false); }
        handleCancelPost();
        loadPeople("");
        Platform.runLater(() -> { if (peopleSearchField != null) peopleSearchField.requestFocus(); });
    }

    private void hideAllPanes() {
        feedScroll.setVisible(false); feedScroll.setManaged(false);
        if (wikiPane != null) { wikiPane.setVisible(false); wikiPane.setManaged(false); }
        if (quotesPane != null) { quotesPane.setVisible(false); quotesPane.setManaged(false); }
        if (groupsPane != null) { groupsPane.setVisible(false); groupsPane.setManaged(false); }
        if (groupDetailPane != null) { groupDetailPane.setVisible(false); groupDetailPane.setManaged(false); }
        if (profilePane != null) { profilePane.setVisible(false); profilePane.setManaged(false); }
        if (peoplePane != null) { peoplePane.setVisible(false); peoplePane.setManaged(false); }
        if (detailView != null) { detailView.setVisible(false); detailView.setManaged(false); }
    }

    private void setTabActive(Button active) {
        for (Button tab : new Button[]{tabHome, tabFeed, tabWiki, tabQuotes, tabPeople}) {
            if (tab == null) continue;
            tab.getStyleClass().remove("community-nav-link-active");
        }
        if (active != null) active.getStyleClass().add("community-nav-link-active");
        SoundManager.getInstance().play(SoundManager.TAB_SWITCH);
    }

    // ═══════════════════════════════════════════
    //  HOME FEED — ranked posts from people you follow
    // ═══════════════════════════════════════════

    /** Engagement score for ranking: reactions*3 + comments*2 + recency bonus. */
    private double engagementScore(Post p) {
        List<Reaction> rx = reactionsCache.get(p.getId());
        int reactions = rx != null ? rx.size() : 0;
        double score = reactions * 3.0 + p.getCommentsCount() * 2.0 + p.getSharesCount() * 1.5;
        // Recency bonus: posts from last 24h get a boost
        if (p.getCreatedAt() != null) {
            long ageHours = ChronoUnit.HOURS.between(p.getCreatedAt().toInstant(), Instant.now());
            if (ageHours < 1) score += 50;
            else if (ageHours < 6) score += 30;
            else if (ageHours < 24) score += 15;
            else if (ageHours < 72) score += 5;
        }
        return score;
    }

    private void loadHomeFeed() {
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) { loadFeed(); return; }
        int myId = me.getId();
        AppThreadPool.io(() -> {
            try {
                Set<Integer> followedIds = serviceFollow.getFollowedIds(myId);
                List<Post> posts = filterHumanPosts(servicePost.recuperer());

                // Filter to only posts from followed users + own posts
                List<Post> homePosts;
                if (followedIds.isEmpty()) {
                    homePosts = posts; // fallback: show all if not following anyone
                } else {
                    Set<Integer> showIds = new HashSet<>(followedIds);
                    showIds.add(myId);
                    homePosts = posts.stream()
                            .filter(p -> showIds.contains(p.getAuthorId()))
                            .collect(Collectors.toList());
                }

                // Pre-fetch reactions for scoring, then rank by engagement
                prefetchReactions(homePosts.subList(0, Math.min(homePosts.size(), PAGE_SIZE * 2)));
                homePosts.sort((a, b) -> Double.compare(engagementScore(b), engagementScore(a)));

                int limit = Math.min(homePosts.size(), PAGE_SIZE);
                List<Post> page = homePosts.subList(0, limit);
                prefetchReactions(page);
                Platform.runLater(() -> {
                    allPosts = homePosts;
                    displayedPostCount = limit;

                    feedContainer.getChildren().clear();
                    if (followedIds.isEmpty()) {
                        // Show hint to follow people
                        Label hint = new Label("👋 Follow people to personalize your Home feed!\nShowing all posts for now.");
                        hint.setWrapText(true);
                        hint.setStyle("-fx-text-fill: #8888AA; -fx-font-size: 13; -fx-padding: 10 16; -fx-background-color: #1A1A2E; -fx-background-radius: 8;");
                        feedContainer.getChildren().add(hint);
                    }
                    renderFeed(page, homePosts.size() > limit);
                });
            } catch (SQLException e) {
                System.err.println("Home feed error: " + e.getMessage());
            }
        });
    }

    // ═══════════════════════════════════════════
    //  GROUPS — list, create, join
    // ═══════════════════════════════════════════

    private void loadGroups() {
        if (groupsContainer == null) return;
        User me = SessionManager.getInstance().getCurrentUser();
        int myId = me != null ? me.getId() : 0;

        AppThreadPool.io(() -> {
            try {
                List<CommunityGroup> groups = serviceGroup.getAll();
                Set<Integer> myGroups = serviceGroupMember.getGroupIdsForUser(myId);

                Platform.runLater(() -> {
                    groupsContainer.getChildren().clear();

                    if (groups.isEmpty()) {
                        Label empty = new Label("No groups yet. Create the first one!");
                        empty.setStyle("-fx-text-fill: #6B6B80; -fx-font-size: 14; -fx-padding: 40;");
                        groupsContainer.getChildren().add(empty);
                        return;
                    }

                    for (CommunityGroup group : groups) {
                        VBox card = buildGroupCard(group, myId, myGroups.contains(group.getId()));
                        card.setMaxWidth(680);
                        groupsContainer.getChildren().add(card);
                    }
                });
            } catch (SQLException e) {
                Platform.runLater(() -> showToast("Failed to load groups: " + e.getMessage(), true));
            }
        });
    }

    private VBox buildGroupCard(CommunityGroup group, int myId, boolean isMember) {
        VBox card = new VBox(8);
        card.getStyleClass().add("community-card");
        card.setPadding(new Insets(16));
        card.setStyle("-fx-cursor: hand;");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        // Group icon
        Label icon = new Label("👥");
        icon.setStyle("-fx-font-size: 32; -fx-min-width: 48; -fx-min-height: 48; -fx-alignment: center; " +
                "-fx-background-color: #2A2A3C; -fx-background-radius: 12;");

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(group.getName());
        nameLabel.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 16; -fx-font-weight: bold;");

        Label desc = new Label(group.getDescription() != null ? group.getDescription() : "");
        desc.setStyle("-fx-text-fill: #8888AA; -fx-font-size: 12;");
        desc.setWrapText(true);
        desc.setMaxHeight(40);

        Label meta = new Label(group.getMemberCount() + " members • " + group.getPrivacy());
        meta.setStyle("-fx-text-fill: #6B6B80; -fx-font-size: 11;");

        info.getChildren().addAll(nameLabel, desc, meta);

        // Join/Leave button
        Button actionBtn;
        if (group.getCreatorId() == myId) {
            actionBtn = new Button("Admin");
            actionBtn.getStyleClass().addAll("community-follow-btn", "community-follow-btn-active");
            actionBtn.setDisable(true);
        } else if (isMember) {
            actionBtn = new Button("Leave");
            actionBtn.getStyleClass().add("community-follow-btn");
            actionBtn.setStyle("-fx-text-fill: #FF4444;");
            actionBtn.setOnAction(e -> {
                AppThreadPool.io(() -> {
                    try {
                        serviceGroupMember.leave(group.getId(), myId);
                        serviceGroup.refreshMemberCount(group.getId());
                        utils.InMemoryCache.evictByPrefix("gmembers:");
                        utils.InMemoryCache.evictByPrefix("cgroups:");
                        Platform.runLater(() -> loadGroups());
                    } catch (SQLException ex) {
                        Platform.runLater(() -> showToast("Failed to leave group: " + ex.getMessage(), true));
                    }
                });
            });
        } else {
            actionBtn = new Button("Join");
            actionBtn.getStyleClass().add("btn-primary");
            actionBtn.setStyle("-fx-font-size: 12; -fx-padding: 6 16;");
            actionBtn.setOnAction(e -> {
                AppThreadPool.io(() -> {
                    try {
                        serviceGroupMember.join(group.getId(), myId, GroupMember.ROLE_MEMBER);
                        serviceGroup.refreshMemberCount(group.getId());
                        utils.InMemoryCache.evictByPrefix("gmembers:");
                        utils.InMemoryCache.evictByPrefix("cgroups:");
                        Platform.runLater(() -> loadGroups());
                    } catch (SQLException ex) {
                        Platform.runLater(() -> showToast("Failed to join group: " + ex.getMessage(), true));
                    }
                });
            });
        }

        header.getChildren().addAll(icon, info, actionBtn);
        card.getChildren().add(header);

        // Click card to open group detail page
        card.setOnMouseClicked(e -> {
            if (!(e.getTarget() instanceof Button)) openGroupDetail(group);
        });

        return card;
    }

    @FXML
    private void handleCreateGroup() {
        Dialog<CommunityGroup> dialog = new Dialog<>();
        DialogHelper.theme(dialog);
        dialog.setTitle("Create Group");
        dialog.initOwner(feedScroll.getScene().getWindow());

        DialogPane dp = dialog.getDialogPane();
        dp.setStyle("-fx-background-color: #1E1E2E;");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox form = new VBox(12);
        form.setPadding(new Insets(16));

        Label title = new Label("Create a New Group");
        title.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 18; -fx-font-weight: bold;");

        TextField nameField = new TextField();
        nameField.setPromptText("Group name");
        nameField.setStyle("-fx-background-color: #2A2A3C; -fx-text-fill: #E0E0F0; -fx-prompt-text-fill: #6B6B80; -fx-background-radius: 8; -fx-padding: 10;");

        TextArea descArea = new TextArea();
        descArea.setPromptText("Description (optional)");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);
        descArea.setStyle("-fx-background-color: #2A2A3C; -fx-text-fill: #E0E0F0; -fx-prompt-text-fill: #6B6B80; -fx-background-radius: 8;");

        ComboBox<String> privacyBox = new ComboBox<>();
        privacyBox.getItems().addAll("PUBLIC", "PRIVATE");
        privacyBox.setValue("PUBLIC");
        privacyBox.setStyle("-fx-background-color: #2A2A3C; -fx-text-fill: #E0E0F0;");

        form.getChildren().addAll(title, new Label("Name:") {{ setStyle("-fx-text-fill: #8888AA;"); }},
                nameField, new Label("Description:") {{ setStyle("-fx-text-fill: #8888AA;"); }},
                descArea, new Label("Privacy:") {{ setStyle("-fx-text-fill: #8888AA;"); }}, privacyBox);
        dp.setContent(form);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK && !nameField.getText().trim().isEmpty()) {
                CommunityGroup g = new CommunityGroup(nameField.getText().trim(), descArea.getText().trim(),
                        SessionManager.getInstance().getCurrentUser().getId());
                g.setPrivacy(privacyBox.getValue());
                return g;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(group -> {
            AppThreadPool.io(() -> {
                try {
                    int groupId = serviceGroup.create(group);
                    if (groupId > 0) {
                        serviceGroupMember.join(groupId, group.getCreatorId(), GroupMember.ROLE_ADMIN);
                        serviceGroup.refreshMemberCount(groupId);
                        utils.InMemoryCache.evictByPrefix("cgroups:");
                        utils.InMemoryCache.evictByPrefix("gmembers:");
                    }
                    Platform.runLater(() -> {
                        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
                        loadGroups();
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> showToast("Failed to create group: " + e.getMessage(), true));
                }
            });
        });
    }

    // ═══════════════════════════════════════════
    //  HEADER — avatar, global search
    // ═══════════════════════════════════════════

    /** Set current user's avatar in the header top-right */
    private void setupHeaderAvatar() {
        if (headerProfileAvatar == null) return;
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) return;
        StackPane av = createAvatar(me, 36);
        headerProfileAvatar.getChildren().clear();
        headerProfileAvatar.getChildren().addAll(av.getChildren());
    }

    /** Global search: searches people first, could expand to posts later */
    @FXML
    private void handleGlobalSearch() {
        if (globalSearchField == null) return;
        String q = globalSearchField.getText();
        if (q == null || q.trim().isEmpty()) return;
        // Switch to People tab and search
        switchToPeople();
        if (peopleSearchField != null) peopleSearchField.setText(q.trim());
        loadPeople(q.trim());
    }

    // ═══════════════════════════════════════════
    //  SIDEBAR — groups list
    // ═══════════════════════════════════════════

    /** Load the user's groups in the left sidebar */
    private void loadSidebarGroups() {
        if (sidebarGroupsList == null) return;
        User me = SessionManager.getInstance().getCurrentUser();
        int myId = me != null ? me.getId() : 0;
        AppThreadPool.io(() -> {
            try {
                List<CommunityGroup> allGroups = serviceGroup.getAll();
                Set<Integer> myGroupIds = serviceGroupMember.getGroupIdsForUser(myId);
                Platform.runLater(() -> {
                    sidebarGroupsList.getChildren().clear();
                    for (CommunityGroup g : allGroups) {
                        if (myGroupIds.contains(g.getId())) {
                            Button groupBtn = new Button("👥  " + g.getName());
                            groupBtn.getStyleClass().add("community-nav-link");
                            groupBtn.setMaxWidth(Double.MAX_VALUE);
                            groupBtn.setAlignment(Pos.CENTER_LEFT);
                            groupBtn.setOnAction(e -> openGroupDetail(g));
                            sidebarGroupsList.getChildren().add(groupBtn);
                        }
                    }
                    // "See All Groups" link
                    Button seeAll = new Button("See all groups →");
                    seeAll.setStyle("-fx-background-color: transparent; -fx-text-fill: #90DDF0; -fx-font-size: 12; -fx-cursor: hand; -fx-padding: 8 14;");
                    seeAll.setMaxWidth(Double.MAX_VALUE);
                    seeAll.setAlignment(Pos.CENTER_LEFT);
                    seeAll.setOnAction(e -> switchToGroups());
                    sidebarGroupsList.getChildren().add(seeAll);
                });
            } catch (SQLException e) {
                // Silently fail - sidebar is not critical
            }
        });
    }

    // ═══════════════════════════════════════════
    //  GROUP DETAIL — dedicated page for a group
    // ═══════════════════════════════════════════

    /** Navigate to a group's own page */
    private void openGroupDetail(CommunityGroup group) {
        currentViewingGroup = group;
        activeTab = "group-detail";
        for (Button tab : new Button[]{tabHome, tabFeed, tabWiki, tabQuotes, tabPeople}) {
            if (tab != null) tab.getStyleClass().remove("community-nav-link-active");
        }
        hideAllPanes();
        if (groupDetailPane != null) { groupDetailPane.setVisible(true); groupDetailPane.setManaged(true); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(false); animNewPostBtn.setManaged(false); }
        handleCancelPost();
        loadGroupDetail(group);
    }

    /** Load group detail page with post form and group-specific feed */
    private void loadGroupDetail(CommunityGroup group) {
        if (groupDetailContent == null) return;
        if (groupDetailName != null) groupDetailName.setText(group.getName());
        if (groupDetailMeta != null) groupDetailMeta.setText(group.getMemberCount() + " members • " + group.getPrivacy());

        User me = SessionManager.getInstance().getCurrentUser();
        int myId = me != null ? me.getId() : 0;

        AppThreadPool.io(() -> {
            try {
                List<Post> allPosts = filterHumanPosts(servicePost.recuperer());
                List<Post> groupPosts = allPosts.stream()
                        .filter(p -> group.getId() == (p.getGroupId() != null ? p.getGroupId() : 0))
                        .collect(Collectors.toList());
                prefetchReactions(groupPosts.subList(0, Math.min(groupPosts.size(), PAGE_SIZE)));

                Set<Integer> memberIds = serviceGroupMember.getMembers(group.getId()).stream()
                        .map(gm -> gm.getUserId()).collect(Collectors.toSet());

                Platform.runLater(() -> {
                    groupDetailContent.getChildren().clear();

                    // Group info card
                    VBox infoCard = new VBox(6);
                    infoCard.getStyleClass().add("community-card");
                    infoCard.setPadding(new Insets(16));
                    infoCard.setMaxWidth(680);
                    Label groupIcon = new Label("👥");
                    groupIcon.setStyle("-fx-font-size: 36;");
                    Label groupName = new Label(group.getName());
                    groupName.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 20; -fx-font-weight: bold;");
                    Label groupDesc = new Label(group.getDescription() != null ? group.getDescription() : "");
                    groupDesc.setStyle("-fx-text-fill: #8888AA; -fx-font-size: 13;");
                    groupDesc.setWrapText(true);
                    Label memberLabel = new Label(memberIds.size() + " members • " + group.getPrivacy());
                    memberLabel.setStyle("-fx-text-fill: #6B6B80; -fx-font-size: 12;");
                    infoCard.getChildren().addAll(groupIcon, groupName, groupDesc, memberLabel);
                    groupDetailContent.getChildren().add(infoCard);

                    // Post creation (inline, for group)
                    VBox postBox = new VBox(8);
                    postBox.getStyleClass().add("community-card");
                    postBox.setPadding(new Insets(14));
                    postBox.setMaxWidth(680);
                    Label postLabel = new Label("Write something to " + group.getName() + "...");
                    postLabel.setStyle("-fx-text-fill: #8888AA; -fx-font-size: 13;");
                    TextArea groupPostArea = new TextArea();
                    groupPostArea.setPromptText("What's on your mind?");
                    groupPostArea.setWrapText(true);
                    groupPostArea.setPrefRowCount(3);
                    groupPostArea.setMaxHeight(120);
                    groupPostArea.getStyleClass().add("chat-textarea");
                    Button postBtn = new Button("Post");
                    postBtn.getStyleClass().add("btn-primary");
                    postBtn.setStyle("-fx-font-size: 12;");
                    postBtn.setOnAction(e -> {
                        String content = groupPostArea.getText();
                        if (content == null || content.trim().isEmpty()) return;
                        // Profanity filter
                        if (BadWordsService.check(content.trim()).hasBadWords) {
                            showToast("Your post contains inappropriate language.", true);
                            return;
                        }
                        postBtn.setDisable(true);
                        AppThreadPool.io(() -> {
                            try {
                                Post p = new Post(myId, content.trim(), null);
                                p.setGroupId(group.getId());
                                p.setVisibility(Post.VISIBILITY_PUBLIC);
                                servicePost.ajouter(p);
                                Platform.runLater(() -> {
                                    groupPostArea.clear();
                                    postBtn.setDisable(false);
                                    SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
                                    loadGroupDetail(group);
                                });
                            } catch (SQLException ex) {
                                Platform.runLater(() -> {
                                    postBtn.setDisable(false);
                                    showToast("Failed to post: " + ex.getMessage(), true);
                                });
                            }
                        });
                    });
                    HBox postActions = new HBox(8);
                    postActions.setAlignment(Pos.CENTER_RIGHT);
                    postActions.getChildren().add(postBtn);
                    postBox.getChildren().addAll(postLabel, groupPostArea, postActions);
                    groupDetailContent.getChildren().add(postBox);

                    // Group posts
                    if (groupPosts.isEmpty()) {
                        Label noPosts = new Label("No posts in this group yet. Be the first!");
                        noPosts.setStyle("-fx-text-fill: #6B6B80; -fx-font-size: 14; -fx-padding: 20;");
                        groupDetailContent.getChildren().add(noPosts);
                    } else {
                        for (int i = 0; i < Math.min(groupPosts.size(), PAGE_SIZE); i++) {
                            VBox postCard = buildPostCard(groupPosts.get(i), myId, false);
                            postCard.setMaxWidth(680);
                            groupDetailContent.getChildren().add(postCard);
                        }
                    }
                });
            } catch (SQLException e) {
                Platform.runLater(() -> showToast("Failed to load group", true));
            }
        });
    }

    @FXML
    private void handleBackToGroups() {
        currentViewingGroup = null;
        switchToGroups();
    }

    // ═══════════════════════════════════════════
    //  PEOPLE — search & browse users
    // ═══════════════════════════════════════════

    /** Load and display all people, optionally filtered by search query */
    private void loadPeople(String query) {
        if (peopleContainer == null) return;
        int myId = SessionManager.getInstance().getCurrentUser().getId();
        AppThreadPool.io(() -> {
            try {
                // Get fresh friend IDs for button state
                Set<Integer> friendIds = serviceFollow.getFriendIds(myId);
                Set<Integer> followedIds = serviceFollow.getFollowedIds(myId);
                List<User> allUsers = serviceUser.recuperer();
                for (User u : allUsers) userCache.put(u.getId(), u);
                String q = query == null ? "" : query.trim().toLowerCase();
                List<User> filtered = allUsers.stream()
                    .filter(u -> u.getId() != myId)
                    .filter(u -> q.isEmpty()
                        || (u.getFullName() != null && u.getFullName().toLowerCase().contains(q))
                        || (u.getBio() != null && u.getBio().toLowerCase().contains(q))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)))
                    .sorted((a, b) -> {
                        boolean af = friendIds.contains(a.getId()), bf = friendIds.contains(b.getId());
                        if (af != bf) return af ? -1 : 1;
                        return String.valueOf(a.getFullName()).compareToIgnoreCase(String.valueOf(b.getFullName()));
                    })
                    .collect(Collectors.toList());
                Platform.runLater(() -> {
                    peopleContainer.getChildren().clear();
                    if (filtered.isEmpty()) {
                        Label empty = new Label("No people found" + (q.isEmpty() ? "" : " for \"" + q + "\""));
                        empty.setStyle("-fx-text-fill: #888; -fx-font-size: 14; -fx-padding: 30 0;");
                        peopleContainer.getChildren().add(empty);
                    } else {
                        Label countLabel = new Label(filtered.size() + " people" + (q.isEmpty() ? "" : " matching \"" + q + "\""));
                        countLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 12; -fx-padding: 0 0 8 0;");
                        peopleContainer.getChildren().add(countLabel);
                        for (User u : filtered) {
                            peopleContainer.getChildren().add(buildPersonCard(u, myId, friendIds, followedIds));
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("Failed to load people", true));
            }
        });
    }

    /** Build a card for a person in the People tab */
    private HBox buildPersonCard(User user, int myId, Set<Integer> friendIds, Set<Integer> followedIds) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setMinHeight(70);
        card.setMaxWidth(620);
        card.setStyle("-fx-background-color: #232228; -fx-background-radius: 12; -fx-cursor: hand; "
            + "-fx-border-color: #333; -fx-border-radius: 12; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 6, 0, 0, 2);");
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #2A2930; -fx-background-radius: 12; -fx-cursor: hand; "
            + "-fx-border-color: #2C666E; -fx-border-radius: 12; "
            + "-fx-effect: dropshadow(gaussian, rgba(44,102,110,0.3), 8, 0, 0, 2);"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: #232228; -fx-background-radius: 12; -fx-cursor: hand; "
            + "-fx-border-color: #333; -fx-border-radius: 12; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 6, 0, 0, 2);"));

        // Avatar
        Circle avatar = new Circle(28);
        avatar.setFill(Color.web("#3A3945"));
        avatar.setStroke(Color.web("#2C666E"));
        avatar.setStrokeWidth(2);
        if (user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
            try {
                File f = new File(user.getAvatarPath());
                if (f.exists()) {
                    Image img = new Image(f.toURI().toString(), 56, 56, true, true);
                    avatar.setFill(new ImagePattern(img));
                }
            } catch (Exception ignored) {}
        }

        // Info column
        VBox info = new VBox(2);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(user.getFullName() != null ? user.getFullName() : "User #" + user.getId());
        name.setStyle("-fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold;");
        info.getChildren().add(name);
        if (user.getBio() != null && !user.getBio().isEmpty()) {
            Label bio = new Label(user.getBio());
            bio.setStyle("-fx-text-fill: #999; -fx-font-size: 12;");
            bio.setWrapText(true);
            bio.setMaxWidth(400);
            info.getChildren().add(bio);
        }
        String subtitle = user.getEmail() != null ? user.getEmail() : "";
        if (!subtitle.isEmpty()) {
            Label emailLbl = new Label(subtitle);
            emailLbl.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
            info.getChildren().add(emailLbl);
        }

        // Relationship badge
        boolean isFriend = friendIds.contains(user.getId());
        boolean isFollowed = followedIds.contains(user.getId());

        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        if (isFriend) {
            Label badge = new Label("✓ Friends");
            badge.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 12; -fx-font-weight: bold; -fx-padding: 4 12; "
                + "-fx-background-color: rgba(44,102,110,0.15); -fx-background-radius: 14;");
            Button msgBtn = new Button("💬");
            msgBtn.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 13; -fx-padding: 4 10; "
                + "-fx-background-color: rgba(44,102,110,0.15); -fx-background-radius: 14; -fx-cursor: hand;");
            msgBtn.setOnAction(ev -> openDirectMessage(user.getId(), user.getFullName()));
            buttons.getChildren().addAll(badge, msgBtn);
        } else {
            // Check pending status
            AppThreadPool.io(() -> {
                try {
                    String relStatus = serviceFollow.getRelationshipStatus(myId, user.getId());
                    String reverseStatus = serviceFollow.getRelationshipStatus(user.getId(), myId);
                    Platform.runLater(() -> {
                        if (UserFollow.STATUS_PENDING.equals(relStatus)) {
                            Label pending = new Label("⏳ Request Sent");
                            pending.setStyle("-fx-text-fill: #F5A623; -fx-font-size: 12; -fx-padding: 4 12; "
                                + "-fx-background-color: rgba(245,166,35,0.15); -fx-background-radius: 14;");
                            buttons.getChildren().add(pending);
                        } else if (UserFollow.STATUS_PENDING.equals(reverseStatus)) {
                            Button accept = new Button("✓ Accept");
                            accept.setStyle("-fx-text-fill: white; -fx-font-size: 11; -fx-padding: 4 12; "
                                + "-fx-background-color: #2C666E; -fx-background-radius: 14; -fx-cursor: hand;");
                            accept.setOnAction(ev -> {
                                AppThreadPool.io(() -> {
                                    try {
                                        serviceFollow.acceptFriendRequest(user.getId(), myId);
                                        User currentMe = SessionManager.getInstance().getCurrentUser();
                                        if (currentMe != null) serviceNotif.notifyFriendAccepted(user.getId(), currentMe.getFullName(), myId);
                                        Platform.runLater(() -> loadPeople(peopleSearchField != null ? peopleSearchField.getText() : ""));
                                    } catch (Exception ex) { Platform.runLater(() -> showToast("Error", true)); }
                                });
                            });
                            Button decline = new Button("✕");
                            decline.setStyle("-fx-text-fill: #FF5555; -fx-font-size: 11; -fx-padding: 4 8; "
                                + "-fx-background-color: rgba(255,85,85,0.15); -fx-background-radius: 14; -fx-cursor: hand;");
                            decline.setOnAction(ev -> {
                                AppThreadPool.io(() -> {
                                    try {
                                        serviceFollow.rejectFriendRequest(user.getId(), myId);
                                        Platform.runLater(() -> loadPeople(peopleSearchField != null ? peopleSearchField.getText() : ""));
                                    } catch (Exception ex) { Platform.runLater(() -> showToast("Error", true)); }
                                });
                            });
                            buttons.getChildren().addAll(accept, decline);
                        } else {
                            Button addFriend = new Button("👥 Add Friend");
                            addFriend.setStyle("-fx-text-fill: white; -fx-font-size: 11; -fx-padding: 4 12; "
                                + "-fx-background-color: #2C666E; -fx-background-radius: 14; -fx-cursor: hand;");
                            addFriend.setOnAction(ev -> {
                                AppThreadPool.io(() -> {
                                    try {
                                        serviceFollow.sendFriendRequest(myId, user.getId());
                                        User currentMe = SessionManager.getInstance().getCurrentUser();
                                        if (currentMe != null) serviceNotif.notifyFriendRequest(user.getId(), currentMe.getFullName(), myId);
                                        Platform.runLater(() -> loadPeople(peopleSearchField != null ? peopleSearchField.getText() : ""));
                                    } catch (Exception ex) { Platform.runLater(() -> showToast("Error: " + ex.getMessage(), true)); }
                                });
                            });
                            buttons.getChildren().add(addFriend);
                            if (!isFollowed) {
                                Button followBtn = new Button("+ Follow");
                                followBtn.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 11; -fx-padding: 4 12; "
                                    + "-fx-background-color: transparent; -fx-border-color: #2C666E; -fx-border-radius: 14; "
                                    + "-fx-background-radius: 14; -fx-cursor: hand;");
                                followBtn.setOnAction(ev -> {
                                    AppThreadPool.io(() -> {
                                        try {
                                            serviceFollow.follow(myId, user.getId());
                                            Platform.runLater(() -> loadPeople(peopleSearchField != null ? peopleSearchField.getText() : ""));
                                        } catch (Exception ex) { Platform.runLater(() -> showToast("Error", true)); }
                                    });
                                });
                                buttons.getChildren().add(followBtn);
                            }
                        }
                    });
                } catch (Exception ignored) {}
            });
        }

        // Click card to view profile
        card.setOnMouseClicked(e -> showUserProfile(user.getId()));

        card.getChildren().addAll(avatar, info, buttons);
        return card;
    }

    // ═══════════════════════════════════════════
    //  PROFILE — user posts, followers/following
    // ═══════════════════════════════════════════

    /** Show another user's profile (from clicking their name on a post) */
    private void showUserProfile(int userId) {
        User user = userCache.get(userId);
        if (user == null) {
            // User not in cache — try fetching
            AppThreadPool.io(() -> {
                try {
                    List<User> all = serviceUser.recuperer();
                    for (User u : all) userCache.put(u.getId(), u);
                    User fetched = userCache.get(userId);
                    if (fetched != null) Platform.runLater(() -> showUserProfile(userId));
                } catch (Exception ignored) {}
            });
            return;
        }
        viewingProfileUser = user;
        activeTab = "profile";
        setTabActive(null); // no sidebar button
        hideAllPanes();
        if (profilePane != null) { profilePane.setVisible(true); profilePane.setManaged(true); }
        if (animNewPostBtn != null) { animNewPostBtn.setVisible(false); animNewPostBtn.setManaged(false); }
        loadProfile(user);
    }

    private void loadProfile(User user) {
        if (profileContainer == null || user == null) return;
        User me = SessionManager.getInstance().getCurrentUser();
        int myId = me != null ? me.getId() : 0;
        int userId = user.getId();
        boolean isOwnProfile = myId == userId;

        AppThreadPool.io(() -> {
            try {
                int followerCount = serviceFollow.getFollowerCount(userId);
                int followingCount = serviceFollow.getFollowingCount(userId);
                int friendCount = serviceFollow.getFriendCount(userId);
                boolean isFollowing = myId > 0 && !isOwnProfile && serviceFollow.isFollowing(myId, userId);
                String relStatus = myId > 0 && !isOwnProfile ? serviceFollow.getRelationshipStatus(myId, userId) : null;
                String reverseStatus = myId > 0 && !isOwnProfile ? serviceFollow.getRelationshipStatus(userId, myId) : null;
                List<UserFollow> pendingRequests = isOwnProfile ? serviceFollow.getPendingRequests(myId) : Collections.emptyList();

                List<Post> allUserPosts = filterHumanPosts(servicePost.recuperer());
                List<Post> userPosts = allUserPosts.stream()
                        .filter(p -> p.getAuthorId() == userId)
                        .collect(Collectors.toList());
                prefetchReactions(userPosts.subList(0, Math.min(userPosts.size(), PAGE_SIZE)));

                Platform.runLater(() -> {
                    profileContainer.getChildren().clear();

                    // ── Profile Card (no cover photo) ──
                    VBox profileCard = new VBox(12);
                    profileCard.getStyleClass().add("community-card");
                    profileCard.setMaxWidth(680);
                    profileCard.setAlignment(Pos.CENTER);
                    profileCard.setPadding(new Insets(24, 24, 20, 24));

                    // Avatar
                    StackPane profileAvatar = createAvatar(user, 90);
                    profileAvatar.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 2);");

                    Label nameLabel = new Label(user.getFirstName() + " " + user.getLastName());
                    nameLabel.getStyleClass().add("community-author-name");
                    nameLabel.setStyle("-fx-font-size: 22;");

                    Label roleLabel = new Label(user.getRole() != null ? user.getRole() : "Member");
                    roleLabel.getStyleClass().add("community-time-label");
                    roleLabel.setStyle("-fx-font-size: 13;");

                    // Bio
                    String bioText = user.getBio() != null && !user.getBio().isEmpty() ? user.getBio() : "No bio yet.";
                    Label bioLabel = new Label(bioText);
                    bioLabel.setWrapText(true);
                    bioLabel.setMaxWidth(500);
                    bioLabel.getStyleClass().add("community-time-label");
                    bioLabel.setStyle("-fx-font-size: 13; -fx-padding: 4 0;");
                    bioLabel.setAlignment(Pos.CENTER);

                    // Stats row — clickable to show popup
                    final int fFollowerCount = followerCount;
                    final int fFollowingCount = followingCount;
                    final int fFriendCount = friendCount;
                    HBox stats = new HBox(24);
                    stats.setAlignment(Pos.CENTER);
                    stats.setPadding(new Insets(10, 0, 6, 0));
                    VBox postsStatBox = buildStatBox(String.valueOf(userPosts.size()), "Posts");
                    VBox friendsStatBox = buildStatBox(String.valueOf(friendCount), "Friends");
                    friendsStatBox.setStyle("-fx-cursor: hand;");
                    friendsStatBox.setOnMouseClicked(e -> showPeoplePopup("Friends", userId, "friends"));
                    VBox followerStatBox = buildStatBox(String.valueOf(followerCount), "Followers");
                    followerStatBox.setStyle("-fx-cursor: hand;");
                    followerStatBox.setOnMouseClicked(e -> showPeoplePopup("Followers", userId, "followers"));
                    VBox followingStatBox = buildStatBox(String.valueOf(followingCount), "Following");
                    followingStatBox.setStyle("-fx-cursor: hand;");
                    followingStatBox.setOnMouseClicked(e -> showPeoplePopup("Following", userId, "following"));
                    stats.getChildren().addAll(postsStatBox, friendsStatBox, followerStatBox, followingStatBox);

                    profileCard.getChildren().addAll(profileAvatar, nameLabel, roleLabel, bioLabel, stats);

                    // ── Action buttons ──
                    if (isOwnProfile) {
                        // Edit Profile button
                        Button editProfileBtn = new Button("✏️ Edit Profile");
                        editProfileBtn.getStyleClass().add("btn-secondary");
                        editProfileBtn.setStyle("-fx-font-size: 13; -fx-padding: 8 24;");
                        editProfileBtn.setOnAction(e -> showEditProfileDialog(user));
                        profileCard.getChildren().add(editProfileBtn);
                    } else if (myId > 0) {
                        HBox actionRow = new HBox(12);
                        actionRow.setAlignment(Pos.CENTER);

                        // Friend request / follow button logic
                        boolean areFriends = serviceFollow.areFriends(myId, userId);
                        if (areFriends) {
                            Button friendBtn = new Button("✓ Friends");
                            friendBtn.getStyleClass().addAll("community-follow-btn", "community-follow-btn-active");
                            friendBtn.setStyle("-fx-font-size: 13; -fx-padding: 8 24;");
                            friendBtn.setOnAction(e -> {
                                AppThreadPool.io(() -> {
                                    try {
                                        serviceFollow.unfollow(myId, userId);
                                        utils.InMemoryCache.evictByPrefix("follows:");
                                        Platform.runLater(() -> loadProfile(user));
                                    } catch (SQLException ex) {
                                        Platform.runLater(() -> showToast("Action failed", true));
                                    }
                                });
                            });
                            actionRow.getChildren().add(friendBtn);

                            // Message button — opens DM chat with this friend
                            Button msgBtn = new Button("💬 Message");
                            msgBtn.getStyleClass().add("community-follow-btn");
                            msgBtn.setStyle("-fx-font-size: 13; -fx-padding: 8 20;");
                            msgBtn.setOnAction(e -> openDirectMessage(userId, user.getFullName()));
                            actionRow.getChildren().add(msgBtn);
                        } else if (UserFollow.STATUS_PENDING.equals(relStatus)) {
                            Button pendingBtn = new Button("⏳ Request Sent");
                            pendingBtn.getStyleClass().add("community-follow-btn");
                            pendingBtn.setStyle("-fx-font-size: 13; -fx-padding: 8 24; -fx-opacity: 0.7;");
                            pendingBtn.setOnAction(e -> {
                                // Cancel request
                                AppThreadPool.io(() -> {
                                    try {
                                        serviceFollow.rejectFriendRequest(myId, userId);
                                        utils.InMemoryCache.evictByPrefix("follows:");
                                        Platform.runLater(() -> loadProfile(user));
                                    } catch (SQLException ex) {
                                        Platform.runLater(() -> showToast("Action failed", true));
                                    }
                                });
                            });
                            actionRow.getChildren().add(pendingBtn);
                        } else if (UserFollow.STATUS_PENDING.equals(reverseStatus)) {
                            // They sent us a request
                            Button acceptBtn = new Button("✓ Accept");
                            acceptBtn.getStyleClass().add("btn-primary");
                            acceptBtn.setStyle("-fx-font-size: 13; -fx-padding: 8 20;");
                            acceptBtn.setOnAction(e -> {
                                AppThreadPool.io(() -> {
                                    try {
                                        serviceFollow.acceptFriendRequest(userId, myId);
                                        // Also follow them back
                                        serviceFollow.follow(myId, userId);
                                        utils.InMemoryCache.evictByPrefix("follows:");
                                        User currentMe = SessionManager.getInstance().getCurrentUser();
                                        if (currentMe != null) serviceNotif.notifyFriendAccepted(userId, currentMe.getFullName(), myId);
                                        Platform.runLater(() -> loadProfile(user));
                                    } catch (SQLException ex) {
                                        Platform.runLater(() -> showToast("Action failed", true));
                                    }
                                });
                            });
                            Button declineBtn = new Button("✕ Decline");
                            declineBtn.getStyleClass().add("btn-secondary");
                            declineBtn.setStyle("-fx-font-size: 13; -fx-padding: 8 20;");
                            declineBtn.setOnAction(e -> {
                                AppThreadPool.io(() -> {
                                    try {
                                        serviceFollow.rejectFriendRequest(userId, myId);
                                        utils.InMemoryCache.evictByPrefix("follows:");
                                        Platform.runLater(() -> loadProfile(user));
                                    } catch (SQLException ex) {
                                        Platform.runLater(() -> showToast("Action failed", true));
                                    }
                                });
                            });
                            actionRow.getChildren().addAll(acceptBtn, declineBtn);
                        } else {
                            // No relationship — show Add Friend + Follow
                            Button addFriendBtn = new Button("👥 Add Friend");
                            addFriendBtn.getStyleClass().add("btn-primary");
                            addFriendBtn.setStyle("-fx-font-size: 13; -fx-padding: 8 20;");
                            addFriendBtn.setOnAction(e -> {
                                AppThreadPool.io(() -> {
                                    try {
                                        serviceFollow.sendFriendRequest(myId, userId);
                                        utils.InMemoryCache.evictByPrefix("follows:");
                                        User currentMe = SessionManager.getInstance().getCurrentUser();
                                        if (currentMe != null) serviceNotif.notifyFriendRequest(userId, currentMe.getFullName(), myId);
                                        Platform.runLater(() -> loadProfile(user));
                                    } catch (SQLException ex) {
                                        Platform.runLater(() -> showToast("Action failed", true));
                                    }
                                });
                            });

                            Button followBtn = new Button(isFollowing ? "✓ Following" : "+ Follow");
                            followBtn.getStyleClass().add("community-follow-btn");
                            if (isFollowing) followBtn.getStyleClass().add("community-follow-btn-active");
                            followBtn.setStyle("-fx-font-size: 13; -fx-padding: 8 20;");
                            followBtn.setOnAction(e -> {
                                AppThreadPool.io(() -> {
                                    try {
                                        if (serviceFollow.isFollowing(myId, userId))
                                            serviceFollow.unfollow(myId, userId);
                                        else
                                            serviceFollow.follow(myId, userId);
                                        utils.InMemoryCache.evictByPrefix("follows:");
                                        Platform.runLater(() -> loadProfile(user));
                                    } catch (SQLException ex) {
                                        Platform.runLater(() -> showToast("Follow failed", true));
                                    }
                                });
                            });
                            actionRow.getChildren().addAll(addFriendBtn, followBtn);
                        }
                        profileCard.getChildren().add(actionRow);
                    }

                    // Add profile card directly (no cover)
                    profileCard.setMaxWidth(680);
                    profileContainer.getChildren().add(profileCard);

                    // ── Pending Friend Requests (own profile) ──
                    if (isOwnProfile && !pendingRequests.isEmpty()) {
                        VBox requestsCard = new VBox(8);
                        requestsCard.getStyleClass().add("community-card");
                        requestsCard.setPadding(new Insets(14, 20, 14, 20));
                        requestsCard.setMaxWidth(680);

                        Label reqTitle = new Label("👥 Friend Requests (" + pendingRequests.size() + ")");
                        reqTitle.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 15; -fx-font-weight: bold;");
                        requestsCard.getChildren().add(reqTitle);

                        for (UserFollow req : pendingRequests) {
                            User sender = userCache.get(req.getFollowerId());
                            if (sender == null) continue;

                            HBox reqRow = new HBox(10);
                            reqRow.setAlignment(Pos.CENTER_LEFT);
                            reqRow.setPadding(new Insets(6, 0, 6, 0));

                            StackPane reqAvatar = createAvatar(sender, 40);
                            Label reqName = new Label(sender.getFirstName() + " " + sender.getLastName());
                            reqName.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 13; -fx-font-weight: bold;");
                            reqName.setCursor(javafx.scene.Cursor.HAND);
                            reqName.setOnMouseClicked(ev -> showUserProfile(sender.getId()));
                            Region spacer = new Region();
                            HBox.setHgrow(spacer, Priority.ALWAYS);

                            Button acceptBtn = new Button("✓ Accept");
                            acceptBtn.getStyleClass().add("btn-primary");
                            acceptBtn.setStyle("-fx-font-size: 11; -fx-padding: 5 12;");
                            acceptBtn.setOnAction(ev -> {
                                AppThreadPool.io(() -> {
                                    try {
                                        serviceFollow.acceptFriendRequest(sender.getId(), myId);
                                        serviceFollow.follow(myId, sender.getId());
                                        utils.InMemoryCache.evictByPrefix("follows:");
                                        User currentMe = SessionManager.getInstance().getCurrentUser();
                                        if (currentMe != null) serviceNotif.notifyFriendAccepted(sender.getId(), currentMe.getFullName(), myId);
                                        Platform.runLater(() -> loadProfile(user));
                                    } catch (SQLException ex) {
                                        Platform.runLater(() -> showToast("Accept failed", true));
                                    }
                                });
                            });

                            Button declineBtn = new Button("✕");
                            declineBtn.getStyleClass().add("btn-secondary");
                            declineBtn.setStyle("-fx-font-size: 11; -fx-padding: 5 8;");
                            declineBtn.setOnAction(ev -> {
                                AppThreadPool.io(() -> {
                                    try {
                                        serviceFollow.rejectFriendRequest(sender.getId(), myId);
                                        utils.InMemoryCache.evictByPrefix("follows:");
                                        Platform.runLater(() -> loadProfile(user));
                                    } catch (SQLException ex) {
                                        Platform.runLater(() -> showToast("Decline failed", true));
                                    }
                                });
                            });

                            reqRow.getChildren().addAll(reqAvatar, reqName, spacer, acceptBtn, declineBtn);
                            requestsCard.getChildren().add(reqRow);
                        }
                        profileContainer.getChildren().add(requestsCard);
                    }

                    // ── User's Posts ──
                    if (userPosts.isEmpty()) {
                        Label noPosts = new Label("No posts yet.");
                        noPosts.setStyle("-fx-text-fill: #6B6B80; -fx-font-size: 14; -fx-padding: 20;");
                        profileContainer.getChildren().add(noPosts);
                    } else {
                        Label postsHeader = new Label("Posts");
                        postsHeader.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 16; -fx-font-weight: bold; -fx-padding: 10 0 4 0;");
                        profileContainer.getChildren().add(postsHeader);

                        int limit = Math.min(userPosts.size(), PAGE_SIZE);
                        for (int i = 0; i < limit; i++) {
                            VBox postCard = buildPostCard(userPosts.get(i), myId, false);
                            postCard.setMaxWidth(680);
                            profileContainer.getChildren().add(postCard);
                        }
                    }
                });
            } catch (SQLException e) {
                Platform.runLater(() -> showToast("Failed to load profile: " + e.getMessage(), true));
            }
        });
    }

    /** Dialog for editing own bio. */
    private void showEditProfileDialog(User user) {
        Dialog<String> dialog = new Dialog<>();
        DialogHelper.theme(dialog);
        dialog.setTitle("Edit Profile");
        dialog.initOwner(ownerWindow());

        DialogPane dp = dialog.getDialogPane();
        dp.setStyle("-fx-background-color: #12111A; -fx-border-color: #2A2A3C; -fx-border-radius: 12; -fx-background-radius: 12;");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox form = new VBox(10);
        form.setPadding(new Insets(16));
        form.setMinWidth(400);

        Label title = new Label("Edit Profile");
        title.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 18; -fx-font-weight: bold;");

        TextField bioField = new TextField(user.getBio() != null ? user.getBio() : "");
        bioField.setPromptText("Write a short bio...");
        bioField.setStyle("-fx-background-color: #2A2A3C; -fx-text-fill: #E0E0F0; -fx-prompt-text-fill: #6B6B80; -fx-background-radius: 8; -fx-padding: 10;");

        form.getChildren().addAll(title,
                new Label("Bio:") {{ setStyle("-fx-text-fill: #8888AA;"); }}, bioField);
        dp.setContent(form);

        dialog.setResultConverter(bt -> bt == ButtonType.OK ? bioField.getText().trim() : null);

        dialog.showAndWait().ifPresent(newBio -> {
            user.setBio(newBio);
            AppThreadPool.io(() -> {
                try {
                    serviceUser.modifier(user);
                    Platform.runLater(() -> {
                        showToast("Profile updated!", false);
                        loadProfile(user);
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> showToast("Update failed: " + e.getMessage(), true));
                }
            });
        });
    }

    private VBox buildStatBox(String value, String label) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        Label valLabel = new Label(value);
        valLabel.getStyleClass().add("community-author-name");
        valLabel.setStyle("-fx-font-size: 20;");
        Label lblLabel = new Label(label);
        lblLabel.getStyleClass().add("community-time-label");
        lblLabel.setStyle("-fx-font-size: 12;");
        box.getChildren().addAll(valLabel, lblLabel);
        return box;
    }

    /** Open a DM chat room with the given user, navigating to the Chat module. */
    private void openDirectMessage(int otherUserId, String otherName) {
        User me = SessionManager.getInstance().getCurrentUser();
        if (me == null) return;
        int myId = me.getId();
        AppThreadPool.io(() -> {
            try {
                // DM room name convention: dm_<lower>_<higher>
                int lo = Math.min(myId, otherUserId);
                int hi = Math.max(myId, otherUserId);
                String roomName = "dm_" + lo + "_" + hi;
                ChatRoom room = serviceChatRoom.getOrCreateRoom(roomName);
                if (room != null) {
                    // Ensure both users are members
                    try { serviceChatMember.addMember(room.getId(), myId, "member"); } catch (Exception ignored) {}
                    try { serviceChatMember.addMember(room.getId(), otherUserId, "member"); } catch (Exception ignored) {}
                }
                Platform.runLater(() -> {
                    // Navigate to Chat via DashboardController
                    try {
                        javafx.scene.Scene scene = feedScroll.getScene();
                        if (scene == null) return;
                        // The root is Dashboard's BorderPane; contentArea is its center
                        javafx.scene.Parent root = scene.getRoot();
                        // Look up the DashboardController's btnMessages button and fire it
                        javafx.scene.Node btnMsg = root.lookup("#btnMessages");
                        if (btnMsg instanceof Button) {
                            ((Button) btnMsg).fire();
                        }
                    } catch (Exception ex) {
                        showToast("Could not open chat", true);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("Failed to open chat: " + e.getMessage(), true));
            }
        });
    }

    /** Show a popup dialog listing Friends / Followers / Following */
    private void showPeoplePopup(String title, int userId, String type) {
        AppThreadPool.io(() -> {
            try {
                Set<Integer> ids;
                switch (type) {
                    case "friends":   ids = serviceFollow.getFriendIds(userId); break;
                    case "followers": ids = serviceFollow.getFollowerIds(userId); break;
                    case "following": ids = serviceFollow.getFollowedIds(userId); break;
                    default: return;
                }
                List<User> allUsers = serviceUser.recuperer();
                Map<Integer, User> uMap = new HashMap<>();
                for (User u : allUsers) uMap.put(u.getId(), u);
                List<User> people = new ArrayList<>();
                for (int id : ids) { if (uMap.containsKey(id)) people.add(uMap.get(id)); }
                people.sort((a, b) -> String.valueOf(a.getFullName()).compareToIgnoreCase(String.valueOf(b.getFullName())));

                // Pre-fetch friend IDs to show message button for friends
                int myId = SessionManager.getInstance().getCurrentUser() != null
                        ? SessionManager.getInstance().getCurrentUser().getId() : 0;
                Set<Integer> myFriends = serviceFollow.getFriendIds(myId);

                Platform.runLater(() -> {
                    boolean dk = SessionManager.getInstance().isDarkTheme();
                    // Theme colors
                    String bgColor    = dk ? "#1A1929" : "#FFFFFF";
                    String cardBg     = dk ? "#222136" : "#F7F5F6";
                    String headerClr  = dk ? "#F0EDEE" : "#0D0A0B";
                    String nameClr    = dk ? "#E8E6E9" : "#1A1318";
                    String roleClr    = dk ? "#8B8A94" : "#8A7C7F";
                    String accentClr  = dk ? "#2C666E" : "#613039";
                    String hoverBg    = dk ? "rgba(44,102,110,0.12)" : "rgba(97,48,57,0.07)";
                    String emptyClr   = dk ? "#6B6B80" : "#9A8C8F";
                    String dividerClr = dk ? "#2A293C" : "#E8E0E2";
                    String btnBg      = dk ? "#2C666E" : "#613039";
                    String btnHover   = dk ? "#348891" : "#7A3D49";

                    // Emoji for title
                    String titleIcon = "friends".equals(type) ? "\uD83E\uDD1D" : "followers".equals(type) ? "\uD83D\uDC65" : "\uD83D\uDC64";

                    Dialog<Void> dialog = new Dialog<>();
                    DialogHelper.theme(dialog);
                    dialog.setTitle(title);
                    dialog.setHeaderText(null);

                    DialogPane dp = dialog.getDialogPane();
                    dp.getStylesheets().addAll(feedScroll.getScene().getStylesheets());
                    dp.setStyle("-fx-background-color: " + bgColor + "; -fx-padding: 0; -fx-background-radius: 14; -fx-border-radius: 14;");
                    dp.getStyleClass().add("community-card");
                    dp.setPrefWidth(400);
                    // Auto-size: min for few people, max for many
                    int rowCount = Math.max(people.size(), 1);
                    double dynamicH = Math.min(80 + rowCount * 64, 520);
                    dp.setPrefHeight(dynamicH);
                    dp.setMinHeight(Region.USE_PREF_SIZE);
                    dp.setMaxHeight(Region.USE_PREF_SIZE);

                    VBox content = new VBox(0);
                    content.setStyle("-fx-background-color: " + bgColor + ";");

                    // ── Top header ──
                    HBox headerRow = new HBox(8);
                    headerRow.setAlignment(Pos.CENTER_LEFT);
                    headerRow.setPadding(new Insets(16, 18, 12, 18));
                    Label iconLbl = new Label(titleIcon);
                    iconLbl.setStyle("-fx-font-size: 20;");
                    Label headerLbl = new Label(title);
                    headerLbl.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: " + headerClr + ";");
                    Label countLbl = new Label(String.valueOf(people.size()));
                    countLbl.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: white; " +
                            "-fx-background-color: " + accentClr + "; -fx-background-radius: 12; " +
                            "-fx-padding: 2 8; -fx-min-width: 24; -fx-alignment: center;");
                    Region hSpacer = new Region();
                    HBox.setHgrow(hSpacer, Priority.ALWAYS);
                    headerRow.getChildren().addAll(iconLbl, headerLbl, countLbl, hSpacer);
                    content.getChildren().add(headerRow);

                    // Divider
                    Region divider = new Region();
                    divider.setMinHeight(1); divider.setMaxHeight(1);
                    divider.setStyle("-fx-background-color: " + dividerClr + ";");
                    content.getChildren().add(divider);

                    // ── List area ──
                    VBox listArea = new VBox(2);
                    listArea.setPadding(new Insets(8, 10, 12, 10));

                    if (people.isEmpty()) {
                        VBox emptyBox = new VBox(8);
                        emptyBox.setAlignment(Pos.CENTER);
                        emptyBox.setPadding(new Insets(28, 0, 20, 0));
                        Label emptyIcon = new Label("friends".equals(type) ? "\uD83D\uDE14" : "\uD83D\uDC64");
                        emptyIcon.setStyle("-fx-font-size: 32;");
                        Label emptyText = new Label("No " + title.toLowerCase() + " yet.");
                        emptyText.setStyle("-fx-text-fill: " + emptyClr + "; -fx-font-size: 14;");
                        emptyBox.getChildren().addAll(emptyIcon, emptyText);
                        listArea.getChildren().add(emptyBox);
                    } else {
                        for (User u : people) {
                            HBox row = new HBox(12);
                            row.setAlignment(Pos.CENTER_LEFT);
                            row.setPadding(new Insets(8, 12, 8, 12));
                            String baseStyle = "-fx-background-color: " + cardBg + "; -fx-background-radius: 10; -fx-cursor: hand;";
                            String hoverStyle = "-fx-background-color: " + hoverBg + "; -fx-background-radius: 10; -fx-cursor: hand;";
                            row.setStyle(baseStyle);
                            row.setOnMouseEntered(e -> row.setStyle(hoverStyle));
                            row.setOnMouseExited(e -> row.setStyle(baseStyle));

                            // Avatar
                            StackPane av = createAvatar(u, 40);

                            // Name + role column
                            VBox infoCol = new VBox(1);
                            HBox.setHgrow(infoCol, Priority.ALWAYS);
                            Label nm = new Label(u.getFullName() != null ? u.getFullName() : "User #" + u.getId());
                            nm.setStyle("-fx-font-size: 13.5; -fx-font-weight: bold; -fx-text-fill: " + nameClr + ";");
                            Label roleLbl = new Label(u.getRole() != null ? u.getRole().replace("_", " ") : "");
                            roleLbl.setStyle("-fx-font-size: 11; -fx-text-fill: " + roleClr + ";");
                            infoCol.getChildren().addAll(nm, roleLbl);

                            row.getChildren().addAll(av, infoCol);

                            // Action buttons (right side)
                            HBox actions = new HBox(6);
                            actions.setAlignment(Pos.CENTER_RIGHT);

                            if (u.getId() != myId && myFriends.contains(u.getId())) {
                                Button msgBtn = new Button("\uD83D\uDCAC");
                                msgBtn.setStyle("-fx-font-size: 14; -fx-background-color: " + btnBg + "; " +
                                        "-fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 4 10; -fx-cursor: hand;");
                                msgBtn.setTooltip(new Tooltip("Send message"));
                                msgBtn.setOnMouseEntered(e -> msgBtn.setStyle("-fx-font-size: 14; -fx-background-color: " + btnHover + "; " +
                                        "-fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 4 10; -fx-cursor: hand;"));
                                msgBtn.setOnMouseExited(e -> msgBtn.setStyle("-fx-font-size: 14; -fx-background-color: " + btnBg + "; " +
                                        "-fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 4 10; -fx-cursor: hand;"));
                                msgBtn.setOnAction(ev -> {
                                    dialog.close();
                                    openDirectMessage(u.getId(), u.getFullName());
                                });
                                actions.getChildren().add(msgBtn);
                            }

                            Button viewBtn = new Button("\u279C");
                            viewBtn.setStyle("-fx-font-size: 13; -fx-background-color: transparent; " +
                                    "-fx-text-fill: " + accentClr + "; -fx-background-radius: 8; " +
                                    "-fx-padding: 4 8; -fx-cursor: hand; -fx-border-color: " + accentClr + "; -fx-border-radius: 8; -fx-border-width: 1;");
                            viewBtn.setTooltip(new Tooltip("View profile"));
                            viewBtn.setOnAction(ev -> { dialog.close(); showUserProfile(u.getId()); });
                            actions.getChildren().add(viewBtn);

                            row.getChildren().add(actions);

                            // Clicking anywhere on the row also navigates
                            row.setOnMouseClicked(e -> {
                                if (!(e.getTarget() instanceof Button)) {
                                    dialog.close();
                                    showUserProfile(u.getId());
                                }
                            });

                            listArea.getChildren().add(row);
                        }
                    }

                    ScrollPane sp = new ScrollPane(listArea);
                    sp.setFitToWidth(true);
                    sp.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-background: transparent;");
                    sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    VBox.setVgrow(sp, Priority.ALWAYS);
                    content.getChildren().add(sp);

                    dp.setContent(content);
                    dp.getButtonTypes().add(ButtonType.CLOSE);
                    Node closeBtn = dp.lookupButton(ButtonType.CLOSE);
                    if (closeBtn != null) { closeBtn.setVisible(false); closeBtn.setManaged(false); }

                    dialog.showAndWait();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showToast("Failed to load " + title.toLowerCase(), true));
            }
        });
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
                    DialogHelper.theme(info);
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
