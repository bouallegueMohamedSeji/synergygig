package controllers;

import entities.Course;
import entities.Quiz;
import entities.Resource;
import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import services.ServiceQuiz;
import services.ServiceResource;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class CourseDetailsController {

    @FXML
    private Label courseTitle;
    @FXML
    private Label courseInstructor;
    @FXML
    private Label courseLevel;
    @FXML
    private Text courseDescription;
    @FXML
    private VBox resourcesContainer;
    @FXML
    private Label noResourcesLabel;
    @FXML
    private Button btnTakeQuiz;
    @FXML
    private Label noQuizLabel;

    private Course course;
    private ServiceResource serviceResource;
    private ServiceQuiz serviceQuiz;
    private Quiz quizForCourse;

    // We need HostServices to open URLs
    private HostServices hostServices;

    public CourseDetailsController() {
        serviceResource = new ServiceResource();
        serviceQuiz = new ServiceQuiz();
    }

    public void setCourse(Course course) {
        this.course = course;
        loadCourseData();
    }

    private void loadCourseData() {
        if (course == null)
            return;

        courseTitle.setText(course.getTitle());
        courseInstructor.setText("Instructor ID: " + course.getInstructorId()); // Ideally fetch name
        courseLevel.setText("Level: " + (course.getSkillLevel() != null ? course.getSkillLevel() : "Beginner"));
        courseDescription.setText(course.getDescription());

        loadResources();
        loadQuiz();
    }

    private void loadResources() {
        resourcesContainer.getChildren().clear();
        try {
            List<Resource> resources = serviceResource.getByCourseId(course.getId());
            if (resources.isEmpty()) {
                noResourcesLabel.setVisible(true);
                noResourcesLabel.setManaged(true);
            } else {
                noResourcesLabel.setVisible(false);
                noResourcesLabel.setManaged(false);

                for (Resource r : resources) {
                    Hyperlink link = new Hyperlink("📄 " + r.getType() + ": " + r.getUrl());
                    link.setOnAction(e -> {
                        // Open URL - simplified approach using getHostServices if passed, or system
                        try {
                            java.awt.Desktop.getDesktop().browse(new java.net.URI(r.getUrl()));
                        } catch (Exception ex) {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setContentText("Could not open link: " + ex.getMessage());
                            alert.show();
                        }
                    });
                    resourcesContainer.getChildren().add(link);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadQuiz() {
        try {
            List<Quiz> quizzes = serviceQuiz.getByCourseId(course.getId());
            if (quizzes.isEmpty()) {
                btnTakeQuiz.setVisible(false);
                btnTakeQuiz.setManaged(false);
                noQuizLabel.setVisible(true);
                noQuizLabel.setManaged(true);
            } else {
                // Assuming one quiz per course for now, or take the first one
                quizForCourse = quizzes.get(0);
                btnTakeQuiz.setVisible(true);
                btnTakeQuiz.setManaged(true);
                noQuizLabel.setVisible(false);
                noQuizLabel.setManaged(false);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void takeQuiz() {
        if (quizForCourse == null)
            return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/QuizTaking.fxml"));
            Parent root = loader.load();

            QuizTakingController controller = loader.getController();
            controller.setQuiz(quizForCourse);

            navigate(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void goBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UserCourseCatalog.fxml"));
            Parent root = loader.load();
            navigate(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void navigate(Parent root) {
        if (courseTitle.getScene().getRoot() instanceof BorderPane) {
            BorderPane borderPane = (BorderPane) courseTitle.getScene().getRoot();
            if (borderPane.getCenter() instanceof javafx.scene.layout.StackPane) {
                javafx.scene.layout.StackPane contentArea = (javafx.scene.layout.StackPane) borderPane.getCenter();
                contentArea.getChildren().setAll(root);
            }
        }
    }
}
