package controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import services.ZAIService;
import utils.AppThreadPool;
import utils.SoundManager;

import java.util.*;

/**
 * HR Policy RAG-style chatbot.
 * Embeds a comprehensive HR policy "knowledge base" in the system prompt,
 * then answers user questions grounded in that context.
 */
public class HRPolicyChatController implements Stoppable {

    @FXML private ScrollPane chatScroll;
    @FXML private VBox chatBox;
    @FXML private TextField inputField;
    @FXML private Button sendBtn;

    private final ZAIService ai = new ZAIService();
    private final List<Map<String, String>> history = new ArrayList<>();
    private volatile boolean cancelled;

    private static final String HR_KNOWLEDGE_BASE = """
        === SynergyGig HR Policy Knowledge Base ===

        1. LEAVE POLICY
        - Annual Leave: 22 working days per year, accrued monthly. Carry-over max 5 days.
        - Sick Leave: 12 days/year with medical certificate required for 3+ consecutive days.
        - Maternity Leave: 16 weeks paid. Paternity Leave: 4 weeks paid.
        - Unpaid Leave: Up to 30 days with manager + HR approval.
        - Public Holidays: Follow national calendar; floating holidays: 2 per year.
        - Leave requests must be submitted at least 5 business days in advance (except emergencies).

        2. REMOTE WORK POLICY
        - Hybrid model: minimum 2 days in-office per week.
        - Fully remote by exception with VP approval.
        - Core hours: 10:00-16:00 local time for meetings/availability.
        - Home office stipend: $500/year for equipment.
        - VPN required for all remote access to company systems.

        3. CODE OF CONDUCT
        - Zero tolerance for harassment, discrimination, or bullying.
        - Dress code: business casual in-office; professional on client calls.
        - Confidentiality: NDA applies to all proprietary information.
        - Social media: Do not share internal data; add disclaimer for personal opinions.
        - Conflict of interest: Disclose any outside employment or financial interests.

        4. COMPENSATION & BENEFITS
        - Salary review: Annual in Q1, based on performance rating.
        - Bonus: Up to 15% of base salary, tied to individual + company KPIs.
        - Health Insurance: Company covers 80% of premium (employee + dependents).
        - Retirement: 401(k)/pension match up to 6%.
        - Education: $3,000/year tuition reimbursement for approved courses.
        - Gym/Wellness: $50/month wellness allowance.

        5. PERFORMANCE & DEVELOPMENT
        - Performance reviews: Semi-annual (June, December).
        - Rating scale: 1 (Needs Improvement) to 5 (Exceptional).
        - PIP (Performance Improvement Plan): 60-day structured program for rating ≤2.
        - Promotion criteria: Min 12 months in current role + rating ≥4 + manager nomination.
        - Internal transfers: Apply after 18 months; no manager veto.

        6. ONBOARDING
        - 2-week structured program: IT setup (Day 1), department intro (Week 1), buddy system (Week 2).
        - Probation period: 3 months with monthly check-ins.
        - Required training: Security Awareness, Anti-Harassment, Data Privacy within first 30 days.

        7. TERMINATION & OFFBOARDING
        - Notice period: 30 days for employees; 15 days during probation.
        - Exit interview: Mandatory with HR.
        - Final pay: Within 14 days of last working day.
        - Equipment return: All company assets returned on last day.
        - Non-compete: 6 months post-departure for direct competitors.

        8. EXPENSES & TRAVEL
        - Per diem: Domestic $75/day; International $120/day.
        - Flights: Economy for <6 hours; business class for 6+ hours.
        - Expense reports: Submit within 30 days; receipts required for amounts >$25.
        - Company credit card: Available for frequent travelers (manager approval).

        9. DATA & SECURITY
        - Password policy: 12+ characters, changed every 90 days, MFA required.
        - Data classification: Public, Internal, Confidential, Restricted.
        - BYOD: Allowed with MDM enrollment; company can remote-wipe work data.
        - Incident reporting: Report security incidents within 4 hours to IT Security.

        10. DIVERSITY & INCLUSION
        - Equal opportunity employer. Accommodations provided upon request.
        - Employee Resource Groups (ERGs) supported with company funding.
        - Bias training: Annual mandatory session for all managers.
        """;

    private static final String SYSTEM_PROMPT = """
        You are SynergyGig's HR Policy Assistant. Your role is to answer employee questions
        accurately using ONLY the company HR policy knowledge base provided below.

        Rules:
        - Ground every answer in the policy text. Cite the relevant section number.
        - If the policy doesn't cover a topic, say so and recommend contacting HR directly.
        - Be concise, friendly, and professional.
        - Use bullet points for multi-part answers.
        - Never invent policies that aren't in the knowledge base.

        """ + HR_KNOWLEDGE_BASE;

    @FXML
    private void initialize() {
        addBotMessage("👋 Hi! I'm the HR Policy Assistant. Ask me anything about leave, benefits, " +
                "remote work, conduct, compensation, or any other company policy.");
    }

    @FXML
    private void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();
        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);

        addUserMessage(text);
        history.add(Map.of("role", "user", "content", text));

        // Thinking indicator
        HBox thinking = buildBotBubble("Thinking...");
        thinking.setId("thinking-indicator");
        chatBox.getChildren().add(thinking);
        scrollToBottom();

        sendBtn.setDisable(true);
        inputField.setDisable(true);

        AppThreadPool.io(() -> {
            String reply = ai.chatWithHistory(SYSTEM_PROMPT, history);
            if (cancelled) return;
            history.add(Map.of("role", "assistant", "content", reply));
            Platform.runLater(() -> {
                chatBox.getChildren().removeIf(n -> "thinking-indicator".equals(n.getId()));
                addBotMessage(reply);
                sendBtn.setDisable(false);
                inputField.setDisable(false);
                inputField.requestFocus();
                SoundManager.getInstance().play(SoundManager.NOTIFICATION_POP);
            });
        });
    }

    private void addUserMessage(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(480);
        lbl.setStyle("-fx-background-color: #2C666E; -fx-text-fill: white; -fx-padding: 10 16; " +
                "-fx-background-radius: 16 16 4 16; -fx-font-size: 13;");
        HBox row = new HBox(lbl);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(0, 0, 0, 60));
        chatBox.getChildren().add(row);
        scrollToBottom();
    }

    private void addBotMessage(String text) {
        chatBox.getChildren().add(buildBotBubble(text));
        scrollToBottom();
    }

    private HBox buildBotBubble(String text) {
        Label icon = new Label("📋");
        icon.setStyle("-fx-font-size: 18;");

        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(480);
        lbl.setStyle("-fx-background-color: rgba(144,221,240,0.1); -fx-text-fill: #F0EDEE; " +
                "-fx-padding: 10 16; -fx-background-radius: 16 16 16 4; -fx-font-size: 13;");

        HBox row = new HBox(8, icon, lbl);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 60, 0, 0));
        return row;
    }

    private void scrollToBottom() {
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    @FXML
    private void handleBack() {
        DashboardController.getInstance().navigateTo("/fxml/HRModule.fxml");
    }

    @Override
    public void stop() {
        cancelled = true;
    }
}
