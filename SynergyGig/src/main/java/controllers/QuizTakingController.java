package controllers;

import entities.Course;
import entities.Question;
import entities.Quiz;
import entities.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import services.ServiceCourse;
import services.ServiceQuestion;
import services.ServiceQuiz;
import services.ServiceUserSkill;
import utils.SessionManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class QuizTakingController {

    @FXML
    private Label quizTitleLabel;
    @FXML
    private VBox questionsContainer;
    @FXML
    private Label resultLabel;
    @FXML
    private Button btnSubmit;
    @FXML
    private Button btnBack;

    private Quiz currentQuiz;
    private ServiceQuestion serviceQuestion;
    private ServiceUserSkill serviceUserSkill;
    private ServiceCourse serviceCourse;

    // Map to store question ID and selected answer
    private Map<Integer, String> userAnswers = new HashMap<>();
    private List<Question> questions;

    public QuizTakingController() {
        serviceQuestion = new ServiceQuestion();
        serviceUserSkill = new ServiceUserSkill();
        serviceCourse = new ServiceCourse();
    }

    public void setQuiz(Quiz quiz) {
        this.currentQuiz = quiz;
        if (quiz != null) {
            quizTitleLabel.setText(quiz.getTitle());
            loadQuestions();
        }
    }

    private void loadQuestions() {
        questionsContainer.getChildren().clear();
        userAnswers.clear();
        resultLabel.setText("");
        btnSubmit.setDisable(false);

        try {
            questions = serviceQuestion.getByQuizId(currentQuiz.getId());

            if (questions.isEmpty()) {
                questionsContainer.getChildren().add(new Label("No questions available for this quiz."));
                btnSubmit.setDisable(true);
                return;
            }

            int qNum = 1;
            for (Question q : questions) {
                VBox qBox = new VBox(10);
                qBox.setStyle(
                        "-fx-border-color: #ddd; -fx-border-radius: 5; -fx-padding: 10; -fx-background-color: white;");

                Label qText = new Label(qNum + ". " + q.getQuestionText());
                qText.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                qText.setWrapText(true);

                ToggleGroup group = new ToggleGroup();

                RadioButton rbA = new RadioButton("A) " + q.getOptionA());
                rbA.setToggleGroup(group);
                rbA.setUserData("A");

                RadioButton rbB = new RadioButton("B) " + q.getOptionB());
                rbB.setToggleGroup(group);
                rbB.setUserData("B");

                RadioButton rbC = new RadioButton("C) " + q.getOptionC());
                rbC.setToggleGroup(group);
                rbC.setUserData("C");

                group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        userAnswers.put(q.getId(), newVal.getUserData().toString());
                    }
                });

                qBox.getChildren().addAll(qText, rbA, rbB, rbC);
                questionsContainer.getChildren().add(qBox);
                qNum++;
            }

        } catch (SQLException e) {
            showError("Failed to load questions: " + e.getMessage());
        }
    }

    @FXML
    private void submitQuiz() {
        if (questions == null || questions.isEmpty())
            return;

        int score = 0;
        int total = questions.size();

        for (Question q : questions) {
            String selected = userAnswers.get(q.getId());
            if (selected != null && selected.equals(q.getCorrectOption())) {
                score++;
            }
        }

        double percentage = (double) score / total * 100;
        String resultMsg = String.format("You scored %d/%d (%.1f%%)", score, total, percentage);

        if (percentage >= 70.0) {
            resultMsg += "\nCongratulations! You passed.";
            resultLabel.setStyle("-fx-text-fill: green;");
            awardSkill();
        } else {
            resultMsg += "\nYou needs 70% to pass. Try again.";
            resultLabel.setStyle("-fx-text-fill: red;");
        }

        resultLabel.setText(resultMsg);
        btnSubmit.setDisable(true); // Prevent resubmission
    }

    private void awardSkill() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null)
            return;

        try {
            // Find the course to get the skill ID
            // Ideally Quiz should optionally store skillId, but it's linked via Course
            Optional<Course> courseOpt = serviceCourse.recuperer().stream()
                    .filter(c -> c.getId() == currentQuiz.getCourseId())
                    .findFirst();

            if (courseOpt.isPresent()) {
                Course course = courseOpt.get();
                if (course.getSkillId() > 0) {
                    serviceUserSkill.addSkillToUser(currentUser.getId(), course.getSkillId());
                    resultLabel.setText(resultLabel.getText() + "\nSkill added to your profile!");
                } else {
                    // No skill associated with this course
                }
            }
        } catch (SQLException e) {
            showError("Failed to award skill: " + e.getMessage());
        }
    }

    @FXML
    private void goBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/QuizManagement.fxml"));
            Parent root = loader.load();

            if (quizTitleLabel.getScene().getRoot() instanceof javafx.scene.layout.BorderPane) {
                javafx.scene.layout.BorderPane borderPane = (javafx.scene.layout.BorderPane) quizTitleLabel.getScene()
                        .getRoot();
                javafx.scene.layout.StackPane contentArea = (javafx.scene.layout.StackPane) borderPane.getCenter();
                contentArea.getChildren().setAll(root);
            } else {
                quizTitleLabel.getScene().setRoot(root);
            }
        } catch (IOException e) {
            showError("Failed to go back: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
