package ai.mcp.agent.agents;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "mcp.client.enabled", havingValue = "true")
public class McpConfig {

    @Bean
    public McpSyncClient mcpSyncClient() {
        var transport = HttpClientSseClientTransport.builder("http://localhost:8083").build();
        var client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("mcp-agent", "1.0.0"))
                .build();
        client.initialize();
        return client;
    }

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(McpSyncClient mcpSyncClient) {
        return new SyncMcpToolCallbackProvider(mcpSyncClient);
    }
}
