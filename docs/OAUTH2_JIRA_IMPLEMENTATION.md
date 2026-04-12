# OAuth2 Authentication with Jira - Implementation Guide

## Overview

This document explains how OAuth2 authentication with Jira is implemented in the Jira Ops Agent application. The implementation uses Spring Security's OAuth2 Login feature to authenticate users via their Jira/Atlassian accounts.

## Architecture

### Authentication Flow

```
┌─────────┐                                    ┌─────────────────┐                                    ┌─────────────────┐
│  User   │                                    │  Our App        │                                    │  Atlassian     │
│ (Browser/Postman)                             │  (Spring Boot)  │                                    │  Auth Server   │
└─────────┘                                    └─────────────────┘                                    └─────────────────┘
      │                                              │                                              │
      │  1. GET /oauth2/authorization/jira           │                                              │
      │ ─────────────────────────────────────────►  │                                              │
      │                                              │                                              │
      │                                              │  2. Redirect to Atlassian                   │
      │  ◄───────────────────────────────────────── │  https://auth.atlassian.com/authorize       │
      │                                              │                                              │
      │  3. User logs in & consents                  │                                              │
      │ ─────────────────────────────────────────────────────────►                              │
      │                                              │                                              │
      │                                              │  4. Return auth code                       │
      │  ◄─────────────────────────────────────────────────────────────────────────────────────  │
      │                                              │                                              │
      │                                              │  5. Exchange code for tokens               │
      │                                              │ ─────────────────────────────────────────►  │
      │                                              │                                              │
      │                                              │  6. Return access_token + refresh_token    │
      │                                              │ ◄─────────────────────────────────────────  │
      │                                              │                                              │
      │  7. Create session (JSESSIONID)             │                                              │
      │ ◄───────────────────────────────────────── │                                              │
      │                                              │                                              │
      │  8. API calls with session                  │                                              │
      │ ─────────────────────────────────────────►  │                                              │
      │                                              │  9. Use OAuth2 token for Jira API calls    │
      │                                              │ ─────────────────────────────────────────►  │
      │                                              │                                              │
      │  ◄───────────────────────────────────────── │                                              │
```

## Step-by-Step Implementation

### Step 1: Create OAuth2 App in Atlassian

1. Go to [Atlassian Developer Console](https://developer.atlassian.com/console/myapps/)
2. Create a new app (OAuth 2.0 - 3LO)
3. Configure permissions:
   - `read:jira-user` - Read user profile
   - `write:jira-work` - Modify Jira issues
   - `offline_access` - Refresh tokens without re-authentication
   - `read:me` - Read current user info
4. Set callback URL: `http://localhost:8081/login/oauth2/code/jira`
5. Get credentials:
   - `client_id`
   - `client_secret`

### Detailed OAuth2 Redirect Flow

This diagram shows exactly how the redirect-uri works:

```
┌──────────────┐                                    ┌──────────────────────┐                                    ┌──────────────────┐
│    User      │                                    │   Our App            │                                    │   Atlassian     │
│  (Browser)   │                                    │   (Spring Boot)      │                                    │   Auth Server   │
└──────────────┘                                    └──────────────────────┘                                    └──────────────────┘
       │                                                    │                                              │
       │  1. GET /oauth2/authorization/jira               │                                              │
       │ ───────────────────────────────────────────────►  │                                              │
       │                                                    │                                              │
       │                                                    │  2. Build Atlassian URL                     │
       │                                                    │     redirect_uri=http://localhost:8081/     │
       │                                                    │     login/oauth2/code/jira                  │
       │                                                    │                                              │
       │                                                    │  3. Redirect to Atlassian                  │
       │  ◄─────────────────────────────────────────────── │  https://auth.atlassian.com/authorize       │
       │                                                    │     ?client_id=...                          │
       │                                                    │     &redirect_uri=...                       │
       │                                                    │     &response_type=code                     │
       │                                                    │     &scope=...                              │
       │                                                    │                                              │
       │  4. Atlassian Login Page                           │                                              │
       │ ───────────────────────────────────────────────►  │                                              │
       │                                                    │                                              │
       │  5. User enters credentials & clicks Accept       │                                              │
       │ ──────────────────────────────────────────────────────────────────────────────────────────────►     │
       │                                                    │                                              │
       │                                                    │  6. Atlassian validates redirect_uri        │
       │                                                    │      (must match what's registered)         │
       │                                                    │                                              │
       │                                                    │  7. Redirect with CODE                      │
       │  ◄─────────────────────────────────────────────── │  http://localhost:8081/                    │
       │                                                    │      login/oauth2/code/jira                 │
       │                                                    │      ?code=XYZ123...                        │
       │                                                    │      &state=...                             │
       │                                                    │                                              │
       │                                                    │  8. Spring receives code                    │
       │                                                    │     (OAuth2LoginAuthenticationFilter)       │
       │                                                    │                                              │
       │                                                    │  9. Exchange code for tokens               │
       │                                                    │     POST https://auth.atlassian.com/       │
       │                                                    │     oauth/token                             │
       │                                                    │     code=XYZ123&client_id=...              │
       │                                                    │ ─────────────────────────────────────────►  │
       │                                                    │                                              │
       │                                                    │  10. Return tokens                          │
       │                                                    │     access_token, refresh_token             │
       │                                                    │ ◄─────────────────────────────────────────  │
       │                                                    │                                              │
       │                                                    │  11. Store tokens in                        │
       │                                                    │      OAuth2AuthorizedClient                 │
       │                                                    │                                              │
       │                                                    │  12. Create HTTP Session                    │
       │                                                    │      (JSESSIONID cookie)                    │
       │                                                    │                                              │
       │                                                    │  13. Redirect to success URL               │
       │  ◄─────────────────────────────────────────────── │  /api/v1/commands                           │
       │                                                    │                                              │
```

**Key Points in the Flow:**

1. **redirect-uri in application.yml** defines where Atlassian sends the user after consent
2. **redirect-uri must be registered** in Jira Developer Console as allowed callback URL
3. **Spring automatically handles** the code-to-token exchange
4. **Session created** after successful authentication
5. **Success URL** (`/api/v1/commands`) is where user lands after login

### Step 2: Configure Application Properties

File: `src/main/resources/application.yml`

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
            scope: read:jira-user, write:jira-work, offline_access, read:me
            client-name: Jira
        provider:
          jira:
            issuer-uri: https://auth.atlassian.com
            authorization-uri: https://auth.atlassian.com/authorize
            token-uri: https://auth.atlassian.com/oauth/token
            user-info-uri: https://api.atlassian.com/me
            user-name-attribute: account_id
```

**Key configurations:**
- `registrationId`: `jira` - used in OAuth2 authorization URL
- `redirect-uri`: Pattern that Spring replaces with actual base URL
- `scope`: Permissions requested from user
- `user-name-attribute`: Which field from Jira response maps to username

**How redirect-uri works:**

The `redirect-uri` in application.yml tells Spring two things:

1. **Where Atlassian should send the user** after consent (must match Jira Developer Console)
2. **Which endpoint** Spring should listen on to receive the authorization code

When the user logs in successfully:
```
Atlassian → redirects to http://localhost:8081/login/oauth2/code/jira?code=XYZ
```

Spring automatically:
1. Receives the authorization code
2. Exchanges it for access_token + refresh_token (internal call to Atlassian)
3. Creates a session (JSESSIONID)
4. Redirects to the success URL defined in SecurityConfig (`/api/v1/commands`)

**Important:** The redirect-uri in application.yml MUST match the callback URL registered in Jira Developer Console. For example:
- Jira Console: `http://localhost:8081/login/oauth2/code/jira`
- application.yml: `{baseUrl}/login/oauth2/code/jira` → resolves to `http://localhost:8081/login/oauth2/code/jira`

### Step 3: Security Configuration

File: `src/main/java/com/jiraops/agent/security/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login/**", "/error", "/oauth2/**").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .defaultSuccessUrl("/api/v1/commands", true)
                .failureUrl("/login?error=true")
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            );

        return http.build();
    }
}
```

**Security rules:**
- `/login/**`, `/error`, `/oauth2/**` - Public (for OAuth2 flow)
- `/api/v1/**` - Requires authentication
- Other endpoints - Public

### Step 4: Custom OAuth2 User Service

File: `src/main/java/com/jiraops/agent/security/CustomOAuth2UserService.java`

```java
@Slf4j
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("Loading user from OAuth2 provider: {}", userRequest.getClientRegistration().getRegistrationId());
        
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.debug("OAuth2 User attributes: {}", oAuth2User.getAttributes());
        
        if ("jira".equalsIgnoreCase(registrationId)) {
            return new JiraOAuthUserPrincipal(oAuth2User);
        }
        
        return oAuth2User;
    }
}
```

This service processes the OAuth2 user info response and wraps it in a custom principal.

### Step 5: Custom OAuth2 User Principal

File: `src/main/java/com/jiraops/agent/security/JiraOAuthUserPrincipal.java`

```java
@Getter
public class JiraOAuthUserPrincipal implements OAuth2User {

    private final OAuth2User delegate;
    private final String accountId;
    private final String email;
    private final String displayName;

    public JiraOAuthUserPrincipal(OAuth2User delegate) {
        this.delegate = delegate;
        this.accountId = delegate.getAttribute("account_id");
        this.email = delegate.getAttribute("email");
        this.displayName = delegate.getAttribute("display_name");
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return accountId != null ? accountId : (email != null ? email : "unknown");
    }
}
```

Extracts key user information from OAuth2 response:
- `account_id` - Jira user ID
- `email` - User email
- `display_name` - User's display name

### Step 6: Token Retrieval Endpoint

File: `src/main/java/com/jiraops/agent/controller/CommandController.java`

```java
@GetMapping("/token")
public ResponseEntity<?> getToken(@AuthenticationPrincipal OAuth2User oauth2User) {
    if (oauth2User == null) {
        return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
    }
    
    var authorizedClient = authorizedClientService.loadAuthorizedClient("jira", oauth2User.getName());
    
    if (authorizedClient == null) {
        return ResponseEntity.status(404).body(Map.of("error", "No OAuth token found"));
    }
    
    OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
    
    Map<String, Object> response = new HashMap<>();
    response.put("access_token", accessToken.getTokenValue());
    response.put("token_type", accessToken.getTokenType().getValue());
    response.put("expires_at", accessToken.getExpiresAt() != null ? accessToken.getExpiresAt().toString() : null);
    
    if (authorizedClient.getRefreshToken() != null) {
        response.put("refresh_token", authorizedClient.getRefreshToken().getValue());
    }
    
    return ResponseEntity.ok(response);
}
```

This endpoint allows authenticated users to retrieve their OAuth2 tokens.

## How to Use

### 1. Login via Browser

1. Navigate to: `http://localhost:8081/oauth2/authorization/jira`
2. You will be redirected to Atlassian login page
3. Enter your Atlassian credentials
4. Review and accept the permissions
5. You will be redirected back to the app and logged in

### 2. Using Postman

#### Option A: Session Cookie (Recommended)

1. Login via browser first
2. Open browser DevTools → Application → Cookies
3. Copy the `JSESSIONID` cookie value
4. In Postman, add header:
   ```
   Cookie: JSESSIONID=your-session-value
   ```
5. Make API calls

#### Option B: Bearer Token

1. Get session cookie as described above
2. Call token endpoint:
   ```
   GET http://localhost:8081/api/v1/token
   Cookie: JSESSIONID=your-session-value
   ```
3. Response:
   ```json
   {
     "access_token": "eyJ...",
     "token_type": "Bearer",
     "expires_at": "2024-04-04T18:00:00Z",
     "refresh_token": "eyJ..."
   }
   ```
4. Use access_token for API calls:
   ```
   Authorization: Bearer eyJ...
   ```

### 3. Calling Jira APIs

The `JiraApiService` uses the OAuth2 access token internally to make API calls to Jira. The application uses Basic Auth with API token (configured separately) for Jira API calls, while OAuth2 is used for user authentication.

## Token Management

### Token Storage

- OAuth2 tokens are stored in `OAuth2AuthorizedClient` (in-memory or database)
- Session cookie (`JSESSIONID`) maintains user session
- Spring Security handles token refresh automatically

### Token Refresh

- When access token expires, Spring Security uses refresh token to get new access token
- This happens transparently - no additional code needed

### Token Expiry

- Access token: ~1 hour
- Refresh token: Long-lived (can be revoked)
- Use `/api/v1/token` to check expiry

## Security Considerations

1. **HTTPS**: Always use HTTPS in production
2. **State Parameter**: CSRF protection via state parameter (handled by Spring)
3. **Token Storage**: Tokens stored server-side, not in browser local storage
4. **Session Timeout**: Configure appropriate session timeout
5. **Scope Validation**: Only request necessary scopes

## Dependencies

Required in `build.gradle`:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
```

## Troubleshooting

### Error: insufficientScope

**Problem**: `Insufficient scope for this action`

**Solution**: Add `read:me` to scope in application.yml:
```yaml
scope: read:jira-user, write:jira-work, offline_access, read:me
```

### Error: authorization_request_not_found

**Problem**: OAuth session expired

**Solution**: Clear browser cookies and start fresh with new login

### Error: redirect_uri_mismatch

**Problem**: Callback URL doesn't match registered URL

**Solution**: Ensure redirect URI in application.yml matches exactly what's registered in Atlassian

## API Endpoints

| Endpoint | Method | Auth Required | Description |
|----------|--------|---------------|-------------|
| `/oauth2/authorization/jira` | GET | No | Initiate OAuth2 login |
| `/login/oauth2/code/jira` | GET | No | OAuth2 callback |
| `/api/v1/commands` | GET | Yes | List commands |
| `/api/v1/token` | GET | Yes | Get OAuth2 tokens |
| `/api/v1/nl-query` | POST | Yes | Natural language query |
| `/logout` | GET | Yes | Logout |

## Summary

The OAuth2 implementation:

1. **User initiates login** → Redirects to Atlassian
2. **User authenticates** → Atlassian returns authorization code
3. **Exchange code for tokens** → Server gets access + refresh tokens
4. **Create session** → Spring creates JSESSIONID cookie
5. **Access APIs** → Use session cookie or bearer token
6. **Token refresh** → Spring handles automatically

This provides secure, stateless authentication with the ability to make Jira API calls on behalf of the user.