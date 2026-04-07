package utils;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Pure-JavaFX emoji picker that downloads Twemoji PNGs via
 * explicit HTTP calls (not relying on JavaFX's Image URL loader).
 * Images are cached in memory after first download.
 */
public class EmojiPicker {

    private static final String CDN =
            "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/";

    /* thread-pool for downloading – limited to 6 concurrent */
    private static final ExecutorService DL = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "emoji-dl");
        t.setDaemon(true);
        return t;
    });

    /* in-memory image cache: hexcode → Image */
    private static final Map<String, Image> IMG_CACHE = new ConcurrentHashMap<>();
    /* raw bytes cache so Image can be recreated if needed */
    private static final Map<String, byte[]> BYTE_CACHE = new ConcurrentHashMap<>();

    /* ─── Emoji catalogue (hex codepoints) ─── */
    private static final Map<String, String[]> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("1f600 Smileys", new String[]{
                "1f600", "1f603", "1f604", "1f601", "1f606", "1f605", "1f923", "1f602",
                "1f642", "1f60a", "1f607", "1f970", "1f60d", "1f929", "1f618", "1f617",
                "1f61a", "1f619", "1f972", "1f60b", "1f61b", "1f61c", "1f92a", "1f61d",
                "1f911", "1f917", "1f92d", "1f92b", "1f914", "1f910", "1f928", "1f610",
                "1f611", "1f636", "1f60f", "1f612", "1f644", "1f62c", "1f925", "1f60c",
                "1f614", "1f62a", "1f924", "1f634", "1f637", "1f912", "1f915", "1f922",
                "1f92e", "1f927", "1f975", "1f976", "1f974", "1f635", "1f92f", "1f920",
                "1f973", "1f978", "1f60e", "1f913", "1f9d0", "1f615", "1f61f", "1f641",
                "1f62e", "1f62f", "1f632", "1f633", "1f97a", "1f626", "1f627", "1f628",
                "1f630", "1f625", "1f622", "1f62d", "1f631", "1f616", "1f623", "1f61e",
                "1f613", "1f629", "1f62b", "1f971", "1f624", "1f620", "1f621", "1f92c",
                "1f47f", "1f480", "1f4a9"
        });

        CATEGORIES.put("2764 Hearts", new String[]{
                "2764", "1f9e1", "1f49b", "1f49a", "1f499", "1f49c", "1f5a4", "1f90d",
                "1f90e", "1f494", "1f495", "1f49e", "1f493", "1f497", "1f496", "1f498",
                "1f49d", "1f49f", "1f48b", "1f48c"
        });

        CATEGORIES.put("1f44b Gestures", new String[]{
                "1f44b", "1f91a", "270b", "1f596", "1f44c", "1f90c", "1f90f", "270c",
                "1f91e", "1f91f", "1f918", "1f919", "1f448", "1f449", "1f446", "1f595",
                "1f447", "261d", "1f44d", "1f44e", "270a", "1f44a", "1f91b", "1f91c",
                "1f44f", "1f64c", "1f450", "1f932", "1f91d", "1f64f", "1f4aa", "1f9be",
                "1f9b5", "1f9b6", "1f440", "1f442", "1f443", "1f9e0", "1f445", "1f444"
        });

        CATEGORIES.put("1f389 Objects", new String[]{
                "1f389", "1f38a", "1f388", "1f381", "1f3c6", "1f947", "1f948", "1f949",
                "26bd", "1f3c0", "1f3ae", "1f3af", "1f3b2", "1f3ad", "1f3a8", "1f3b5",
                "1f3b6", "1f3b9", "1f3b8", "1f3ba", "1f4f1", "1f4bb", "1f5a5",
                "1f4f7", "1f4f9", "1f3a5", "1f4fa", "1f4fb", "23f0", "1f514", "1f4e2",
                "1f4e3", "1f4a1", "1f526", "1f4da", "1f4d6", "1f4dd", "1f4c8", "1f4ca"
        });

        CATEGORIES.put("1f525 Symbols", new String[]{
                "1f525", "1f4af", "2728", "2b50", "1f31f", "1f4ab", "26a1", "1f308",
                "2600", "1f319", "1f30d", "1f4a7", "1f30a", "1f340", "1f338", "1f33a",
                "1f33b", "1f339", "1f337", "1f335", "1f355", "1f354", "2615", "1f37b",
                "1f942", "1f370", "1f382", "1f369", "2705", "274c", "26a0", "2753",
                "2757", "1f4ac", "1f4ad", "267b", "1f504", "1f197", "1f195", "1f199"
        });

        CATEGORIES.put("1f436 Animals", new String[]{
                "1f436", "1f431", "1f42d", "1f439", "1f430", "1f98a", "1f43b", "1f43c",
                "1f428", "1f42f", "1f981", "1f42e", "1f437", "1f438", "1f435", "1f648",
                "1f649", "1f64a", "1f412", "1f414", "1f427", "1f426", "1f424", "1f423",
                "1f986", "1f985", "1f989", "1f987", "1f43a", "1f417", "1f434", "1f984",
                "1f41d", "1f41b", "1f98b", "1f40c", "1f41e", "1f41c", "1f422", "1f40d"
        });

        CATEGORIES.put("1f468 People", new String[]{
                "1f476", "1f467", "1f9d2", "1f466", "1f469", "1f9d1", "1f468", "1f471",
                "1f9d4", "1f475", "1f474", "1f478", "1f934", "1f9b8", "1f9b9", "1f936",
                "1f385", "1f9de", "1f9dc", "1f9da", "1f47c", "1f47b", "1f47d", "1f916",
                "1f47e", "1f608", "1f479", "1f47a", "1f921", "1f48d", "1f451", "1f393",
                "1f9e2", "1f453", "1f97d", "1f454", "1f457", "1f460", "1f45f", "1f4bc"
        });

        CATEGORIES.put("1f697 Travel", new String[]{
                "1f697", "1f695", "1f68c", "1f68e", "1f3ce", "1f691", "1f692",
                "1f693", "1f69a", "1f682", "2708", "1f680", "1f6f8", "1f6a2",
                "26f5", "1f3e0", "1f3e2", "1f3eb", "1f3e5", "1f3ea", "26ea", "1f5fd",
                "1f5fc", "1f3f0", "1f3a2", "1f3a1", "1f3a0", "26f2", "1f305", "1f304",
                "1f303", "1f306", "1f307", "1f309", "1f30c", "1f301", "1f3d6",
                "1f3dd", "26f0", "1f3d4"
        });
    }

    /** Hex codepoint → Unicode string. */
    static String cpToUnicode(String cp) {
        StringBuilder sb = new StringBuilder();
        for (String part : cp.split("-")) {
            sb.appendCodePoint(Integer.parseInt(part, 16));
        }
        return sb.toString();
    }

    /** Unicode string → hex codepoint notation (e.g. "1f600" or "1f1e6-1f1e8"). */
    static String unicodeToHex(String unicode) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < unicode.length(); ) {
            int cp = unicode.codePointAt(i);
            if (sb.length() > 0) sb.append('-');
            sb.append(Integer.toHexString(cp));
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /* ─── Download helpers ─── */

    /** Download PNG bytes from CDN (blocking). Returns null on failure. */
    private static byte[] downloadPng(String hex) {
        byte[] cached = BYTE_CACHE.get(hex);
        if (cached != null) return cached;
        try {
            URL url = new URL(CDN + hex + ".png");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "SynergyGig/1.0");
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code == 200) {
                byte[] bytes = c.getInputStream().readAllBytes();
                BYTE_CACHE.put(hex, bytes);
                return bytes;
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Get (or create) a JavaFX Image from cache. */
    private static Image getImage(String hex, double w, double h) {
        String key = hex + "_" + (int) w;
        Image img = IMG_CACHE.get(key);
        if (img != null) return img;
        byte[] bytes = BYTE_CACHE.get(hex);
        if (bytes != null) {
            img = new Image(new ByteArrayInputStream(bytes), w, h, true, true);
            IMG_CACHE.put(key, img);
        }
        return img;
    }

    /** Get an image for the given emoji from cache. Public for use in chat rendering. */
    public static Image getCachedEmojiImage(String hex, double size) {
        return getImage(hex, size, size);
    }

    /** Pre-download PNG and update an ImageView asynchronously. */
    private static void loadInto(String hex, ImageView iv, double w, double h) {
        Image cached = getImage(hex, w, h);
        if (cached != null) {
            iv.setImage(cached);
            return;
        }
        DL.submit(() -> {
            byte[] bytes = downloadPng(hex);
            if (bytes != null) {
                Image img = new Image(new ByteArrayInputStream(bytes), w, h, true, true);
                String key = hex + "_" + (int) w;
                IMG_CACHE.put(key, img);
                Platform.runLater(() -> iv.setImage(img));
            }
        });
    }

    /* ─── Check if a character is an emoji that we can render as image ─── */

    /** Lookup set of all known hex codes. */
    private static final Set<String> KNOWN_HEX = new HashSet<>();
    static {
        for (String[] arr : CATEGORIES.values())
            Collections.addAll(KNOWN_HEX, arr);
    }

    /** Test whether the given code-point (hex) is a known emoji we have. */
    public static boolean isKnownEmoji(String hex) {
        return KNOWN_HEX.contains(hex);
    }

    /** Get the Twemoji CDN base URL (for external use). */
    public static String getCdnBase() { return CDN; }

    /* ═══════════════════════════════════════════
     *  UI – Popup
     * ═══════════════════════════════════════════ */

    private static Popup popup;
    private static Consumer<String> currentCallback;
    private static FlowPane grid;
    private static HBox tabBar;
    private static String activeCatKey;
    private static final List<String> catKeys = new ArrayList<>();

    public static void show(Window owner, double anchorX, double anchorY, Consumer<String> onSelect) {
        currentCallback = onSelect;
        if (popup != null && popup.isShowing()) popup.hide();

        if (popup == null) {
            catKeys.addAll(CATEGORIES.keySet());
            buildUI();
        }

        activeCatKey = catKeys.get(0);
        showCategory(activeCatKey);
        refreshTabs();
        popup.show(owner, anchorX, anchorY);
    }

    public static void hide() {
        if (popup != null && popup.isShowing()) popup.hide();
    }

    /* ─── Build UI ─── */

    private static void buildUI() {
        TextField search = new TextField();
        search.setPromptText("Search emoji...");
        search.setStyle(
                "-fx-background-color: #12121f; -fx-text-fill: #e0e0e0; "
              + "-fx-prompt-text-fill: #555555; -fx-border-color: #2a2a3e; "
              + "-fx-border-radius: 8; -fx-background-radius: 8; "
              + "-fx-padding: 7 10; -fx-font-size: 12px;");
        search.setFocusTraversable(false);
        search.textProperty().addListener((obs, o, q) -> doSearch(q));

        tabBar = new HBox(2);
        tabBar.setPadding(new Insets(0, 0, 6, 0));
        tabBar.setStyle("-fx-border-color: transparent transparent #2a2a3e transparent; -fx-border-width: 0 0 1 0;");
        buildTabs();

        grid = new FlowPane();
        grid.setHgap(2);
        grid.setVgap(2);
        grid.setPadding(new Insets(4));
        grid.setStyle("-fx-background-color: transparent;");

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPrefHeight(340);
        scroll.setStyle("-fx-background-color: #1a1a2e; -fx-background: #1a1a2e; -fx-border-color: transparent;");

        VBox root = new VBox(6, search, tabBar, scroll);
        root.setPadding(new Insets(8));
        root.setPrefSize(392, 430);
        root.setStyle(
                "-fx-background-color: #1a1a2e; -fx-border-color: #2a2a3e; "
              + "-fx-border-width: 1; -fx-border-radius: 12; -fx-background-radius: 12; "
              + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 20, 0, 0, 5);");

        popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(root);

        /* Pre-download first category in background so it's ready fast */
        DL.submit(() -> {
            for (String cp : CATEGORIES.get(catKeys.get(0))) downloadPng(cp);
            /* also preload tab icons */
            for (String key : catKeys) {
                int sp = key.indexOf(' ');
                downloadPng(sp > 0 ? key.substring(0, sp) : key);
            }
        });
    }

    /* ─── Tabs ─── */

    private static void buildTabs() {
        tabBar.getChildren().clear();
        for (String key : catKeys) {
            int sp = key.indexOf(' ');
            String cp = sp > 0 ? key.substring(0, sp) : key;
            String label = sp > 0 ? key.substring(sp + 1) : key;

            ImageView iv = new ImageView();
            iv.setFitWidth(22);
            iv.setFitHeight(22);
            iv.setSmooth(true);
            loadInto(cp, iv, 22, 22);

            Button tab = new Button();
            tab.setGraphic(iv);
            tab.setTooltip(new Tooltip(label));
            tab.setUserData(key);
            tab.setMinSize(36, 34);
            tab.setPrefSize(36, 34);
            tab.setStyle(tabCss(false));

            tab.setOnMouseEntered(e -> {
                if (!key.equals(activeCatKey)) tab.setStyle(tabHoverCss());
            });
            tab.setOnMouseExited(e -> tab.setStyle(tabCss(key.equals(activeCatKey))));

            tab.setOnAction(e -> {
                activeCatKey = key;
                showCategory(key);
                refreshTabs();
            });
            tabBar.getChildren().add(tab);
        }
    }

    private static void refreshTabs() {
        for (var node : tabBar.getChildren()) {
            if (node instanceof Button btn) {
                btn.setStyle(tabCss(activeCatKey.equals(btn.getUserData())));
            }
        }
    }

    private static String tabCss(boolean active) {
        return "-fx-background-color: " + (active ? "rgba(44,102,110,0.35)" : "transparent")
             + "; -fx-border-color: transparent; -fx-padding: 4 6; "
             + "-fx-cursor: hand; -fx-background-radius: 8;";
    }

    private static String tabHoverCss() {
        return "-fx-background-color: #2a2a3e; -fx-border-color: transparent; "
             + "-fx-padding: 4 6; -fx-cursor: hand; -fx-background-radius: 8;";
    }

    /* ─── Grid ─── */

    private static void showCategory(String catKey) {
        grid.getChildren().clear();
        String[] emojis = CATEGORIES.get(catKey);
        if (emojis == null) return;
        for (String cp : emojis) {
            grid.getChildren().add(emojiButton(cp));
        }
    }

    private static Button emojiButton(String cp) {
        ImageView iv = new ImageView();
        iv.setFitWidth(30);
        iv.setFitHeight(30);
        iv.setSmooth(true);
        loadInto(cp, iv, 30, 30);

        Button btn = new Button();
        btn.setGraphic(iv);
        btn.setPrefSize(42, 42);
        btn.setMinSize(42, 42);
        btn.setMaxSize(42, 42);
        btn.setStyle(emojiCss());

        btn.setOnMouseEntered(e -> btn.setStyle(emojiHoverCss()));
        btn.setOnMouseExited(e -> btn.setStyle(emojiCss()));

        btn.setOnAction(e -> {
            if (currentCallback != null) currentCallback.accept(cpToUnicode(cp));
        });
        return btn;
    }

    private static String emojiCss() {
        return "-fx-background-color: transparent; -fx-border-color: transparent; "
             + "-fx-padding: 4; -fx-cursor: hand; -fx-background-radius: 8;";
    }

    private static String emojiHoverCss() {
        return "-fx-background-color: #2a2a3e; -fx-border-color: transparent; "
             + "-fx-padding: 4; -fx-cursor: hand; -fx-background-radius: 8; "
             + "-fx-scale-x: 1.18; -fx-scale-y: 1.18;";
    }

    /* ─── Search ─── */

    private static void doSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            showCategory(activeCatKey);
            return;
        }
        String q = query.trim().toLowerCase();
        grid.getChildren().clear();
        boolean hit = false;
        for (var entry : CATEGORIES.entrySet()) {
            if (entry.getKey().toLowerCase().contains(q)) {
                hit = true;
                for (String cp : entry.getValue()) grid.getChildren().add(emojiButton(cp));
            }
        }
        if (!hit) {
            for (String[] arr : CATEGORIES.values())
                for (String cp : arr) grid.getChildren().add(emojiButton(cp));
        }
    }
}
