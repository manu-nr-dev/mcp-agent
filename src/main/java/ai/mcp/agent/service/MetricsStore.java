package ai.mcp.agent.service;

import ai.mcp.agent.model.AgentBudgetSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.List;

@Component
public class MetricsStore {

    private static final int MAX_SIZE = 50;
    private final ArrayDeque<AgentBudgetSnapshot> buffer = new ArrayDeque<>();

    public synchronized void record(AgentBudgetSnapshot snapshot) {
        if (buffer.size() >= MAX_SIZE) {
            buffer.pollFirst();
        }
        buffer.addLast(snapshot);
    }

    public synchronized List<AgentBudgetSnapshot> getAll() {
        return List.copyOf(buffer);
    }
}
