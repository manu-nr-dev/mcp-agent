package ai.mcp.agent.agents;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolCallbackProviderFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(ToolCallbackProvider.class)
    public ToolCallbackProvider toolCallbackProvider() {
        // When MCP is disabled (e.g., tests or local dev without the MCP server),
        // still provide a ToolCallbackProvider so dependent beans can be created.
        return () -> new ToolCallback[0];
    }
}

