package ai.mcp.agent.service;

import ai.mcp.agent.tools.SubAgentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class OrchestratorService {

    private final ChatClient chatClient;
    private final SubAgentTools subAgentTools;

    public OrchestratorService(ChatClient.Builder builder, SubAgentTools subAgentTools) {
        this.chatClient = builder
                .defaultSystem("""
                        You are an orchestrator with two tools:
                        - delegateResearch: for RAG lookups, knowledge queries
                        - delegateAction: for DB queries, data retrieval
                        
                        You MUST call both tools. Do NOT simulate or describe tool calls.
                        After getting tool results, respond with ONLY valid JSON, no explanation, no preamble:
                        { "research": "...", "action": "...", "summary": "..." }
                        """)
                .build();
        this.subAgentTools = subAgentTools;
    }

    public String orchestrate(String task) {
        subAgentTools.resetSpawnCount();

        String raw = chatClient.prompt()
                .user(task)
                .tools(subAgentTools)
                .call()
                .content();

        assert raw != null;
        int start = raw.indexOf('{');
        return start >= 0 ? raw.substring(start) : raw;
    }
}