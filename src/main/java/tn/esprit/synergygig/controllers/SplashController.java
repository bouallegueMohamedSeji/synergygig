package tn.esprit.synergygig.controllers;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.control.Label;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
public class SplashController {

    @FXML private Pane animatedBackground;
    @FXML private ImageView logoImage;
    @FXML
    private Label nameLabel;
    private MediaPlayer mediaPlayer;

    @FXML
    public void initialize() {

        // Charger logo
        logoImage.setImage(new Image(
                getClass().getResourceAsStream("/tn/esprit/synergygig/gui/images/anaslogo1.png")
        ));

        createStars();
        animateLogo();
        animateName();
        autoSwitch();
        playMusic();

    }

    private void createStars() {

        for (int i = 0; i < 300; i++) { // 300 = plus fluide que 500

            double radius = Math.random() * 2 + 0.5;
            Circle star = new Circle(radius);

            star.setLayoutX(Math.random() * 1200);
            star.setLayoutY(Math.random() * 800);

            // 🌌 Couleurs Galaxy
            double random = Math.random();

            if (random < 0.4) {
                star.setStyle("-fx-fill: rgba(255,255,255,0.9);"); // blanc brillant
            }
            else if (random < 0.7) {
                star.setStyle("-fx-fill: #60a5fa;"); // bleu clair
            }
            else {
                star.setStyle("-fx-fill: #3b82f6;"); // bleu galaxy
            }

            // 🌟 Animation scintillement
            FadeTransition fade = new FadeTransition(
                    Duration.seconds(2 + Math.random() * 3),
                    star
            );

            fade.setFromValue(0.3);
            fade.setToValue(1);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.play();

            animatedBackground.getChildren().add(star);
        }
    }


    private void animateLogo() {

        // Scale animation
        ScaleTransition scale = new ScaleTransition(Duration.seconds(2), logoImage);
        scale.setFromX(0.9);
        scale.setFromY(0.9);
        scale.setToX(1.1);
        scale.setToY(1.1);
        scale.setAutoReverse(true);
        scale.setCycleCount(Animation.INDEFINITE);
        scale.play();

        // Glow dynamique
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#8b5cf6"));
        glow.setRadius(20);
        logoImage.setEffect(glow);

        Timeline glowPulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(glow.radiusProperty(), 20)),
                new KeyFrame(Duration.seconds(2),
                        new KeyValue(glow.radiusProperty(), 50))
        );

        glowPulse.setAutoReverse(true);
        glowPulse.setCycleCount(Animation.INDEFINITE);
        glowPulse.play();
    }

    private void autoSwitch() {

        PauseTransition pause = new PauseTransition(Duration.seconds(8));

        pause.setOnFinished(event -> {
            try {

                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/tn/esprit/synergygig/gui/MainLayout.fxml")
                );

                Parent root = loader.load();

                Stage stage = (Stage) logoImage.getScene().getWindow();

                // 🔥 Récupérer la scène existante (meilleure méthode)
                Scene scene = stage.getScene();

                // Fade out splash
                FadeTransition fadeOut = new FadeTransition(Duration.millis(500), scene.getRoot());
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);

                fadeOut.setOnFinished(e -> {

                    // 🎵 Stop music si existe
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                    }

                    // 🔁 Remplacer seulement le root (garde CSS)
                    scene.setRoot(root);

                    // Fade in main layout
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(500), root);
                    fadeIn.setFromValue(0);
                    fadeIn.setToValue(1);
                    fadeIn.play();
                });

                fadeOut.play();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        pause.play();
    }

    private void playMusic() {

        try {

            String path = getClass()
                    .getResource("/tn/esprit/synergygig/gui/sounds/startup.mp3")
                    .toExternalForm();

            Media media = new Media(path);

            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(0.4); // volume 0 à 1

            mediaPlayer.play();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void animateName() {

        nameLabel.setOpacity(0);
        nameLabel.setTranslateY(30);

        FadeTransition fade = new FadeTransition(Duration.seconds(2), nameLabel);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.seconds(2), nameLabel);
        slide.setFromY(30);
        slide.setToY(0);

        ParallelTransition show = new ParallelTransition(fade, slide);
        show.play();

        // Glow néon animé
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#8b5cf6"));
        glow.setRadius(20);
        nameLabel.setEffect(glow);

        Timeline glowPulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(glow.radiusProperty(), 20)),
                new KeyFrame(Duration.seconds(2),
                        new KeyValue(glow.radiusProperty(), 50))
        );

        glowPulse.setAutoReverse(true);
        glowPulse.setCycleCount(Animation.INDEFINITE);
        glowPulse.play();
    }


}
