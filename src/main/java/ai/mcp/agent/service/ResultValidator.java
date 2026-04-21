package ai.mcp.agent.service;

import ai.mcp.agent.model.AgentResult;
import org.springframework.stereotype.Component;

@Component
public class ResultValidator {

    private static final int MIN_LENGTH = 10;

    public AgentResult validate(String agentName, String raw) {
        if (raw == null || raw.isBlank()) {
            return AgentResult.failure(agentName, AgentResult.Status.EMPTY,
                    "Agent returned null or blank response");
        }

        String trimmed = raw.strip();

        if (trimmed.length() < MIN_LENGTH) {
            return AgentResult.failure(agentName, AgentResult.Status.INVALID,
                    "Response too short to be meaningful: [" + trimmed + "]");
        }

        // Catch LLM error passthrough patterns
        if (trimmed.toLowerCase().contains("i'm sorry") ||
                trimmed.toLowerCase().contains("i cannot") ||
                trimmed.toLowerCase().contains("error:")) {
            return AgentResult.failure(agentName, AgentResult.Status.ERROR,
                    "Agent returned an error message: " + trimmed);
        }

        return AgentResult.success(agentName, trimmed);
    }
}