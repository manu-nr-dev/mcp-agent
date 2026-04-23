package ai.mcp.agent.service;

import ai.mcp.agent.context.BudgetHolder;
import ai.mcp.agent.exception.OrchestratorException;
import ai.mcp.agent.model.AgentBudget;
import ai.mcp.agent.model.AgentBudgetSnapshot;
import ai.mcp.agent.model.AgentResult;
import ai.mcp.agent.tools.SubAgentTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;


@Service
public class OrchestratorService {

    private static final int MAX_RETRIES = 3;
    private final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final ChatClient chatClient;
    private final SubAgentTools subAgentTools;
    private final ResultValidator validator;
    private final ObjectMapper objectMapper;
    private final PromptVersionLogger promptVersionLogger;
    private final MetricsStore metricsStore;

    public OrchestratorService(ChatClient.Builder builder,
                               SubAgentTools subAgentTools,
                               ResultValidator validator,
                               ObjectMapper objectMapper, PromptVersionLogger promptVersionLogger, MetricsStore metricsStore) {
        this.chatClient = builder
                .defaultSystem("""
                You are an orchestrator with two tools:
                - delegateResearch: use ONLY for knowledge base queries, past incidents, runbook lookups
                - delegateAction: use ONLY for product searches and database queries
                
                Routing rules:
                - If the task is about incidents, errors, or procedures → call delegateResearch only
                - If the task is about products or data → call delegateAction only
                - If the task needs both → call both
                
                Do NOT call a tool unless the task requires it.
                After getting tool results, respond with ONLY valid JSON, no explanation, no preamble:
                { "research": "...", "action": "...", "summary": "..." }
                Use null for any tool you did not call.
                """)
                .build();
        this.subAgentTools = subAgentTools;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.promptVersionLogger = promptVersionLogger;
        this.metricsStore = metricsStore;
    }

    @PostConstruct
    public void logPromptVersion() {
        promptVersionLogger.hashAndLog("orchestrator", """
                You are an orchestrator with two tools:
                - delegateResearch: use ONLY for knowledge base queries, past incidents, runbook lookups
                - delegateAction: use ONLY for product searches and database queries
                
                Routing rules:
                - If the task is about incidents, errors, or procedures → call delegateResearch only
                - If the task is about products or data → call delegateAction only
                - If the task needs both → call both
                
                Do NOT call a tool unless the task requires it.
                After getting tool results, respond with ONLY valid JSON, no explanation, no preamble:
                { "research": "...", "action": "...", "summary": "..." }
                Use null for any tool you did not call.
                """);
    }

    public String orchestrate(String task) {
        AgentBudget budget = new AgentBudget(5000);
        String requestId = UUID.randomUUID().toString();
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            final int currentAttempt = attempt;
            try {
                String result = ScopedValue
                        .where(BudgetHolder.BUDGET, budget)
                        .where(BudgetHolder.REQUEST_ID, requestId)
                        .call(() -> {
                            logger.info("[{}] Orchestrator attempt {}/{}", requestId, currentAttempt, MAX_RETRIES);

                            ChatResponse response = chatClient.prompt()
                                    .user(task)
                                    .tools(subAgentTools)
                                    .call()
                                    .chatResponse();

                            String raw = response.getResult().getOutput().getText();
                            int tokens = extractTokens(response, raw);
                            budget.record("orchestrator", tokens);

                            if (budget.isBudgetExceeded()) {
                                logger.warn("[{}] Budget exceeded after orchestrator call: {}", requestId, budget.summary());
                            }

                            logger.info("[{}] {}", requestId, budget.summary());
                            return validateAndExtract(raw);
                        });

                // snapshot after success — outside ScopedValue scope, budget is fully populated
                metricsStore.record(AgentBudgetSnapshot.of(requestId, budget));
                return result;

            } catch (OrchestratorException e) {
                lastException = e;
                logger.warn("[{}] Attempt {}/{} failed: {}", requestId, attempt, MAX_RETRIES, e.getMessage());
            } catch (Exception e) {
                lastException = new OrchestratorException("Unexpected error: " + e.getMessage());
                logger.error("[{}] Unexpected error on attempt {}: {}", requestId, attempt, e.getMessage());
            }
        }

        throw new OrchestratorException(
                "All " + MAX_RETRIES + " attempts failed. Last error: " + lastException.getMessage());
    }

    private int extractTokens(ChatResponse response, String text) {
        try {
            Usage usage = response.getMetadata().getUsage();
            if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
                return usage.getTotalTokens().intValue();
            }
        } catch (Exception e) {
            logger.warn("Token metadata unavailable, falling back to estimate: {}", e.getMessage());
        }
        return text == null ? 0 : text.length() / 4;
    }

    private String validateAndExtract(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new OrchestratorException("Orchestrator returned empty response");
        }

        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            throw new OrchestratorException("No valid JSON block found in response: " + raw);
        }
        String jsonBlock = raw.substring(start, end + 1);

        try {
            Map<?, ?> parsed = objectMapper.readValue(jsonBlock, Map.class);

            AgentResult researchResult = validator.validate(
                    "ResearchAgent", (String) parsed.get("research"));
            AgentResult actionResult = validator.validate(
                    "ActionAgent", (String) parsed.get("action"));

// Only validate fields that were actually used
            if (parsed.get("research") != null && !researchResult.isUsable()) {
                throw new OrchestratorException(
                        "ResearchAgent result invalid: " + researchResult.failureReason());
            }
            if (parsed.get("action") != null && !actionResult.isUsable()) {
                throw new OrchestratorException(
                        "ActionAgent result invalid: " + actionResult.failureReason());
            }

            return jsonBlock;

        } catch (OrchestratorException e) {
            throw e;
        } catch (Exception e) {
            throw new OrchestratorException("Failed to parse aggregated response: " + e.getMessage());
        }
    }
}
