# Jira Automation Agent MVP (Web + Agent-Assisted Bulk Operations)

## 1) Problem Statement
Teams spend significant time doing repetitive Jira operations manually:
- Moving issue status (`To Do -> In Progress -> Done`)
- Updating due dates in bulk (for example, end-of-month rollover)
- Handling repeated monthly administrative updates

This is error-prone and time-consuming.  
We need a safe automation layer with a web dashboard plus agent-assisted command execution.

## 2) MVP Goal
Build a web-based Jira automation tool that allows users to:
1. Fetch and filter their Jira stories/issues.
2. Bulk update due dates.
3. Bulk change issue status (using valid workflow transitions).
4. Use an agent command bar for natural-language automation.
5. Execute updates safely with preview, approval, audit log, and retry.

## 3) In-Scope (MVP)
- Jira Cloud integration (`atlassian.net`)
- User auth via Jira OAuth 2.0 (preferred) or API token (single-user mode)
- “My Issues” dashboard with filter/search
- Bulk action: due date shift
- Bulk action: status transition
- Agent command bar that converts user intent to a structured execution plan
- Dry-run preview before execution
- Approval-confirmation step
- Job execution history and audit logs

## 4) Out-of-Scope (MVP)
- Fully autonomous agent with no approval
- Complex workflow editing/creation in Jira
- Cross-project admin policy engine
- AI chat-only interface as primary UX
- Advanced analytics dashboards

## 5) Primary Use Cases

### UC-1: Bulk Due Date Shift
**User input:** “Shift due date of my SCRUM stories due this month by +1 month.”

**Expected behavior:**
1. Agent generates JQL + update action.
2. System shows impacted issues and before/after due dates.
3. User confirms.
4. System updates issues in batches.
5. Result report shown (success/fail/partial failures).

### UC-2: Bulk Status Change
**User input:** “Move my `To Do` SCRUM stories to `In Progress`.”

**Expected behavior:**
1. System fetches issues via JQL.
2. For each issue, checks valid Jira transitions.
3. Applies transition only if valid.
4. Reports skipped issues with reasons (invalid transition, permission, etc).

### UC-3: Monthly Rollover Job
**User input:** “At month-end, push unresolved due dates to next month.”

**Expected behavior:**
- Run as scheduled job (optional in MVP v1.1) or manual command now.
- Use saved filter + action template.
- Generate audit trail for all changes.

## 6) Product UX Recommendation
Use **Dashboard-first + Command Bar**, not chat-only.

### UI Modules
1. **My Issues Dashboard**
   - Filters: project, assignee, status, due date range, labels
   - Saved views
2. **Bulk Action Panel**
   - Action type: due-date shift / status transition
   - Preview impacted issues
3. **Agent Command Bar**
   - Natural language input
   - Shows parsed intent and generated plan
4. **Execution History**
   - Job list, status, counts, errors
5. **Audit Log View**
   - Per issue before/after values + actor + timestamp

## 7) Agent Role (MVP)
The agent does **planning**, not unrestricted execution.

### Agent responsibilities
- Parse natural language command.
- Generate structured plan:
  - JQL query
  - operation type
  - field changes
  - safety checks
- Ask for confirmation if scope is large/ambiguous.

### Non-responsibilities
- Directly mutate Jira without preview/approval.
- Execute unsupported operations.

## 8) Command Grammar (MVP)
Examples:
- `fetch my SCRUM stories for this month`
- `shift due date +1 month for my SCRUM stories with status in ("To Do","In Progress")`
- `change status To Do -> In Progress for project SCRUM assignee me`

Structured internal format:
```json
{
  "action": "UPDATE_DUEDATE",
  "jql": "project=SCRUM AND assignee=currentUser() AND duedate <= endOfMonth()",
  "update": {
    "type": "SHIFT_DUEDATE",
    "delta": "+1M"
  },
  "dryRun": true
}
