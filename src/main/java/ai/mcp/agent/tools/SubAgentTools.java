package ai.mcp.agent.tools;

import ai.mcp.agent.context.BudgetHolder;
import ai.mcp.agent.model.AgentBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SubAgentTools {

    private final ChatClient.Builder builder;
    private final ToolCallbackProvider mcpToolCallbackProvider;
    private final Logger logger = LoggerFactory.getLogger(SubAgentTools.class);

    public SubAgentTools(ChatClient.Builder builder,
                         ToolCallbackProvider mcpToolCallbackProvider) {
        this.builder = builder;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }

    @Tool(name = "delegateResearch", description = "Delegate a research question. Use for RAG lookups, past incidents, runbook queries.")
    public String delegateResearch(String query) {
        logger.info("delegateResearch input: {}", query);
        try {
            String result = builder.build().mutate()
                    .defaultSystem("""
                        You have access to the rag_lookup tool.
                        Use it to search the knowledge base. Be concise and direct.
                        """)
                    .defaultToolCallbacks(mcpToolCallbackProvider)
                    .build()
                    .prompt()
                    .user(query)
                    .call()
                    .content();

            AgentBudget agentbudget = BudgetHolder.get();
            if (agentbudget != null) {
                agentbudget.record("researchAgent", estimateTokens(result));
            }

            if (agentbudget.isBudgetExceeded()) {
                logger.warn("Budget exceeded after researchAgent: {}", agentbudget.summary());
            }

            logger.info("delegateResearch result: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("delegateResearch failed: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool(name = "delegateAction", description = "Delegate an action task. Use for DB queries, product lookups, remediation steps.")
    public String delegateAction(String task) {
        logger.info("delegateAction input: {}", task);

        try {
            String result = builder.build().mutate()
                    .defaultSystem("""
                            You have access to the lookup_products tool.
                            Use it to query the database. Be concise and direct.
                            """)
                    .defaultToolCallbacks(mcpToolCallbackProvider)
                    .build()
                    .prompt()
                    .user(task)
                    .call()
                    .content();

            AgentBudget agentbudget = BudgetHolder.get();
            if (agentbudget != null) {
                agentbudget.record("researchAgent", estimateTokens(result));
            }

            if (agentbudget.isBudgetExceeded()) {
                logger.warn("Budget exceeded after researchAgent: {}", agentbudget.summary());
            }

            logger.info("delegateAction result: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("delegateAction failed: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        // rough approximation: 1 token ≈ 4 chars
        return text.length() / 4;
    }
}