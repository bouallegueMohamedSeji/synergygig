package controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import utils.DialogHelper;
import utils.SessionManager;
import utils.WeatherService;
import utils.WeatherService.CurrentWeather;
import utils.WeatherService.DayForecast;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A glassmorphism-style weather popup that shows:
 * - Current weather for user's city
 * - Sunrise/sunset + daylight hours
 * - Rain chance, humidity, wind
 * - 3-day forecast row
 * - City search to check other locations
 */
public class WeatherPopup {

    private static final String DEFAULT_CITY = "Tunis";
    private CurrentWeather currentData;
    private VBox popupContent;
    private TextField searchField;
    private StackPane loadingOverlay;

    // Labels to update
    private Label dateTimeLabel;
    private Label conditionLabel;
    private Label locationLabel;
    private Label sunriseLabel;
    private Label sunlightLabel;
    private Label sunsetLabel;
    private Label rainLabel;
    private Label humidityLabel;
    private Label windLabel;
    private HBox forecastRow;
    private Label conditionEmoji;

    /**
     * Show the weather popup as a Dialog matching app theme.
     */
    public void show(javafx.scene.Node ownerNode) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(null);
        dialog.setHeaderText(null);

        // Build the popup content
        StackPane root = buildUI();

        DialogHelper.theme(dialog);
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(root);
        DialogHelper.hideCloseButton(pane);
        pane.getStyleClass().add("weather-dialog-pane");
        pane.setMaxWidth(420);
        pane.setMaxHeight(560);

        // Fetch weather for default city
        fetchWeather(DEFAULT_CITY);

        dialog.showAndWait();
    }

    private StackPane buildUI() {
        // === Main container ===
        popupContent = new VBox(0);
        popupContent.getStyleClass().add("weather-popup");
        popupContent.setPrefWidth(400);
        popupContent.setMaxWidth(400);
        popupContent.setMinWidth(380);

        // -- Search bar at top --
        HBox searchBar = buildSearchBar();

        // -- Main card --
        VBox mainCard = buildMainCard();

        // -- Sun info bar (with horizontal padding wrapper) --
        HBox sunBar = buildSunBar();
        VBox sunBarWrapper = new VBox(sunBar);
        sunBarWrapper.setPadding(new Insets(0, 16, 0, 16));

        // -- Rain bar --
        HBox rainBar = buildRainBar();
        VBox rainBarWrapper = new VBox(rainBar);
        rainBarWrapper.setPadding(new Insets(4, 16, 0, 16));

        // -- Stats row (humidity, wind) --
        HBox statsRow = buildStatsRow();

        // -- Forecast row --
        forecastRow = buildForecastRow();
        ScrollPane forecastScroll = new ScrollPane(forecastRow);
        forecastScroll.getStyleClass().add("weather-forecast-scroll");
        forecastScroll.setFitToHeight(true);
        forecastScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        forecastScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        popupContent.getChildren().addAll(
                searchBar,
                mainCard,
                sunBarWrapper,
                rainBarWrapper,
                statsRow,
                forecastScroll
        );

        // Loading overlay
        loadingOverlay = new StackPane();
        loadingOverlay.getStyleClass().add("weather-loading-overlay");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(40, 40);
        spinner.getStyleClass().add("weather-spinner");
        loadingOverlay.getChildren().add(spinner);
        loadingOverlay.setVisible(false);

        StackPane root = new StackPane(popupContent, loadingOverlay);
        root.getStyleClass().add("weather-popup-root");
        return root;
    }

    private HBox buildSearchBar() {
        searchField = new TextField();
        searchField.setPromptText("Search city...");
        searchField.getStyleClass().add("weather-search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchField.setOnAction(e -> {
            String city = searchField.getText().trim();
            if (!city.isEmpty()) fetchWeather(city);
        });

        Button searchBtn = new Button("🔍");
        searchBtn.getStyleClass().add("weather-search-btn");
        searchBtn.setOnAction(e -> {
            String city = searchField.getText().trim();
            if (!city.isEmpty()) fetchWeather(city);
        });

        HBox bar = new HBox(8, searchField, searchBtn);
        bar.getStyleClass().add("weather-search-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 4, 16));
        return bar;
    }

    private VBox buildMainCard() {
        // Date/time
        dateTimeLabel = new Label("Loading...");
        dateTimeLabel.getStyleClass().add("weather-datetime");

        // Condition + temp row
        conditionEmoji = new Label("🌤️");
        conditionEmoji.getStyleClass().add("weather-emoji-large");

        conditionLabel = new Label("--°C");
        conditionLabel.getStyleClass().add("weather-temp-label");

        HBox condRow = new HBox(12, conditionEmoji, conditionLabel);
        condRow.setAlignment(Pos.CENTER_LEFT);

        // Location
        locationLabel = new Label("--");
        locationLabel.getStyleClass().add("weather-location-label");

        VBox card = new VBox(4, dateTimeLabel, condRow, locationLabel);
        card.getStyleClass().add("weather-main-card");
        card.setPadding(new Insets(16, 20, 12, 20));
        return card;
    }

    private HBox buildSunBar() {
        Label sunIcon = new Label("☀↑");
        sunIcon.getStyleClass().add("weather-sun-icon");

        sunriseLabel = new Label("--:--");
        sunriseLabel.getStyleClass().add("weather-sun-time");

        Label dash1 = new Label("─");
        dash1.getStyleClass().add("weather-sun-dash");

        sunlightLabel = new Label("-- h -- m");
        sunlightLabel.getStyleClass().add("weather-sunlight-duration");

        Label dash2 = new Label("─");
        dash2.getStyleClass().add("weather-sun-dash");

        sunsetLabel = new Label("--:--");
        sunsetLabel.getStyleClass().add("weather-sun-time");

        Label sunsetIcon = new Label("☀↓");
        sunsetIcon.getStyleClass().add("weather-sun-icon");

        HBox bar = new HBox(8, sunIcon, sunriseLabel, dash1, sunlightLabel, dash2, sunsetLabel, sunsetIcon);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("weather-sun-bar");
        bar.setPadding(new Insets(8, 16, 8, 16));
        return bar;
    }

    private HBox buildRainBar() {
        Label rainIcon = new Label("🌧️");
        rainIcon.setStyle("-fx-font-size: 16;");

        Label rainText = new Label("Rain:");
        rainText.getStyleClass().add("weather-detail-text");

        rainLabel = new Label("--%");
        rainLabel.getStyleClass().add("weather-detail-value");

        HBox bar = new HBox(8, rainIcon, rainText, rainLabel);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("weather-rain-bar");
        bar.setPadding(new Insets(8, 20, 8, 20));
        return bar;
    }

    private HBox buildStatsRow() {
        // Humidity
        VBox humidityBox = new VBox(2);
        humidityBox.setAlignment(Pos.CENTER);
        Label humTitle = new Label("Humidity");
        humTitle.getStyleClass().add("weather-stat-title");
        humidityLabel = new Label("--%");
        humidityLabel.getStyleClass().add("weather-stat-value");
        humidityBox.getChildren().addAll(humTitle, humidityLabel);

        // Separator
        Region sep = new Region();
        sep.setMinWidth(1);
        sep.setMaxWidth(1);
        sep.setPrefHeight(30);
        sep.getStyleClass().add("weather-stat-separator");

        // Wind
        VBox windBox = new VBox(2);
        windBox.setAlignment(Pos.CENTER);
        Label windTitle = new Label("Wind");
        windTitle.getStyleClass().add("weather-stat-title");
        windLabel = new Label("-- km/h");
        windLabel.getStyleClass().add("weather-stat-value");
        windBox.getChildren().addAll(windTitle, windLabel);

        HBox row = new HBox(40, humidityBox, sep, windBox);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("weather-stats-row");
        row.setPadding(new Insets(6, 20, 10, 20));

        HBox.setHgrow(humidityBox, Priority.ALWAYS);
        HBox.setHgrow(windBox, Priority.ALWAYS);
        return row;
    }

    private HBox buildForecastRow() {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("weather-forecast-row");
        row.setPadding(new Insets(8, 12, 12, 12));
        // Will be populated by updateUI
        return row;
    }

    private VBox buildForecastDayCard(DayForecast df, boolean isToday) {
        Label dayLabel = new Label(df.dayName);
        dayLabel.getStyleClass().add("weather-forecast-day");
        if (isToday) dayLabel.getStyleClass().add("weather-forecast-today");

        Label emoji = new Label(df.getConditionEmoji());
        emoji.getStyleClass().add("weather-forecast-emoji");

        Label maxTemp = new Label(df.maxTempC + "°");
        maxTemp.getStyleClass().add("weather-forecast-max");

        Label minTemp = new Label(df.minTempC + "°");
        minTemp.getStyleClass().add("weather-forecast-min");

        VBox card = new VBox(4, dayLabel, emoji, maxTemp, minTemp);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("weather-forecast-card");
        if (isToday) card.getStyleClass().add("weather-forecast-card-today");
        card.setPadding(new Insets(8, 10, 8, 10));
        card.setMinWidth(54);
        card.setPrefWidth(60);
        return card;
    }

    /**
     * Fetch weather data in background thread and update UI.
     */
    private void fetchWeather(String city) {
        loadingOverlay.setVisible(true);

        Thread thread = new Thread(() -> {
            CurrentWeather data = WeatherService.fetch(city);
            Platform.runLater(() -> {
                loadingOverlay.setVisible(false);
                if (data != null) {
                    currentData = data;
                    updateUI(data);
                } else {
                    conditionLabel.setText("City not found");
                    locationLabel.setText("Try a different search");
                }
            });
        }, "weather-fetch");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Update all labels with new weather data.
     */
    private void updateUI(CurrentWeather w) {
        // DateTime
        LocalDateTime now = LocalDateTime.now();
        String dayOfWeek = now.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        String time = now.format(DateTimeFormatter.ofPattern("HH:mm"));
        dateTimeLabel.setText(dayOfWeek + ", " + time);

        // Condition + emoji
        String emoji = WeatherService.mapConditionEmoji(w.condition);
        conditionEmoji.setText(emoji);
        conditionLabel.setText(w.condition + " " + w.tempC + "°C");

        // Location
        locationLabel.setText(w.city + ", " + w.country);

        // Sun bar
        if (w.sunrise != null) sunriseLabel.setText(w.sunrise);
        if (w.sunset != null) sunsetLabel.setText(w.sunset);
        if (w.sunlightMinutes > 0) {
            long hours = w.sunlightMinutes / 60;
            long mins = w.sunlightMinutes % 60;
            sunlightLabel.setText(hours + " h " + mins + " m");
        }

        // Rain
        rainLabel.setText(w.chanceOfRain + "%");

        // Stats
        humidityLabel.setText(w.humidity + "%");
        windLabel.setText(w.windKmph + " km/h");

        // Forecast
        forecastRow.getChildren().clear();
        for (int i = 0; i < w.forecast.size(); i++) {
            DayForecast df = w.forecast.get(i);
            VBox card = buildForecastDayCard(df, i == 0);
            forecastRow.getChildren().add(card);
            HBox.setHgrow(card, Priority.ALWAYS);
        }
    }
}
