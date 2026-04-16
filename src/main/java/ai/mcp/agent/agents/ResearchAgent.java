package ai.mcp.agent.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ResearchAgent {
    @SystemMessage("""
        You are a research specialist. You have access to the rag_lookup tool.
        Use the rag_lookup tool to search the knowledge base for information.
        Return the findings from the tool execution. Be concise and direct.
        """)
    String research(@UserMessage String query);
}