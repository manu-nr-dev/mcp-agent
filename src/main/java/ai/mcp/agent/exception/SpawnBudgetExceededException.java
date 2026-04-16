package ai.mcp.agent.exception;

public class SpawnBudgetExceededException extends RuntimeException {
    public SpawnBudgetExceededException(String message) {
        super(message);
    }
}