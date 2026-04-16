package ai.mcp.agent.tools;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DbQueryTool {
    private static final Logger log = LoggerFactory.getLogger(DbQueryTool.class);
    private final JdbcTemplate jdbc;

    public DbQueryTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public McpServerFeatures.SyncToolSpecification specification() {
        // Define the input schema with optional category and name parameters
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "category", Map.of(
                                "type", "string",
                                "description", "Filter products by category (e.g., Electronics, Furniture)"
                        ),
                        "name", Map.of(
                                "type", "string",
                                "description", "Filter products by partial name match"
                        )
                ),
                List.of(),  // Empty required list - both args are optional
                false
        );

        // Create the tool definition
        McpSchema.Tool tool = new McpSchema.Tool(
                "lookup_products",
                "Searches the product database. Use this when the task involves finding products, "
                        + "checking prices, or listing items by category. "
                        + "Args: 'category' (e.g. Electronics, Furniture) OR 'name' (partial product name). "
                        + "Both args are optional — omit to get all products.",
                inputSchema
        );

        // Return the tool specification with handler
        return new McpServerFeatures.SyncToolSpecification(tool,
                (_exchange, args) -> execute(args));
    }

    public McpSchema.CallToolResult execute(Map<String, Object> args) {
        String requestId = "unknown";
        try {
            Class<?> ctxClass = Class.forName("ai.mcp.agent.AgentContext");
            var reqIdField = ctxClass.getDeclaredField("REQUEST_ID");
            reqIdField.setAccessible(true);
            Object reqIdOpt = reqIdField.get(null);
            requestId = reqIdOpt != null ? reqIdOpt.toString() : "unknown";
        } catch (Exception ignore) {}

        log.debug("[requestId={}] db_lookup invoked", requestId);

        List<Map<String, Object>> rows;

        if (args.containsKey("category")) {
            String category = (String) args.get("category");
            log.debug("DB query: category = {}", category);
            rows = jdbc.queryForList(
                    "SELECT id, name, price, category FROM products WHERE category ILIKE ?",
                    "%" + category + "%"
            );
        } else if (args.containsKey("name")) {
            String name = (String) args.get("name");
            log.debug("DB query: name = {}", name);
            rows = jdbc.queryForList(
                    "SELECT id, name, price, category FROM products WHERE name ILIKE ?",
                    "%" + name + "%"
            );
        } else {
            log.debug("DB query: all products");
            rows = jdbc.queryForList(
                    "SELECT id, name, price, category FROM products ORDER BY category, name"
            );
        }

        String result;
        if (rows.isEmpty()) {
            result = "No products found.";
        } else {
            result = rows.stream()
                    .map(r -> String.format("[%s] %s — ₹%.2f (%s)",
                            r.get("id"), r.get("name"),
                            r.get("price") instanceof Number ? ((Number) r.get("price")).doubleValue() : 0.0,
                            r.get("category")))
                    .collect(Collectors.joining("\n"));
        }

        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(result)))
                .isError(false)
                .build();
    }
}
