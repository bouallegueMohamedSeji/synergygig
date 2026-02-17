package controllers;

import entities.Question;
import entities.Quiz;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import services.ServiceQuestion;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class QuestionController {

    @FXML
    private Label quizTitleLabel;
    @FXML
    private TextArea questionArea;
    @FXML
    private TextField optionAField;
    @FXML
    private TextField optionBField;
    @FXML
    private TextField optionCField;
    @FXML
    private ComboBox<String> correctOptionComboBox;

    @FXML
    private TableView<Question> questionTable;
    @FXML
    private TableColumn<Question, Integer> colId;
    @FXML
    private TableColumn<Question, String> colText;
    @FXML
    private TableColumn<Question, String> colOptionA;
    @FXML
    private TableColumn<Question, String> colOptionB;
    @FXML
    private TableColumn<Question, String> colOptionC;
    @FXML
    private TableColumn<Question, String> colCorrect;

    @FXML
    private Label statusLabel;
    @FXML
    private Button btnDelete;

    private ServiceQuestion serviceQuestion;
    private ObservableList<Question> questionList;
    private Question selectedQuestion;
    private Quiz currentQuiz;

    public QuestionController() {
        serviceQuestion = new ServiceQuestion();
    }

    @FXML
    public void initialize() {
        // Initialize columns
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colText.setCellValueFactory(new PropertyValueFactory<>("questionText"));
        colOptionA.setCellValueFactory(new PropertyValueFactory<>("optionA"));
        colOptionB.setCellValueFactory(new PropertyValueFactory<>("optionB"));
        colOptionC.setCellValueFactory(new PropertyValueFactory<>("optionC"));
        colCorrect.setCellValueFactory(new PropertyValueFactory<>("correctOption"));

        // Initialize ComboBox
        correctOptionComboBox.setItems(FXCollections.observableArrayList("A", "B", "C"));

        // Listen for selection
        questionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectQuestion(newVal);
            }
        });
    }

    public void setQuiz(Quiz quiz) {
        this.currentQuiz = quiz;
        if (quiz != null) {
            quizTitleLabel.setText("Questions for: " + quiz.getTitle());
            loadQuestions();
        }
    }

    private void loadQuestions() {
        if (currentQuiz == null)
            return;
        try {
            // We need a method in ServiceQuestion to get by Quiz ID.
            // Assuming getByQuizId exists or we filter manually.
            List<Question> questions = serviceQuestion.getByQuizId(currentQuiz.getId());
            questionList = FXCollections.observableArrayList(questions);
            questionTable.setItems(questionList);
        } catch (SQLException e) {
            showError("Failed to load questions: " + e.getMessage());
        }
    }

    private void selectQuestion(Question q) {
        selectedQuestion = q;
        questionArea.setText(q.getQuestionText());
        optionAField.setText(q.getOptionA());
        optionBField.setText(q.getOptionB());
        optionCField.setText(q.getOptionC());
        correctOptionComboBox.setValue(q.getCorrectOption());
    }

    @FXML
    private void saveQuestion() {
        if (currentQuiz == null)
            return;

        String text = questionArea.getText().trim();
        String optA = optionAField.getText().trim();
        String optB = optionBField.getText().trim();
        String optC = optionCField.getText().trim();
        String correct = correctOptionComboBox.getValue();

        if (text.isEmpty() || optA.isEmpty() || optB.isEmpty() || optC.isEmpty() || correct == null) {
            statusLabel.setText("All fields are required.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        try {
            if (selectedQuestion == null) {
                // Add
                Question newQ = new Question(currentQuiz.getId(), text, optA, optB, optC, correct);
                serviceQuestion.ajouter(newQ);
                statusLabel.setText("Question added.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                // Update
                selectedQuestion.setQuestionText(text);
                selectedQuestion.setOptionA(optA);
                selectedQuestion.setOptionB(optB);
                selectedQuestion.setOptionC(optC);
                selectedQuestion.setCorrectOption(correct);
                serviceQuestion.modifier(selectedQuestion);
                statusLabel.setText("Question updated.");
                statusLabel.setStyle("-fx-text-fill: green;");
            }
            clearForm();
            loadQuestions();
        } catch (SQLException e) {
            showError("Operation failed: " + e.getMessage());
        }
    }

    @FXML
    private void deleteQuestion() {
        Question selected = questionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a question to delete.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Question");
        alert.setHeaderText("Delete this question?");
        alert.setContentText(selected.getQuestionText());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                serviceQuestion.supprimer(selected.getId());
                loadQuestions();
                clearForm();
                statusLabel.setText("Question deleted.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } catch (SQLException e) {
                showError("Delete failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void clearForm() {
        questionArea.clear();
        optionAField.clear();
        optionBField.clear();
        optionCField.clear();
        correctOptionComboBox.setValue(null);
        selectedQuestion = null;
        questionTable.getSelectionModel().clearSelection();
        statusLabel.setText("");
    }

    @FXML
    private void goBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/QuizManagement.fxml"));
            Parent root = loader.load();

            // Get the current scene's content area (StackPane) if possible,
            // or just replace the root if we were in a full window.
            // Since we are likely inside the Dashboard's StackPane, we need to replace THIS
            // view
            // with the QuizManagement view in the SAME parent container.

            if (quizTitleLabel.getScene() != null
                    && quizTitleLabel.getScene().getRoot() instanceof javafx.scene.layout.BorderPane) {
                // We are in Dashboard
                javafx.scene.layout.BorderPane borderPane = (javafx.scene.layout.BorderPane) quizTitleLabel.getScene()
                        .getRoot();
                javafx.scene.layout.StackPane contentArea = (javafx.scene.layout.StackPane) borderPane.getCenter();
                contentArea.getChildren().setAll(root);
            } else {
                // Fallback if structure is different
                quizTitleLabel.getScene().setRoot(root);
            }

        } catch (IOException e) {
            e.printStackTrace();
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
