package controllers;

import entities.Interview;
import entities.User;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import services.ServiceInterview;
import services.ServiceUser;
import utils.DialogHelper;
import utils.SessionManager;
import utils.SoundManager;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A monthly calendar popup for interview scheduling.
 * Admin/HR see all interviews, normal users see only their own.
 */
public class InterviewCalendarPopup {

    private YearMonth currentMonth;
    private Label monthYearLabel;
    private GridPane calendarGrid;
    private VBox popupContent;

    private final ServiceInterview serviceInterview = new ServiceInterview();
    private final ServiceUser serviceUser = new ServiceUser();
    private Map<Integer, User> userCache = new HashMap<>();
    private List<Interview> interviews = new ArrayList<>();

    private static final String[] DAY_NAMES = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] STATUS_COLORS = {
            "#3B82F6", // PENDING – blue
            "#22C55E", // ACCEPTED – green
            "#EF4444"  // REJECTED – red
    };

    public void show(javafx.scene.Node ownerNode) {
        currentMonth = YearMonth.now();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(null);
        dialog.setHeaderText(null);

        StackPane root = buildUI();

        DialogHelper.theme(dialog);
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(root);
        DialogHelper.hideCloseButton(pane);
        pane.getStyleClass().add("interview-calendar-dialog");
        pane.setMaxWidth(820);
        pane.setMaxHeight(640);
        pane.setPrefWidth(800);
        pane.setPrefHeight(620);

        loadData();
        renderMonth();

        dialog.showAndWait();
    }

    private StackPane buildUI() {
        popupContent = new VBox(0);
        popupContent.getStyleClass().add("interview-calendar-popup");
        popupContent.setPrefWidth(780);
        popupContent.setMaxWidth(780);

        // ── Header bar ──
        HBox header = buildHeader();

        // ── Day-of-week header ──
        GridPane dayHeader = new GridPane();
        dayHeader.getStyleClass().add("interview-cal-day-header");
        dayHeader.setPadding(new Insets(8, 0, 8, 0));
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHalignment(HPos.CENTER);
            dayHeader.getColumnConstraints().add(cc);

            Label dayLbl = new Label(DAY_NAMES[i]);
            dayLbl.getStyleClass().add("interview-cal-day-name");
            dayHeader.add(dayLbl, i, 0);
        }

        // ── Calendar grid ──
        calendarGrid = new GridPane();
        calendarGrid.getStyleClass().add("interview-cal-grid");
        calendarGrid.setGridLinesVisible(false);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            calendarGrid.getColumnConstraints().add(cc);
        }
        VBox.setVgrow(calendarGrid, Priority.ALWAYS);

        // ── Legend bar ──
        HBox legend = buildLegend();

        popupContent.getChildren().addAll(header, dayHeader, calendarGrid, legend);

        StackPane root = new StackPane(popupContent);
        root.getStyleClass().add("interview-calendar-root");
        return root;
    }

    private HBox buildHeader() {
        Button prevBtn = new Button("‹");
        prevBtn.getStyleClass().add("interview-cal-nav-btn");
        prevBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            currentMonth = currentMonth.minusMonths(1);
            renderMonth();
        });

        Button nextBtn = new Button("›");
        nextBtn.getStyleClass().add("interview-cal-nav-btn");
        nextBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            currentMonth = currentMonth.plusMonths(1);
            renderMonth();
        });

        Button todayBtn = new Button("today");
        todayBtn.getStyleClass().add("interview-cal-today-btn");
        todayBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            currentMonth = YearMonth.now();
            renderMonth();
        });

        monthYearLabel = new Label();
        monthYearLabel.getStyleClass().add("interview-cal-month-label");

        HBox leftNav = new HBox(6, prevBtn, nextBtn, todayBtn);
        leftNav.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, leftNav, spacer, monthYearLabel);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(16, 20, 10, 20));
        header.getStyleClass().add("interview-cal-header");
        return header;
    }

    private HBox buildLegend() {
        HBox legend = new HBox(20);
        legend.setAlignment(Pos.CENTER);
        legend.setPadding(new Insets(10, 0, 14, 0));

        String[][] items = {
                {"Pending", STATUS_COLORS[0]},
                {"Accepted", STATUS_COLORS[1]},
                {"Rejected", STATUS_COLORS[2]}
        };
        for (String[] item : items) {
            Region dot = new Region();
            dot.setMinSize(10, 10);
            dot.setMaxSize(10, 10);
            dot.setStyle("-fx-background-color: " + item[1] + "; -fx-background-radius: 5;");
            Label lbl = new Label(item[0]);
            lbl.getStyleClass().add("interview-cal-legend-text");
            HBox entry = new HBox(6, dot, lbl);
            entry.setAlignment(Pos.CENTER);
            legend.getChildren().add(entry);
        }
        return legend;
    }

    private void loadData() {
        try {
            List<User> users = serviceUser.recuperer();
            for (User u : users) userCache.put(u.getId(), u);

            User me = SessionManager.getInstance().getCurrentUser();
            List<Interview> all = serviceInterview.recuperer();

            if (me != null && isAdminOrHr(me.getRole())) {
                interviews = all;
            } else if (me != null) {
                // Normal user: only their interviews (as candidate or organizer)
                int myId = me.getId();
                interviews = all.stream()
                        .filter(i -> i.getCandidateId() == myId || i.getOrganizerId() == myId)
                        .collect(Collectors.toList());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isAdminOrHr(String role) {
        return "ADMIN".equals(role) || "HR_MANAGER".equals(role);
    }

    private void renderMonth() {
        monthYearLabel.setText(currentMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + currentMonth.getYear());

        calendarGrid.getChildren().clear();
        calendarGrid.getRowConstraints().clear();

        LocalDate firstOfMonth = currentMonth.atDay(1);
        int startDay = firstOfMonth.getDayOfWeek().getValue() % 7; // Sun=0
        int daysInMonth = currentMonth.lengthOfMonth();

        // Calculate total rows needed
        int totalCells = startDay + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7.0);

        for (int r = 0; r < rows; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setVgrow(Priority.ALWAYS);
            rc.setMinHeight(75);
            calendarGrid.getRowConstraints().add(rc);
        }

        LocalDate today = LocalDate.now();
        int dayNum = 1;

        // Previous month days
        LocalDate prevMonth = firstOfMonth.minusDays(startDay);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 7; col++) {
                int cellIndex = row * 7 + col;
                VBox cell = new VBox(2);
                cell.getStyleClass().add("interview-cal-cell");
                cell.setPadding(new Insets(4, 4, 4, 4));

                if (cellIndex < startDay) {
                    // Previous month
                    LocalDate d = prevMonth.plusDays(cellIndex);
                    Label numLbl = new Label(String.valueOf(d.getDayOfMonth()));
                    numLbl.getStyleClass().addAll("interview-cal-day-num", "interview-cal-day-other");
                    cell.getChildren().add(numLbl);
                    cell.getStyleClass().add("interview-cal-cell-other");
                } else if (dayNum <= daysInMonth) {
                    LocalDate date = firstOfMonth.plusDays(dayNum - 1);
                    Label numLbl = new Label(String.valueOf(dayNum));
                    numLbl.getStyleClass().add("interview-cal-day-num");

                    if (date.equals(today)) {
                        numLbl.getStyleClass().add("interview-cal-day-today");
                    }

                    cell.getChildren().add(numLbl);

                    // Add interviews for this date
                    List<Interview> dayInterviews = getInterviewsForDate(date);
                    int maxShow = 2;
                    for (int i = 0; i < Math.min(dayInterviews.size(), maxShow); i++) {
                        HBox eventChip = buildEventChip(dayInterviews.get(i));
                        cell.getChildren().add(eventChip);
                    }
                    if (dayInterviews.size() > maxShow) {
                        Label more = new Label("+" + (dayInterviews.size() - maxShow) + " more");
                        more.getStyleClass().add("interview-cal-more");
                        cell.getChildren().add(more);
                    }

                    // Click on cell → show day detail popup
                    final LocalDate clickDate = date;
                    final List<Interview> dayIntrvs = dayInterviews;
                    cell.setOnMouseClicked(e -> {
                        if (!dayIntrvs.isEmpty()) {
                            SoundManager.getInstance().play(SoundManager.INTERVIEW_SCHEDULED);
                            showDayDetail(clickDate, dayIntrvs, cell);
                        }
                    });
                    if (!dayInterviews.isEmpty()) {
                        cell.setStyle("-fx-cursor: hand;");
                    }

                    dayNum++;
                } else {
                    // Next month
                    int nextDayNum = cellIndex - startDay - daysInMonth + 1;
                    Label numLbl = new Label(String.valueOf(nextDayNum));
                    numLbl.getStyleClass().addAll("interview-cal-day-num", "interview-cal-day-other");
                    cell.getChildren().add(numLbl);
                    cell.getStyleClass().add("interview-cal-cell-other");
                }

                calendarGrid.add(cell, col, row);
            }
        }
    }

    private List<Interview> getInterviewsForDate(LocalDate date) {
        return interviews.stream()
                .filter(i -> i.getDateTime() != null)
                .filter(i -> {
                    LocalDate iDate = i.getDateTime().toLocalDateTime().toLocalDate();
                    return iDate.equals(date);
                })
                .sorted(Comparator.comparing(Interview::getDateTime))
                .collect(Collectors.toList());
    }

    private HBox buildEventChip(Interview interview) {
        String color = getStatusColor(interview.getStatus());
        String time = "";
        if (interview.getDateTime() != null) {
            time = interview.getDateTime().toLocalDateTime()
                    .format(DateTimeFormatter.ofPattern("h:mma")).toLowerCase();
        }

        // Get candidate name
        User candidate = userCache.get(interview.getCandidateId());
        String name = candidate != null
                ? candidate.getFirstName() + " " + candidate.getLastName().charAt(0) + "."
                : "User #" + interview.getCandidateId();

        Region dot = new Region();
        dot.setMinSize(6, 6);
        dot.setMaxSize(6, 6);
        dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");

        Label chipLabel = new Label(time + " " + name);
        chipLabel.getStyleClass().add("interview-cal-chip-text");
        chipLabel.setMaxWidth(90);

        HBox chip = new HBox(4, dot, chipLabel);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("interview-cal-chip");
        chip.setStyle("-fx-background-color: " + color + "22; -fx-background-radius: 4;");
        chip.setPadding(new Insets(1, 4, 1, 4));

        return chip;
    }

    private void showDayDetail(LocalDate date, List<Interview> dayInterviews, javafx.scene.Node anchor) {
        // Show a small tooltip/popup with full details
        Dialog<Void> detail = new Dialog<>();
        detail.setTitle(null);
        detail.setHeaderText(null);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));
        content.getStyleClass().add("interview-cal-detail");
        content.setPrefWidth(360);

        Label title = new Label(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));
        title.getStyleClass().add("interview-cal-detail-title");

        content.getChildren().add(title);

        for (Interview interview : dayInterviews) {
            VBox card = buildDetailCard(interview);
            content.getChildren().add(card);
        }

        DialogHelper.theme(detail);
        DialogPane pane = detail.getDialogPane();
        pane.setContent(content);
        DialogHelper.hideCloseButton(pane);
        pane.getStyleClass().add("interview-calendar-dialog");
        pane.setMaxWidth(400);

        detail.showAndWait();
    }

    private VBox buildDetailCard(Interview interview) {
        String color = getStatusColor(interview.getStatus());

        User organizer = userCache.get(interview.getOrganizerId());
        User candidate = userCache.get(interview.getCandidateId());
        String orgName = organizer != null
                ? organizer.getFirstName() + " " + organizer.getLastName() : "User #" + interview.getOrganizerId();
        String candName = candidate != null
                ? candidate.getFirstName() + " " + candidate.getLastName() : "User #" + interview.getCandidateId();

        String timeStr = "";
        if (interview.getDateTime() != null) {
            timeStr = interview.getDateTime().toLocalDateTime()
                    .format(DateTimeFormatter.ofPattern("h:mm a"));
        }

        VBox card = new VBox(6);
        card.getStyleClass().add("interview-cal-detail-card");
        card.setStyle("-fx-border-color: " + color + "; -fx-border-width: 0 0 0 3; -fx-border-radius: 0;");
        card.setPadding(new Insets(10, 12, 10, 12));

        // Time + Status badge
        Label timeLbl = new Label("⏰ " + timeStr);
        timeLbl.getStyleClass().add("interview-cal-detail-time");

        Label statusBadge = new Label(interview.getStatus());
        statusBadge.setStyle("-fx-background-color: " + color + "33; -fx-text-fill: " + color
                + "; -fx-padding: 2 8; -fx-background-radius: 10; -fx-font-size: 10;");

        HBox topRow = new HBox(8, timeLbl, new Region(), statusBadge);
        HBox.setHgrow(topRow.getChildren().get(1), Priority.ALWAYS);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Participants
        Label orgLbl = new Label("📋 Organizer: " + orgName);
        orgLbl.getStyleClass().add("interview-cal-detail-info");
        Label candLbl = new Label("👤 Candidate: " + candName);
        candLbl.getStyleClass().add("interview-cal-detail-info");

        card.getChildren().addAll(topRow, orgLbl, candLbl);

        // Meet link
        if (interview.getMeetLink() != null && !interview.getMeetLink().isEmpty()) {
            Hyperlink meetLink = new Hyperlink("🔗 Join Meeting");
            meetLink.getStyleClass().add("interview-cal-detail-link");
            meetLink.setOnAction(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(interview.getMeetLink()));
                } catch (Exception ignored) {}
            });
            card.getChildren().add(meetLink);
        }

        return card;
    }

    private String getStatusColor(String status) {
        if (status == null) return STATUS_COLORS[0];
        switch (status.toUpperCase()) {
            case "ACCEPTED": return STATUS_COLORS[1];
            case "REJECTED": return STATUS_COLORS[2];
            default: return STATUS_COLORS[0]; // PENDING
        }
    }
}
