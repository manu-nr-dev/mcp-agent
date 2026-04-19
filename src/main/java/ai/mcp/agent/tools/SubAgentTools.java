package ai.mcp.agent.tools;

import ai.mcp.agent.agents.ActionAgent;
import ai.mcp.agent.agents.ResearchAgent;
import ai.mcp.agent.exception.SpawnBudgetExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SubAgentTools {

    private final ResearchAgent researchAgent;
    private final ActionAgent actionAgent;
    private final AtomicInteger spawnCount = new AtomicInteger(0);
    private static final int MAX_SPAWNS = 3;
    private final Logger logger= LoggerFactory.getLogger(SubAgentTools.class);

    public SubAgentTools(ResearchAgent researchAgent, ActionAgent actionAgent) {
        this.researchAgent = researchAgent;
        this.actionAgent = actionAgent;
    }

    @Tool(name = "delegateResearch", description = "Delegate a research question to the ResearchAgent. Use for RAG lookups, past incidents, and runbook queries.")
    public String delegateResearch(String query) {
        logger.info("research");
        checkSpawnBudget();
        String result=researchAgent.research(query);
        logger.info("ResearchAgent LLM result: {}", result);
        return result;
    }

    @Tool(name = "delegateAction", description = "Delegate an action task to the ActionAgent. Use for DB queries, HTTP calls, and remediation steps.")
    public String delegateAction(String task) {
        logger.info("action");
        checkSpawnBudget();
        String result=actionAgent.execute(task);
        logger.info("Action LLM result: {}", result);
        return result;
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
