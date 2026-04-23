# mcp-agent

An AI orchestration service that routes tasks to specialized tools via the Model Context Protocol (MCP). Built with Spring Boot, Spring AI, and Gemini.

**Companion service:** [mcp-tools-server](https://github.com/manu-nr-dev/mcp-server) — exposes the RAG and database tools this agent consumes.

---

## What It Does

`mcp-agent` receives a task, determines which tools are needed, delegates to them via MCP, validates the results, and returns a structured JSON response.

The orchestrator uses a ReAct-style loop with smart routing — it only calls the tools the task actually requires, not both on every request.

---

## Architecture

```
Client
  │
  ▼
OrchestratorController  (POST /api/orchestrator/task)
  │
  ▼
OrchestratorService  ──── retry loop (max 2 attempts)
  │                  ──── ScopedValue: AgentBudget + requestId
  │                  ──── prompt version tracking on startup
  │
  ├── delegateResearch (@Tool)
  │     └── ChatClient → mcp-tools-server → rag_lookup → pgvector
  │
  └── delegateAction (@Tool)
        └── ChatClient → mcp-tools-server → lookup_products → PostgreSQL
  │
  ▼
ResultValidator ── validates non-null fields
  │
  ▼
{ "research": "...", "action": "...", "summary": "..." }
```

---

## Smart Routing

The orchestrator does not blindly call both tools on every request:

| Task type | Tools called | LLM calls |
|---|---|---|
| Incident / runbook / past error | `delegateResearch` only | 2 |
| Product / database query | `delegateAction` only | 2 |
| Mixed | Both | 3 |

This was the primary latency fix — collapsing from 3 mandatory LLM calls to 2 for single-domain tasks.

---

## Observability

Every request carries a `requestId` (UUID) through all log lines:

```
[a3f9b2c1] Orchestrator attempt 1/2
[a3f9b2c1] delegateResearch result (tokens=142): ...
[a3f9b2c1] Budget: 312/5000 tokens used. Per agent: {orchestrator=170, researchAgent=142}
```

Token counts are real — extracted from `ChatResponse.getMetadata().getUsage()`, with a character-length estimate as fallback.

Prompt versions are tracked in `prompt_versions.log` on startup:

```
2026-04-22T10:00:00 agent=orchestrator hash=3f9a2c1 version=1
2026-04-22T11:30:00 agent=orchestrator hash=9b2f441 version=2
```

Live metrics available at `GET /ai-metrics` — returns the last 50 requests with token breakdown per agent.

---

## Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 (preview enabled) |
| Framework | Spring Boot 4.0.3 / Spring MVC / Tomcat |
| AI | Spring AI 1.1.2 BOM |
| LLM | Gemini 2.0 Flash via Google GenAI |
| MCP Client | `spring-ai-starter-mcp-client` |
| Serialization | Jackson |

---

## Key Design Decisions

### Why Spring AI over LangChain4j

Started with LangChain4j. Hit a hard bug: Tomcat cannot reliably flush SSE (Server-Sent Events) streams, which MCP requires. LangChain4j's MCP client depends on SSE. The connection would hang or deliver partial tool results unpredictably.

Spring AI's MCP client is built for WebFlux/Netty on the server side and handles SSE correctly on the client side. Switching resolved the issue entirely.

### Why lazy `ChatClient` construction in `SubAgentTools`

Early implementation built `ChatClient` at bean creation time. MCP tool registration happens asynchronously via SSE handshake — if `ChatClient` was built before the handshake completed, it had no tools. Lazy construction via `builder.build().mutate()` inside each `@Tool` method ensures tools are always registered before the client is used.

### Why sub-agents were collapsed

Original design had `ResearchAgent` and `ActionAgent` as separate components, each making their own LLM call. This produced 3 LLM calls per request minimum (orchestrator + 2 agents). At ~40s per Gemini call on free tier, worst case was 80s+ latency.

Collapsed to `@Tool` methods inside `SubAgentTools`. The orchestrator's LLM call now drives tool invocation directly. Result: 2 LLM calls for single-domain tasks, 3 only when both tools are genuinely needed.

### Why `ThreadLocal` for budget propagation (upgrade path: `ScopedValue`)

`AgentBudget` and `requestId` need to flow from `OrchestratorService` into `@Tool` methods without passing them as parameters (Spring AI controls the tool invocation signature). `ThreadLocal` works because Spring AI dispatches `@Tool` methods on the same thread as the caller.

Upgrade path: Java 23 stable `ScopedValue` — swap `BudgetHolder` only, no other changes needed. The class is already structured for this swap.

---

## Prerequisites

- Java 21
- `mcp-tools-server` running on port 8083
- Gemini API key

---

## Running

```bash
# Set your Gemini API key
export SPRING_AI_GOOGLE_GENAI_API_KEY=your_key_here

# Run
./mvnw spring-boot:run
```

Service starts on **port 8082**.

---

## API

### POST /api/orchestrator/task

```json
// Request
{ "task": "What runbook should I follow for a database connection timeout?" }

// Response
{
  "research": "Follow runbook RB-042: check connection pool settings...",
  "action": null,
  "summary": "Database timeout resolution steps retrieved from knowledge base."
}
```

### GET /ai-metrics

Returns last 50 requests with token usage per agent.

```json
[
  {
    "requestId": "a3f9b2c1-4d5e-...",
    "totalTokensUsed": 312,
    "tokenLimit": 5000,
    "perAgentTokens": {
      "orchestrator": 170,
      "researchAgent": 142
    },
    "budgetExceeded": false,
    "timestamp": "2026-04-22T10:32:15"
  }
]
```

---

## Related

- [mcp-tools-server](https://github.com/manu-nr-dev/mcp-server) — MCP tool server exposing RAG and database tools