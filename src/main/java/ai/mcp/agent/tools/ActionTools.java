package ai.mcp.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ActionTools {

    private final DbQueryTool dbQueryTool;

    public ActionTools(DbQueryTool dbQueryTool) {
        this.dbQueryTool = dbQueryTool;
    }

    @Tool("Query database for products by category")
    public String queryProductsByCategory(String category) {
        Map<String, Object> args = Map.of("category", category);
        McpSchema.CallToolResult result = dbQueryTool.execute(args);
        return extractContentAsString(result);
    }

    @Tool("Query database for products by name")
    public String queryProductsByName(String name) {
        Map<String, Object> args = Map.of("name", name);
        McpSchema.CallToolResult result = dbQueryTool.execute(args);
        return extractContentAsString(result);
    }

    @Tool("Get all products from database")
    public String getAllProducts() {
        Map<String, Object> args = Map.of();
        McpSchema.CallToolResult result = dbQueryTool.execute(args);
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

