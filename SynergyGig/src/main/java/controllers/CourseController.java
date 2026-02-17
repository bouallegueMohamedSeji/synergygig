package controllers;

import entities.Course;
import entities.Skill;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import services.ServiceCourse;
import services.ServiceSkill;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class CourseController {

    @FXML
    private TextField titleField;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private TextField instructorIdField;
    @FXML
    private ComboBox<Skill> skillComboBox; // Added Skill ComboBox

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
    private TableColumn<Course, Integer> colSkill; // Optional: Show Skill ID in table

    @FXML
    private Label statusLabel;

    private ServiceCourse serviceCourse;
    private ServiceSkill serviceSkill;
    private ObservableList<Course> courseList;
    private Course selectedCourse; // For editing

    @FXML
    private VBox formContainer;
    @FXML
    private Button btnDelete;
    @FXML
    private Button btnSave;
    @FXML
    private Button btnClear;

    public CourseController() {
        serviceCourse = new ServiceCourse();
        serviceSkill = new ServiceSkill();
    }

    @FXML
    public void initialize() {
        // Initialize columns
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colInstructor.setCellValueFactory(new PropertyValueFactory<>("instructorId"));
        // If you want to show Skill ID in the table, ensure column exists in FXML or
        // add it dynamically
        // colSkill.setCellValueFactory(new PropertyValueFactory<>("skillId"));

        // Load data
        loadCourses();
        loadSkills();

        // RBAC logic
        entities.User currentUser = utils.SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            String role = currentUser.getRole();
            if ("ADMIN".equals(role)) {
                // Admin: Delete only, no create/edit
                formContainer.setVisible(false);
                formContainer.setManaged(false);
                btnDelete.setVisible(true);
            } else if ("HR_MANAGER".equals(role) || "PROJECT_OWNER".equals(role)) {
                // HR/Project Owner: Full access
                formContainer.setVisible(true);
                btnDelete.setVisible(true);
            } else {
                // User/GigWorker: Read only
                formContainer.setVisible(false);
                formContainer.setManaged(false);
                btnDelete.setVisible(false);
                btnDelete.setManaged(false);
            }
        }

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

    private void loadSkills() {
        try {
            List<Skill> skills = serviceSkill.recuperer();
            skillComboBox.setItems(FXCollections.observableArrayList(skills));

            // Converter to display Skill Name in ComboBox
            skillComboBox.setConverter(new StringConverter<Skill>() {
                @Override
                public String toString(Skill skill) {
                    return skill == null ? "" : skill.getName();
                }

                @Override
                public Skill fromString(String string) {
                    return null; // Not needed
                }
            });

        } catch (SQLException e) {
            showError("Failed to load skills: " + e.getMessage());
        }
    }

    private void selectCourse(Course course) {
        selectedCourse = course;
        titleField.setText(course.getTitle());
        descriptionArea.setText(course.getDescription());
        instructorIdField.setText(String.valueOf(course.getInstructorId()));

        // Select the skill in ComboBox
        if (course.getSkillId() > 0) {
            for (Skill s : skillComboBox.getItems()) {
                if (s.getId() == course.getSkillId()) {
                    skillComboBox.setValue(s);
                    break;
                }
            }
        } else {
            skillComboBox.setValue(null);
        }
    }

    @FXML
    private void saveCourse() {
        String title = titleField.getText().trim();
        String description = descriptionArea.getText().trim();
        String instructorIdStr = instructorIdField.getText().trim();
        Skill selectedSkill = skillComboBox.getValue();

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

        int skillId = selectedSkill != null ? selectedSkill.getId() : 0;

        try {
            if (selectedCourse == null) {
                // Add new
                Course newCourse = new Course(title, description, instructorId, skillId);
                serviceCourse.ajouter(newCourse);
                statusLabel.setText("Course added successfully.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                // Update existing
                selectedCourse.setTitle(title);
                selectedCourse.setDescription(description);
                selectedCourse.setInstructorId(instructorId);
                selectedCourse.setSkillId(skillId);
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
        skillComboBox.setValue(null);
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
