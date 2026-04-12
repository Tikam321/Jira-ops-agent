# Model Context Protocol (MCP) - Technical Documentation

## What is MCP?

MCP (Model Context Protocol) is a standardized protocol that allows AI applications to connect to external tools and services. It provides a structured way for LLMs to call functions/tools and get results back.

---

## How MCP Works

### Simple Flow

```
User Query + Tool Definitions (JSON)  →  LLM  →  Tool Call Response (JSON)
```

### Example

**User says:** "assign SCRUM-3 to me"

**1. We send to LLM:**
```json
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "assign_issue",
        "description": "Assign a Jira issue to a user",
        "parameters": {
          "type": "object",
          "properties": {
            "issueKey": {"type": "string"},
            "assignee": {"type": "string"}
          },
          "required": ["issueKey", "assignee"]
        }
      }
    }
  ]
}
```

**2. LLM analyzes and returns:**
```json
{
  "tool_calls": [
    {
      "function": {
        "name": "assign_issue",
        "arguments": "{\"issueKey\":\"SCRUM-3\",\"assignee\":\"me\"}"
      }
    }
  ]
}
```

---

## MCP Request Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `model` | String | LLM model to use |
| `messages` | Array | Conversation (system + user) |
| `tools` | Array | Tool definitions |
| `tool_choice` | String | "auto" - LLM decides, "none" - no tool |
| `temperature` | Float | 0.0-1.0 (use 0.1-0.4 for tool calls) |
| `max_tokens` | Integer | Max response size |

---

## Tool Definition Structure

```json
{
  "type": "function",
  "function": {
    "name": "tool_name",
    "description": "What this tool does",
    "parameters": { /* JSON Schema */ }
  }
}
```

### JSON Schema Parameters

```json
{
  "type": "object",
  "properties": {
    "issueKey": {
      "type": "string",
      "description": "Issue key like PROJ-123"
    },
    "assignee": {
      "type": "string", 
      "description": "Username or 'me'"
    }
  },
  "required": ["issueKey", "assignee"]
}
```

---

## LLM Providers with Tool Calling

| Provider | Tool Calling Support | Format |
|----------|----------------------|--------|
| OpenAI | ✅ GPT-4, GPT-3.5 | `tool_calls` |
| Groq | ✅ Llama, Mixtral | `tool_calls` (OpenAI style) |
| Anthropic | ✅ Claude 3.5+ | `tool_calls` |
| Google | ✅ Gemini 1.5 | `functionCalls` |

---

## MCP Server vs MCP Client

### MCP Server
A program that exposes tools via MCP protocol, waiting for clients to connect.

**Our Implementation (JiraMcpServer):**
```java
@PostConstruct
public void startMcpServer() {
    StdioServerTransportProvider transport = new StdioServerTransportProvider(...);
    
    server = McpServer.sync(transport)
        .serverInfo("jira-mcp-server", "1.0.0")
        .toolCall(searchIssuesTool)
        .toolCall(assignIssueTool)
        .build();
}
```

### MCP Client
Code that connects TO an external MCP server to call tools.

**Our Implementation (McpClientService):**
```java
// Connects to external Node.js MCP server via stdin/stdout
Process process = getMcpProcess();
OutputStream out = process.getOutputStream();
out.write((requestJson + "\n").getBytes());
```

---

## Our Application Architecture

```
User Query ("assign SCRUM-3 to me")
    ↓
CommandController (/api/v1/mcp-query)
    ↓
GroqMCP Service (LLM + Tool Definitions)
    ↓
LLM decides: { tool: "assign_issue", params: {...} }
    ↓
McpToolExecutor (routes to handler)
    ↓
JiraToolService (business logic)
    ↓
JiraApiService (REST calls to Atlassian)
    ↓
Atlassian API
```

### Layers

| Layer | Purpose |
|-------|---------|
| Frontend | User interface |
| Controller | REST endpoint |
| GroqMCPService | LLM + tool definitions |
| McpToolExecutor | Routes to correct tool |
| JiraToolService | Business logic |
| JiraApiService | Atlassian REST API |

---

## Our Agent Definition

**Yes, this is an AI Agent:**

- Input: Natural language ("assign SCRUM-3 to me")
- LLM (Brain): Decides which tool to call
- Tool Definitions: Available actions
- Execution: Executes tool and returns result
- Output: "Successfully assigned SCRUM-3 to you"

---

## MCP Implementation Options

### Option 1: Via REST API (What We Have)
```
MCP Server → REST Client → Atlassian API
```
We built `JiraApiService` using WebClient to call Atlassian REST API.

### Option 2: Via Java SDK
```
MCP Server → Atlassian Java SDK → Atlassian
```
Use Atlassian's Java SDK instead of custom REST client.

### Option 3: External MCP Server
```
MCP Client → Node.js MCP Server → Atlassian
```
Run Atlassian's official MCP server (Node.js) and connect via HTTP.

---

## Atlassian MCP Server (Official)

Atlassian provides an official MCP server:
- **GitHub:** `github.com/atlassian/atlassian-mcp-server`
- Provides: Full Jira + Confluence operations
- Language: Node.js

---

## Free Site API Access Limitation

Atlassian restricts API access on free/trial sites:

| Plan | API Access | Cost |
|------|------------|------|
| Free | LIMITED/RESTRICTED | $0 |
| Standard | Full | ~$8/user/month |
| Premium | Full + advanced | ~$14/user/month |

**Common Errors:**
- 403 Forbidden on many endpoints
- Works with paid sites, fails with free

---

## Comparison: Previous vs Current Design

### Previous (Direct)
```
User Query → GroqService → JiraApiService → Atlassian API
              (LLM describes, Code decides)
```

### Current (MCP)
```
User Query → GroqMCPService → McpToolExecutor → JiraApiService
                     ↓
              LLM returns structured tool call
```

| Aspect | Previous | Current |
|--------|----------|---------|
| LLM Role | Describes action | Returns tool call |
| Decision | Code (if/else) | LLM |
| Extensibility | Add code | Add tool definition |
| Debugging | Hard to trace | Clear in logs |

---

## Key Takeaways for Interview

1. **MCP is a protocol** - Pass tools (JSON) to LLM, get tool calls (JSON) back
2. **We built an AI Agent** - Natural language input → LLM decision → Tool execution → Result
3. **Still uses REST API** - MCP is the protocol, calls Atlassian REST underneath
4. **Tool definitions matter** - Good descriptions = correct tool selection
5. **Free sites have limitations** - API access restricted on free Atlassian sites

---

## MCP Java SDK

Official Java SDK: `github.com/modelcontextprotocol/java-sdk`

Used for:
- Building MCP servers in Java
- Building MCP clients in Java

---

*Generated: April 2026*
*Project: Jira Ops Agent*