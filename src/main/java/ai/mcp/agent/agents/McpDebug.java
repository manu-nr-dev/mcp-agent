package ai.mcp.agent.agents;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class McpDebug {
    public McpDebug(ToolCallbackProvider toolCallbackProvider) {
        System.out.println("=== MCP BEAN TYPE: " + toolCallbackProvider.getClass().getName());
        System.out.println("=== MCP TOOLS: " +
                Arrays.stream(toolCallbackProvider.getToolCallbacks())
                        .map(t -> t.getToolDefinition().name())
                        .toList());
    }
}