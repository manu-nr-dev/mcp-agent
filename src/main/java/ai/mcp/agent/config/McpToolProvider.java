package ai.mcp.agent.config;

import ai.mcp.agent.tools.DbQueryTool;
import ai.mcp.agent.tools.RagSearchTool;
import dev.langchain4j.agent.tool.Tool;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Provides MCP tools to LangChain4j agents.
 * Bridges between MCP tool specifications and LangChain4j tool framework.
 */
@Component
public class McpToolProvider {

    private final DbQueryTool dbQueryTool;
    private final RagSearchTool ragSearchTool;

    public McpToolProvider(DbQueryTool dbQueryTool, RagSearchTool ragSearchTool) {
        this.dbQueryTool = dbQueryTool;
        this.ragSearchTool = ragSearchTool;
    }

    /**
     * Tool method: Lookup products from database
     */
    @Tool("Searches the product database. Use this when the task involves finding products, checking prices, or listing items by category. Args: 'category' (e.g. Electronics, Furniture) OR 'name' (partial product name). Both args are optional — omit to get all products.")
    public String lookupProducts(String category, String name) {
        Map<String, Object> args = Map.of();
        if (category != null && !category.isBlank()) {
            args = Map.of("category", category);
        } else if (name != null && !name.isBlank()) {
            args = Map.of("name", name);
        }
        McpSchema.CallToolResult result = dbQueryTool.execute(args);
        return extractContentAsString(result);
    }

    /**
     * Tool method: Search RAG documents
     */
    @Tool("Searches RAG documents for relevant information. Use this for finding runbooks, past incidents, or documentation.")
    public String ragLookup(String query) {
        McpSchema.CallToolResult result = ragSearchTool.execute(Map.of("query", query));
        return extractContentAsString(result);
    }

    /**
     * Extract the first text content from a CallToolResult
     */
    private String extractContentAsString(McpSchema.CallToolResult result) {
        if (result.content() != null && !result.content().isEmpty()) {
            McpSchema.Content content = result.content().get(0);
            // TextContent is a subclass of Content with a text field
            if (content instanceof McpSchema.TextContent) {
                McpSchema.TextContent textContent = (McpSchema.TextContent) content;
                return textContent.text();
            }
            // Fallback if it's just a plain Content object
            return content.toString();
        }
        return "No result returned";
    }
}


