package ai.mcp.agent.agents;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ActionAgent {
    @SystemMessage("""
        You are an action specialist. You have access to the lookup_products tool.
        Use this tool to query the database for products, execute queries, or perform tasks.
        Report the actual results from the tool execution. Be concise and direct.
        """)
    String execute(@UserMessage String task);
}
