package ai.mcp.agent.config;

import ai.mcp.agent.tools.DbQueryTool;
import ai.mcp.agent.tools.RagSearchTool;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class McpServerConfig {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> tools(
            DbQueryTool dbQueryTool,
            RagSearchTool ragSearchTool) {
        return List.of(
                dbQueryTool.specification(),
                ragSearchTool.specification()
        );
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins("*").allowedMethods("*").allowedHeaders("*");
            }
        };
    }
}
