package ai.mcp.agent.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface OrchestratorAgent {
@SystemMessage("""
        You are an orchestrator. You receive a task to complete using available tools.
        
        Steps:
        1. Decompose into: (a) what needs to be researched, (b) what action needs to be taken
        2. Delegate research to researchAgent, actions to actionAgent
        3. You may spawn at most 3 sub-agent calls total across all sub-agents
        4. Aggregate results into a final response
        
        Always return: { "research": "...", "action": "...", "summary": "..." }
        """)
    String orchestrate(@UserMessage String incidentDescription);
}