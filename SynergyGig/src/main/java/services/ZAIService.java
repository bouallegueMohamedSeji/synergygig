package services;

import com.google.gson.*;
import utils.AppConfig;

import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;

/**
 * Z.AI GLM integration service with multi-key, multi-provider fallback.
 * Provides AI-powered features: contract generation, risk analysis,
 * applicant scoring, email drafting, chat, sprint planning, and more.
 *
 * Fallback chain (8 attempts):
 *   1. Z.AI primary key  → GLM-5
 *   2. Z.AI primary key  → GLM-4.7-Flash
 *   3. Z.AI backup  key  → GLM-5
 *   4. Z.AI backup  key  → GLM-4.7-Flash
 *   5. SiliconFlow key 1 → THUDM/glm-4-9b-chat
 *   6. SiliconFlow key 1 → Qwen/Qwen2.5-7B-Instruct
 *   7. SiliconFlow key 2 → THUDM/glm-4-9b-chat
 *   8. SiliconFlow key 2 → Qwen/Qwen2.5-7B-Instruct
 */
public class ZAIService {

    // ── Provider endpoints ──────────────────────────────────────────
    private static final String ZAI_URL = "https://api.z.ai/api/paas/v4/chat/completions";
    private static final String SILICONFLOW_URL = "https://api.siliconflow.cn/v1/chat/completions";

    // ── Models ──────────────────────────────────────────────────────
    private static final String ZAI_PRIMARY   = "glm-5";
    private static final String ZAI_FALLBACK  = "glm-4.7-flash";
    private static final String SF_PRIMARY    = "THUDM/glm-4-9b-chat";
    private static final String SF_FALLBACK   = "Qwen/Qwen2.5-7B-Instruct";

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // ── Fallback chain ──────────────────────────────────────────────
    private record Provider(String url, String key, String model, String label) {}
    private final List<Provider> providers = new ArrayList<>();

    public ZAIService() {
        String zaiKey       = AppConfig.get("zai.api.key", "");
        String zaiBackup    = AppConfig.get("zai.api.key.backup", "");
        String sfKey        = AppConfig.get("siliconflow.api.key", "");
        String sfBackup     = AppConfig.get("siliconflow.api.key.backup", "");

        // Build ordered fallback chain — skip entries with empty keys
        if (!zaiKey.isEmpty()) {
            providers.add(new Provider(ZAI_URL, zaiKey, ZAI_PRIMARY,  "Z.AI-1/GLM-5"));
            providers.add(new Provider(ZAI_URL, zaiKey, ZAI_FALLBACK, "Z.AI-1/GLM-4.7-Flash"));
        }
        if (!zaiBackup.isEmpty()) {
            providers.add(new Provider(ZAI_URL, zaiBackup, ZAI_PRIMARY,  "Z.AI-2/GLM-5"));
            providers.add(new Provider(ZAI_URL, zaiBackup, ZAI_FALLBACK, "Z.AI-2/GLM-4.7-Flash"));
        }
        if (!sfKey.isEmpty()) {
            providers.add(new Provider(SILICONFLOW_URL, sfKey, SF_PRIMARY,  "SiliconFlow-1/GLM-4-9B"));
            providers.add(new Provider(SILICONFLOW_URL, sfKey, SF_FALLBACK, "SiliconFlow-1/Qwen2.5-7B"));
        }
        if (!sfBackup.isEmpty()) {
            providers.add(new Provider(SILICONFLOW_URL, sfBackup, SF_PRIMARY,  "SiliconFlow-2/GLM-4-9B"));
            providers.add(new Provider(SILICONFLOW_URL, sfBackup, SF_FALLBACK, "SiliconFlow-2/Qwen2.5-7B"));
        }

        if (providers.isEmpty()) {
            System.err.println("⚠ No AI API keys configured in config.properties");
        } else {
            System.out.println("✅ AI fallback chain: " + providers.size() + " providers configured");
        }
    }

    /**
     * Called after API keys are updated in AppConfig.
     * Since ZAIService is instantiated fresh on each use, this just logs confirmation.
     * Any subsequent {@code new ZAIService()} will pick up the new keys automatically.
     */
    public static void refreshInstance() {
        System.out.println("🔄 API keys updated — next ZAIService instance will use new keys");
    }

    // ==================== Core Chat Completion ====================

    /**
     * Send a chat completion request — walks the full fallback chain.
     * Scenarios handled:
     *   • 429 Insufficient balance / rate limit → next provider
     *   • 401 Invalid API key               → next provider
     *   • 500+ Server error                 → next provider
     *   • Timeout / network error           → next provider
     *   • All providers exhausted           → graceful error message
     */
    public String chat(String systemPrompt, String userMessage) {
        JsonArray messages = buildMessages(systemPrompt, userMessage);
        return callWithFallback(messages);
    }

    /**
     * Send a chat completion with full message history (for chat-with-contract).
     */
    public String chatWithHistory(String systemPrompt, List<Map<String, String>> messages) {
        JsonArray msgArray = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        msgArray.add(sysMsg);
        for (Map<String, String> msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.get("role"));
            m.addProperty("content", msg.get("content"));
            msgArray.add(m);
        }
        return callWithFallback(msgArray);
    }

    // ── Internal: build system+user message array ───────────────────
    private JsonArray buildMessages(String systemPrompt, String userMessage) {
        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
        return messages;
    }

    // ── Internal: walk fallback chain ───────────────────────────────
    private String callWithFallback(JsonArray messages) {
        if (providers.isEmpty()) return "AI service not configured — no API keys found.";

        for (int i = 0; i < providers.size(); i++) {
            Provider p = providers.get(i);
            String result = callProvider(p, messages);
            if (result != null) return result;

            // Log fallback
            if (i + 1 < providers.size()) {
                Provider next = providers.get(i + 1);
                System.out.println("⚠ " + p.label() + " failed, falling back to " + next.label() + "...");
            }
        }

        System.err.println("❌ All " + providers.size() + " AI providers exhausted.");
        return "AI service temporarily unavailable — all providers exhausted. Please try again later.";
    }

    // ── Internal: call a single provider ────────────────────────────
    private String callProvider(Provider provider, JsonArray messages) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", provider.model());
            body.add("messages", messages);
            body.addProperty("temperature", 0.7);
            body.addProperty("stream", false);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(provider.url()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + provider.key())
                    .POST(BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());

            // Retriable errors: 429 (rate limit/balance), 401 (bad key), 5xx (server error)
            if (resp.statusCode() == 429 || resp.statusCode() == 401 || resp.statusCode() >= 500) {
                System.err.println("❌ " + provider.label() + " → " + resp.statusCode() + ": " + resp.body());
                return null;  // try next provider
            }
            // Other client errors (400, 403, 404) — still try next but log
            if (resp.statusCode() >= 400) {
                System.err.println("❌ " + provider.label() + " → " + resp.statusCode() + ": " + resp.body());
                return null;
            }

            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();
        } catch (java.net.http.HttpTimeoutException e) {
            System.err.println("⏱ " + provider.label() + " timed out: " + e.getMessage());
            return null;  // try next provider
        } catch (java.io.IOException e) {
            System.err.println("🌐 " + provider.label() + " network error: " + e.getMessage());
            return null;  // try next provider
        } catch (Exception e) {
            System.err.println("❌ " + provider.label() + " error: " + e.getMessage());
            return null;  // try next provider
        }
    }

    // ==================== Applicant Scoring (Recruitment AI) ====================

    /**
     * Score an applicant against an offer's requirements.
     * Returns JSON: {"score": 82, "strengths": [...], "gaps": [...], "summary": "..."}
     */
    public String scoreApplicant(String offerTitle, String requiredSkills, String coverLetter, String applicantName) {
        String system = """
            You are an expert AI recruitment analyst. Evaluate how well a candidate matches a job offer.
            Return ONLY valid JSON with this exact structure:
            {"score": <0-100>, "strengths": ["strength1", "strength2"], "gaps": ["gap1", "gap2"], "summary": "<2 sentences>"}
            Be fair but thorough. Consider transferable skills. Score 70+ means strong fit.
            """;
        String user = String.format(
            "Offer: %s\nRequired Skills: %s\nApplicant: %s\nCover Letter: %s\n\nAnalyze the fit and return JSON.",
            offerTitle, requiredSkills, applicantName, coverLetter
        );
        return chat(system, user);
    }

    // ==================== Contract Generation ====================

    /**
     * Generate contract terms from offer + applicant details.
     */
    public String generateContract(String offerTitle, String offerDescription, double amount,
                                    String applicantName, String startDate, String endDate) {
        String system = """
            You are a professional contract writer for SynergyGig platform.
            Generate clear, comprehensive contract terms covering:
            1. Scope of work (based on offer description)
            2. Duration and key dates
            3. Compensation and payment terms
            4. Deliverables and milestones
            5. Confidentiality and IP ownership
            6. Termination conditions
            7. Dispute resolution
            Write in formal legal language. Be specific and professional.
            Return ONLY the contract terms text, no JSON wrapping.
            """;
        String user = String.format(
            "Generate a contract for:\nOffer: %s\nDescription: %s\nAmount: $%.2f\nContractor: %s\nStart: %s\nEnd: %s",
            offerTitle, offerDescription, amount, applicantName, startDate, endDate
        );
        return chat(system, user);
    }

    // ==================== Risk Analysis ====================

    /**
     * Analyze contract risk. Returns JSON: {"risk_score": 45, "factors": [...], "recommendations": [...]}
     */
    public String analyzeRisk(String contractTerms, double amount, String duration) {
        String system = """
            You are a contract risk analyst. Evaluate the risk of a contract on a scale of 0-100.
            0-30 = Low risk (green), 31-70 = Medium risk (yellow), 71-100 = High risk (red).
            Consider: financial exposure, vague terms, duration, legal gaps, liability issues.
            Return ONLY valid JSON: {"risk_score": <0-100>, "factors": ["factor1", ...], "recommendations": ["rec1", ...]}
            """;
        String user = String.format("Contract Terms:\n%s\n\nAmount: $%.2f\nDuration: %s", contractTerms, amount, duration);
        return chat(system, user);
    }

    // ==================== Contract Summary ====================

    /**
     * Summarize a contract in 2-3 sentences.
     */
    public String summarizeContract(String contractTerms) {
        String system = "You are a contract summarizer. Provide a clear, concise 2-3 sentence summary of the key terms. Include: what work is being done, the compensation, and the duration. Return only the summary text.";
        return chat(system, "Summarize this contract:\n" + contractTerms);
    }

    // ==================== Improve Contract Terms ====================

    /**
     * Suggest improvements to contract terms.
     */
    public String improveTerms(String contractTerms) {
        String system = "You are a senior legal advisor. Review the contract terms and provide an improved version that is clearer, more protective for both parties, and more professional. Return only the improved contract terms.";
        return chat(system, "Improve these contract terms:\n" + contractTerms);
    }

    // ==================== Email Drafting ====================

    /**
     * Draft an email for application accept/reject or contract notification.
     * @param type "ACCEPTED", "REJECTED", or "CONTRACT_READY"
     * @param style "Professional", "Friendly", or "Direct"
     */
    public String draftEmail(String type, String style, String recipientName,
                             String offerTitle, String additionalContext) {
        String system = String.format("""
            You are a professional email writer for SynergyGig HR platform.
            Style: %s. Write a %s notification email.
            Structure: Subject line, then email body.
            Format: Start with "Subject: ..." on first line, then blank line, then body.
            Keep it under 200 words. Be %s.
            """,
            style,
            type.equals("ACCEPTED") ? "job offer acceptance" :
            type.equals("REJECTED") ? "diplomatic rejection" : "contract delivery",
            style.equals("Direct") ? "brief and action-oriented" :
            style.equals("Friendly") ? "warm but professional" : "polished and respectful"
        );
        String user = String.format(
            "Recipient: %s\nOffer: %s\nType: %s\nContext: %s\n\nDraft the email.",
            recipientName, offerTitle, type, additionalContext
        );
        return chat(system, user);
    }

    // ==================== Chat with Contract ====================

    /**
     * Answer a question about a specific contract.
     */
    public String chatWithContract(String contractTerms, List<Map<String, String>> chatHistory, String question) {
        String system = "You are a helpful contract assistant. Answer questions about the following contract accurately and concisely. If the answer isn't in the contract, say so.\n\nContract:\n" + contractTerms;
        List<Map<String, String>> messages = new ArrayList<>(chatHistory);
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", question);
        messages.add(userMsg);
        return chatWithHistory(system, messages);
    }

    // ==================== Sprint Planner ====================

    /**
     * Plan a sprint from a list of tasks.
     * Returns JSON with story points, capacity, and recommended scope.
     */
    public String planSprint(String taskListJson, int teamSize, int sprintDays) {
        String system = """
            You are an agile coach. Plan a 2-week sprint using Fibonacci estimation (1/2/3/5/8/13).
            Calculate capacity: team_size × sprint_days × 6 productive hours. Target 70-80% capacity.
            Return ONLY valid JSON:
            {"tasks": [{"id": <id>, "title": "<title>", "points": <1-13>, "reason": "<why>"}],
             "total_points": <sum>, "capacity_points": <capacity>, "recommended_ids": [<ids that fit>],
             "warnings": ["<dependency or risk warnings>"]}
            """;
        String user = String.format("Team size: %d\nSprint days: %d\nTasks:\n%s", teamSize, sprintDays, taskListJson);
        return chat(system, user);
    }

    // ==================== Meeting Prep ====================

    /**
     * Generate a meeting agenda from project state.
     */
    public String prepMeeting(String projectName, String tasksJson, String teamJson) {
        String system = """
            You are a meeting preparation assistant. Generate a structured meeting agenda including:
            1. Numbered agenda items (max 8)
            2. Risk alerts (overdue tasks, blocked items) marked with ⚠
            3. Talking points per team member
            4. Suggested time allocation per item
            Return formatted text (not JSON), using clear headings and bullet points.
            """;
        String user = String.format("Project: %s\nTasks:\n%s\nTeam:\n%s\n\nGenerate the meeting agenda.", projectName, tasksJson, teamJson);
        return chat(system, user);
    }

    // ==================== Decision Helper ====================

    /**
     * Help compare options using weighted criteria analysis.
     */
    public String helpDecide(String question, String optionsJson, String criteriaJson) {
        String system = """
            You are a decision coach. Apply weighted criteria scoring to compare options.
            Process: 1) Evaluate each option against each criterion (1-10). 2) Apply weights. 3) Sum scores.
            4) Check for biases. 5) Recommend with confidence (High/Medium/Low).
            Return formatted text with: comparison table, scores, bias check, recommendation.
            """;
        String user = String.format("Decision: %s\nOptions: %s\nCriteria: %s", question, optionsJson, criteriaJson);
        return chat(system, user);
    }

    // ==================== Meeting Notes Summarizer ====================

    /**
     * Summarize a meeting transcript into structured notes.
     */
    public String summarizeMeeting(String transcript) {
        String system = """
            You are an expert meeting summarizer. Create a structured summary with:
            1. Key decisions made
            2. Action items: format as "- [ ] @Owner: Task — Due: Date"
            3. Open questions / parking lot
            4. Next steps
            Keep concise (under 1 page). Every action item must have an owner.
            """;
        return chat(system, "Meeting transcript:\n" + transcript + "\n\nCreate the summary.");
    }

    // ==================== Strategy Advisor ====================

    /**
     * SWOT analysis and strategic advice for an offer.
     */
    public String adviseOfferStrategy(String offerTitle, String description, double amount,
                                       String location, String requiredSkills) {
        String system = """
            You are a strategic advisor for a gig marketplace. Analyze the offer and provide:
            1. SWOT Analysis (Strengths, Weaknesses, Opportunities, Threats)
            2. Suggested price range (is the amount competitive?)
            3. Description improvement tips (3 specific suggestions)
            4. Best time to publish
            Return formatted text with clear section headings.
            """;
        String user = String.format(
            "Offer: %s\nDescription: %s\nAmount: $%.2f\nLocation: %s\nRequired Skills: %s",
            offerTitle, description, amount, location, requiredSkills
        );
        return chat(system, user);
    }

    // ==================== RAG Database Routing ====================

    /**
     * Classify a natural language question to determine which module should handle it.
     * Returns: "HR", "PM", "OFFERS", "TRAINING", "COMMUNITY", or "UNKNOWN"
     */
    public String routeQuestion(String question) {
        String system = """
            You are a question router for SynergyGig platform. Classify the user's question into one of these categories:
            - HR: employees, departments, attendance, leave, payroll, interviews
            - PM: projects, tasks, sprints, deadlines, team members
            - OFFERS: offers, gigs, applications, contracts, freelance work
            - TRAINING: courses, enrollments, certificates, learning
            - COMMUNITY: posts, messages, chat, video calls
            Return ONLY the category name (one word): HR, PM, OFFERS, TRAINING, or COMMUNITY.
            If unclear, return UNKNOWN.
            """;
        return chat(system, question).trim().toUpperCase();
    }

    // ==================== Offer Description Enhancer ====================

    /**
     * Enhance an offer description from bullet points into professional prose.
     */
    public String enhanceOfferDescription(String bulletPoints, String offerTitle) {
        String system = "You are a professional copywriter for a gig marketplace. Transform the bullet points into a polished, engaging offer description (3-4 paragraphs). Be specific and professional. Return only the enhanced description.";
        return chat(system, "Offer title: " + offerTitle + "\nBullet points:\n" + bulletPoints);
    }

    // ==================== AI Sprint Task Generator ====================

    /**
     * Generate smart project tasks using AI based on project name, description, and team info.
     * Returns JSON array of tasks: [{"title":"...", "description":"...", "priority":"HIGH/MEDIUM/LOW"}]
     */
    public String generateProjectTasks(String projectName, String projectDescription, int teamSize) {
        String system = """
            You are an expert project manager. Based on the project info, generate 6-10 smart, actionable tasks.
            Consider the team size and project scope. Prioritize correctly.
            Return ONLY a valid JSON array (no markdown/code fences):
            [{"title": "<task title>", "description": "<1-2 sentence description>", "priority": "HIGH|MEDIUM|LOW"}]
            """;
        String user = String.format("Project: %s\nDescription: %s\nTeam size: %d",
                projectName, projectDescription != null ? projectDescription : "No description", teamSize);
        return chat(system, user);
    }

    // ==================== Training Recommendations ====================

    /**
     * Recommend training courses for a user based on their role, completed courses, and interests.
     */
    public String recommendCourses(String userRole, String completedCourses, String availableCourses) {
        String system = """
            You are a learning & development advisor. Based on the user's role and completed courses,
            recommend which available courses they should take next and why.
            Prioritize skill gaps and career growth. Be concise and practical.
            Format as numbered recommendations with brief reasoning (max 5).
            """;
        String user = String.format("Role: %s\nCompleted courses: %s\nAvailable courses:\n%s",
                userRole, completedCourses.isEmpty() ? "None yet" : completedCourses, availableCourses);
        return chat(system, user);
    }

    // ==================== Sentiment Analysis ====================

    /**
     * Analyze the sentiment and tone of a community post.
     * Returns: sentiment (positive/negative/neutral), tone, toxicity flag, and suggestion.
     */
    public String analyzeSentiment(String postContent) {
        String system = """
            You are a content moderator and sentiment analyst. Analyze the post and return ONLY valid JSON:
            {"sentiment": "positive|negative|neutral|mixed",
             "confidence": 0.0-1.0,
             "tone": "<professional|casual|enthusiastic|frustrated|toxic|etc>",
             "toxic": true|false,
             "summary": "<one-line summary of the post mood>"}
            """;
        return chat(system, "Post content:\n" + postContent);
    }

    // ==================== AI Writing Assistant ====================

    /**
     * Rewrite or improve a community post draft.
     */
    public String improvePost(String draft, String style) {
        String system = "You are a writing assistant. Improve the post to be more " + style +
                ". Keep the core message but enhance clarity, grammar, and engagement. " +
                "Return ONLY the improved text, no explanations.";
        return chat(system, draft);
    }

    // ==================== HR Analytics & Insights ====================

    /**
     * Generate HR insights from attendance, leave, and payroll data summaries.
     */
    public String generateHRInsights(String attendanceData, String leaveData, String payrollData) {
        String system = """
            You are an HR analytics expert. Analyze the provided HR data summaries and generate actionable insights.
            Include: 1) Key observations 2) Potential issues/risks 3) Recommendations
            Be data-driven and concise. Use emoji indicators (✅ ⚠️ 🔴) for status. Max 10 bullet points.
            """;
        String user = String.format("Attendance Summary:\n%s\n\nLeave Summary:\n%s\n\nPayroll Summary:\n%s",
                attendanceData, leaveData, payrollData);
        return chat(system, user);
    }

    /**
     * Generate a personalized learning path for skill development.
     */
    public String generateLearningPath(String userRole, String skills, String goals) {
        String system = """
            You are a career development coach. Create a concise learning path with 4-6 steps.
            Each step: title, why it matters, estimated time. Consider progression from basics to advanced.
            Format clearly with numbered steps and emoji.
            """;
        String user = String.format("Role: %s\nCurrent skills: %s\nGoals: %s", userRole, skills, goals);
        return chat(system, user);
    }
}
