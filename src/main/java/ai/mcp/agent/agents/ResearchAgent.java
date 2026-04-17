package ai.mcp.agent.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ResearchAgent {

    private final ChatClient chatClient;

    public ResearchAgent(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                You are a research specialist. You have access to the rag_lookup tool.
                Use the rag_lookup tool to search the knowledge base for information.
                Return the findings from the tool execution. Be concise and direct.
                """)
                .build();
    }

    public String research(String query) {
        return chatClient.prompt()
                .user(query)
                .call()
                .content();
    }
}
