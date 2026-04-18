package com.jiraops.agent.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Getter
public class JiraOAuthUserPrincipal implements OAuth2User, Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String name;
    private final String accountId;
    private final String email;
    private final String displayName;

    public JiraOAuthUserPrincipal(OAuth2User delegate) {
        this.attributes = delegate.getAttributes();
        this.authorities = delegate.getAuthorities();
        this.name = delegate.getName();
        this.accountId = (String) attributes.get("account_id");
        this.email = (String) attributes.get("email");
        this.displayName = (String) attributes.get("display_name");
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getAccessToken() {
        if (attributes != null && attributes.containsKey("access_token")) {
            return (String) attributes.get("access_token");
        }
        return null;
    }

    @Override
    public String toString() {
        return "JiraOAuthUserPrincipal{accountId='" + accountId + "', email='" + email + "', displayName='" + displayName + "'}";
    }
}
