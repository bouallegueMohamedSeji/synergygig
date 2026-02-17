package controllers;

import entities.Course;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import services.ServiceCourse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class UserCourseCatalogController {

    @FXML
    private TextField searchField;
    @FXML
    private FlowPane coursesContainer;

    private ServiceCourse serviceCourse;
    private List<Course> allCourses;

    public UserCourseCatalogController() {
        serviceCourse = new ServiceCourse();
    }

    @FXML
    public void initialize() {
        loadCourses();

        // Add search listener
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterCourses(newValue);
        });
    }

    private void loadCourses() {
        try {
            allCourses = serviceCourse.recuperer();
            displayCourses(allCourses);
        } catch (SQLException e) {
            e.printStackTrace();
            coursesContainer.getChildren().add(new Label("Failed to load courses."));
        }
    }

    private void filterCourses(String query) {
        if (query == null || query.isEmpty()) {
            displayCourses(allCourses);
            return;
        }

        String lowerQuery = query.toLowerCase();
        List<Course> filtered = allCourses.stream()
                .filter(c -> c.getTitle().toLowerCase().contains(lowerQuery) ||
                        c.getDescription().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
        displayCourses(filtered);
    }

    private void displayCourses(List<Course> courses) {
        coursesContainer.getChildren().clear();

        if (courses.isEmpty()) {
            coursesContainer.getChildren().add(new Label("No courses found."));
            return;
        }

        for (Course course : courses) {
            VBox card = createCourseCard(course);
            coursesContainer.getChildren().add(card);
        }
    }

    private VBox createCourseCard(Course course) {
        VBox card = new VBox(10);
        card.setStyle(
                "-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 0);");
        card.setPrefWidth(280);
        card.setPrefHeight(200);

        Label title = new Label(course.getTitle());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        title.setWrapText(true);

        String levelText = course.getSkillLevel() != null ? course.getSkillLevel() : "Beginner";
        Label level = new Label(levelText);
        level.setStyle(
                "-fx-background-color: #E3F2FD; -fx-text-fill: #1976D2; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 12px;");

        Label desc = new Label(course.getDescription());
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #666;");
        desc.setMaxHeight(80); // Limit height

        Button viewBtn = new Button("View Details");
        viewBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-cursor: hand;");
        viewBtn.setMaxWidth(Double.MAX_VALUE);
        viewBtn.setOnAction(e -> openCourseDetails(course));

        card.getChildren().addAll(title, level, desc, viewBtn);
        return card;
    }

    private void openCourseDetails(Course course) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CourseDetails.fxml"));
            Parent root = loader.load();

            CourseDetailsController controller = loader.getController();
            controller.setCourse(course);

            // Navigate
            if (coursesContainer.getScene().getRoot() instanceof BorderPane) {
                BorderPane borderPane = (BorderPane) coursesContainer.getScene().getRoot();
                // Assuming center is a StackPane or similar, but simplified:
                if (borderPane.getCenter() instanceof javafx.scene.layout.StackPane) {
                    javafx.scene.layout.StackPane contentArea = (javafx.scene.layout.StackPane) borderPane.getCenter();
                    contentArea.getChildren().setAll(root);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
