package ai.mcp.agent.tools;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class RagSearchTool {
    private static final Logger log = LoggerFactory.getLogger(RagSearchTool.class);
    private static final String RAG_URL = "http://localhost:8082/api/rag/search";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public McpServerFeatures.SyncToolSpecification specification() {
        // Define the input schema for the RAG search tool
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("query", Map.of(
                        "type", "string",
                        "description", "Search query for the knowledge base"
                )),
                List.of("query"),
                false
        );

        // Create the tool definition
        McpSchema.Tool tool = new McpSchema.Tool(
                "rag_lookup",
                "Search internal knowledge base for context on a topic. "
                        + "Use when the question requires domain-specific or internal knowledge. "
                        + "Be descriptive, as RAG uses semantic search. If there is enough context, it will provide effective results.",
                inputSchema
        );

        // Return the tool specification with handler
        return new McpServerFeatures.SyncToolSpecification(tool,
                (_exchange, args) -> execute(args));
    }

    public McpSchema.CallToolResult execute(Map<String, Object> args) {
        String requestId = "unknown";
        String userId = "unknown";
        try {
            Class<?> ctxClass = Class.forName("ai.mcp.agent.AgentContext");
            var reqIdField = ctxClass.getDeclaredField("REQUEST_ID");
            var userIdField = ctxClass.getDeclaredField("USER_ID");
            reqIdField.setAccessible(true);
            userIdField.setAccessible(true);
            Object reqIdOpt = reqIdField.get(null);
            Object userIdOpt = userIdField.get(null);
            requestId = reqIdOpt != null ? reqIdOpt.toString() : "unknown";
            userId = userIdOpt != null ? userIdOpt.toString() : "unknown";
        } catch (Exception ignore) {}

        log.debug("[requestId={}] [userId={}] rag_lookup invoked", requestId, userId);

        String query = (String) args.get("query");
        if (query == null || query.isBlank()) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("ERROR: 'query' argument is required for rag_lookup.")))
                    .isError(true)
                    .build();
        }

        query = query + " and be concise no long answers";
        String body = "{\"query\":\"" + query.replace("\"", "\\\"") + "\"}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RAG_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                String error = "ERROR: RAG service returned status " + response.statusCode();
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(error)))
                        .isError(true)
                        .build();
            }

            log.debug("RAG result for '{}': {}", query, response.body());
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(response.body().trim())))
                    .isError(false)
                    .build();
        } catch (Exception e) {
            log.error("Error calling RAG service", e);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("ERROR: " + e.getMessage())))
                    .isError(true)
                    .build();
        }
    }
}