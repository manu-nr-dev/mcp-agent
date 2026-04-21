package ai.mcp.agent.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class AgentBudget {

    private static final int DEFAULT_TOKEN_LIMIT = 5000;

    private final int tokenLimit;
    private final AtomicInteger totalTokensUsed = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Integer> perAgentTokens = new ConcurrentHashMap<>();

    public AgentBudget() {
        this.tokenLimit = DEFAULT_TOKEN_LIMIT;
    }

    public AgentBudget(int tokenLimit) {
        this.tokenLimit = tokenLimit;
    }

    public void record(String agentName, int tokens) {
        totalTokensUsed.addAndGet(tokens);
        perAgentTokens.merge(agentName, tokens, Integer::sum);
    }

    public boolean isBudgetExceeded() {
        return totalTokensUsed.get() > tokenLimit;
    }

    public int getTotalTokensUsed() {
        return totalTokensUsed.get();
    }

    public int getTokenLimit() {
        return tokenLimit;
    }

    public Map<String, Integer> getPerAgentTokens() {
        return Map.copyOf(perAgentTokens);
    }

    public String summary() {
        return String.format("Budget: %d/%d tokens used. Per agent: %s",
                totalTokensUsed.get(), tokenLimit, perAgentTokens);
    }
}
