package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
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


    private final DashboardService service = new DashboardService();

    @FXML
    public void initialize() {
        loadNumbers();
        animateCards();
        loadCharts();
        createStars();
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

}
