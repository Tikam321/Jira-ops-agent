# Jira Automation Agent - Technical Documentation

## Overview

Jira Automation Agent is a Spring Boot application that provides **two modes** of Jira bulk operations:

1. **Template-Based Commands**: Predefined commands with JQL queries
2. **LLM-Powered Natural Language**: Process natural language queries using Groq/Gemini with function calling

---

## Table of Contents

1. [Architecture](#architecture)
2. [Project Structure](#project-structure)
3. [Tech Stack](#tech-stack)
4. [Database Schema](#database-schema)
5. [API Endpoints](#api-endpoints)
6. [LLM Integration](#llm-integration)
7. [Command Templates](#command-templates)
8. [Design Patterns](#design-patterns)
9. [Code Deep Dive](#code-deep-dive)
10. [Configuration](#configuration)
11. [Build & Run](#build--run)
12. [Future Enhancements](#future-enhancements)

---

## Architecture

### Dual-Mode Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Client (curl/Browser)                        │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │ HTTP (Basic Auth)
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     CommandController (REST API)                          │
│                     /api/v1/commands, /preview, /execute                  │
│                     /api/v1/nl-query (NEW)                               │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
          ┌───────────────────────┼───────────────────────┐
          ▼                       ▼                       ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ CommandTemplate  │  │   Execution     │  │   LLM Services  │
│   Service       │  │   Service      │  │                 │
│                 │  │                │  │ - GroqService   │
│ - 4 predefined  │  │ - Preview      │  │ - GeminiService │
│   commands      │  │ - Execute      │  │                 │
│ - Returns JQL   │  │ - Audit logs   │  │ - NL Processing │
└─────────────────┘  └─────────────────┬─────────────────┘
                                       │
                                       ▼
                           ┌───────────────────────┐
                           │     JiraApiService    │
                           │                       │
                           │ - searchIssues       │
                           │ - transitionIssue    │
                           │ - addComment          │
                           │ - updateDuedate       │
                           │ - assignIssue         │
                           └───────────┬───────────┘
                                       │
              ┌────────────────────────┼────────────────────────┐
              ▼                        ▼                        ▼
┌───────────────────────┐  ┌───────────────────────┐  ┌───────────────────────┐
│    PostgreSQL          │  │    Groq API           │  │    Jira Cloud API     │
│  (Audit, Jobs, Logs)  │  │  (LLM + Tools)        │  │   (atlassian.net)     │
└───────────────────────┘  └───────────────────────┘  └───────────────────────┘
```

### LLM Flow (Function Calling)

```
User: "show me my bugs due this week"
          ↓
┌─────────────────────────┐
│  NaturalLanguageService │
└───────────┬─────────────┘
            ↓
┌─────────────────────────┐
│      GroqService        │
│  (LLM with tools)       │
│                         │
│  Tools provided:        │
│  - search_issues        │
│  - transition_issue     │
│  - add_comment          │
│  - update_duedate      │
│  - assign_issue         │
└───────────┬─────────────┘
            ↓
LLM analyzes query → selects "search_issues" tool
            ↓
┌─────────────────────────┐
│    JiraApiService       │
└───────────┬─────────────┘
            ↓
┌─────────────────────────┐
│    Jira Cloud API       │
│  /rest/api/3/search/jql │
└─────────────────────────┘
```

---

## Project Structure

```
jira-ops-agent/
├── build.gradle                    # Dependencies & build config
├── settings.gradle
├── docker-compose.yml              # PostgreSQL container
├── README.md                       # Setup instructions
├── gradlew                         # Gradle wrapper
└── src/
    ├── main/
    │   ├── java/com/jiraops/agent/
    │   │   ├── JiraOpsAgentApplication.java
    │   │   ├── controller/
    │   │   │   └── CommandController.java      # REST API endpoints
    │   │   ├── service/
    │   │   │   ├── CommandTemplateService.java # 4 predefined commands
    │   │   │   ├── ExecutionService.java       # Preview & execute logic
    │   │   │   ├── JiraApiService.java          # Jira REST API client
    │   │   │   ├── GroqService.java            # Groq LLM with function calling
    │   │   │   ├── GeminiService.java          # Gemini LLM (alternative)
    │   │   │   └── NaturalLanguageService.java # NL query orchestration
    │   │   ├── model/
    │   │   │   ├── dto/
    │   │   │   │   ├── CommandTemplate.java
    │   │   │   │   ├── ExecutionResult.java
    │   │   │   │   ├── JiraIssueDto.java
    │   │   │   │   ├── PreviewChange.java
    │   │   │   │   ├── PreviewResult.java
    │   │   │   │   ├── NlQueryRequest.java      # NEW
    │   │   │   │   └── NlQueryResponse.java     # NEW
    │   │   │   ├── entity/
    │   │   │   │   ├── AuditLog.java
    │   │   │   │   ├── ExecutionJob.java
    │   │   │   │   └── JiraIssue.java
    │   │   │   └── enums/
    │   │   │       ├── ActionType.java
    │   │   │       └── JobStatus.java
    │   │   ├── repository/
    │   │   ├── security/
    │   │   └── exception/
    │   └── resources/
    │       ├── application.yml
    │       └── application-local.yml
    └── test/
```

---

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.2.5 | Application framework |
| Java | 17+ | Programming language |
| Gradle | 8.7 | Build tool |
| PostgreSQL | 16 | Primary database |
| H2 | - | Test database |
| Spring Data JPA | - | ORM |
| Spring WebFlux WebClient | - | HTTP client |
| Spring Security | - | Authentication |
| Groq API | - | LLM with function calling |
| Google Gemini | 1.28.0 | Alternative LLM |
| Lombok | - | Boilerplate reduction |

---

## Database Schema

### Table: execution_jobs

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| command_id | VARCHAR | Command identifier (CMD001, etc.) |
| jql | TEXT | JQL query used |
| action_type | VARCHAR | Action type (FETCH, UPDATE_DUEDATE, etc.) |
| total_issues | INTEGER | Total issues affected |
| success_count | INTEGER | Successfully updated |
| failed_count | INTEGER | Failed updates |
| status | VARCHAR | PENDING, RUNNING, COMPLETED, FAILED |
| created_at | TIMESTAMP | Job creation time |
| executed_at | TIMESTAMP | Job completion time |
| error_message | TEXT | Error details if any |

### Table: audit_logs

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| issue_key | VARCHAR | Jira issue key (e.g., SCRUM-101) |
| field_name | VARCHAR | Field changed (duedate, status) |
| old_value | VARCHAR | Previous value |
| new_value | VARCHAR | New value |
| action | VARCHAR | Action type performed |
| actor | VARCHAR | User who performed (system/user) |
| job_id | BIGINT | FK to execution_jobs |
| executed_at | TIMESTAMP | When change was made |

---

## API Endpoints

### Base URL: `http://localhost:8080/api/v1`

### Authentication
- **Method**: Basic Authentication
- **Default Credentials**: `admin` / `admin`

---

### 1. List All Commands

```
GET /commands
```

---

### 2. Get Command by ID

```
GET /commands/{commandId}
```

---

### 3. Preview Command (Dry Run)

```
POST /preview/{commandId}
```

---

### 4. Execute Command

```
POST /execute/{commandId}
```

---

### 5. Get Execution History

```
GET /jobs
```

---

### 6. Natural Language Query (NEW)

```
POST /nl-query
```

**Request Body:**
```json
{
  "query": "show me my bugs due this week"
}
```

**Response:**
```json
{
  "originalQuery": "show me my bugs due this week",
  "generatedJql": "",
  "actionType": "FETCH",
  "confidence": 0.9,
  "message": "Found 5 issue(s):\n\n- SCRUM-101: Fix login bug\n  Status: To Do\n  Type: Bug\n  Due: 2026-03-25\n\n..."
}
```

---

## LLM Integration

### GroqService

Located: `src/main/java/com/jiraops/agent/service/GroqService.java`

Uses Groq API with **function calling** (MCP-inspired architecture).

#### Configuration

```yaml
groq:
  api-key: your-groq-api-key
```

#### Model Used
- **Model**: `llama-3.3-70b-versatile`
- **Temperature**: 0.3 (deterministic)
- **Max Tokens**: 512

#### Available Tools (Function Calling)

**Single Issue Tools:**
```java
// 1. search_issues - Search Jira with JQL
Map.of(
    "name", "search_issues",
    "parameters", Map.of(
        "jql", "string - JQL query",
        "maxResults", "integer - max results (default 50)"
    )
)

// 2. transition_issue - Change status (single)
Map.of(
    "name", "transition_issue",
    "parameters", Map.of(
        "issueKey", "string - PROJ-123",
        "status", "string - target status"
    )
)

// 3. add_comment - Add comment (single)
Map.of(
    "name", "add_comment",
    "parameters", Map.of(
        "issueKey", "string",
        "comment", "string"
    )
)

// 4. update_duedate - Update due date (single)
Map.of(
    "name", "update_duedate",
    "parameters", Map.of(
        "issueKey", "string",
        "dueDate", "string - YYYY-MM-DD"
    )
)

// 5. assign_issue - Assign to user (single)
Map.of(
    "name", "assign_issue",
    "parameters", Map.of(
        "issueKey", "string - PROJ-123",
        "assignee", "string - accountId or 'me'"
    )
)
```

**Bulk Operation Tools (NEW):**
```java
// 6. bulk_transition - Change status of multiple issues
Map.of(
    "name", "bulk_transition",
    "parameters", Map.of(
        "jql", "string - JQL query to select issues",
        "status", "string - target status"
    )
)
// Example: "move all my bugs to done"
// JQL: assignee = currentUser() AND issuetype = Bug AND status = 'To Do'

// 7. bulk_update_duedate - Update due date of multiple issues
Map.of(
    "name", "bulk_update_duedate",
    "parameters", Map.of(
        "jql", "string - JQL query",
        "dueDate", "string - YYYY-MM-DD"
    )
)

// 8. bulk_add_comment - Add comment to multiple issues
Map.of(
    "name", "bulk_add_comment",
    "parameters", Map.of(
        "jql", "string - JQL query",
        "comment", "string - comment text"
    )
)

// 9. bulk_assign - Assign multiple issues
Map.of(
    "name", "bulk_assign",
    "parameters", Map.of(
        "jql", "string - JQL query",
        "assignee", "string - 'me' or accountId"
    )
)
```
        "issueKey", "string",
        "assignee", "string - accountId or 'me'"
    )
)
```

#### How It Works

```java
// 1. Build request with tools
Map<String, Object> requestBody = Map.of(
    "model", "llama-3.3-70b-versatile",
    "messages", List.of(systemPrompt, userQuery),
    "tools", tools,  // Function definitions
    "tool_choice", "auto"  // Let LLM decide
);

// 2. LLM returns tool call
{
    "choices": [{
        "message": {
            "tool_calls": [{
                "function": {
                    "name": "search_issues",
                    "arguments": "{\"jql\": \"assignee = currentUser() AND issuetype = Bug\", \"maxResults\": 50}"
                }
            }]
        }
    }]
}

// 3. Execute tool and return result
```

### GeminiService

Located: `src/main/java/com/jiraops/agent/service/GeminiService.java`

Alternative LLM using Google Gemini 2.0 Flash.

#### Configuration

```yaml
gemini:
  api-key: your-gemini-api-key
```

#### Current Use
- JQL generation from natural language (standalone)

---

## Command Templates

| ID | Name | Action Type | JQL | Description |
|----|------|-------------|-----|-------------|
| CMD001 | Fetch My Issues | FETCH | `assignee = currentUser()` | Fetch all issues assigned to current user |
| CMD002 | Shift Due Date +1 Month | UPDATE_DUEDATE | `assignee = currentUser() AND duedate <= endOfMonth() AND status NOT IN (Done, Closed)` | Shift due dates forward by 1 month |
| CMD003 | To Do → In Progress | CHANGE_STATUS | `assignee = currentUser() AND status = "To Do"` | Transition To Do to In Progress |
| CMD004 | In Progress → Done | CHANGE_STATUS | `assignee = currentUser() AND status = "In Progress"` | Transition In Progress to Done |

---

## Design Patterns

### 1. Service Layer Pattern
Business logic separated from controllers.

```
Controller → Service → Repository
```

### 2. Repository Pattern
Data access abstracted via Spring Data JPA.

### 3. DTO Pattern
Data Transfer Objects separate API contracts from entities.

### 4. Strategy Pattern
Different action types with same interface.

### 5. Function Calling (MCP-Inspired)
LLM selects appropriate tool based on natural language input.

```
Query → LLM Analysis → Tool Selection → Tool Execution → Result
```

### 6. Chain of Responsibility (LLM)
GroqService acts as a coordinator:
- Receives natural language query
- Passes to LLM with available tools
- Executes selected tool
- Returns formatted result

---

## Code Deep Dive

### GroqService.java

#### Tool Definition (Lines 64-140)

```java
List<Map<String, Object>> tools = List.of(
    Map.of(
        "type", "function",
        "function", Map.of(
            "name", "search_issues",
            "description", "Search Jira issues using JQL query",
            "parameters", Map.of(
                "type", "object",
                "properties", Map.of(
                    "jql", Map.of("type", "string"),
                    "maxResults", Map.of("type", "integer")
                ),
                "required", List.of("jql")
            )
        )
    ),
    // ... other tools
);
```

#### Tool Execution (Lines 226-281 + Bulk Operations)

**Single Issue Tools:**
```java
switch (toolName) {
    case "search_issues" -> {
        var issues = jiraApiService.searchIssues(jql, maxResults);
        result = formatIssuesResponse(issues);
    }
    case "transition_issue" -> {
        boolean success = jiraApiService.transitionIssueByStatus(issueKey, status);
        result = success ? "Moved " + issueKey + " to " + status : "Failed";
    }
    case "add_comment" -> {
        boolean success = jiraApiService.addComment(issueKey, comment);
        result = success ? "Added comment to " + issueKey : "Failed";
    }
    case "update_duedate" -> {
        boolean success = jiraApiService.updateDuedate(issueKey, dueDate);
        result = success ? "Updated due date" : "Failed";
    }
    case "assign_issue" -> {
        // Resolve 'me' to accountId
        if (assignee.contains("me")) {
            assignee = jiraApiService.getCurrentUserAccountId();
        }
        boolean success = jiraApiService.assignIssue(issueKey, assignee);
        result = success ? "Assigned " + issueKey : "Failed";
    }
}
```

**Bulk Operation Tools (NEW):**
```java
case "bulk_transition" -> {
    // 1. Search for matching issues
    List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100);
    
    // 2. Transition each issue
    int successCount = 0, failCount = 0;
    for (JiraIssueDto issue : issues) {
        try {
            boolean success = jiraApiService.transitionIssueByStatus(issue.getKey(), status);
            if (success) successCount++; else failCount++;
        } catch (Exception e) { failCount++; }
    }
    
    result = String.format("Successfully transitioned %d issues. Failed: %d", 
                           successCount, failCount);
}

case "bulk_update_duedate" -> {
    List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100);
    int successCount = 0, failCount = 0;
    for (JiraIssueDto issue : issues) {
        try {
            boolean success = jiraApiService.updateDuedate(issue.getKey(), dueDate);
            if (success) successCount++; else failCount++;
        } catch (Exception e) { failCount++; }
    }
    result = String.format("Updated due date for %d issues. Failed: %d", 
                           successCount, failCount);
}

case "bulk_add_comment" -> {
    List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100);
    int successCount = 0, failCount = 0;
    for (JiraIssueDto issue : issues) {
        try {
            boolean success = jiraApiService.addComment(issue.getKey(), comment);
            if (success) successCount++; else failCount++;
        } catch (Exception e) { failCount++; }
    }
    result = String.format("Added comment to %d issues. Failed: %d", 
                           successCount, failCount);
}

case "bulk_assign" -> {
    // Resolve 'me' to accountId if needed
    if (assignee.contains("me")) {
        assignee = jiraApiService.getCurrentUserAccountId();
    }
    
    List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100);
    int successCount = 0, failCount = 0;
    for (JiraIssueDto issue : issues) {
        try {
            boolean success = jiraApiService.assignIssue(issue.getKey(), assignee);
            if (success) successCount++; else failCount++;
        } catch (Exception e) { failCount++; }
    }
    result = String.format("Assigned %d issues. Failed: %d", 
                           successCount, failCount);
}
```

### NaturalLanguageService.java

Orchestrates the NL query flow:

```java
public NlQueryResponse processQuery(NlQueryRequest request) {
    // 1. Call GroqService with natural language
    String result = groqService.processNaturalLanguageQuery(query);
    
    // 2. Determine action type from query
    ActionType actionType = determineActionType(query);
    
    // 3. Build response
    return NlQueryResponse.builder()
        .originalQuery(query)
        .actionType(actionType)
        .confidence(0.9)
        .message(result)
        .build();
}
```

### JiraApiService.java

Exposes tools for execution:

| Method | Purpose | Jira Endpoint |
|--------|---------|---------------|
| `searchIssues(jql, maxResults)` | Search issues | GET /rest/api/3/search/jql |
| `transitionIssueByStatus(key, status)` | Change status | POST /rest/api/3/issue/{key}/transitions |
| `addComment(key, comment)` | Add comment | POST /rest/api/3/issue/{key}/comment |
| `updateDuedate(key, date)` | Update due date | PUT /rest/api/3/issue/{key} |
| `assignIssue(key, assignee)` | Assign issue | PUT /rest/api/3/issue/{key}/assignee |
| `getCurrentUserAccountId()` | Get current user | GET /rest/api/3/myself |

---

## Configuration

### application.yml

```yaml
spring:
  application:
    name: jira-ops-agent

  datasource:
    url: jdbc:postgresql://localhost:5432/jira_ops
    username: postgres
    password: postgres

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

jira:
  base-url: https://your-domain.atlassian.net
  email: your-email@example.com
  api-token: your-api-token

groq:
  api-key: your-groq-api-key

gemini:
  api-key: your-gemini-api-key

server:
  port: 8080
```

### Environment Variables

```bash
export JIRA_BASE_URL=https://your-domain.atlassian.net
export JIRA_EMAIL=your-email@example.com
export JIRA_API_TOKEN=your-api-token
export GROQ_API_KEY=your-groq-api-key
export GEMINI_API_KEY=your-gemini-api-key
```

---

## Build & Run

```bash
# 1. Start PostgreSQL
docker-compose up -d

# 2. Build the project
./gradlew build

# 3. Run tests
./gradlew test

# 4. Run application
./gradlew bootRun

# Or run JAR directly
java -jar build/libs/jira-ops-agent-1.0.0.jar
```

### Test the API

```bash
# List commands
curl -u admin:admin http://localhost:8080/api/v1/commands

# Preview command
curl -u admin:admin -X POST http://localhost:8080/api/v1/preview/CMD001

# Execute command
curl -u admin:admin -X POST http://localhost:8080/api/v1/execute/CMD001

# Natural language query
curl -u admin:admin -X POST http://localhost:8080/api/v1/nl-query \
  -H "Content-Type: application/json" \
  -d '{"query": "show me my bugs due this week"}'

# View history
curl -u admin:admin http://localhost:8080/api/v1/jobs
```

---

## Future Enhancements

### Phase 2: MCP Server Implementation
- True MCP server using Spring AI MCP
- Universal tool exposure for any MCP-compatible LLM

### Phase 3: Additional Commands
- More command templates
- Custom JQL builder
- Scheduled job execution

### Phase 4: Enhanced LLM
- Multi-turn conversations
- Context retention
- Better error handling

### Phase 5: Web Dashboard
- React/Next.js frontend
- Visual issue management
- Real-time job status

---

## Troubleshooting

### Groq API Errors
**Cause:** Invalid API key or rate limit

**Solution:** 
1. Get API key from https://console.groq.com/
2. Check rate limits

### Jira API Errors
**Cause:** Invalid credentials or permissions

**Solution:**
1. Verify Jira email and API token
2. Check token has required permissions

### LLM Not Calling Tool
**Cause:** Query unclear or ambiguous

**Solution:** Rephrase query to be more explicit

---

## License

MIT License
