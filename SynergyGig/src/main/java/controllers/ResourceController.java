package controllers;

import entities.Course;
import entities.Resource;
import javafx.collections.FXCollections;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
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

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
