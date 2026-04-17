package ai.mcp.agent.tools;

import ai.mcp.agent.agents.ActionAgent;
import ai.mcp.agent.agents.ResearchAgent;
import ai.mcp.agent.exception.SpawnBudgetExceededException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SubAgentTools {

    private final ResearchAgent researchAgent;
    private final ActionAgent actionAgent;
    private final AtomicInteger spawnCount = new AtomicInteger(0);
    private static final int MAX_SPAWNS = 3;

    public SubAgentTools(ResearchAgent researchAgent, ActionAgent actionAgent) {
        this.researchAgent = researchAgent;
        this.actionAgent = actionAgent;
    }

    @Tool(name = "delegateResearch", description = "Delegate a research question to the ResearchAgent. Use for RAG lookups, past incidents, and runbook queries.")
    public String delegateResearch(String query) {
        checkSpawnBudget();
        return researchAgent.research(query);
    }

    @Tool(name = "delegateAction", description = "Delegate an action task to the ActionAgent. Use for DB queries, HTTP calls, and remediation steps.")
    public String delegateAction(String task) {
        checkSpawnBudget();
        return actionAgent.execute(task);
    }

    private void checkSpawnBudget() {
        if (spawnCount.incrementAndGet() > MAX_SPAWNS) {
            throw new SpawnBudgetExceededException(
                    "Orchestrator spawn cap (" + MAX_SPAWNS + ") exceeded. Aggregating with available results.");
        }
    }

    public void resetSpawnCount() {
        spawnCount.set(0);
    }
}
