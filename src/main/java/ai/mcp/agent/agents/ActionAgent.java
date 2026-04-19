package ai.mcp.agent.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class ActionAgent {

    private final ChatClient.Builder builder;
    private final ToolCallbackProvider mcpToolCallbackProvider;

    public ActionAgent(ChatClient.Builder builder,
                       ToolCallbackProvider mcpToolCallbackProvider) {
        this.builder = builder;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }

    public String execute(String task) {
        return builder
                .defaultSystem("""
                        You are an action specialist. You have access to the lookup_products tool.
                        Use this tool to query the database for products, execute queries, or perform tasks.
                        Report the actual results from the tool execution. Be concise and direct.
                        """)
                .defaultToolCallbacks(mcpToolCallbackProvider)
                .build()
                .prompt()
                .user(task)
                .call()
                .content();
    }
}