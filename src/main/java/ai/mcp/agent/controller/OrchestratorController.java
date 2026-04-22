package ai.mcp.agent.controller;

import ai.mcp.agent.dto.TaskRequest;
import ai.mcp.agent.dto.TaskResponse;
import ai.mcp.agent.service.OrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrator")
public class OrchestratorController {

    private final OrchestratorService orchestratorService;

    public OrchestratorController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/task")
    public ResponseEntity<TaskResponse> handleIncident(
            @RequestBody TaskRequest request) {

        String result = orchestratorService.orchestrate(request.task());

        return ResponseEntity.ok(new TaskResponse(result));
    }
}
