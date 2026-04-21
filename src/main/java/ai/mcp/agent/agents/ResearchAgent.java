package ai.mcp.agent.agents;

import ai.mcp.agent.tools.SubAgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class ResearchAgent {

    private final ChatClient.Builder builder;
    private final ToolCallbackProvider mcpToolCallbackProvider;
    private final Logger log = LoggerFactory.getLogger(ResearchAgent.class);

    public ResearchAgent(ChatClient.Builder builder,
                         ToolCallbackProvider mcpToolCallbackProvider) {
        this.builder = builder;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }

    public String research(String query) {
        log.info("MCP tools available: {}",
                Arrays.stream(mcpToolCallbackProvider.getToolCallbacks())
                        .map(t -> t.getToolDefinition().name())
                        .toList());
        return builder
                .defaultSystem("""
                        You are a research specialist. You have access to the rag_lookup tool.
                        Use the rag_lookup tool to search the knowledge base for information.
                        Return the findings from the tool execution. Be concise and direct.
                        Don't use this when asked about products.
                        """)
                .defaultToolCallbacks(mcpToolCallbackProvider)
                .build()
                .prompt()
                .user(query)
                .call()
                .content();
    }
}
