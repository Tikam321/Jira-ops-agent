package com.jiraops.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

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