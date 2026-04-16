package ai.mcp.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ResearchTools {

    private final RagSearchTool ragSearchTool;

    public ResearchTools(RagSearchTool ragSearchTool) {
        this.ragSearchTool = ragSearchTool;
    }

    @Tool("Search knowledge base for documentation and runbooks")
    public String searchKnowledgeBase(String query) {
        Map<String, Object> args = Map.of("query", query);
        McpSchema.CallToolResult result = ragSearchTool.execute(args);
        return extractContentAsString(result);
    }

    @Tool("Search for incident history and resolution patterns")
    public String searchIncidentHistory(String keyword) {
        Map<String, Object> args = Map.of("query", "incident " + keyword);
        McpSchema.CallToolResult result = ragSearchTool.execute(args);
        return extractContentAsString(result);
    }

    @Tool("Find runbook and procedure documentation")
    public String searchRunbooks(String topic) {
        Map<String, Object> args = Map.of("query", "runbook procedure " + topic);
        McpSchema.CallToolResult result = ragSearchTool.execute(args);
        return extractContentAsString(result);
    }

    private String extractContentAsString(McpSchema.CallToolResult result) {
        if (result.content() != null && !result.content().isEmpty()) {
            McpSchema.Content content = result.content().get(0);
            if (content instanceof McpSchema.TextContent) {
                return ((McpSchema.TextContent) content).text();
            }
            return content.toString();
        }
        return "No result found";
    }
}

