
How I Built an AI Agent That Saves My Team 17 Hours a Week on Jira Tasks

From 90 minutes of daily manual work to 5 minutes of automation


THE FRUSTRATION THAT STARTED IT ALL

It was Friday afternoon. Sprint end. I had 47 bugs to move from "In Progress" to "Done."

So I did what every developer does:

Open Jira
Create filter
Select all 47 issues
Click "Change Status"
Click "Done"
Confirm
Repeat for due dates
Repeat for comments

45 minutes gone. Just for sprint cleanup.

Multiply that by 10 developers, every sprint...

That's 7.5 hours of pure administrative work. Every two weeks. Forever.

I thought: There has to be a better way.


THE FIRST ATTEMPT: COMMAND-BASED AUTOMATION

My initial idea was simple — create predefined commands for common tasks.

GET /api/v1/commands
CMD001: Fetch My Issues
CMD002: Shift Due Date +1 Month
CMD003: To Do → In Progress
CMD004: In Progress → Done

How it worked:

User selects a command (e.g., CMD004)
System generates JQL query
Preview shows affected issues
User confirms
Execute on Jira API

Example: curl -X POST /api/v1/execute/CMD004
Result: Successfully updated 47 issues

It worked! But it was limited. What if I wanted something not in the predefined list?


THE REALIZATION: WHY NOT USE NATURAL LANGUAGE?

The template approach was too rigid. What if I could just say what I wanted?

Instead of: "Use CMD004 to move my issues to Done"

I wanted: "Move all my bugs to done"

That's when I discovered Groq's Function Calling feature.


THE LIGHTBULB MOMENT: FUNCTION CALLING

Function calling is like giving the LLM a toolbox instead of letting it wander around a hardware store.

Old approach (Custom Prompts):
User: "move my bugs to done"
LLM: "Let me write an API call..."
LLM: "POST /rest/api/3/issue/PROJ-123/transitions..."
Result: Unpredictable. Often wrong.

New approach (Function Calling):
User: "move my bugs to done"
LLM: "I should use the transition_issue tool"
System: Executes the tool correctly
Result: Predictable. Reliable.


BUILDING THE LLM INTEGRATION

Here's where the magic happens. I used Groq's API with a model called llama-3.3-70b-versatile.

The System Prompt:

You are a Jira assistant. Based on the user's request, determine the appropriate action and call the relevant tool.

Available tools:
search_issues: Search Jira issues using JQL
transition_issue: Change status of ONE issue
bulk_transition: Change status of MULTIPLE issues
update_duedate: Update due date
bulk_update_duedate: Update due dates for MULTIPLE issues
add_comment: Add comment
bulk_add_comment: Add comment to MULTIPLE issues
assign_issue: Assign ONE issue
bulk_assign: Assign MULTIPLE issues

Use SINGLE tools for specific issues.
Use BULK tools for multiple issues.

The Tool Definitions:

name: bulk_transition
description: Change status of multiple Jira issues matching a JQL filter
parameters:
jql: JQL query to select issues (string)
status: Target status name (string)
required: jql, status

The Execution Flow:

1. User sends natural language query: "move all my bugs to in progress"

2. Groq returns tool call:
   tool: bulk_transition
   params:
   jql: assignee = currentUser() AND issuetype = Bug AND status = 'To Do'
   status: In Progress

3. Execute the tool:
   Search for issues matching JQL
   Loop through each issue
   Transition to new status

4. Return result: "Successfully transitioned 47 issues to 'In Progress'"


THE TOOL THAT CHANGED EVERYTHING

Here's what users can now do with simple natural language:

Single Issue Operations:

"move PROJ-123 to done"
"add comment to PROJ-456 saying please review"
"set due date for PROJ-789 to next Friday"

Bulk Operations (The Real Power):

"move all my bugs to in progress"
"add comment to all my tasks saying sprint review done"
"assign all unassigned stories to me"
"set due date for all my stories to 2026-04-15"

The difference is subtle but powerful:

Single: One specific issue key mentioned
Bulk: General pattern ("all my bugs")

The LLM learns to distinguish based on context. Magic? No. Good prompt engineering.


TECHNICAL ARCHITECTURE

User Interface (curl, Postman, or API)
|
v
Spring Boot API
|
|- CommandController (Template Mode)
|- NaturalLanguageController (LLM Mode)
|
v
GroqService (LLM + Function Calling)
|
Tools: search_issues, transition_issue, bulk_transition, bulk_update_duedate, bulk_add_comment, bulk_assign
|
v
JiraApiService
|
searchIssues() -> Jira REST API
transitionIssueByStatus() -> Jira Transitions API
updateDuedate() -> Jira Update API
addComment() -> Jira Comments API
assignIssue() -> Jira Assign API
|
v
Jira Cloud API (atlassian.net)


WHY GROQ? WHY NOT OPENAi?

Good question. Here's my reasoning:

Factor | Groq | OpenAI
Cost | Free tier | Paid
Speed | Very fast | Medium
Function Calling | Excellent | Good
Setup Complexity | Low | Low

I chose Groq because:

Free tier is generous — perfect for side projects
Incredibly fast — responses in less than 1 second
Native function calling — works out of the box

No reason to pay $100/month when Groq does the job for free.


THE DEPLOYMENT STORY

Getting this from local to cloud was surprisingly easy thanks to Docker.

Step 1: Create the Dockerfile

Multi-stage build for smaller image
Use eclipse-temurin:17-jdk for build
Use eclipse-temurin:17-jre for runtime
Copy jar from build stage
Expose port 8080
Run java -jar app.jar

Step 2: Docker Compose for Local Testing

postgres: PostgreSQL 16 Alpine
app: Build from Dockerfile
Ports: 8080:8080
Environment variables for Jira and Groq API keys

Step 3: Deploy to Render

GitHub Push -> Render detects Dockerfile -> Auto-deploy -> Live!

Total deployment time: 10 minutes
Monthly cost: $0 (free tier)
Uptime: 99.9%


THE NUMBERS THAT MATTER

After deploying to our team, we tracked metrics for a month:

Metric | Before | After | Improvement
Daily Jira time (per dev) | 90 min | 5 min | 95% reduction
Bulk update time | 3 min/issue | 2 sec/issue | 90x faster
Team weekly savings | 0 hrs | 17 hrs | 17 hrs/week
Operations logged | Manual | Auto | 100%

ROI: Infinite. We paid $0 and got 17 hours of time back every week.


CODE: THE GROQSERVICE IMPLEMENTATION

For those who want the actual implementation:

Service class using Spring @Service annotation
Model: llama-3.3-70b-versatile
API URL: https://api.groq.com/openai/v1/chat/completions
Inject Groq API key from configuration
Build request with system prompt and tool definitions
Call Groq API with tool_choice set to "auto"
Parse LLM response to extract tool call
Execute tool via JiraApiService
Return formatted result

The full code is available on my GitHub.


CHALLENGES I FACED (AND HOW I SOLVED THEM)

Challenge 1: LLM Hallucinations

Problem: Early versions let the LLM generate raw API calls. It often made mistakes.
Solution: Function calling with strict schemas. The LLM doesn't generate calls — it selects tools.

Challenge 2: Bulk Operation Safety

Problem: What if someone accidentally transitions 500 issues?
Solution: Preview before execute. The system shows affected issues before any changes.

Challenge 3: Jira Transition IDs

Problem: Each Jira project has different workflow transition IDs.
Solution: Transition by status name, not ID. The system resolves names to IDs at runtime.

Challenge 4: Self-Reference

Problem: Users say "assign to me" but Jira needs accountId.
Solution: Resolve "me" to actual accountId via the /rest/api/3/myself endpoint.


WHAT I LEARNED

Function calling is better than Custom prompts — Structured tools prevent hallucinations

Preview before execute — Safety nets matter for bulk operations

Dual-mode is best — Templates for reliability, LLM for flexibility

Free tier is sufficient — No need to pay for side projects

Docker simplifies everything — Containerization equals consistent deployments


THE IMPACT ON MY TEAM

Before this tool:

Sprint cleanup took 45 minutes
Due date updates took 30 minutes
Bulk comments took 20 minutes
Total: 95 minutes of manual work per sprint

After this tool:

"move all my bugs to done" -> 5 seconds
"set due dates for new sprint" -> 10 seconds
"add comments to all tasks" -> 15 seconds
Total: 30 seconds of automation per sprint

That's 94 minutes saved per developer per sprint.
For a 10-person team: 940 minutes equals 15.6 hours per sprint.


FUTURE ROADMAP

What's next for this project:

React Dashboard — Visual interface for non-technical users
Multi-turn Conversations — "Find my bugs... now move them to done"
Scheduled Automation — Cron-based execution for recurring tasks
True MCP Server — Full MCP protocol implementation
Team Analytics — Dashboard showing time saved, operations performed


CONCLUSION: WORK SMARTER, NOT HARDER

We spend too much time on tools that should work for us.

Every click we automate is a click we can spend on actual problem-solving.

This project started as a frustration and became a tool my entire team uses daily.

The best automation is one that doesn't require you to learn anything new.

Just say what you want. Done.


GET THE CODE

The full source code is available on GitHub.

Tech stack:
Spring Boot 3.2.5
Java 17
Groq API
PostgreSQL
Docker

Deploy your own in 10 minutes on Render's free tier.


LET'S CONNECT

Have questions about the implementation?
Want to collaborate on the React dashboard?
Drop a comment below or reach out!

I'm always happy to chat about:
LLM integrations
Developer productivity
Jira automation
Building side projects that actually get used


#automation #jira #llm #productivity #developer-tools #ai #groq #springboot #docker


If this saved you time reading, imagine how much time it could save your team. Share it with a colleague who might need it.
