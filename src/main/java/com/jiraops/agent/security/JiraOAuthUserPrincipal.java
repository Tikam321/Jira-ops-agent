package com.jiraops.agent.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    public String getAccessToken() {
        Map<String, Object> attrs = getAttributes();
        if (attrs != null && attrs.containsKey("access_token")) {
            return (String) attrs.get("access_token");
        }
        return null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return accountId != null ? accountId : (email != null ? email : "unknown");
    }

    @Override
    public String toString() {
        return "JiraOAuthUserPrincipal{accountId='" + accountId + "', email='" + email + "', displayName='" + displayName + "'}";
    }
}