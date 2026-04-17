package ai.mcp.agent.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ActionAgent {

    private final ChatClient chatClient;

    public ActionAgent(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                You are an action specialist. You have access to the lookup_products tool.
                Use this tool to query the database for products, execute queries, or perform tasks.
                Report the actual results from the tool execution. Be concise and direct.
                """)
                .build();
    }

    public String execute(String task) {
        return chatClient.prompt()
                .user(task)
                .call()
                .content();
    }
}
