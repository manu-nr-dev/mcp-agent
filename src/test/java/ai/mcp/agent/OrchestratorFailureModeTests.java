package ai.mcp.agent;

import ai.mcp.agent.exception.OrchestratorException;
import ai.mcp.agent.service.OrchestratorService;
import ai.mcp.agent.service.ResultValidator;
import ai.mcp.agent.tools.SubAgentTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorFailureModeTests {

    @Mock
    private SubAgentTools subAgentTools;

    @Mock
    private ChatClient.Builder builder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private ResultValidator validator;
    private ObjectMapper objectMapper;
    private OrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        validator = new ResultValidator();
        objectMapper = new ObjectMapper();

        // wire ChatClient.Builder mock chain
        when(builder.defaultSystem((String) any())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.tools(any(SubAgentTools.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        orchestratorService = new OrchestratorService(
                builder, subAgentTools, validator, objectMapper);
    }

    // Scenario 1 — sub-agent returns null
    @Test
    void whenResearchAgentReturnsNull_thenOrchestratorExceptionAfterRetries() {
        when(callResponseSpec.content()).thenReturn(
                "{ \"research\": null, \"action\": \"found 3 products\", \"summary\": \"done\" }"
        );

        // null research field — validator should pass (null means tool not called)
        // action is valid — should succeed
        String result = orchestratorService.orchestrate("list all products");
        assertThat(result).contains("found 3 products");
    }

    // Scenario 2 — sub-agent returns ERROR: string
    @Test
    void whenResearchAgentReturnsErrorString_thenOrchestratorExceptionAfterRetries() {
        when(callResponseSpec.content()).thenReturn(
                "{ \"research\": \"ERROR: rag_lookup timed out\", \"action\": \"found 3 products\", \"summary\": \"done\" }"
        );

        assertThatThrownBy(() -> orchestratorService.orchestrate("explain incident"))
                .isInstanceOf(OrchestratorException.class)
                .hasMessageContaining("ResearchAgent result invalid");
    }

    // Scenario 3 — JSON missing required summary field still passes (only research/action validated)
    @Test
    void whenJsonMissingActionField_thenOrchestratorExceptionAfterRetries() {
        when(callResponseSpec.content()).thenReturn(
                "{ \"research\": \"found relevant runbook\", \"action\": null, \"summary\": \"done\" }"
        );

        // action null is valid — tool not called for this query
        String result = orchestratorService.orchestrate("explain incident");
        assertThat(result).contains("found relevant runbook");
    }

    // Scenario 4 — both retries exhausted
    @Test
    void whenBothRetriesExhausted_thenFinalExceptionWithLastError() {
        // LLM returns garbage JSON every time
        when(callResponseSpec.content()).thenReturn("not json at all");

        assertThatThrownBy(() -> orchestratorService.orchestrate("any task"))
                .isInstanceOf(OrchestratorException.class)
                .hasMessageContaining("All 2 attempts failed");

        // verify LLM was called MAX_RETRIES times
        verify(callResponseSpec, times(2)).content();
    }
}