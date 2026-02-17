package controllers;

import entities.Course;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import services.ServiceCourse;

import java.sql.SQLException;
import java.util.Optional;

public class CourseController {

    @FXML
    private TextField titleField;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private TextField instructorIdField;
    @FXML
    private TableView<Course> courseTable;
    @FXML
    private TableColumn<Course, Integer> colId;
    @FXML
    private TableColumn<Course, String> colTitle;
    @FXML
    private TableColumn<Course, String> colDescription;
    @FXML
    private TableColumn<Course, Integer> colInstructor;
    @FXML
    private Label statusLabel;

    private ServiceCourse serviceCourse;
    private ObservableList<Course> courseList;
    private Course selectedCourse; // For editing

    public CourseController() {
        serviceCourse = new ServiceCourse();
    }

    @FXML
    public void initialize() {
        // Initialize columns
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colInstructor.setCellValueFactory(new PropertyValueFactory<>("instructorId"));

        // Load data
        loadCourses();

        // Listen for selection changes
        courseTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectCourse(newSelection);
            }
        });
    }

    private void loadCourses() {
        try {
            courseList = FXCollections.observableArrayList(serviceCourse.recuperer());
            courseTable.setItems(courseList);
        } catch (SQLException e) {
            showError("Failed to load courses: " + e.getMessage());
        }
    }

    private void selectCourse(Course course) {
        selectedCourse = course;
        titleField.setText(course.getTitle());
        descriptionArea.setText(course.getDescription());
        instructorIdField.setText(String.valueOf(course.getInstructorId()));
    }

    @FXML
    private void saveCourse() {
        String title = titleField.getText().trim();
        String description = descriptionArea.getText().trim();
        String instructorIdStr = instructorIdField.getText().trim();

        if (title.isEmpty() || instructorIdStr.isEmpty()) {
            statusLabel.setText("Title and Instructor ID are required.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        int instructorId;
        try {
            instructorId = Integer.parseInt(instructorIdStr);
        } catch (NumberFormatException e) {
            statusLabel.setText("Instructor ID must be a number.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        try {
            if (selectedCourse == null) {
                // Add new
                Course newCourse = new Course(title, description, instructorId);
                serviceCourse.ajouter(newCourse);
                statusLabel.setText("Course added successfully.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                // Update existing
                selectedCourse.setTitle(title);
                selectedCourse.setDescription(description);
                selectedCourse.setInstructorId(instructorId);
                serviceCourse.modifier(selectedCourse);
                statusLabel.setText("Course updated successfully.");
                statusLabel.setStyle("-fx-text-fill: green;");
            }
            clearForm();
            loadCourses();
        } catch (SQLException e) {
            showError("Operation failed: " + e.getMessage());
        }
    }

    @FXML
    private void deleteCourse() {
        Course selected = courseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a course to delete.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Course");
        alert.setHeaderText("Are you sure you want to delete this course?");
        alert.setContentText("Course: " + selected.getTitle());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                serviceCourse.supprimer(selected.getId());
                loadCourses();
                clearForm();
                statusLabel.setText("Course deleted.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } catch (SQLException e) {
                showError("Delete failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void clearForm() {
        titleField.clear();
        descriptionArea.clear();
        instructorIdField.clear();
        selectedCourse = null;
        courseTable.getSelectionModel().clearSelection();
        statusLabel.setText("");
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
