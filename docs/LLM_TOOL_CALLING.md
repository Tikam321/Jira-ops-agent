# LLM Tool Calling - Technical Documentation

## Overview

This document explains how Large Language Models (LLMs) execute tool calls through API requests, specifically focusing on the Groq API implementation in this project.

---

## 1. Request Structure

When sending a request to an LLM with tool capabilities, the request body includes:

```java
Map<String, Object> requestBody = new HashMap<>();
requestBody.put("model", MODEL_NAME);
requestBody.put("messages", List.of(
    Map.of("role", "system", "content", systemPrompt), 
    Map.of("role", "user", "content", query)
));
requestBody.put("tools", tools);
requestBody.put("tool_choice", "auto");
requestBody.put("temperature", 0.3);
requestBody.put("max_tokens", 512);
```

### Parameter Explanation

| Parameter | Type | Description |
|-----------|------|-------------|
| `model` | String | LLM model to use (e.g., "llama-3.3-70b-versatile") |
| `messages` | Array | Conversation history (system prompt + user query) |
| `tools` | Array | Available tools with their definitions |
| `tool_choice` | String | How LLM selects tools ("auto", "none", "required") |
| `temperature` | Float | Randomness (0.0-1.0). Use 0.1-0.4 for tool calls |
| `max_tokens` | Integer | Maximum response size |

---

## 2. Tools Object Structure

The `tools` parameter follows a standardized format originating from **OpenAI's Function Calling** (June 2023), which became the industry standard.

### Standard Format:

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

### Breakdown:

#### 2.1 `type: "function"`
- Tells the LLM this is a callable function
- Not regular text response

#### 2.2 `function` object

| Field | Purpose |
|-------|---------|
| `name` | Unique identifier (no spaces, use underscore) |
| `description` | LLM uses this to decide WHEN to use the tool |
| `parameters` | JSON Schema defining input parameters |

#### 2.3 `parameters` - JSON Schema

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

## 3. How LLM Uses Tools

### Flow:

```
User Query + Tools Definition → LLM Analyzes → LLM Generates Tool Call
```

### Example:

**User says:** "assign SCRUM-3 to me"

**LLM sees:**
- Tool: `assign_issue` with params `issueKey`, `assignee`
- Required: `issueKey`, `assignee`

**LLM generates:**
```json
{
  "tool": "assign_issue",
  "arguments": {
    "issueKey": "SCRUM-3",
    "assignee": "me"
  }
}
```

### Key Points:

1. **Schema matters** - LLM uses `parameters` to know what args to include
2. **Descriptions help** - Good descriptions = correct parameter mapping
3. **Required fields** - LLM ensures all required params are present
4. **Type checking** - LLM matches types (string, number, etc.)

---

## 4. Response Format

When LLM decides to call a tool, it returns:

```json
{
  "choices": [{
    "message": {
      "tool_calls": [{
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "assign_issue",
          "arguments": "{\"issueKey\":\"SCRUM-3\",\"assignee\":\"me\"}"
        }
      }]
    }
  }]
}
```

**Note:** `arguments` is a **JSON string** inside the response - need to parse it!

### Parsing in Code:

```java
List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
String toolName = (String) function.get("name");
String arguments = (String) function.get("arguments");
Map<String, Object> params = objectMapper.readValue(arguments, Map.class);
```

---

## 5. Available LLM Providers with Tool Calling

| Provider | Tool Calling Support | Format |
|----------|---------------------|--------|
| **OpenAI** | ✅ GPT-4, GPT-3.5 Turbo | `tool_calls` |
| **Groq** | ✅ Llama 3.3, Mixtral | `tool_calls` (OpenAI style) |
| **Anthropic** | ✅ Claude 3.5+ | `tool_calls` |
| **Google** | ✅ Gemini 1.5 Pro | `functionCalls` |
| **Meta Llama 3** | ⚠️ Partial | Via fine-tuned versions |

---

## 6. Application Architecture

### Flow Diagram:

```
Frontend (React)
    ↓
CommandController (/api/v1/mcp-query)
    ↓
GroqMcpService (LLM + Tool definitions)
    ↓
McpToolExecutor (Execute tool)
    ↓
JiraToolService (Business logic)
    ↓
JiraApiService (Atlassian API calls)
    ↓
Atlassian Jira Cloud API
```

### Layers:

| Layer | Purpose |
|-------|---------|
| **Frontend** | User interface, sends natural language queries |
| **Controller** | REST endpoint, extracts OAuth token from session |
| **GroqMcpService** | Sends query to LLM with tools, gets tool call decision |
| **McpToolExecutor** | Routes to correct tool handler with MCP-style logging |
| **JiraToolService** | Business logic (resolves "me" to accountId) |
| **JiraApiService** | Makes actual API calls to Atlassian |

---

## 7. Previous vs Current Design

### Previous (Direct):

```
User Query → GroqService → JiraApiService → Atlassian API
              (direct, no tools)
```

### Current (MCP Integrated):

```
User Query → GroqMcpService → McpToolExecutor → JiraToolService → JiraApiService
                    ↓               ↓
              LLM decides    MCP Protocol
              (structured)   (logged)
```

### Key Differences:

| Aspect | Previous | Current |
|--------|----------|---------|
| Tool Definitions | None | Defined in prompt |
| Tool Selection | Code if/else | LLM decides |
| Extensibility | Add code | Add definition |
| Logging | Basic | MCP protocol style |

---

## 8. CSRF (Cross-Site Request Forgery)

### What is CSRF?

CSRF is an attack where a malicious website tricks a user's browser into sending an unwanted request to a trusted site.

### How Token Works:

```
Server: Generates unique CSRF token for session
Browser: Must include token in POST/PUT/DELETE requests
Server: Rejects requests without valid token
```

### Implementation in Project:

```java
// SecurityConfig.java - Disable CSRF for API endpoints
.csrf(csrf -> csrf
    .ignoringRequestMatchers("/logout", "/api/**")
)
```

---

## 9. Atlassian API Notes

### API Endpoint Format:
```
https://api.atlassian.com/ex/jira/{siteId}/rest/api/3
```

### Getting Site ID:
```java
GET https://api.atlassian.com/oauth/token/accessible-resources
Response: [{ "id": "uuid", "url": "site.atlassian.net", ... }]
```

### Common Issues:
- **403 Forbidden**: Scope mismatch or free site API restrictions
- **404 Not Found**: Issue doesn't exist or no permission
- **410 Gone**: Deprecated endpoint (use `/search/jql` instead of `/search`)

---

## 10. OAuth2 Token Flow

1. User visits `/login` → redirected to Atlassian
2. User approves → OAuth callback with code
3. Backend exchanges code for access token
4. Token stored in session
5. Each API request includes `Authorization: Bearer {token}`

---

## Appendix: Tool Definitions in Code

Tools are defined programmatically in `GroqMcpService.createToolDefinitions()`:

```java
private List<Map<String, Object>> createToolDefinitions() {
    return List.of(
        Map.of("type", "function", "function", Map.of(
            "name", "search_issues",
            "description", "Search Jira issues using JQL query",
            "parameters", Map.of(...)
        )),
        // ... more tools
    );
}
```

---

*Generated: April 2026*
*Project: Jira Ops Agent*