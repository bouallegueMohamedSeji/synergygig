package controllers;

import entities.Course;
import entities.Quiz;
import entities.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import services.ServiceCourse;
import services.ServiceQuiz;
import utils.SessionManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class QuizController {

    @FXML
    private ComboBox<Course> courseComboBox;
    @FXML
    private TextField titleField;
    @FXML
    private TableView<Quiz> quizTable;
    @FXML
    private TableColumn<Quiz, Integer> colId;
    @FXML
    private TableColumn<Quiz, Integer> colCourse; // Display Course ID/Name
    @FXML
    private TableColumn<Quiz, String> colTitle;
    @FXML
    private Label statusLabel;

    @FXML
    private VBox formContainer;
    @FXML
    private Button btnDelete;
    @FXML
    private Button btnSave;
    @FXML
    private Button btnClear;
    @FXML
    private Button btnManageQuestions;

    private ServiceQuiz serviceQuiz;
    private ServiceCourse serviceCourse;
    private ObservableList<Quiz> quizList;
    private ObservableList<Course> courseList;
    private Quiz selectedQuiz;

    public QuizController() {
        serviceQuiz = new ServiceQuiz();
        serviceCourse = new ServiceCourse();
    }

    @FXML
    public void initialize() {
        // Initialize columns
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCourse.setCellValueFactory(new PropertyValueFactory<>("courseId"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));

        // Load data
        loadCourses();
        loadQuizzes();

        // Initial RBAC for visibility
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            String role = currentUser.getRole();
            // Create is for HR or Project Owner
            boolean canCreate = "HR_MANAGER".equals(role) || "PROJECT_OWNER".equals(role);
            formContainer.setVisible(canCreate);
            formContainer.setManaged(canCreate);

            // Delete button visibility - logic handled more specifically on selection,
            // but Hide it initially for Employees who can't delete anything
            boolean isEmployee = "EMPLOYEE".equals(role) || "GIG_WORKER".equals(role);
            if (isEmployee) {
                btnDelete.setVisible(false);
                btnDelete.setManaged(false);
            }
        }

        if (btnManageQuestions != null) {
            btnManageQuestions.setVisible(false);
        }

        // Listen for selection changes to update form and Delete button logic
        quizTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectQuiz(newVal);
                updateDeleteAccess(newVal);
                updateManageQuestionsAccess(newVal);
            } else {
                if (btnManageQuestions != null)
                    btnManageQuestions.setVisible(false);
            }
        });
    }

    private void updateDeleteAccess(Quiz quiz) {
        if (canEditOrDelete(quiz)) {
            btnDelete.setVisible(true);
        } else {
            btnDelete.setVisible(false);
        }
    }

    private void updateManageQuestionsAccess(Quiz quiz) {
        if (btnManageQuestions == null)
            return;

        if (canEditOrDelete(quiz)) {
            btnManageQuestions.setVisible(true);
        } else {
            btnManageQuestions.setVisible(false);
        }
    }

    private void loadCourses() {
        try {
            courseList = FXCollections.observableArrayList(serviceCourse.recuperer());
            courseComboBox.setItems(courseList);

            courseComboBox.setConverter(new StringConverter<Course>() {
                @Override
                public String toString(Course course) {
                    return course == null ? "" : course.getId() + " - " + course.getTitle();
                }

                @Override
                public Course fromString(String string) {
                    return null;
                }
            });
        } catch (SQLException e) {
            showError("Failed to load courses: " + e.getMessage());
        }
    }

    private void loadQuizzes() {
        try {
            quizList = FXCollections.observableArrayList(serviceQuiz.recuperer());
            quizTable.setItems(quizList);
        } catch (SQLException e) {
            showError("Failed to load quizzes: " + e.getMessage());
        }
    }

    private void selectQuiz(Quiz quiz) {
        selectedQuiz = quiz;
        titleField.setText(quiz.getTitle());

        // Select associated course
        for (Course c : courseComboBox.getItems()) {
            if (c.getId() == quiz.getCourseId()) {
                courseComboBox.setValue(c);
                break;
            }
        }
    }

    @FXML
    private void saveQuiz() {
        Course selectedCourse = courseComboBox.getValue();
        String title = titleField.getText().trim();

        if (selectedCourse == null || title.isEmpty()) {
            statusLabel.setText("Course and Title are required.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        try {
            if (selectedQuiz == null) {
                // Add new
                Quiz newQuiz = new Quiz(selectedCourse.getId(), title);
                serviceQuiz.ajouter(newQuiz);
                statusLabel.setText("Quiz added successfully.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                // Update - CHECK PERMISSIONS FIRST
                // Requirement: "only that instructor can edit the quiz or the admin"
                if (!canEditOrDelete(selectedQuiz)) {
                    showError("Permission Denied: Only the Admin or the Instructor of this course can edit this quiz.");
                    return;
                }

                selectedQuiz.setCourseId(selectedCourse.getId());
                selectedQuiz.setTitle(title);
                serviceQuiz.modifier(selectedQuiz);
                statusLabel.setText("Quiz updated successfully.");
                statusLabel.setStyle("-fx-text-fill: green;");
            }
            clearForm();
            loadQuizzes();
        } catch (SQLException e) {
            showError("Operation failed: " + e.getMessage());
        }
    }

    @FXML
    private void deleteQuiz() {
        Quiz selected = quizTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a quiz to delete.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        // CHECK PERMISSIONS
        if (!canEditOrDelete(selected)) {
            showError("Permission Denied: Only the Admin or the Instructor of this course can delete this quiz.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Quiz");
        alert.setHeaderText("Delete this quiz?");
        alert.setContentText("Title: " + selected.getTitle());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                serviceQuiz.supprimer(selected.getId());
                loadQuizzes();
                clearForm();
                statusLabel.setText("Quiz deleted.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } catch (SQLException e) {
                showError("Delete failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void manageQuestions() {
        if (selectedQuiz == null)
            return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/QuestionManagement.fxml"));
            Parent root = loader.load();

            QuestionController controller = loader.getController();
            controller.setQuiz(selectedQuiz);

            // Replace content in Dashboard
            if (quizTable.getScene() != null
                    && quizTable.getScene().getRoot() instanceof javafx.scene.layout.BorderPane) {
                javafx.scene.layout.BorderPane borderPane = (javafx.scene.layout.BorderPane) quizTable.getScene()
                        .getRoot();
                javafx.scene.layout.StackPane contentArea = (javafx.scene.layout.StackPane) borderPane.getCenter();
                contentArea.getChildren().setAll(root);
            } else {
                // Fallback
                quizTable.getScene().setRoot(root);
            }

        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to load Question Management: " + e.getMessage());
        }
    }

    private boolean canEditOrDelete(Quiz quiz) {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null)
            return false;

        if ("ADMIN".equals(currentUser.getRole())) {
            return true;
        }

        // Check if current user is the instructor of the course
        try {
            Optional<Course> courseOpt = serviceCourse.recuperer().stream()
                    .filter(c -> c.getId() == quiz.getCourseId())
                    .findFirst();
            if (courseOpt.isPresent()) {
                return courseOpt.get().getInstructorId() == currentUser.getId();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @FXML
    private void clearForm() {
        courseComboBox.setValue(null);
        titleField.clear();
        selectedQuiz = null;
        quizTable.getSelectionModel().clearSelection();
        statusLabel.setText("");
        if (btnManageQuestions != null)
            btnManageQuestions.setVisible(false);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
