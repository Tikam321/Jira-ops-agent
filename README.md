# Jira Automation Agent MVP (Web + Agent-Assisted Bulk Operations + LLM Natural Language)

## Prerequisites

- **Java 17+**
- **PostgreSQL 14+**
- **Gradle 8.x** (or use included wrapper)

## Quick Setup

### 1. Database Setup
```sql
CREATE DATABASE jira_ops;
```

### 2. Configure Jira Credentials

Update `src/main/resources/application.yml`:
```yaml
jira:
  base-url: https://your-domain.atlassian.net
  email: your-email@example.com
  api-token: your-api-token  # Generate at https://id.atlassian.com/manage-profile/security/api-tokens

groq:
  api-key: your-groq-api-key  # Get from https://console.groq.com/

gemini:
  api-key: your-gemini-api-key  # Optional alternative LLM
```

Or use environment variables:
```bash
export JIRA_BASE_URL=https://your-domain.atlassian.net
export JIRA_EMAIL=your-email@example.com
export JIRA_API_TOKEN=your-api-token
export GROQ_API_KEY=your-groq-api-key
export GEMINI_API_KEY=your-gemini-api-key
```

### 3. Build & Run
```bash
./gradlew bootRun
```

Or build JAR first:
```bash
./gradlew build
java -jar build/libs/jira-ops-agent-1.0.0.jar
```

### 4. Access API
Base URL: `http://localhost:8080/api/v1`

Default credentials: `admin` / `admin`

## API Endpoints

### Template-Based Commands
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/commands` | List all 4 predefined commands |
| GET | `/commands/{id}` | Get specific command details |
| POST | `/preview/{id}` | Preview affected issues before execution |
| POST | `/execute/{id}` | Execute the command on Jira |
| GET | `/jobs` | View execution history |

### LLM Natural Language (With Bulk Support)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/nl-query` | Natural language query processing |

## Available Commands

| ID | Name | Action |
|----|------|--------|
| CMD001 | Fetch My Issues | Fetch all issues assigned to current user |
| CMD002 | Shift Due Date +1 Month | Shift due dates forward by 1 month |
| CMD003 | Change Status: To Do → In Progress | Move To Do issues to In Progress |
| CMD004 | Change Status: In Progress → Done | Move In Progress issues to Done |

## Natural Language Query (LLM-Powered with Bulk Support)

### Single Issue Operations

```bash
# Show my bugs due this week
curl -u admin:admin -X POST http://localhost:8080/api/v1/nl-query \
  -H "Content-Type: application/json" \
  -d '{"query": "show me my bugs due this week"}'

# Move a specific issue to done
curl -u admin:admin -X POST http://localhost:8080/api/v1/nl-query \
  -H "Content-Type: application/json" \
  -d '{"query": "move PROJ-123 to done"}'

# Add a comment to a specific issue
curl -u admin:admin -X POST http://localhost:8080/api/v1/nl-query \
  -H "Content-Type: application/json" \
  -d '{"query": "add a comment to PROJ-123 saying please fix this ASAP"}'
```

### Bulk Operations (NEW!)

```bash
# Move all my bugs to In Progress
curl -u admin:admin -X POST http://localhost:8080/api/v1/nl-query \
  -H "Content-Type: application/json" \
  -d '{"query": "move all my bugs to in progress"}'

# Transition all in-progress tasks to Done
curl -u admin:admin -X POST http://localhost:8080/api/v1/nl-query \
  -H "Content-Type: application/json" \
  -d '{"query": "move all my in progress tasks to done"}'

# Add comment to all my bugs
curl -u admin:admin -X POST http://localhost:8080/api/v1/nl-query \
  -H "Content-Type: application/json" \
  -d '{"query": "add comment to all my bugs saying please review"}'

# Assign all unassigned issues to me
curl -u admin:admin -X POST http://localhost:8080/api/v1/nl-query \
  -H "Content-Type: application/json" \
  -d '{"query": "assign all unassigned bugs to me"}'

# Update due dates for all tasks
curl -u admin:admin -X POST http://localhost:8080/api/v1/nl-query \
  -H "Content-Type: application/json" \
  -d '{"query": "set due date for all my tasks to 2026-04-15"}'
```

### Supported Natural Language Actions

| Type | Single Issue | Bulk Operations |
|------|-------------|-----------------|
| **Search** | "show me my bugs" | N/A |
| **Transition** | "move PROJ-123 to done" | "move all my bugs to done" |
| **Comment** | "add comment to PROJ-123" | "add comment to all my bugs" |
| **Due Date** | "set due date for PROJ-123" | "set due date for all my tasks" |
| **Assign** | "assign PROJ-123 to me" | "assign all unassigned to me" |

## Project Structure

```
src/main/java/com/jiraops/agent/
├── controller/          # REST endpoints
├── service/            # Business logic
│   ├── GroqService.java           # Groq LLM with function calling
│   ├── GeminiService.java         # Gemini LLM (alternative)
│   └── NaturalLanguageService.java # NL query orchestration
├── model/
│   ├── dto/           # Request/Response objects
│   ├── entity/        # JPA entities
│   └── enums/         # ActionType, JobStatus
├── repository/        # JPA repositories
├── security/          # Auth config
└── exception/         # Exception handling
```

## Architecture

### Template-Based Flow
```
User Selects Command → Generate JQL → Preview Issues → User Confirms → Execute on Jira API → Log Audit
```

### LLM-Powered Flow (With Bulk Support)
```
User Query (Natural Language)
         ↓
NaturalLanguageService
         ↓
GroqService (LLM with Function Calling)
    ↓
LLM decides tool:
  Single: search_issues | transition_issue | add_comment | update_duedate | assign_issue
  Bulk:   bulk_transition | bulk_add_comment | bulk_update_duedate | bulk_assign
    ↓
JiraApiService (Tool Execution)
    ↓
Jira Cloud API
```

## Tech Stack

| Technology | Purpose |
|------------|---------|
| Spring Boot 3.2.5 | Application framework |
| Java 17+ | Programming language |
| Groq API | LLM with function calling |
| Gemini API | Alternative LLM |
| PostgreSQL | Database |
| WebClient | HTTP client for Jira API |

## Future Enhancements (Phase 2+)

- [ ] OAuth 2.0 authentication
- [ ] Add more command templates
- [ ] MCP server implementation (true standard)
- [ ] Scheduled job execution
- [ ] Web dashboard UI
