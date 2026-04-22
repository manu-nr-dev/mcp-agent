package ai.mcp.agent.tools;

import ai.mcp.agent.context.BudgetHolder;
import ai.mcp.agent.model.AgentBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
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
            ChatResponse response = builder.build().mutate()
                    .defaultSystem("""
            You have access to the rag_lookup tool.
            Use it to search the knowledge base. Be concise and direct.
            """)
                    .defaultToolCallbacks(mcpToolCallbackProvider)
                    .build()
                    .prompt()
                    .user(query)
                    .call()
                    .chatResponse();

            String result = response.getResult().getOutput().getText();
            int tokens = extractTokens(response, result);

            AgentBudget agentBudget = BudgetHolder.BUDGET.isBound() ? BudgetHolder.BUDGET.get() : null;
            String requestId = BudgetHolder.REQUEST_ID.isBound() ? BudgetHolder.REQUEST_ID.get() : "no-request-id";

            if (agentBudget != null) {
                agentBudget.record("researchAgent", tokens);
                if (agentBudget.isBudgetExceeded()) {
                    logger.warn("[{}] Budget exceeded after researchAgent: {}", requestId, agentBudget.summary());
                }
            }

            logger.info("[{}] delegateResearch result (tokens={}): {}", requestId, tokens, result);
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
            ChatResponse response = builder.build().mutate()
                    .defaultSystem("""
                            You have access to the lookup_products tool.
                            Use it to query the database. Be concise and direct.
                            """)
                    .defaultToolCallbacks(mcpToolCallbackProvider)
                    .build()
                    .prompt()
                    .user(task)
                    .call()
                    .chatResponse();

            AgentBudget agentBudget = BudgetHolder.BUDGET.isBound() ? BudgetHolder.BUDGET.get() : null;
            String requestId = BudgetHolder.REQUEST_ID.isBound() ? BudgetHolder.REQUEST_ID.get() : "no-request-id";
            String result = response.getResult().getOutput().getText();
            int tokens = extractTokens(response, result);

            if (agentBudget != null) {
                agentBudget.record("ActionAgent", tokens);
                if (agentBudget.isBudgetExceeded()) {
                    logger.warn("[{}] Budget exceeded after ActionAgent: {}", requestId, agentBudget.summary());
                }
            }

            logger.info("[{}] ActionAgent result (tokens={}): {}", requestId, tokens, result);
            return result;
        } catch (Exception e) {
            logger.error("delegateAction failed: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private int extractTokens(ChatResponse response, String text) {
        try {
            Usage usage = response.getMetadata().getUsage();
            if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
                return usage.getTotalTokens().intValue();
            }
        } catch (Exception e) {
            logger.warn("Token metadata unavailable, falling back to estimate: {}", e.getMessage());
        }
        return text == null ? 0 : text.length() / 4;
    }
}