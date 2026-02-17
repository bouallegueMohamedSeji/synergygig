package controllers;

import entities.UserSkillView;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import services.ServiceUserSkill;
import utils.SessionManager;

import java.sql.SQLException;

public class UserSkillsController {

    @FXML
    private TableView<UserSkillView> skillsTable;
    @FXML
    private TableColumn<UserSkillView, String> colSkillName;
    @FXML
    private TableColumn<UserSkillView, String> colSkillLevel;

    private ServiceUserSkill serviceUserSkill;

    public UserSkillsController() {
        serviceUserSkill = new ServiceUserSkill();
    }

    @FXML
    public void initialize() {
        colSkillName.setCellValueFactory(new PropertyValueFactory<>("skillName"));
        colSkillLevel.setCellValueFactory(new PropertyValueFactory<>("skillLevel"));

        loadUserSkills();
    }

    private void loadUserSkills() {
        entities.User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null)
            return;

        try {
            ObservableList<UserSkillView> skills = FXCollections.observableArrayList(
                    serviceUserSkill.getUserSkills(currentUser.getId()));
            skillsTable.setItems(skills);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
