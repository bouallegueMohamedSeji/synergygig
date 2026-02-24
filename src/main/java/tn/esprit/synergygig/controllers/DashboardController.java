package tn.esprit.synergygig.controllers;
import javafx.scene.control.ListView;
import tn.esprit.synergygig.services.NewsService;

import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.services.DashboardService;
import javafx.scene.shape.Circle;
import javafx.animation.FadeTransition;
import javafx.animation.Animation;
import javafx.util.Duration;
import tn.esprit.synergygig.entities.NewsArticles;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.ListCell;
import java.awt.Desktop;
import java.net.URI;
import tn.esprit.synergygig.services.WeatherService;




public class DashboardController {

    // ===== LABELS =====
    @FXML private Label totalOffers;
    @FXML private Label publishedOffers;
    @FXML private Label inProgressOffers;
    @FXML private Label completedOffers;
    @FXML private Label cancelledOffers;

    // ===== CARDS =====
    @FXML private VBox cardTotal;
    @FXML private VBox cardPublished;
    @FXML private VBox cardProgress;
    @FXML private VBox cardCompleted;
    @FXML private VBox cardCancelled;

    // ===== CHARTS =====
    @FXML private PieChart offerPieChart;
    @FXML private BarChart<String, Number> offerBarChart;
    @FXML
    private Pane animatedBackground;
    @FXML private ProgressBar healthProgress;
    @FXML private Label healthPercent;
    @FXML private Label healthBadge;
    @FXML
    private ListView<NewsArticles> newsListView;
    // ===== WEATHER =====
    @FXML private VBox weatherCard;
    @FXML private Label weatherCity;
    @FXML private Label weatherTemp;
    @FXML private Label weatherDesc;
    @FXML private ImageView weatherIcon;





    private final DashboardService service = new DashboardService();
    WeatherService weatherService = new WeatherService();

    @FXML
    public void initialize() {

        loadNumbers();
        animateCards();
        loadCharts();
        createStars();
        loadPlatformHealth();
        loadNews();
        loadWeather();
    }

    // ======================
    // NUMBERS
    // ======================
    private void loadNumbers() {
        try {
            animateNumber(totalOffers, service.countAllOffers());
            animateNumber(publishedOffers, service.countByStatus(OfferStatus.PUBLISHED));
            animateNumber(inProgressOffers, service.countByStatus(OfferStatus.IN_PROGRESS));
            animateNumber(completedOffers, service.countByStatus(OfferStatus.COMPLETED));
            animateNumber(cancelledOffers, service.countByStatus(OfferStatus.CANCELLED));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void animateNumber(Label label, int target) {
        Timeline timeline = new Timeline();
        int duration = 700;

        for (int i = 0; i <= target; i++) {
            int value = i;
            timeline.getKeyFrames().add(
                    new KeyFrame(
                            Duration.millis((double) duration / Math.max(target, 1) * i),
                            e -> label.setText(String.valueOf(value))
                    )
            );
        }
        timeline.play();
    }

    // ======================
    // CARD ANIMATION
    // ======================
    private void animateCards() {
        animate(cardTotal, 0);
        animate(cardPublished, 120);
        animate(cardProgress, 240);
        animate(cardCompleted, 360);
        animate(cardCancelled, 480);
    }

    private void animate(VBox card, int delay) {
        card.setOpacity(0);
        card.setTranslateY(20);

        FadeTransition fade = new FadeTransition(Duration.millis(500), card);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setDelay(Duration.millis(delay));

        TranslateTransition slide = new TranslateTransition(Duration.millis(500), card);
        slide.setFromY(20);
        slide.setToY(0);
        slide.setDelay(Duration.millis(delay));

        fade.play();
        slide.play();
    }

    // ======================
    // CHARTS
    // ======================
    private void loadCharts() {
        try {
            int published  = service.countByStatus(OfferStatus.PUBLISHED);
            int progress   = service.countByStatus(OfferStatus.IN_PROGRESS);
            int draft      = service.countByStatus(OfferStatus.DRAFT);
            int completed  = service.countByStatus(OfferStatus.COMPLETED);
            int cancelled  = service.countByStatus(OfferStatus.CANCELLED);

            loadPieChart(published, progress, draft, completed, cancelled);
            loadBarChart(published, progress, draft, completed, cancelled);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== PIE CHART =====
    private void loadPieChart(int published, int progress, int draft,
                              int completed, int cancelled) {

        ObservableList<PieChart.Data> data = FXCollections.observableArrayList(
                new PieChart.Data("Published", published),
                new PieChart.Data("In Progress", progress),
                new PieChart.Data("Draft", draft),
                new PieChart.Data("Completed", completed),
                new PieChart.Data("Cancelled", cancelled)
        );

        offerPieChart.setData(data);

        javafx.application.Platform.runLater(() -> {
            for (PieChart.Data d : offerPieChart.getData()) {
                if (d.getNode() == null) continue;

                switch (d.getName()) {
                    case "Published" ->
                            d.getNode().setStyle("-fx-pie-color: #0B1E6D;");
                    case "In Progress" ->
                            d.getNode().setStyle("-fx-pie-color: #1F51FF;");
                    case "Draft" ->
                            d.getNode().setStyle("-fx-pie-color: #4DA6FF;");
                    case "Completed" ->
                            d.getNode().setStyle("-fx-pie-color: #6A0DAD;");
                    case "Cancelled" ->
                            d.getNode().setStyle("-fx-pie-color: #2E1A47;");
                }
            }
        });
    }

    // ===== BAR CHART =====
    private void loadBarChart(int published, int progress, int draft,
                              int completed, int cancelled) {

        XYChart.Series<String, Number> series = new XYChart.Series<>();

        series.getData().add(new XYChart.Data<>("Published", published));
        series.getData().add(new XYChart.Data<>("In Progress", progress));
        series.getData().add(new XYChart.Data<>("Draft", draft));
        series.getData().add(new XYChart.Data<>("Completed", completed));
        series.getData().add(new XYChart.Data<>("Cancelled", cancelled));

        offerBarChart.getData().clear();
        offerBarChart.getData().add(series);

        javafx.application.Platform.runLater(() -> {
            for (XYChart.Data<String, Number> d : series.getData()) {
                if (d.getNode() == null) continue;

                switch (d.getXValue()) {
                    case "Published" ->
                            d.getNode().setStyle("-fx-bar-fill: #0B1E6D;");
                    case "In Progress" ->
                            d.getNode().setStyle("-fx-bar-fill: #1F51FF;");
                    case "Draft" ->
                            d.getNode().setStyle("-fx-bar-fill: #4DA6FF;");
                    case "Completed" ->
                            d.getNode().setStyle("-fx-bar-fill: #6A0DAD;");
                    case "Cancelled" ->
                            d.getNode().setStyle("-fx-bar-fill: #2E1A47;");
                }
            }
        });
    }
    private void createStars() {



        for (int i = 0; i < 700; i++) {

            Circle star = new Circle(Math.random() * 2);

            star.setTranslateX(Math.random() * 1600);
            star.setTranslateY(Math.random() * 900);

            if (Math.random() > 0.5) {
                star.setStyle("-fx-fill: #396afc;");
            } else {
                star.setStyle("-fx-fill: rgba(255,255,255,0.8);");
            }

            FadeTransition fade = new FadeTransition(
                    Duration.seconds(2 + Math.random() * 3),
                    star
            );

            fade.setFromValue(0.2);
            fade.setToValue(1);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.play();

            animatedBackground.getChildren().add(star);
        }
    }
    private void loadPlatformHealth() {

        try {

            int total = service.countAllOffers();
            int completed = service.countByStatus(OfferStatus.COMPLETED);

            if (total == 0) {
                healthProgress.setProgress(0);
                healthPercent.setText("0%");
                healthBadge.setText("No Data");
                return;
            }

            double score = (double) completed / total;
            int percent = (int) (score * 100);

            animateHealthBar(score, percent);

            updateHealthBadge(percent);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void animateHealthBar(double target, int percent) {

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(1),
                        new KeyValue(healthProgress.progressProperty(), target)
                )
        );

        timeline.play();

        animateNumber(healthPercent, percent);
    }
    private void updateHealthBadge(int percent) {

        if (percent >= 70) {
            healthBadge.setText("🔥 Excellent");
            healthBadge.setStyle("-fx-text-fill: #00f260;");
        }
        else if (percent >= 40) {
            healthBadge.setText("⚠️ Average");
            healthBadge.setStyle("-fx-text-fill: #ffee58;");
        }
        else {
            healthBadge.setText("❌ Low");
            healthBadge.setStyle("-fx-text-fill: #ff4d4d;");
        }
    }
    private void loadNews() {

        NewsService newsService = new NewsService();

        javafx.concurrent.Task<java.util.List<NewsArticles>> task =
                new javafx.concurrent.Task<>() {
                    @Override
                    protected java.util.List<NewsArticles> call() {
                        return newsService.getTop5Articles(); // ✅ correction ici
                    }
                };

        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                newsListView.getItems().setAll(task.getValue());
            }
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();

        newsListView.setCellFactory(list -> new ListCell<>() {

            @Override
            protected void updateItem(NewsArticles article, boolean empty) {
                super.updateItem(article, empty);

                if (empty || article == null) {
                    setGraphic(null);
                    return;
                }

                HBox root = new HBox(15);
                root.setStyle("-fx-background-color: rgba(255,255,255,0.05);" +
                        "-fx-background-radius:15;" +
                        "-fx-padding:15;");

                ImageView imageView = new ImageView();
                imageView.setFitWidth(100);
                imageView.setFitHeight(70);
                imageView.setPreserveRatio(true);

                if (article.getImageUrl() != null &&
                        !article.getImageUrl().isBlank()) {
                    try {
                        imageView.setImage(new Image(article.getImageUrl(), true));
                    } catch (Exception ignored) {}
                }

                VBox textBox = new VBox(6);

                Label title = new Label(article.getTitle());
                title.setStyle("-fx-text-fill: #4da6ff;" +
                        "-fx-font-size:15;" +
                        "-fx-font-weight:bold;");

                Label desc = new Label(article.getDescription());
                desc.setWrapText(true);
                desc.setStyle("-fx-text-fill: black; -fx-font-size:13;");

                Label date = new Label(article.getPublishedAt());
                date.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size:11;");

                textBox.getChildren().addAll(title, desc, date);
                root.getChildren().addAll(imageView, textBox);

                root.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 &&
                            article.getUrl() != null &&
                            !article.getUrl().isBlank()) {
                        try {
                            Desktop.getDesktop().browse(new URI(article.getUrl()));
                        } catch (Exception ignored) {}
                    }
                });

                setGraphic(root);
            }
        });
    }
    private void loadWeather() {

        try {

            String city = "Tunis"; // tu peux changer

            String json = weatherService.getRawWeather(city);

            if (json == null) return;

            org.json.JSONObject obj = new org.json.JSONObject(json);

            double temp = obj.getJSONObject("main").getDouble("temp");
            String desc = obj.getJSONArray("weather")
                    .getJSONObject(0)
                    .getString("description");

            String iconCode = obj.getJSONArray("weather")
                    .getJSONObject(0)
                    .getString("icon");

            weatherCity.setText(city);
            weatherTemp.setText((int) temp + "°C");
            weatherDesc.setText(desc.toUpperCase());

            String iconUrl =
                    "https://openweathermap.org/img/wn/"
                            + iconCode + "@2x.png";

            weatherIcon.setImage(new Image(iconUrl, true));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
