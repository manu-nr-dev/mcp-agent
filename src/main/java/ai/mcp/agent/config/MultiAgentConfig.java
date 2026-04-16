package ai.mcp.agent.config;

import ai.mcp.agent.agents.ActionAgent;
import ai.mcp.agent.agents.OrchestratorAgent;
import ai.mcp.agent.agents.ResearchAgent;
import ai.mcp.agent.tools.SubAgentTools;
import ai.mcp.agent.tools.ResearchTools;
import ai.mcp.agent.tools.ActionTools;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class MultiAgentConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(@Value("${langchain4j.google-ai-gemini.chat-model.api-key}") String apiKey,
                                               @Value("${langchain4j.google-ai-gemini.chat-model.model-name:gemini-1.5-flash}") String modelName,
                                               @Value("${langchain4j.google-ai-gemini.chat-model.temperature:0.7}") double temperature,
                                               @Value("${langchain4j.google-ai-gemini.chat-model.max-tokens:1000}") int maxTokens) {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxOutputTokens(maxTokens)
                .build();
    }

    @Bean
    public ResearchAgent researchAgent(ChatLanguageModel model, McpToolProvider mcpToolProvider, ResearchTools researchTools) {
        return AiServices.builder(ResearchAgent.class)
                .chatLanguageModel(model)
                .tools(mcpToolProvider)
                .tools(researchTools)
                .build();
    }

    @Bean
    public ActionAgent actionAgent(ChatLanguageModel model, McpToolProvider mcpToolProvider, ActionTools actionTools) {
        return AiServices.builder(ActionAgent.class)
                .chatLanguageModel(model)
                .tools(mcpToolProvider)
                .tools(actionTools)
                .build();
    }


    @Bean
    @RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public SubAgentTools subAgentTools(ResearchAgent researchAgent, ActionAgent actionAgent) {
        return new SubAgentTools(researchAgent, actionAgent);
    }

    @Bean
    public OrchestratorAgent orchestratorAgent(
            ChatLanguageModel model,
            SubAgentTools subAgentTools) {  // Spring injects the proxy here

        return AiServices.builder(OrchestratorAgent.class)
                .chatLanguageModel(model)
                .tools(subAgentTools)
                .build();
    }
}