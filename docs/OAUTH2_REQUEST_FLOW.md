# OAuth2 Request Flow - Complete Documentation

## Overview

This document explains the complete OAuth2 authentication flow in the Jira Ops Agent application, from the moment a user clicks the login button until they are authenticated and can make API calls.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                    FRONTEND                                           │
│                                                                                         │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│  │ frontend/src/config/index.ts                                                   │   │
│  │                                                                                  │   │
│  │ const config = {                                                                │   │
│  │   authUrl: isProduction                                                         │   │
│  │     ? 'https://jira-ops-agent-vom2.onrender.com/oauth2/authorization/jira'   │   │
│  │     : 'http://localhost:8081/oauth2/authorization/jira'                        │   │
│  │ };                                                                              │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                         │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│  │ frontend/src/contexts/AuthContext.tsx                                          │   │
│  │                                                                                  │   │
│  │ const login = () => window.location.href = config.authUrl;                    │   │
│  │ const checkAuth = () => backendApi.get('/auth/me');                            │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────┬──────┘
                                                                                  │
                                    1. Login Button Click                           ↓
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                                                                  │
│  ╔═══════════════════════════════════════════════════════════════════════════════╗
│  ║                     SPRING SECURITY AUTO-GENERATED                            ║
│  ║                                                                                ║
│  ║  These endpoints are AUTO-CREATED by Spring Security when you configure:    ║
│  ║                                                                                ║
║  ║    http://localhost:8081/oauth2/authorization/jira                          ║  │
║  ║           ↓                                                                   ║  │
║  ║    /oauth2/authorization/{registrationId}                                     ║  │
║  ║                                                                                ║
║  ║    http://localhost:8081/login/oauth2/code/jira                              ║  │
║  ║           ↓                                                                   ║  │
║  ║    /login/oauth2/code/{registrationId} - OAuth2 callback                     ║
║  ╚═══════════════════════════════════════════════════════════════════════════════╝
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┬──────┘
                                                                                  │
                                    2. Redirect to Atlassian                       ↓
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                                                                  │
│  HTTP 302 → https://auth.atlassian.com/authorize                                 │
│                                                                                  │
│  Parameters:                                                                      │
│  ┌────────────────────────────────────────────────────────────────────────────┐   │
│  │ ?client_id=xxx                         ← From application.yml              │   │
│  │ &redirect_uri=http://localhost:8081/login/oauth2/code/jira                │   │
│  │ &response_type=code                                                            │   │
│  │ &scope=read:jira-user,read:jira-work,write:jira-work,offline_access,read:me│   │
│  │ &state=xxx                                                                    │   │
│  └────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┬──────┘
                                                                                  │
                                    3. User Authenticates                          ↓
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │                           JIRA LOGIN SCREEN                                  │   │
│  │                                                                             │   │
│  │   ┌─────────────────────────────────────────────────────────────────┐     │   │
│  │   │  📧 Email: [user@company.com]                                  │     │   │
│  │   │  🔒 Password: [************]                                    │     │   │
│  │   │                                                                 │     │   │
│  │   │  ☐ [✓] I agree to the permissions                              │     │   │
│  │   │                                                                 │     │   │
│  │   │              [LOGIN]                                            │     │   │
│  │   └─────────────────────────────────────────────────────────────────┘     │   │
│  │                                                                             │   │
│  │   Permissions Requested:                                                    │   │
│  │   • Read Jira user profile (read:jira-user)                               │   │
│  │   • Read Jira work data (read:jira-work)                                  │   │
│  │   • Modify Jira work data (write:jira-work)                               │   │
│  │   • Offline access (offline_access)                                       │   │
│  │   • Read your profile (read:me)                                           │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┬──────┘
                                                                                  │
                                    4. Redirect with CODE                         ↓
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                                                                  │
│  HTTP 302 → http://localhost:8081/login/oauth2/code/jira                       │
│                                                                                  │
│  Parameters:                                                                      │
│  ┌────────────────────────────────────────────────────────────────────────────┐   │
│  │ ?code=ATAAxxxx...xxxx      ← Authorization code from Atlassian            │   │
│  │ &state=xxx                 ← State parameter for CSRF protection          │   │
│  └────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┬──────┘
                                                                                  │
                                    5. Spring Security Processes                   ↓
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │ OAuth2LoginAuthenticationFilter                                           │   │
│  │                                                                            │   │
│  │  1. Receives authorization code                                           │   │
│  │                                                                            │   │
│  │  2. Exchanges code for tokens:                                            │   │
│  │     POST https://auth.atlassian.com/oauth/token                           │   │
│  │                                                                            │   │
│  │     Request:                                                               │   │
│  │     ┌─────────────────────────────────────────────────────────────────┐   │   │
│  │     │ grant_type=authorization_code                                   │   │   │
│  │     │ code=ATAAxxxx...                                                 │   │   │
│  │     │ client_id=xxx                                                   │   │   │
│  │     │ client_secret=xxx                                               │   │   │
│  │     │ redirect_uri=http://localhost:8081/login/oauth2/code/jira       │   │   │
│  │     └─────────────────────────────────────────────────────────────────┘   │   │
│  │                                                                            │   │
│  │     Response:                                                              │   │
│  │     ┌─────────────────────────────────────────────────────────────────┐   │   │
│  │     │ {                                                                │   │   │
│  │     │   "access_token": "eyJ...",                                     │   │   │
│  │     │   "refresh_token": "eyJ...",                                    │   │   │
│  │     │   "expires_in": 3600                                            │   │   │
│  │     │ }                                                                │   │   │
│  │     └─────────────────────────────────────────────────────────────────┘   │   │
│  │                                                                            │   │
│  │  3. CustomOAuth2UserService.loadUser() - Creates user principal         │   │
│  │                                                                            │   │
│  │  4. Creates HTTP Session - JSESSIONID cookie                            │   │
│  │                                                                            │   │
│  │  5. Redirects to: /dashboard (defaultSuccessUrl)                        │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┬──────┘
                                                                                  │
                                    6. Frontend Auth Check                         ↓
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │ AuthContext.tsx - useEffect on app load                                    │   │
│  │                                                                            │   │
│  │ checkAuth()                                                                │   │
│  │    ↓                                                                      │   │
│  │ backendApi.get('/auth/me')  ← With JSESSIONID cookie                    │   │
│  └────────────────────────────────┬───────────────────────────────────────────┘   │
│                                   ↓                                              │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │ CommandController.getCurrentUser()                                        │   │
│  │                                                                            │   │
│  │ @GetMapping("/auth/me")                                                    │   │
│  │ public ResponseEntity<?> getCurrentUser(                                  │   │
│  │     @AuthenticationPrincipal OAuth2User oauth2User) {                    │   │
│  │     if (oauth2User == null) {                                             │   │
│  │         return ResponseEntity.status(401)...                             │   │
│  │     }                                                                      │   │
│  │     return ResponseEntity.ok(userData);                                  │   │
│  │ }                                                                          │   │
│  └────────────────────────────────┬───────────────────────────────────────────┘   │
│                                   ↓                                              │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │ SUCCESS - Returns 200 with user data                                       │   │
│  │ {                                                                          │   │
│  │   "name": "John Doe",                                                     │   │
│  │   "email": "john@company.com",                                           │   │
│  │   "accountId": "abc123...",                                              │   │
│  │   "picture": "https://..."                                               │   │
│  │ }                                                                          │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## Configuration Locations

### 1. Frontend Configuration

**File:** `frontend/src/config/index.ts`

```typescript
const config = {
  authUrl: isProduction
    ? `https://jira-ops-agent-vom2.onrender.com/oauth2/authorization/jira`
    : `http://localhost:8081/oauth2/authorization/jira`,
};
```

### 2. Backend Application Configuration

**File:** `src/main/resources/application.yml`

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          jira:
            client-id: ${JIRA_OAUTH_CLIENT_ID}
            client-secret: ${JIRA_OAUTH_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: read:jira-user, read:jira-work, write:jira-work, offline_access, read:me
            client-name: Jira
        provider:
          jira:
            issuer-uri: https://auth.atlassian.com
            authorization-uri: https://auth.atlassian.com/authorize
            token-uri: https://auth.atlassian.com/oauth/token
            user-info-uri: https://api.atlassian.com/me
            user-name-attribute: account_id
```

### 3. Security Configuration

**File:** `src/main/java/com/jiraops/agent/security/SecurityConfig.java`

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/login", 
                "/error", 
                "/oauth2/**", 
                "/login/oauth2/**"
            ).permitAll()
            .anyRequest().authenticated()
        )
        .oauth2Login(oauth2 -> oauth2
            .userInfoEndpoint(userInfo -> userInfo
                .userService(customOAuth2UserService)
            )
            .defaultSuccessUrl("/dashboard", true)
            .failureHandler(failureHandler)
        );
    return http.build();
}
```

---

## Key Endpoints Summary

| Endpoint | Purpose | Auth Required | Who Creates |
|----------|---------|---------------|-------------|
| `/oauth2/authorization/jira` | Start OAuth2 flow | No | Spring Security Auto |
| `/login/oauth2/code/jira` | OAuth2 callback | No | Spring Security Auto |
| `/api/v1/auth/me` | Check login status | Optional | CommandController |
| `/dashboard` | After login redirect | Yes | Frontend Route |

---

## Session Management

### Session Cookie

```yaml
# application.yml - Session configuration
server:
  servlet:
    session:
      cookie:
        name: JIRAOPS_SESSION      # Session cookie name
        http-only: true           # Not accessible via JavaScript
        secure: true              # Only sent over HTTPS
        same-site: none           # Cross-site cookies enabled
        max-age: 1d               # 1 day expiration
```

### Session Flow

```
User completes login
       ↓
Spring Security creates session
       ↓
Response includes: Set-Cookie: JIRAOPS_SESSION=xxx; Path=/; HttpOnly; Secure; SameSite=None
       ↓
Browser stores cookie
       ↓
Subsequent requests include: Cookie: JIRAOPS_SESSION=xxx
       ↓
Spring Security validates session → @AuthenticationPrincipal populated
```

---

## Step-by-Step Flow Summary

| Step | Action | URL/Component |
|------|--------|---------------|
| 1 | User clicks Login | Frontend: `config.authUrl` |
| 2 | Redirect to Jira | `https://auth.atlassian.com/authorize` |
| 3 | User enters credentials | Jira login screen |
| 4 | Jira redirects with CODE | `/login/oauth2/code/jira?code=xxx` |
| 5 | Spring exchanges code for token | `OAuth2LoginAuthenticationFilter` |
| 6 | Create session + redirect | JSESSIONID cookie → `/dashboard` |
| 7 | Frontend checks auth | `GET /api/v1/auth/me` |
| 8 | Returns user data | 200 OK with user info |

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| 401 on `/auth/me` | User not logged in | Complete OAuth2 flow |
| redirect_uri_mismatch | Callback URL mismatch | Check application.yml vs Jira Console |
| insufficientScope | Missing scope | Add required scopes to application.yml |
| Session lost | Cookie not sent | Check browser allows third-party cookies |

### Debug Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/api/v1/auth/debug` | Shows session info |
| `/api/v1/auth/me` | Shows current user |
| `/api/v1/token` | Shows OAuth token details |

---

## Related Documentation

- [OAuth2_JIRA_IMPLEMENTATION.md](./OAuth2_JIRA_IMPLEMENTATION.md) - Detailed OAuth2 setup
- [MCP_PROTOCOL.md](./MCP_PROTOCOL.md) - MCP tool calling explanation

---

*Generated: April 2026*
*Project: Jira Ops Agent*