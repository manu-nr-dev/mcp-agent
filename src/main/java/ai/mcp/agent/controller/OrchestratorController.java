package ai.mcp.agent.controller;

import ai.mcp.agent.dto.TaskRequest;
import ai.mcp.agent.dto.TaskResponse;
import ai.mcp.agent.service.OrchestratorService;
import ai.mcp.agent.tools.SubAgentTools;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrator")
public class OrchestratorController {

    private final OrchestratorService orchestratorService;
    private final SubAgentTools subAgentTools;

    public OrchestratorController(OrchestratorService orchestratorService, SubAgentTools subAgentTools) {
        this.orchestratorService = orchestratorService;
        this.subAgentTools = subAgentTools;
    }

    @PostMapping("/task")
    public ResponseEntity<TaskResponse> handleIncident(
            @RequestBody TaskRequest request) {

        subAgentTools.resetSpawnCount();
        String result = orchestratorService.orchestrate(request.task());

        return ResponseEntity.ok(new TaskResponse(result));
    }
}
