# MCP (Model Context Protocol) - Documentation

## Overview

MCP (Model Context Protocol) is a standardized protocol that enables AI models/agents to communicate with external tools and services. Think of it as a **"USB port for AI"** - a universal way to connect AI models to any data source or tool.

---

## Table of Contents

1. [What is MCP?](#what-is-mcp)
2. [Why MCP?](#why-mcp)
3. [MCP vs Function Calling](#mcp-vs-function-calling)
4. [Architecture](#architecture)
5. [Key Concepts](#key-concepts)
6. [Available MCP Servers](#available-mcp-servers)
7. [Our Implementation](#our-implementation)
8. [True MCP Server Implementation](#true-mcp-server-implementation)
9. [Benefits](#benefits)
10. [Resume Points](#resume-points)

---

## What is MCP?

MCP is a protocol (like HTTP) that defines how AI models interact with external tools. It was created by Anthropic and is now an open standard.

**Problem MCP solves:**
- Every AI integration needs custom code
- LLM doesn't know what tools are available
- No standard way to describe tool capabilities

**MCP solution:**
- Universal protocol for AI-tool communication
- Tools are explicitly defined with schemas
- Any MCP-compatible LLM can use any MCP server

---

## Why MCP?

| Traditional Approach | MCP Approach |
|---------------------|--------------|
| Custom code for each AI-tool integration | One protocol, many integrations |
| LLM guesses what API can do | LLM knows exact tools available |
| One-off integrations | Reusable across any LLM |
| Prompt engineering is fragile | Tool schemas are explicit |
| LLM generates code/API calls | LLM calls tools directly |

---

## MCP vs Function Calling

### Function Calling (What We Used)

```
┌─────────────┐     JSON Tools     ┌─────────────┐
│   Our App   │ ────────────────→  │  Groq API   │
│             │    (in request)    │             │
└─────────────┘                    └─────────────┘
```

**Characteristics:**
- Tools passed as JSON schemas in API request body
- LLM provider (Groq) handles tool selection internally
- Only works with LLMs that support function calling
- No separate server or protocol
- Proprietary/varies by provider

### True MCP (Standard Protocol)

```
┌─────────────┐    MCP Protocol    ┌─────────────┐
│     LLM     │ ←─────────────────→ │ MCP Server  │
│  (Claude,   │   (HTTP/SSE/STDIO) │ (Jira, Git, │
│   GPT, etc) │                    │   Slack)    │
└─────────────┘                    └─────────────┘
```

**Characteristics:**
- Separate server runs independently
- Standardized protocol (JSON-RPC based)
- Any MCP-compatible LLM can connect
- Like USB - universal connection standard
- Server exposes tools, not embedded in request

---

## Architecture

### MCP Flow

```
User: "Show me my bugs due this week"
          ↓
┌─────────────────┐
│   LLM/Agent     │
│  (Claude/GPT)   │
└────────┬────────┘
         │ MCP Protocol
         ↓
┌─────────────────┐
│  MCP Client     │
│  (in LLM app)   │
└────────┬────────┘
         │ JSON-RPC over HTTP/SSE
         ↓
┌─────────────────┐
│  MCP Server     │
│  (Jira Tools)   │
│  - search_issues │
│  - transition    │
│  - add_comment   │
│  - update_duedate│
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│   Jira API      │
└─────────────────┘
```

### MCP Server Components

```
MCP Server
├── Server Info (name, version, description)
├── Resources (data to read)
│   ├── Jira Projects
│   ├── Issue Types
│   └── Users
├── Tools (actions to execute)
│   ├── search_issues
│   ├── transition_issue
│   ├── add_comment
│   └── update_duedate
└── Prompts (pre-defined templates)
```

---

## Key Concepts

### 1. Tools
Actions the LLM can invoke. Each tool has:
- **Name**: Unique identifier
- **Description**: What the tool does
- **Input Schema**: Parameters it accepts

```json
{
  "name": "search_issues",
  "description": "Search Jira issues using JQL",
  "inputSchema": {
    "type": "object",
    "properties": {
      "jql": {
        "type": "string",
        "description": "JQL query string"
      },
      "maxResults": {
        "type": "integer",
        "description": "Maximum results"
      }
    },
    "required": ["jql"]
  }
}
```

### 2. Resources
Data the server exposes for reading (not modification).

### 3. Prompts
Pre-defined templates for common tasks.

### 4. Transport
How MCP clients and servers communicate:
- **HTTP + SSE**: Web-based communication
- **STDIO**: Command-line based (for local tools)
- **WebSocket**: Real-time bidirectional

---

## Available MCP Servers

### Official/Anthropic
- **GitHub MCP**: Issues, PRs, repos, files
- **Filesystem MCP**: Read/write local files
- **Brave Search MCP**: Web search

### Community
- **Slack MCP**: Send messages, list channels
- **PostgreSQL MCP**: Database queries
- **Google Drive MCP**: Access Drive files
- **Notion MCP**: Read/write Notion pages

### For Our Project
We built tools that could be exposed via MCP:
- `search_issues`: Search Jira with JQL
- `transition_issue`: Change issue status
- `add_comment`: Add comments
- `update_duedate`: Update due dates

---

## Our Implementation

### Current Approach (Function Calling)

We implemented LLM-based Jira assistant using **Groq's native function calling**:

```
User Request → GroqService → Groq API (with tools JSON) → Tool Execution → Jira API
```

**Files involved:**
- `GroqService.java`: Handles LLM interaction
- `JiraApiService.java`: Jira API calls
- `NaturalLanguageService.java`: Orchestrates the flow

### Code Example (Function Calling)

```java
// Define tools as JSON in request
List<Map<String, Object>> tools = List.of(
    Map.of(
        "type", "function",
        "function", Map.of(
            "name", "search_issues",
            "description", "Search Jira issues",
            "parameters", Map.of(...)
        )
    )
);

// LLM returns tool call
Map<String, Object> toolCall = response.get("tool_calls").get(0);
String toolName = toolCall.get("function").get("name");
```

---

## True MCP Server Implementation

### What Would Be Needed

To implement a true MCP server for Jira:

#### 1. Dependencies
```gradle
// Using Spring AI MCP
implementation 'org.springframework.ai:spring-ai-mcp-server-webflux-spring-boot-starter'
```

#### 2. MCP Server Configuration
```java
@SpringBootApplication
@EnableMcpServer
public class JiraMcpApplication {
    // MCP server auto-configured
}
```

#### 3. Tool Definition
```java
@McpTool(name = "search_issues")
public ToolResult searchIssues(
    @ToolParam(description = "JQL query") String jql,
    @ToolParam(description = "Max results") Integer maxResults
) {
    List<JiraIssue> issues = jiraApi.search(jql, maxResults);
    return ToolResult.success(issues);
}
```

#### 4. Configuration
```yaml
spring:
  ai:
    mcp:
      server:
        name: jira-mcp-server
        version: 1.0.0
        port: 8082
```

### Benefits of True MCP

1. **Universal Access**: Any MCP-compatible AI can use our server
2. **Separation of Concerns**: Server runs independently
3. **Standardized**: Follows open protocol
4. **Debuggable**: Clear tool calls and responses
5. **Secure**: Server controls tool exposure

---

## Benefits

### For Developers
- Reuse integrations across projects
- No need to write custom code for each LLM
- Standard debugging and testing

### For Users
- Natural language interface to complex tools
- Works with any compatible AI assistant
- Consistent experience

### For Organizations
- One integration, multiple AI options
- Future-proof architecture
- Easier to maintain

---

## Resume Points

### Technical (Backend Developer)
```
Designed and developed a Spring Boot microservice integrating Groq LLM with function-calling 
architecture, enabling natural language processing of Jira operations (search, transition, 
comment, update) via dynamic tool routing
```

### With MCP Knowledge
```
Built an AI agent microservice using Spring Boot that leverages LLM function-calling 
(inspired by Model Context Protocol) to interpret natural language queries and execute 
Jira API operations
```

### Full Description
```
- Implemented LLM-powered Jira assistant using Groq API with native function calling
- Designed tool-calling architecture for natural language to Jira operations
- Built Spring Boot services for JQL generation, issue search, and status management
- Integrated with Jira REST API for real-time issue operations
- Added comprehensive logging for debugging and monitoring
```

---

## Resources

### Official Documentation
- [MCP Protocol Specification](https://modelcontextprotocol.io)
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
- [Spring AI MCP](https://docs.spring.ai/spring-ai/reference/mcp/)

### Community
- [Awesome MCP Servers](https://github.com/punkpeye/awesome-mcp-servers)
- [MCP GitHub Repository](https://github.com/modelcontextprotocol)

---

## Summary

| Aspect | Function Calling | True MCP |
|--------|----------------|----------|
| Protocol | Proprietary (per LLM) | Standardized |
| Server | Not separate | Independent |
| Compatibility | LLM-specific | Universal |
| Complexity | Lower | Higher |
| Use Case | Quick integration | Production/Universal |

**Our implementation** demonstrates the concept of tool-calling and can be easily upgraded to true MCP by exposing the tools via Spring AI MCP starters.

---

*Document Version: 1.0*  
*Last Updated: March 21, 2026*
