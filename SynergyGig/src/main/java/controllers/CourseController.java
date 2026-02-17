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
    private ComboBox<String> skillLevelComboBox;

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
    private Label lblInstructorId;

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

        // Initialize Skill Levels
        skillLevelComboBox.setItems(FXCollections.observableArrayList("Beginner", "Intermediate", "Advanced"));
        skillLevelComboBox.setValue("Beginner"); // Default

        // RBAC logic
        entities.User currentUser = utils.SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            String role = currentUser.getRole();

            if ("ADMIN".equals(role)) {
                formContainer.setVisible(true);
                formContainer.setManaged(true);
                btnDelete.setVisible(true);
                setInstructorFieldVisible(true);

            } else if ("HR_MANAGER".equals(role) || "PROJECT_OWNER".equals(role)) {
                formContainer.setVisible(true);
                btnDelete.setVisible(true);
                setInstructorFieldVisible(false);

            } else {
                formContainer.setVisible(false);
                formContainer.setManaged(false);
                btnDelete.setVisible(false);
            }
        }

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
        if (lblInstructorId != null) {
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

            // Enable AutoComplete
            utils.ComboBoxAutoComplete.setup(skillComboBox);

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
            instructorIdField.setText(String.valueOf(course.getInstructorId()));
            setInstructorFieldVisible(true);
        } else {
            setInstructorFieldVisible(false);
        }

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

        // Set Skill Level
        if (course.getSkillLevel() != null) {
            skillLevelComboBox.setValue(course.getSkillLevel());
        } else {
            skillLevelComboBox.setValue("Beginner");
        }
    }

    @FXML
    private void saveCourse() {
        String title = titleField.getText().trim();
        String description = descriptionArea.getText().trim();
        Skill selectedSkill = skillComboBox.getValue();
        String selectedLevel = skillLevelComboBox.getValue();

        if (title.isEmpty()) {
            statusLabel.setText("Title is required.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        entities.User currentUser = utils.SessionManager.getInstance().getCurrentUser();
        int instructorId = 0;

        if (selectedCourse == null) {
            if (currentUser != null) {
                if ("ADMIN".equals(currentUser.getRole())) {
                    String idStr = instructorIdField.getText().trim();
                    if (!idStr.isEmpty()) {
                        try {
                            instructorId = Integer.parseInt(idStr);
                        } catch (NumberFormatException e) {
                            statusLabel.setText("Instructor ID must be a number.");
                            return;
                        }
                    } else {
                        instructorId = currentUser.getId();
                    }
                } else {
                    instructorId = currentUser.getId();
                }
            }
        } else {
            if (currentUser != null && "ADMIN".equals(currentUser.getRole())) {
                String idStr = instructorIdField.getText().trim();
                try {
                    instructorId = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    statusLabel.setText("Instructor ID must be a number.");
                    return;
                }
            } else {
                instructorId = selectedCourse.getInstructorId();
            }
        }

        int skillId = selectedSkill != null ? selectedSkill.getId() : 0;
        String level = selectedLevel != null ? selectedLevel : "Beginner";

        try {
            if (selectedCourse == null) {
                Course newCourse = new Course(title, description, instructorId, skillId, level);
                serviceCourse.ajouter(newCourse);
                statusLabel.setText("Course added successfully.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                selectedCourse.setTitle(title);
                selectedCourse.setDescription(description);
                selectedCourse.setInstructorId(instructorId);
                selectedCourse.setSkillId(skillId);
                selectedCourse.setSkillLevel(level);
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
        skillLevelComboBox.setValue("Beginner");
        selectedCourse = null;
        courseTable.getSelectionModel().clearSelection();
        statusLabel.setText("");

        entities.User currentUser = utils.SessionManager.getInstance().getCurrentUser();
        if (currentUser != null && !"ADMIN".equals(currentUser.getRole())) {
            setInstructorFieldVisible(false);
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
