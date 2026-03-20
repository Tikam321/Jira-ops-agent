# Jira Automation Agent MVP (Web + Agent-Assisted Bulk Operations)

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
```

Or use environment variables:
```bash
export JIRA_BASE_URL=https://your-domain.atlassian.net
export JIRA_EMAIL=your-email@example.com
export JIRA_API_TOKEN=your-api-token
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

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/commands` | List all 4 predefined commands |
| GET | `/commands/{id}` | Get specific command details |
| POST | `/preview/{id}` | Preview affected issues before execution |
| POST | `/execute/{id}` | Execute the command on Jira |
| GET | `/jobs` | View execution history |

## Available Commands

| ID | Name | Action |
|----|------|--------|
| CMD001 | Fetch My Issues | Fetch all issues assigned to current user |
| CMD002 | Shift Due Date +1 Month | Shift due dates forward by 1 month |
| CMD003 | Change Status: To Do → In Progress | Move To Do issues to In Progress |
| CMD004 | Change Status: In Progress → Done | Move In Progress issues to Done |

## Example Usage

### 1. Preview which issues will be affected
```bash
curl -u admin:admin -X POST http://localhost:8080/api/v1/preview/CMD002
```

### 2. Execute the command
```bash
curl -u admin:admin -X POST http://localhost:8080/api/v1/execute/CMD002
```

### 3. Check execution history
```bash
curl -u admin:admin http://localhost:8080/api/v1/jobs
```

## Project Structure

```
src/main/java/com/jiraops/agent/
├── controller/          # REST endpoints
├── service/            # Business logic
├── model/
│   ├── dto/           # Request/Response objects
│   ├── entity/        # JPA entities
│   └── enums/         # ActionType, JobStatus
├── repository/        # JPA repositories
├── security/          # Auth config
└── exception/         # Exception handling
```

## Architecture

```
User Selects Command → Generate JQL → Preview Issues → User Confirms → Execute on Jira API → Log Audit
```

## Future Enhancements (Phase 2+)

- [ ] OAuth 2.0 authentication
- [ ] Add more command templates
- [ ] Rule-based NLP parser
- [ ] LLM-powered command understanding
- [ ] Scheduled job execution
- [ ] Web dashboard UI
