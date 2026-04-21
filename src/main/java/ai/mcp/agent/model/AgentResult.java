package ai.mcp.agent.model;

public record AgentResult(
        String agentName,
        String value,
        Status status,
        String failureReason
) {
    public enum Status { SUCCESS, EMPTY, INVALID, ERROR }

    public boolean isUsable() {
        return status == Status.SUCCESS;
    }

    public static AgentResult success(String agentName, String value) {
        return new AgentResult(agentName, value, Status.SUCCESS, null);
    }

    public static AgentResult failure(String agentName, Status status, String reason) {
        return new AgentResult(agentName, null, status, reason);
    }
}