package ai.mcp.agent.controller;

import ai.mcp.agent.model.AgentBudgetSnapshot;
import ai.mcp.agent.service.MetricsStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai-metrics")
public class MetricsController {

    private final MetricsStore metricsStore;

    public MetricsController(MetricsStore metricsStore) {
        this.metricsStore = metricsStore;
    }

    @GetMapping
    public ResponseEntity<List<AgentBudgetSnapshot>> getMetrics() {
        return ResponseEntity.ok(metricsStore.getAll());
    }
}
