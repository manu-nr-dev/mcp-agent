package ai.mcp.agent.controller;

import ai.mcp.agent.tools.DbQueryTool;
import ai.mcp.agent.tools.RagSearchTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stream")
public class McpStreamableHttpController {
    private static final Logger logger = LoggerFactory.getLogger(McpStreamableHttpController.class);
    private final List<McpServerFeatures.SyncToolSpecification> tools;
    private final RagSearchTool ragSearchTool;
    private final DbQueryTool dbQueryTool;

    public McpStreamableHttpController(List<McpServerFeatures.SyncToolSpecification> tools,
                                      RagSearchTool ragSearchTool,
                                      DbQueryTool dbQueryTool) {
        this.tools = tools;
        this.ragSearchTool = ragSearchTool;
        this.dbQueryTool = dbQueryTool;
    }

    @GetMapping(produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public StreamingResponseBody handleStreamableHttpConnection() {
        logger.info("New StreamableHttp connection established");
        return outputStream -> {
            try {
                String connectionMessage = String.format(
                    "data: {\"type\": \"connection\", \"message\": \"Connected to MCP Server with %d tools\"}\n\n",
                    tools.size()
                );
                outputStream.write(connectionMessage.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                String toolsMessage = String.format(
                    "data: {\"type\": \"tools\", \"count\": %d, \"message\": \"Available tools: %d\"}\n\n",
                    tools.size(), tools.size()
                );
                outputStream.write(toolsMessage.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                while (!Thread.currentThread().isInterrupted()) {
                    String heartbeat = "data: {\"type\": \"heartbeat\", \"timestamp\": " + System.currentTimeMillis() + "}\n\n";
                    outputStream.write(heartbeat.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    Thread.sleep(30000);
                }
            } catch (InterruptedException e) {
                logger.info("StreamableHttp connection interrupted");
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                logger.error("Error in StreamableHttp stream", e);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    logger.error("Error closing output stream", e);
                }
            }
        };
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String handleMcpMessage(@RequestBody String message) {
        logger.info("Received MCP message: {}", message);
        try {
            JsonNode jsonNode = new ObjectMapper().readTree(message);
            String method = jsonNode.get("method").asText();
            String jsonrpc = jsonNode.get("jsonrpc").asText();
            int id = jsonNode.get("id").asInt();

            if ("initialize".equals(method)) {
                String response = String.format(
                    "{\"jsonrpc\":\"%s\",\"id\":%d,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{\"tools\":{\"listChanged\":true}},\"serverInfo\":{\"name\":\"ai-mcp-agent\",\"version\":\"1.0.0\"}}}",
                    jsonrpc, id
                );
                logger.info("Responding to initialize request: {}", response);
                return response;
            } else if ("tools/list".equals(method)) {
                StringBuilder toolsJson = new StringBuilder();
                toolsJson.append("[");
                for (int i = 0; i < tools.size(); i++) {
                    McpServerFeatures.SyncToolSpecification toolSpec = tools.get(i);
                    if (i > 0) toolsJson.append(",");
                    try {
                        McpSchema.Tool tool = toolSpec.tool();
                        String toolName = tool.name();
                        String toolDescription = tool.description();
                        McpSchema.JsonSchema inputSchema = tool.inputSchema();
                        String schemaJson = String.format(
                            "{\"type\":\"%s\",\"properties\":%s,\"required\":%s}",
                            inputSchema.type(),
                            convertPropertiesToJson(inputSchema.properties()),
                            convertRequiredToJson(inputSchema.required())
                        );
                        toolsJson.append(String.format(
                            "{\"name\":\"%s\",\"description\":\"%s\",\"inputSchema\":%s}",
                            toolName, toolDescription, schemaJson
                        ));
                    } catch (Exception e) {
                        logger.warn("Could not parse tool specification", e);
                        toolsJson.append(String.format(
                            "{\"name\":\"tool_%d\",\"description\":\"Tool %d\",\"inputSchema\":{\"type\":\"object\"}}",
                            i + 1, i + 1
                        ));
                    }
                }
                toolsJson.append("]");
                String response = String.format(
                    "{\"jsonrpc\":\"%s\",\"id\":%d,\"result\":{\"tools\":%s}}",
                    jsonrpc, id, toolsJson
                );
                logger.info("Responding to tools/list request: {}", response);
                return response;
            } else if ("tools/call".equals(method)) {
                JsonNode params = jsonNode.get("params");
                if (params != null && params.has("name")) {
                    String toolName = params.get("name").asText();
                    JsonNode arguments = params.has("arguments") ? params.get("arguments") : null;
                    logger.info("Tool call requested: {} with arguments: {}", toolName, arguments);
                    try {
                        Map<String, Object> argumentsMap = new java.util.HashMap<>();
                        if (arguments != null && arguments.isObject()) {
                            arguments.fields().forEachRemaining(entry ->
                                argumentsMap.put(entry.getKey(), entry.getValue().asText())
                            );
                        }
                        McpSchema.CallToolResult toolResult = null;
                        if ("rag_lookup".equals(toolName)) {
                            toolResult = ragSearchTool.execute(argumentsMap);
                        } else if ("lookup_products".equals(toolName)) {
                            toolResult = dbQueryTool.execute(argumentsMap);
                        } else {
                            return String.format(
                                "{\"jsonrpc\":\"%s\",\"id\":%d,\"error\":{\"code\":-32602,\"message\":\"Tool not found: %s\"}}",
                                jsonrpc, id, toolName
                            );
                        }
                        StringBuilder contentJson = new StringBuilder("[");
                        List<McpSchema.Content> content = toolResult.content();
                        for (int i = 0; i < content.size(); i++) {
                            if (i > 0) contentJson.append(",");
                            McpSchema.Content c = content.get(i);
                            if (c instanceof McpSchema.TextContent) {
                                String text = ((McpSchema.TextContent) c).text();
                                contentJson.append("{\"type\":\"text\",\"text\":\"")
                                    .append(escapeJson(text))
                                    .append("\"}");
                            }
                        }
                        contentJson.append("]");
                        String response = String.format(
                            "{\"jsonrpc\":\"%s\",\"id\":%d,\"result\":{\"content\":%s}}",
                            jsonrpc, id, contentJson
                        );
                        logger.info("Tool executed successfully");
                        return response;
                    } catch (Exception e) {
                        logger.error("Error executing tool: {}", toolName, e);
                        return String.format(
                            "{\"jsonrpc\":\"%s\",\"id\":%d,\"error\":{\"code\":-32603,\"message\":\"Tool execution error: %s\"}}",
                            jsonrpc, id, e.getMessage()
                        );
                    }
                } else {
                    return String.format(
                        "{\"jsonrpc\":\"%s\",\"id\":%d,\"error\":{\"code\":-32602,\"message\":\"Invalid params\"}}",
                        jsonrpc, id
                    );
                }
            } else {
                logger.warn("Unsupported MCP method: {}", method);
                return String.format(
                    "{\"jsonrpc\":\"%s\",\"id\":%d,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}",
                    jsonrpc, id
                );
            }
        } catch (Exception e) {
            logger.error("Error processing MCP message", e);
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}";
        }
    }

    private String convertPropertiesToJson(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof Map) {
                sb.append(convertMapToJson((Map<String, Object>) entry.getValue()));
            } else {
                sb.append("\"").append(entry.getValue()).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String convertMapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String convertRequiredToJson(List<String> required) {
        if (required == null || required.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < required.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(required.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}

