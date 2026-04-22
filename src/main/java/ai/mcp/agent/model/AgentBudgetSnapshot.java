package ai.mcp.agent.model;

import java.time.LocalDateTime;
import java.util.Map;

public record AgentBudgetSnapshot(
        String requestId,
        int totalTokensUsed,
        int tokenLimit,
        Map<String, Integer> perAgentTokens,
        boolean budgetExceeded,
        LocalDateTime timestamp
) {
    public static AgentBudgetSnapshot of(String requestId, AgentBudget budget) {
        return new AgentBudgetSnapshot(
                requestId,
                budget.getTotalTokensUsed(),
                budget.getTokenLimit(),
                budget.getPerAgentTokens(),
                budget.isBudgetExceeded(),
                LocalDateTime.now()
        );
    }
}
