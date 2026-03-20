# Jira Automation Agent - Technical Documentation

## Overview

Jira Automation Agent is a Spring Boot application that provides bulk operations on Jira issues through a simple REST API. It uses a **template-based command system** (not NLP) where users select from predefined commands that generate JQL queries and execute actions on Jira Cloud.

---

## Table of Contents

1. [Architecture](#architecture)
2. [Project Structure](#project-structure)
3. [Tech Stack](#tech-stack)
4. [Database Schema](#database-schema)
5. [API Endpoints](#api-endpoints)
6. [Command Templates](#command-templates)
7. [Design Patterns](#design-patterns)
8. [Code Deep Dive](#code-deep-dive)
9. [Configuration](#configuration)
10. [Build & Run](#build--run)
11. [Future Enhancements](#future-enhancements)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client (curl/Browser)                    │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTP (Basic Auth)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     CommandController (REST API)                  │
│                     /api/v1/commands, /preview, /execute          │
└─────────────────────────────┬───────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ CommandTemplate  │  │   Execution     │  │   JiraApi       │
│   Service       │  │   Service      │  │   Service       │
│                 │  │                │  │                 │
│ - 4 predefined  │  │ - Preview      │  │ - searchIssues  │
│   commands      │  │ - Execute      │  │ - transitions   │
│ - Returns JQL   │  │ - Audit logs   │  │ - updateIssue   │
└─────────────────┘  └─────────────────┬─────────────────┘
                                      │
                                      ▼
                          ┌───────────────────────┐
                          │     PostgreSQL        │
                          │  (Audit, Jobs, Logs)  │
                          └───────────────────────┘
                                      │
                                      ▼
                          ┌───────────────────────┐
                          │    Jira Cloud API     │
                          │   (atlassian.net)     │
                          └───────────────────────┘
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
    │   │   │   └── JiraApiService.java         # Jira REST API client
    │   │   ├── model/
    │   │   │   ├── dto/
    │   │   │   │   ├── CommandTemplate.java     # Command definition
    │   │   │   │   ├── ExecutionResult.java      # Execution response
    │   │   │   │   ├── JiraIssueDto.java        # Issue DTO
    │   │   │   │   ├── PreviewChange.java       # Change preview
    │   │   │   │   ├── PreviewResult.java       # Preview response
    │   │   │   │   └── ApiError.java            # Error response
    │   │   │   ├── entity/
    │   │   │   │   ├── AuditLog.java            # Audit log entity
    │   │   │   │   ├── ExecutionJob.java        # Job tracking entity
    │   │   │   │   └── JiraIssue.java           # Issue entity
    │   │   │   └── enums/
    │   │   │       ├── ActionType.java          # FETCH, UPDATE_DUEDATE, CHANGE_STATUS
    │   │   │       └── JobStatus.java           # PENDING, RUNNING, COMPLETED, etc.
    │   │   ├── repository/
    │   │   │   ├── AuditLogRepository.java       # Audit log data access
    │   │   │   └── ExecutionJobRepository.java  # Job data access
    │   │   ├── security/
    │   │   │   └── SecurityConfig.java          # Basic auth configuration
    │   │   └── exception/
    │   │       ├── JiraApiException.java        # Custom exception
    │   │       └── GlobalExceptionHandler.java  # Error handling
    │   └── resources/
    │       ├── application.yml                  # Main config
    │       └── application-local.yml            # Local overrides
    └── test/
        ├── java/com/jiraops/agent/
        │   ├── controller/
        │   │   └── CommandControllerTest.java   # Controller tests
        │   └── service/
        │       └── CommandTemplateServiceTest.java
        └── resources/
            └── application.yml                  # Test config (H2)
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
| Lombok | - | Boilerplate reduction |

---

## Database Schema

### Table: execution_jobs

Tracks each execution run.

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

Logs every field change for audit trail.

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

**Response:**
```json
[
  {
    "id": "CMD001",
    "name": "Fetch My Issues",
    "description": "Fetch all issues assigned to current user",
    "actionType": "FETCH",
    "jql": "assignee = currentUser() ORDER BY updated DESC",
    "parameters": null
  },
  {
    "id": "CMD002",
    "name": "Shift Due Date +1 Month",
    "actionType": "UPDATE_DUEDATE",
    "jql": "assignee = currentUser() AND duedate <= endOfMonth() AND status NOT IN (Done, Closed)",
    "parameters": "{\"type\": \"SHIFT_DUEDATE\", \"delta\": \"+1M\"}"
  }
]
```

---

### 2. Get Command by ID

```
GET /commands/{commandId}
```

**Example:** `GET /commands/CMD001`

---

### 3. Preview Command (Dry Run)

```
POST /preview/{commandId}
```

Shows which issues will be affected and what changes will be made.

**Response:**
```json
{
  "jql": "assignee = currentUser() AND status = \"To Do\"",
  "totalIssues": 5,
  "issues": [
    {
      "id": "12345",
      "key": "SCRUM-101",
      "summary": "Implement login",
      "status": "To Do",
      "assignee": "John",
      "project": "SCRUM",
      "dueDate": "2026-03-25",
      "issueType": "Story"
    }
  ],
  "changes": [
    {
      "issueKey": "SCRUM-101",
      "field": "status",
      "currentValue": "To Do",
      "newValue": "In Progress"
    }
  ]
}
```

---

### 4. Execute Command

```
POST /execute/{commandId}
```

Executes the command on Jira Cloud.

**Response:**
```json
{
  "jobId": 1,
  "status": "COMPLETED",
  "totalIssues": 5,
  "successCount": 5,
  "failedCount": 0,
  "executedAt": "2026-03-20T10:30:00",
  "message": "All issues updated successfully"
}
```

---

### 5. Get Execution History

```
GET /jobs
```

**Response:**
```json
[
  {
    "id": 1,
    "commandId": "CMD002",
    "jql": "assignee = currentUser() AND duedate <= endOfMonth()",
    "actionType": "UPDATE_DUEDATE",
    "totalIssues": 10,
    "successCount": 10,
    "failedCount": 0,
    "status": "COMPLETED",
    "createdAt": "2026-03-20T10:29:00",
    "executedAt": "2026-03-20T10:30:00"
  }
]
```

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

```java
public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, Long>
```

### 3. DTO Pattern
Data Transfer Objects separate API contracts from entities.

```java
// Entity: JiraIssue (database)
// DTO: JiraIssueDto (API response)
```

### 4. Strategy Pattern
Different action types with same interface.

```java
public enum ActionType {
    FETCH,
    UPDATE_DUEDATE,
    CHANGE_STATUS
}
```

### 5. Template Method Pattern
Same workflow, different implementations.

```java
preview() → Fetch issues → Show changes
execute() → Fetch issues → Make changes → Log audit
```

### 6. Factory Pattern
Command creation via CommandTemplateService.

---

## Code Deep Dive

### JiraApiService.java

Handles all communication with Jira Cloud REST API.

#### 1. WebClient Setup (Basic Auth)

```java
private WebClient getWebClient() {
    if (webClient == null) {
        webClient = WebClient.builder()
            .baseUrl(jiraBaseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, 
                "Basic " + Base64.getEncoder().encodeToString(
                    (jiraEmail + ":" + jiraApiToken).getBytes()))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .build();
    }
    return webClient;
}
```

**How Basic Auth works:**
1. Combine email + API token: `email:token`
2. Encode to Base64: `ZW1haWw6dG9rZW4=`
3. Add to header: `Authorization: Basic ZW1haWw6dG9rZW4=`

#### 2. Search Issues (GET /rest/api/3/search/jql)

```java
public List<JiraIssueDto> searchIssues(String jql, int maxResults) {
    Map response = getWebClient()
        .get()
        .uri(uriBuilder -> uriBuilder
            .path("/rest/api/3/search/jql")
            .queryParam("jql", jql)
            .queryParam("maxResults", maxResults)
            .queryParam("fields", "summary,status,assignee,project,duedate,issuetype")
            .build())
        .retrieve()
        .bodyToMono(Map.class)
        .block();

    List<Map<String, Object>> issues = (List<Map<String, Object>>) response.get("issues");
    return parseIssues(issues);
}
```

**Jira API Response Structure:**
```json
{
  "issues": [
    {
      "id": "12345",
      "key": "SCRUM-101",
      "fields": {
        "summary": "Fix bug",
        "status": {"name": "To Do"},
        "assignee": {"displayName": "John"},
        "project": {"key": "SCRUM"},
        "duedate": "2026-03-25"
      }
    }
  ]
}
```

#### 3. Get Transitions

```java
public List<Map<String, String>> getTransitions(String issueKey) {
    Map<String, Object> response = getWebClient()
        .get()
        .uri("/rest/api/3/issue/" + issueKey + "/transitions")
        .retrieve()
        .bodyToMono(Map.class)
        .block();

    List<Map<String, Object>> transitionList = (List<Map<String, Object>>) response.get("transitions");
    List<Map<String, String>> result = new ArrayList<>();
    
    if (transitionList != null) {
        for (Map<String, Object> t : transitionList) {
            Map<String, String> item = new HashMap<>();
            item.put("id", String.valueOf(t.get("id")));
            item.put("name", (String) t.get("name"));
            result.add(item);
        }
    }
    return result;
}
```

**Why needed?**
- Jira workflows have specific transition IDs
- "To Do" → "In Progress" might have ID "3"
- Must use correct transition ID to change status

#### 4. Update Issue (PUT)

```java
public boolean updateIssue(String issueKey, Map<String, Object> fields) {
    getWebClient()
        .put()
        .uri("/rest/api/3/issue/" + issueKey)
        .bodyValue(Map.of("fields", fields))
        .retrieve()
        .bodyToMono(Void.class)
        .block();
    return true;
}
```

**Example:** Update due date
```java
jiraApiService.updateIssue("SCRUM-101", Map.of("duedate", "2026-04-25"));
```

#### 5. Transition Issue (POST)

```java
public boolean transitionIssue(String issueKey, String transitionId) {
    getWebClient()
        .post()
        .uri("/rest/api/3/issue/" + issueKey + "/transitions")
        .bodyValue(Map.of("transition", Map.of("id", transitionId)))
        .retrieve()
        .bodyToMono(Void.class)
        .block();
    return true;
}
```

---

### ExecutionService.java

Orchestrates the preview and execute flow.

#### Preview Flow

```java
public PreviewResult preview(String commandId) {
    // 1. Get command template
    CommandTemplate command = commandTemplateService.getCommandById(commandId);

    // 2. Search Jira for matching issues
    List<JiraIssueDto> issues = jiraApiService.searchIssues(command.getJql(), MAX_RESULTS);
    
    // 3. Generate preview of changes
    List<PreviewChange> changes = generatePreviewChanges(command, issues);
    
    // 4. Create job record
    ExecutionJob job = ExecutionJob.builder()
        .commandId(commandId)
        .jql(command.getJql())
        .actionType(command.getActionType().name())
        .totalIssues(issues.size())
        .status(JobStatus.PREVIEW.name())
        .createdAt(LocalDateTime.now())
        .build();
    jobRepository.save(job);

    return PreviewResult.builder()
        .jql(command.getJql())
        .totalIssues(issues.size())
        .issues(issues)
        .changes(changes)
        .build();
}
```

#### Execute Flow

```java
public ExecutionResult execute(String commandId) {
    // 1. Get command
    CommandTemplate command = commandTemplateService.getCommandById(commandId);
    
    // 2. Search issues
    List<JiraIssueDto> issues = jiraApiService.searchIssues(command.getJql(), MAX_RESULTS);
    
    // 3. Create job
    ExecutionJob job = ExecutionJob.builder()
        .status(JobStatus.RUNNING.name())
        .build();
    job = jobRepository.save(job);

    int successCount = 0;
    int failedCount = 0;

    // 4. Process each issue
    for (JiraIssueDto issue : issues) {
        try {
            executeAction(command, issue, job.getId());
            successCount++;
        } catch (Exception e) {
            failedCount++;
            logAudit(issue.getKey(), "action", "N/A", "FAILED", 
                     command.getActionType().name(), job.getId());
        }
    }

    // 5. Update job with results
    job.setStatus(failedCount == 0 ? JobStatus.COMPLETED.name() : JobStatus.FAILED.name());
    job.setSuccessCount(successCount);
    job.setFailedCount(failedCount);
    job.setExecutedAt(LocalDateTime.now());
    jobRepository.save(job);

    return ExecutionResult.builder()
        .jobId(job.getId())
        .status(JobStatus.valueOf(job.getStatus()))
        .totalIssues(issues.size())
        .successCount(successCount)
        .failedCount(failedCount)
        .build();
}
```

#### Action Execution

```java
private void executeAction(CommandTemplate command, JiraIssueDto issue, Long jobId) {
    if (command.getActionType() == ActionType.UPDATE_DUEDATE) {
        // Calculate new date (+1 month)
        String newDate = calculateNewDueDate(issue.getDueDate(), "+1M");
        
        // Update Jira
        jiraApiService.updateIssue(issue.getKey(), Map.of("duedate", newDate));
        
        // Log audit
        logAudit(issue.getKey(), "duedate", issue.getDueDate(), newDate, 
                 "UPDATE_DUEDATE", jobId);
        
    } else if (command.getActionType() == ActionType.CHANGE_STATUS) {
        // Get available transitions
        List<Map<String, String>> transitions = jiraApiService.getTransitions(issue.getKey());
        
        // Parse parameters
        Map<String, Object> params = objectMapper.readValue(
            command.getParameters().toString(), Map.class);
        String toStatus = (String) params.get("toStatus");
        
        // Find transition ID
        String transitionId = transitions.stream()
            .filter(t -> t.get("name").equals(toStatus))
            .findFirst()
            .map(t -> t.get("id"))
            .orElse(null);
        
        // Execute if found
        if (transitionId != null) {
            jiraApiService.transitionIssue(issue.getKey(), transitionId);
            logAudit(issue.getKey(), "status", issue.getStatus(), toStatus, 
                     "CHANGE_STATUS", jobId);
        }
    }
}
```

---

### CommandTemplateService.java

Stores predefined commands.

```java
@Service
public class CommandTemplateService {

    private static final List<CommandTemplate> COMMANDS = Arrays.asList(
        CommandTemplate.builder()
            .id("CMD001")
            .name("Fetch My Issues")
            .actionType(ActionType.FETCH)
            .jql("assignee = currentUser() ORDER BY updated DESC")
            .parameters(null)
            .build(),

        CommandTemplate.builder()
            .id("CMD002")
            .name("Shift Due Date +1 Month")
            .actionType(ActionType.UPDATE_DUEDATE)
            .jql("assignee = currentUser() AND duedate <= endOfMonth() AND status NOT IN (Done, Closed)")
            .parameters("{\"type\": \"SHIFT_DUEDATE\", \"delta\": \"+1M\"}")
            .build(),

        CommandTemplate.builder()
            .id("CMD003")
            .name("Change Status: To Do → In Progress")
            .actionType(ActionType.CHANGE_STATUS)
            .jql("assignee = currentUser() AND status = \"To Do\"")
            .parameters("{\"fromStatus\": \"To Do\", \"toStatus\": \"In Progress\"}")
            .build(),

        CommandTemplate.builder()
            .id("CMD004")
            .name("Change Status: In Progress → Done")
            .actionType(ActionType.CHANGE_STATUS)
            .jql("assignee = currentUser() AND status = \"In Progress\"")
            .parameters("{\"fromStatus\": \"In Progress\", \"toStatus\": \"Done\"}")
            .build()
    );

    public List<CommandTemplate> getAllCommands() {
        return COMMANDS;
    }

    public CommandTemplate getCommandById(String id) {
        return COMMANDS.stream()
            .filter(cmd -> cmd.getId().equals(id))
            .findFirst()
            .orElse(null);
    }
}
```

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
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

jira:
  base-url: https://your-domain.atlassian.net
  email: your-email@example.com
  api-token: your-api-token

server:
  port: 8080
```

### Environment Variables

```bash
export JIRA_BASE_URL=https://your-domain.atlassian.net
export JIRA_EMAIL=your-email@example.com
export JIRA_API_TOKEN=your-api-token
```

### Docker Compose (PostgreSQL)

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: jira-ops-postgres
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

---

## Build & Run

### Prerequisites

- Java 17+
- Gradle 8.x (or use gradlew)
- PostgreSQL 16
- Jira Cloud account with API token

### Steps

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

# View history
curl -u admin:admin http://localhost:8080/api/v1/jobs
```

---

## Future Enhancements

### Phase 2: OAuth 2.0 Authentication
- Replace Basic Auth with Jira OAuth 2.0
- Support multiple Jira accounts
- Token refresh mechanism

### Phase 3: Additional Commands
- Add more command templates
- Custom JQL builder
- Scheduled job execution

### Phase 4: NLP Parser
- Rule-based parser for natural language
- Map commands like "shift due date by 1 month" to actions

### Phase 5: LLM Integration
- GPT-powered command understanding
- Complex query handling
- Suggestions and autocomplete

### Phase 6: Web Dashboard
- React/Next.js frontend
- Visual issue management
- Real-time job status

---

## Troubleshooting

### 410 Gone Error
**Cause:** Using deprecated API endpoint.

**Solution:** Ensure using `/rest/api/3/search/jql` (GET) not `/rest/api/3/search` (POST).

### Database Not Found
**Cause:** Database not created.

**Solution:**
```bash
docker-compose down -v
docker-compose up -d
```

### Invalid Credentials
**Cause:** Wrong Jira email or API token.

**Solution:** 
1. Go to https://id.atlassian.com/manage-profile/security/api-tokens
2. Create new token
3. Update `application.yml`

---

## License

MIT License
