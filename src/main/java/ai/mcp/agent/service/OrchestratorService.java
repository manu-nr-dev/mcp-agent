package ai.mcp.agent.service;

import ai.mcp.agent.tools.SubAgentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class OrchestratorService {

    private final ChatClient chatClient;
    private final SubAgentTools subAgentTools;

    public OrchestratorService(ChatClient chatClient, SubAgentTools subAgentTools) {
        this.chatClient = chatClient;
        this.subAgentTools = subAgentTools;
    }

    public String orchestrate(String task) {
        String raw=chatClient.prompt()
                .system("""
                        You are an orchestrator with two tools:
                        - delegateResearch: for RAG lookups, knowledge queries
                        - delegateAction: for DB queries, data retrieval
                        
                        You MUST call both tools. Do NOT simulate or describe tool calls.
                        After getting tool results, respond with ONLY valid JSON, no explanation, no preamble:
                        { "research": "...", "action": "...", "summary": "..." }
                        """)
                .user(task)
                .tools(subAgentTools)
                .call()
                .content();

        assert raw != null;
        int start = raw.indexOf('{');
        return start >= 0 ? raw.substring(start) : raw;
    }
}