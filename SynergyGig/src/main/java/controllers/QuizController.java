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
    @FXML
    private Button btnTakeQuiz;

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

            // Allow Admin to see form ONLY for layout purposes (checking items),
            // but requirements say "only hr/po create". Admin is usually edit/delete.
            // Let's hide creation form for Admin unless they select something to edit?
            // Actually, keep simple: Create is HR/PO.
            formContainer.setVisible(canCreate);
            formContainer.setManaged(canCreate);

            // Delete button visibility
            boolean isEmployee = "EMPLOYEE".equals(role) || "GIG_WORKER".equals(role);
            if (isEmployee) {
                btnDelete.setVisible(false);
                btnDelete.setManaged(false);
            }
        }

        // Hide action buttons initially
        if (btnManageQuestions != null)
            btnManageQuestions.setVisible(false);
        if (btnTakeQuiz != null)
            btnTakeQuiz.setVisible(false);

        // Listen for selection changes
        quizTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectQuiz(newVal);
                updateActionButtons(newVal);
            } else {
                if (btnManageQuestions != null)
                    btnManageQuestions.setVisible(false);
                if (btnTakeQuiz != null)
                    btnTakeQuiz.setVisible(false);
            }
        });
    }

    private void updateActionButtons(Quiz quiz) {
        // Manage Questions: Admin or Owner
        if (btnManageQuestions != null) {
            btnManageQuestions.setVisible(canEditOrDelete(quiz));
        }

        // Take Quiz: Available to everyone (except maybe not needed for creator? but
        // useful for testing)
        // Let's make it available to all
        if (btnTakeQuiz != null) {
            btnTakeQuiz.setVisible(true);
        }

        // Delete Access
        if (btnDelete != null) {
            btnDelete.setVisible(canEditOrDelete(quiz));
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

        // Admin or Owner should see the form populated to EDIT
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (canEditOrDelete(quiz)) {
            // Show form for editing even if it was hidden (e.g. for Admin)
            formContainer.setVisible(true);
            formContainer.setManaged(true);
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
                // Update
                if (!canEditOrDelete(selectedQuiz)) {
                    showError("Permission Denied: Only Admin or Instructor cause edit.");
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
        if (selected == null)
            return;

        if (!canEditOrDelete(selected)) {
            showError("Permission Denied.");
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
        navigate("/fxml/QuestionManagement.fxml", selectedQuiz);
    }

    @FXML
    private void takeQuiz() {
        navigate("/fxml/QuizTaking.fxml", selectedQuiz);
    }

    private void navigate(String fxmlPath, Quiz quiz) {
        if (quiz == null)
            return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            // Pass quiz to controller
            Object controller = loader.getController();
            if (controller instanceof QuestionController) {
                ((QuestionController) controller).setQuiz(quiz);
            } else if (controller instanceof QuizTakingController) {
                ((QuizTakingController) controller).setQuiz(quiz);
            }

            if (quizTable.getScene() != null
                    && quizTable.getScene().getRoot() instanceof javafx.scene.layout.BorderPane) {
                javafx.scene.layout.BorderPane borderPane = (javafx.scene.layout.BorderPane) quizTable.getScene()
                        .getRoot();
                javafx.scene.layout.StackPane contentArea = (javafx.scene.layout.StackPane) borderPane.getCenter();
                contentArea.getChildren().setAll(root);
            } else {
                quizTable.getScene().setRoot(root);
            }
        } catch (IOException e) {
            e.printStackTrace();
            showError("Navigation failed: " + e.getMessage());
        }
    }

    private boolean canEditOrDelete(Quiz quiz) {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null)
            return false;

        if ("ADMIN".equals(currentUser.getRole())) {
            return true;
        }

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

        // Reset visibility based on basic role permissions
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            String role = currentUser.getRole();
            boolean canCreate = "HR_MANAGER".equals(role) || "PROJECT_OWNER".equals(role);
            formContainer.setVisible(canCreate);
            formContainer.setManaged(canCreate);
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
