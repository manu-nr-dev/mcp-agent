package ai.mcp.agent.context;

import ai.mcp.agent.model.AgentBudget;

public class BudgetHolder {

    public static final ScopedValue<AgentBudget> BUDGET = ScopedValue.newInstance();
    public static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    private BudgetHolder() {}
}