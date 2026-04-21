package ai.mcp.agent.service;

import ai.mcp.agent.exception.OrchestratorException;
import ai.mcp.agent.model.AgentResult;
import ai.mcp.agent.tools.SubAgentTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class OrchestratorService {

    private static final int MAX_RETRIES = 2;
    private final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final ChatClient chatClient;
    private final SubAgentTools subAgentTools;
    private final ResultValidator validator;
    private final ObjectMapper objectMapper;

    public OrchestratorService(ChatClient.Builder builder,
                               SubAgentTools subAgentTools,
                               ResultValidator validator,
                               ObjectMapper objectMapper) {
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
    }

    public String orchestrate(String task) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Orchestrator attempt {}/{}", attempt, MAX_RETRIES);

                String raw = chatClient.prompt()
                        .user(task)
                        .tools(subAgentTools)
                        .call()
                        .content();

                return validateAndExtract(raw);

            } catch (OrchestratorException e) {
                lastException = e;
                logger.warn("Attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    logger.info("Retrying...");
                }
            }
        }

        throw new OrchestratorException(
                "All " + MAX_RETRIES + " attempts failed. Last error: " + lastException.getMessage());
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