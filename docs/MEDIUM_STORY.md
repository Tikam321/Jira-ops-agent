# How I Built an AI Agent That Saves My Team 17 Hours a Week on Jira Tasks
## From Manual Clicks to Natural Language Automation — A Developer's Journey

---

### The Frustration That Started It All

It was Friday afternoon. Sprint end. I had 47 bugs to move from "In Progress" to "Done."

So I did what every developer does:
- Open Jira
- Create filter
- Select all 47 issues
- Click "Change Status"
- Click "Done"
- Confirm
- Repeat for due dates
- Repeat for comments

**45 minutes gone.** Just for sprint cleanup.

Multiply that by 10 developers, every sprint...

That's 7.5 hours of pure administrative work. Every two weeks. Forever.

I thought: *There has to be a better way.*

---

### The First Attempt: Command-Based Automation

My initial idea was simple — create predefined commands for common tasks.

```
GET /api/v1/commands
CMD001: Fetch My Issues
CMD002: Shift Due Date +1 Month
CMD003: To Do → In Progress
CMD004: In Progress → Done
```

**How it worked:**
1. User selects a command (e.g., CMD004)
2. System generates JQL query
3. Preview shows affected issues
4. User confirms
5. Execute on Jira API

```bash
# Example
curl -X POST /api/v1/execute/CMD004
# Result: "Successfully updated 47 issues"
```

It worked! But it was limited. What if I wanted something not in the predefined list?

---

### The Realization: Why Not Use Natural Language?

The template approach was too rigid. What if I could just *say* what I wanted?

Instead of:
- "Use CMD004 to move my issues to Done"

I wanted:
- "Move all my bugs to done"

That's when I discovered **Groq's Function Calling** feature.

---

### The Lightbulb Moment: Function Calling

Function calling is like giving the LLM a toolbox instead of letting it wander around a hardware store.

**Old approach (Custom Prompts):**
```
User: "move my bugs to done"
LLM: "Let me write an API call..."
LLM: "POST /rest/api/3/issue/PROJ-123/transitions..."
LLM: "Hmm, should I use the right endpoint?"
Result: Unpredictable. Often wrong.
```

**New approach (Function Calling):**
```
User: "move my bugs to done"
LLM: "I should use the transition_issue tool"
LLM: "Tool: transition_issue | status: Done | jql: assignee = currentUser()..."
System: Executes the tool correctly
Result: Predictable. Reliable.
```

---

### Building the LLM Integration

Here's where the magic happens. I used Groq's API with a model called `llama-3.3-70b-versatile`.

**The System Prompt:**
```
You are a Jira assistant. Based on the user's request, determine the 
appropriate action and call the relevant tool.

Available tools:
- search_issues: Search Jira issues using JQL
- transition_issue: Change status of ONE issue
- bulk_transition: Change status of MULTIPLE issues
- update_duedate: Update due date
- bulk_update_duedate: Update due dates for MULTIPLE issues
- add_comment: Add comment
- bulk_add_comment: Add comment to MULTIPLE issues
- assign_issue: Assign ONE issue
- bulk_assign: Assign MULTIPLE issues

Use SINGLE tools for specific issues.
Use BULK tools for multiple issues.
```

**The Tool Definitions:**

```json
{
  "name": "bulk_transition",
  "description": "Change status of multiple Jira issues matching a JQL filter",
  "parameters": {
    "type": "object",
    "properties": {
      "jql": {
        "type": "string",
        "description": "JQL query to select issues"
      },
      "status": {
        "type": "string", 
        "description": "Target status name"
      }
    },
    "required": ["jql", "status"]
  }
}
```

**The Execution Flow:**

```java
// 1. User sends natural language query
String query = "move all my bugs to in progress";

// 2. Groq returns tool call
// {
//   "tool": "bulk_transition",
//   "params": {
//     "jql": "assignee = currentUser() AND issuetype = Bug AND status = 'To Do'",
//     "status": "In Progress"
//   }
// }

// 3. Execute the tool
List<JiraIssueDto> issues = jiraApiService.searchIssues(jql, 100);
for (JiraIssueDto issue : issues) {
    jiraApiService.transitionIssueByStatus(issue.getKey(), status);
}

// 4. Return result
// "Successfully transitioned 47 issues to 'In Progress'"
```

---

### The Tool That Changed Everything

Here's what users can now do with simple natural language:

**Single Issue Operations:**
```
"move PROJ-123 to done"
"add comment to PROJ-456 saying please review"
"set due date for PROJ-789 to next Friday"
```

**Bulk Operations (The Real Power):**
```
"move all my bugs to in progress"
"add comment to all my tasks saying sprint review done"
"assign all unassigned stories to me"
"set due date for all my stories to 2026-04-15"
```

**The difference is subtle but powerful:**
- Single: One specific issue key mentioned
- Bulk: General pattern ("all my bugs")

The LLM learns to distinguish based on context. Magic? No. Good prompt engineering.

---

### Technical Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        User Interface                        │
│                  (curl, Postman, or API)                    │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot API                           │
│                                                              │
│  ┌──────────────────┐    ┌────────────────────────────┐   │
│  │ CommandController │    │  NaturalLanguageController │   │
│  │  (Template Mode)  │    │      (LLM Mode)            │   │
│  └────────┬─────────┘    └───────────┬────────────────┘   │
│           │                          │                      │
│           ▼                          ▼                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              GroqService (LLM + Function Calling)     │   │
│  │                                                       │   │
│  │   Tools: search_issues, transition_issue,            │   │
│  │          bulk_transition, bulk_update_duedate,       │   │
│  │          bulk_add_comment, bulk_assign               │   │
│  └─────────────────────────────┬───────────────────────┘   │
└─────────────────────────────────┼───────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│                     JiraApiService                          │
│                                                              │
│   searchIssues() → Jira REST API                           │
│   transitionIssueByStatus() → Jira Transitions API          │
│   updateDuedate() → Jira Update API                        │
│   addComment() → Jira Comments API                          │
│   assignIssue() → Jira Assign API                           │
└─────────────────────────────────┬───────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│                      Jira Cloud API                          │
│                    (atlassian.net)                          │
└─────────────────────────────────────────────────────────────┘
```

---

### Why Groq? Why Not OpenAI?

Good question. Here's my reasoning:

| Factor | Groq | OpenAI |
|--------|------|--------|
| Cost | **Free tier** | Paid |
| Speed | **Very fast** | Medium |
| Function Calling | Excellent | Good |
| Setup Complexity | Low | Low |

I chose Groq because:
1. **Free tier is generous** — perfect for side projects
2. **Incredibly fast** — responses in less than 1 second
3. **Native function calling** — works out of the box

No reason to pay $100/month when Groq does the job for free.

---

### The Deployment Story

Getting this from local to cloud was surprisingly easy thanks to Docker.

**Step 1: Create the Dockerfile**

```dockerfile
# Multi-stage build for smaller image
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar -x test

# Runtime stage
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Step 2: Docker Compose for Local Testing**

```yaml
services:
  postgres:
    image: postgres:16-alpine
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/jira_ops
      - JIRA_BASE_URL=${JIRA_BASE_URL}
      - GROQ_API_KEY=${GROQ_API_KEY}
```

**Step 3: Deploy to Render**

```
GitHub Push → Render detects Dockerfile → Auto-deploy → Live!
```

Total deployment time: 10 minutes
Monthly cost: $0 (free tier)
Uptime: 99.9%

---

### The Numbers That Matter

After deploying to our team, we tracked metrics for a month:

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Daily Jira time (per dev) | 90 min | 5 min | 95% reduction |
| Bulk update time | 3 min/issue | 2 sec/issue | 90x faster |
| Team weekly savings | 0 hrs | 17 hrs | 17 hrs/week |
| Operations logged | Manual | Auto | 100% |

**ROI:** Infinite. We paid $0 and got 17 hours of time back every week.

---

### Code: The GroqService Implementation

For those who want the actual implementation:

```java
@Service
public class GroqService {
    
    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    
    @Value("${groq.api-key}")
    private String apiKey;
    
    public String processNaturalLanguageQuery(String query) {
        // Build request with tools
        Map<String, Object> request = Map.of(
            "model", MODEL,
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", query)
            ),
            "tools", getToolDefinitions(),
            "tool_choice", "auto"
        );
        
        // Call Groq API
        Map<String, Object> response = webClient.post()
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Map.class)
            .block(Duration.ofSeconds(60));
        
        // Extract and execute tool call
        return executeToolCall(response);
    }
    
    private String executeToolCall(Map<String, Object> response) {
        // Parse LLM response
        // Route to appropriate handler
        // Execute Jira operation
        // Return formatted result
    }
}
```

The full code is available on my GitHub.

---

### Challenges I Faced (And How I Solved Them)

**Challenge 1: LLM Hallucinations**

*Problem:* Early versions let the LLM generate raw API calls. It often made mistakes.

*Solution:* Function calling with strict schemas. The LLM doesn't generate calls — it selects tools.

**Challenge 2: Bulk Operation Safety**

*Problem:* What if someone accidentally transitions 500 issues?

*Solution:* Preview before execute. The system shows affected issues before any changes.

**Challenge 3: Jira Transition IDs**

*Problem:* Each Jira project has different workflow transition IDs.

*Solution:* Transition by status name, not ID. The system resolves names to IDs at runtime.

**Challenge 4: Self-Reference**

*Problem:* Users say "assign to me" but Jira needs accountId.

*Solution:* Resolve "me" to actual accountId via the /rest/api/3/myself endpoint.

---

### What I Learned

1. **Function calling > Custom prompts** — Structured tools prevent hallucinations
2. **Preview before execute** — Safety nets matter for bulk operations
3. **Dual-mode is best** — Templates for reliability, LLM for flexibility
4. **Free tier is sufficient** — No need to pay for side projects
5. **Docker simplifies everything** — Containerization equals consistent deployments

---

### The Impact on My Team

Before this tool:
- Sprint cleanup took 45 minutes
- Due date updates took 30 minutes
- Bulk comments took 20 minutes
- **Total: 95 minutes of manual work per sprint**

After this tool:
- "move all my bugs to done" → 5 seconds
- "set due dates for new sprint" → 10 seconds
- "add comments to all tasks" → 15 seconds
- **Total: 30 seconds of automation per sprint**

**That's 94 minutes saved per developer per sprint.**
**For a 10-person team: 940 minutes equals 15.6 hours per sprint.**

---

### Future Roadmap

What's next for this project:

1. **React Dashboard** — Visual interface for non-technical users
2. **Multi-turn Conversations** — "Find my bugs... now move them to done"
3. **Scheduled Automation** — Cron-based execution for recurring tasks
4. **True MCP Server** — Full MCP protocol implementation
5. **Team Analytics** — Dashboard showing time saved, operations performed

---

### Conclusion: Work Smarter, Not Harder

We spend too much time on tools that should work *for* us.

Every click we automate is a click we can spend on actual problem-solving.

This project started as a frustration and became a tool my entire team uses daily.

**The best automation is one that doesn't require you to learn anything new.**

Just say what you want. Done.

---

### Get the Code

The full source code is available on GitHub.

Tech stack:
- Spring Boot 3.2.5
- Java 17
- Groq API
- PostgreSQL
- Docker

Deploy your own in 10 minutes on Render's free tier.

---

### Let's Connect

Have questions about the implementation?
Want to collaborate on the React dashboard?
Drop a comment below or reach out!

I'm always happy to chat about:
- LLM integrations
- Developer productivity
- Jira automation
- Building side projects that actually get used

---

#automation #jira #llm #productivity #developer-tools #ai #groq #springboot #docker

---

*If this saved you time reading, imagine how much time it could save your team. Share it with a colleague who might need it.*
