package controllers;

import entities.Post;
import entities.Task;
import entities.User;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import services.ServicePost;
import services.ServiceTask;
import services.ServiceUser;
import utils.AppThreadPool;
import utils.SessionManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Employee of the Month — ranks employees by task completion (weighted by priority)
 * and lets HR/Admin post the announcement to the community feed.
 */
public class EmployeeOfMonthController {

    @FXML private Label lblMonth;
    @FXML private Button btnCalculate, btnPost;
    @FXML private StackPane loadingPane;
    @FXML private VBox resultPane;

    // Winner spotlight
    @FXML private Label lblWinnerName, lblWinnerDept, lblTasksDone, lblHighPriority, lblScore;
    @FXML private TextArea txtAnnouncement;

    // Leaderboard table
    @FXML private TableView<RankedEmployee> leaderboard;
    @FXML private TableColumn<RankedEmployee, Number> colRank, colDone, colHigh, colTotal;
    @FXML private TableColumn<RankedEmployee, String> colName;

    private final ServiceTask serviceTask = new ServiceTask();
    private final ServiceUser serviceUser = new ServiceUser();
    private final ServicePost servicePost = new ServicePost();

    private RankedEmployee winner;

    @FXML
    private void initialize() {
        LocalDate now = LocalDate.now();
        lblMonth.setText(now.format(DateTimeFormatter.ofPattern("MMMM yyyy")));

        colRank.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().rank));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        colDone.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().done));
        colHigh.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().highPriority));
        colTotal.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().score));
    }

    @FXML
    private void handleCalculate() {
        btnCalculate.setDisable(true);
        loadingPane.setVisible(true);
        resultPane.setVisible(false);
        resultPane.setManaged(false);

        AppThreadPool.submit(() -> {
            try {
                List<Task> allTasks = serviceTask.recuperer();
                List<User> allUsers = serviceUser.recuperer();
                System.out.println("[EOM] Tasks fetched: " + allTasks.size() + ", Users: " + allUsers.size());

                Map<Integer, User> userMap = new HashMap<>();
                for (User u : allUsers) userMap.put(u.getId(), u);

                // Count completed tasks per assignee this month
                LocalDate now = LocalDate.now();
                int thisMonth = now.getMonthValue();
                int thisYear  = now.getYear();

                // score = completed tasks x priority weight (HIGH=3, MEDIUM=2, LOW=1)
                Map<Integer, int[]> stats = new HashMap<>(); // userId -> [done, highPriority, score]

                for (Task t : allTasks) {
                    if (!"DONE".equalsIgnoreCase(t.getStatus())) continue;
                    if (t.getAssigneeId() == 0) continue;

                    int uid = t.getAssigneeId();
                    stats.computeIfAbsent(uid, k -> new int[3]);
                    int[] s = stats.get(uid);
                    s[0]++; // done
                    int w = 1;
                    if ("HIGH".equalsIgnoreCase(t.getPriority())) {
                        w = 3;
                        s[1]++; // high priority count
                    } else if ("MEDIUM".equalsIgnoreCase(t.getPriority())) {
                        w = 2;
                    }
                    s[2] += w; // score
                }

                System.out.println("[EOM] DONE tasks with assignees: " + stats.size() + " employees");
                for (var entry : stats.entrySet()) {
                    System.out.println("[EOM]   User " + entry.getKey() + " -> done=" + entry.getValue()[0] + " score=" + entry.getValue()[2]);
                }

                // Build ranked list
                List<RankedEmployee> ranked = new ArrayList<>();
                for (var entry : stats.entrySet()) {
                    User u = userMap.get(entry.getKey());
                    String name = u != null ? (u.getFirstName() + " " + u.getLastName()) : "User #" + entry.getKey();
                    String dept = u != null && u.getDepartmentId() != null && u.getDepartmentId() > 0 ? "Dept #" + u.getDepartmentId() : "";
                    int[] s = entry.getValue();
                    ranked.add(new RankedEmployee(0, name, dept, s[0], s[1], s[2]));
                }
                ranked.sort((a, b) -> Integer.compare(b.score, a.score));
                for (int i = 0; i < ranked.size(); i++) ranked.get(i).rank = i + 1;

                RankedEmployee top = ranked.isEmpty() ? null : ranked.get(0);

                Platform.runLater(() -> {
                    btnCalculate.setDisable(false);
                    loadingPane.setVisible(false);
                    resultPane.setVisible(true);
                    resultPane.setManaged(true);

                    if (top == null) {
                        lblWinnerName.setText("No completed tasks this month");
                        lblWinnerDept.setText("");
                        lblTasksDone.setText("0");
                        lblHighPriority.setText("0");
                        lblScore.setText("0");
                        txtAnnouncement.setText("");
                        btnPost.setDisable(true);
                    } else {
                        winner = top;
                        lblWinnerName.setText(top.name);
                        lblWinnerDept.setText(top.dept);
                        lblTasksDone.setText(String.valueOf(top.done));
                        lblHighPriority.setText(String.valueOf(top.highPriority));
                        lblScore.setText(String.valueOf(top.score));

                        String month = now.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
                        txtAnnouncement.setText(
                            "🏆 Employee of the Month — " + month + " 🏆\n\n" +
                            "We are happy to announce that " + top.name +
                            " has been recognized as our Employee of the Month!\n\n" +
                            "📊 Stats: " + top.done + " tasks completed, " +
                            top.highPriority + " high-priority tasks, " +
                            "total score: " + top.score + "\n\n" +
                            "Congratulations and thank you for your outstanding contributions! 🎉"
                        );
                        btnPost.setDisable(false);
                    }

                    leaderboard.setItems(FXCollections.observableArrayList(
                            ranked.size() > 10 ? ranked.subList(0, 10) : ranked));
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    btnCalculate.setDisable(false);
                    loadingPane.setVisible(false);
                    resultPane.setVisible(true);
                    resultPane.setManaged(true);
                    lblWinnerName.setText("Error: " + ex.getMessage());
                });
            }
        });
    }

    @FXML
    private void handlePostToCommunity() {
        if (winner == null) return;
        String announcement = txtAnnouncement.getText().trim();
        if (announcement.isEmpty()) return;

        btnPost.setDisable(true);
        btnPost.setText("Posting...");

        AppThreadPool.submit(() -> {
            try {
                User me = SessionManager.getInstance().getCurrentUser();
                int authorId = me != null ? me.getId() : 1;
                Post post = new Post(authorId, announcement);
                post.setVisibility("PUBLIC");
                servicePost.ajouter(post);

                Platform.runLater(() -> {
                    btnPost.setText("✅ Posted!");
                    btnPost.setDisable(true);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    btnPost.setText("❌ Failed — retry");
                    btnPost.setDisable(false);
                });
            }
        });
    }

    // ── Navigation ──

    @FXML
    private void handleBack() {
        DashboardController.getInstance().navigateTo("/fxml/HRModule.fxml");
    }

    // ── Inner helper class ──

    public static class RankedEmployee {
        int rank;
        final String name;
        final String dept;
        final int done;
        final int highPriority;
        final int score;

        RankedEmployee(int rank, String name, String dept, int done, int highPriority, int score) {
            this.rank = rank;
            this.name = name;
            this.dept = dept;
            this.done = done;
            this.highPriority = highPriority;
            this.score = score;
        }
    }
}
