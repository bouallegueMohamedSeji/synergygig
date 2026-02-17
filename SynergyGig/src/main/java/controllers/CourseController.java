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
    private ComboBox<Skill> skillComboBox;

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
    @FXML
    private Label lblInstructorId; // Label for the field

    private ServiceCourse serviceCourse;
    private ServiceSkill serviceSkill;
    private ObservableList<Course> courseList;
    private Course selectedCourse;

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

        loadCourses();
        loadSkills();

        // RBAC logic
        entities.User currentUser = utils.SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            String role = currentUser.getRole();

            if ("ADMIN".equals(role)) {
                // Admin: Full access (Can edit instructor ID)
                formContainer.setVisible(true); // Changed from false to true
                formContainer.setManaged(true);
                btnDelete.setVisible(true);

                // Show instructor field for Admins
                setInstructorFieldVisible(true);

            } else if ("HR_MANAGER".equals(role) || "PROJECT_OWNER".equals(role)) {
                // HR/Project Owner: Create/Edit/Delete
                formContainer.setVisible(true);
                btnDelete.setVisible(true);

                // Hide instructor field for these users (Auto-assigned)
                setInstructorFieldVisible(false);

            } else {
                // Others: Read Only
                formContainer.setVisible(false);
                formContainer.setManaged(false);
                btnDelete.setVisible(false);
            }
        }

        // Listen for selection changes
        courseTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectCourse(newSelection);
            }
        });
    }

    private void setInstructorFieldVisible(boolean visible) {
        if (instructorIdField != null) {
            instructorIdField.setVisible(visible);
            instructorIdField.setManaged(visible);
        }
        if (lblInstructorId != null) { // Assuming we bind a label in FXML
            lblInstructorId.setVisible(visible);
            lblInstructorId.setManaged(visible);
        }
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

            skillComboBox.setConverter(new StringConverter<Skill>() {
                @Override
                public String toString(Skill skill) {
                    return skill == null ? "" : skill.getName();
                }

                @Override
                public Skill fromString(String string) {
                    return null;
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

        entities.User currentUser = utils.SessionManager.getInstance().getCurrentUser();

        if (currentUser != null && "ADMIN".equals(currentUser.getRole())) {
            // Admin sees and can edit instructor ID
            instructorIdField.setText(String.valueOf(course.getInstructorId()));
            setInstructorFieldVisible(true);
        } else {
            // Others don't need to see it in the form, but if they did, it would require a
            // Label
            // We already hid it in initialize, but let's ensure consistency
            setInstructorFieldVisible(false);
        }

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
        Skill selectedSkill = skillComboBox.getValue();

        if (title.isEmpty()) {
            statusLabel.setText("Title is required.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        entities.User currentUser = utils.SessionManager.getInstance().getCurrentUser();
        int instructorId = 0;

        if (selectedCourse == null) {
            // New Course
            if (currentUser != null) {
                if ("ADMIN".equals(currentUser.getRole())) {
                    // Admin creating course: must specify instructor ID? Or auto-set?
                    // Let's check field
                    String idStr = instructorIdField.getText().trim();
                    if (!idStr.isEmpty()) {
                        try {
                            instructorId = Integer.parseInt(idStr);
                        } catch (NumberFormatException e) {
                            statusLabel.setText("Instructor ID must be a number.");
                            return;
                        }
                    } else {
                        // Default to Admin's ID if left empty? Or required?
                        instructorId = currentUser.getId();
                    }
                } else {
                    // HR/PO creating: Auto-assign their ID
                    instructorId = currentUser.getId();
                }
            }
        } else {
            // Editing
            if (currentUser != null && "ADMIN".equals(currentUser.getRole())) {
                String idStr = instructorIdField.getText().trim();
                try {
                    instructorId = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    statusLabel.setText("Instructor ID must be a number.");
                    return;
                }
            } else {
                // Keep existing ID
                instructorId = selectedCourse.getInstructorId();
            }
        }

        int skillId = selectedSkill != null ? selectedSkill.getId() : 0;

        try {
            if (selectedCourse == null) {
                Course newCourse = new Course(title, description, instructorId, skillId);
                serviceCourse.ajouter(newCourse);
                statusLabel.setText("Course added successfully.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
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
        if (selected == null)
            return;

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

        // Reset visibility for Admin (if they were viewing a course, form might be
        // visible)
        // Generally keep form visibility static based on role, just reset fields
        entities.User currentUser = utils.SessionManager.getInstance().getCurrentUser();
        if (currentUser != null && !"ADMIN".equals(currentUser.getRole())) {
            setInstructorFieldVisible(false); // Ensure hidden
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
