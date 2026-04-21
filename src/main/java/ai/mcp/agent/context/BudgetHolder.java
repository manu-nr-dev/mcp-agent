package ai.mcp.agent.context;

import ai.mcp.agent.model.AgentBudget;

public class BudgetHolder {
    private static final ThreadLocal<AgentBudget> current = new ThreadLocal<>();

    public static void set(AgentBudget budget) { current.set(budget); }
    public static AgentBudget get() { return current.get(); }
    public static void clear() { current.remove(); }
}