package controllers;

import entities.Course;
import entities.Resource;
import javafx.collections.FXCollections;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import services.ServiceCourse;
import services.ServiceResource;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ResourceController {

    @FXML
    private ComboBox<Course> courseComboBox;
    @FXML
    private ComboBox<String> typeComboBox;
    @FXML
    private TextField urlField;
    @FXML
    private TableView<Resource> resourceTable;
    @FXML
    private TableColumn<Resource, Integer> colId;
    @FXML
    private TableColumn<Resource, Integer> colCourseId;
    @FXML
    private TableColumn<Resource, String> colType;
    @FXML
    private TableColumn<Resource, String> colUrl;
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

    private ServiceResource serviceResource;
    private ServiceCourse serviceCourse;
    private Resource selectedResource;

    public ResourceController() {
        serviceResource = new ServiceResource();
        serviceCourse = new ServiceCourse();
    }

    @FXML
    public void initialize() {
        // Initialize columns
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCourseId.setCellValueFactory(new PropertyValueFactory<>("courseId"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colUrl.setCellValueFactory(new PropertyValueFactory<>("url"));

        // Initialize ComboBoxes
        typeComboBox.setItems(FXCollections.observableArrayList("VIDEO", "PDF"));
        loadCourses();

        // Load data
        loadResources();

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

        // Listen for selection
        resourceTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectResource(newVal);
            }
        });
    }

    private void loadCourses() {
        try {
            List<Course> courses = serviceCourse.recuperer();
            courseComboBox.setItems(FXCollections.observableArrayList(courses));

            // Define how to display Course in ComboBox
            courseComboBox.setConverter(new StringConverter<Course>() {
                @Override
                public String toString(Course course) {
                    return course == null ? "" : course.getId() + " - " + course.getTitle();
                }

                @Override
                public Course fromString(String string) {
                    return null; // Not needed
                }
            });

        } catch (SQLException e) {
            showError("Failed to load courses: " + e.getMessage());
        }
    }

    private void loadResources() {
        try {
            resourceTable.setItems(FXCollections.observableArrayList(serviceResource.recuperer()));
        } catch (SQLException e) {
            showError("Failed to load resources: " + e.getMessage());
        }
    }

    private void selectResource(Resource resource) {
        selectedResource = resource;
        urlField.setText(resource.getUrl());
        typeComboBox.setValue(resource.getType());

        // Find and select the course in ComboBox
        for (Course c : courseComboBox.getItems()) {
            if (c.getId() == resource.getCourseId()) {
                courseComboBox.setValue(c);
                break;
            }
        }
    }

    @FXML
    private void saveResource() {
        Course selectedCourse = courseComboBox.getValue();
        String type = typeComboBox.getValue();
        String url = urlField.getText().trim();

        // Auto-convert local paths (e.g., "C:\Users\...") to file URIs
        if (url.matches("^[a-zA-Z]:\\\\.*") || url.startsWith("\\\\")) {
            try {
                url = java.nio.file.Paths.get(url).toUri().toString();
            } catch (Exception e) {
                // If conversion fails, keep original (will likely fail later or be invalid)
                System.err.println("Failed to convert path to URI: " + e.getMessage());
            }
        }

        if (selectedCourse == null || type == null || url.isEmpty()) {
            statusLabel.setText("All fields are required.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        try {
            if (this.selectedResource == null) {
                // Add new
                Resource newResource = new Resource(selectedCourse.getId(), type, url);
                serviceResource.ajouter(newResource);
                statusLabel.setText("Resource added successfully.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                // Update
                this.selectedResource.setCourseId(selectedCourse.getId());
                this.selectedResource.setType(type);
                this.selectedResource.setUrl(url);
                serviceResource.modifier(this.selectedResource);
                statusLabel.setText("Resource updated successfully.");
                statusLabel.setStyle("-fx-text-fill: green;");
            }
            clearForm();
            loadResources();
        } catch (SQLException e) {
            showError("Operation failed: " + e.getMessage());
        }
    }

    @FXML
    private void deleteResource() {
        Resource selected = resourceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a resource to delete.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Resource");
        alert.setHeaderText("Delete this resource?");
        alert.setContentText("URL: " + selected.getUrl());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                serviceResource.supprimer(selected.getId());
                loadResources();
                clearForm();
                statusLabel.setText("Resource deleted.");
                statusLabel.setStyle("-fx-text-fill: green;");
            } catch (SQLException e) {
                showError("Delete failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void clearForm() {
        courseComboBox.setValue(null);
        typeComboBox.setValue(null);
        urlField.clear();
        selectedResource = null;
        resourceTable.getSelectionModel().clearSelection();
        statusLabel.setText("");
    }

    @FXML
    private void handleBrowseFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Resource File");

        // Add filters if needed, e.g. PDF and Video
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*"),
                new javafx.stage.FileChooser.ExtensionFilter("PDF Documents", "*.pdf"),
                new javafx.stage.FileChooser.ExtensionFilter("Videos", "*.mp4", "*.mkv", "*.avi"));

        // Show open dialog
        if (urlField.getScene() != null) {
            java.io.File file = fileChooser.showOpenDialog(urlField.getScene().getWindow());
            if (file != null) {
                // Convert to URI and set text
                urlField.setText(file.toURI().toString());
            }
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
