package com.jiraops.agent.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        String dashboardUrl = frontendUrl + "/dashboard";
        String loginUrl = frontendUrl + "/login";

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/swagger-ui/**", "/v3/api-docs/**", "/api/v1/auth/debug").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .defaultSuccessUrl(dashboardUrl, true)
                .failureUrl(loginUrl + "?error=oauth_failed")
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/logout", "/api/**")
            )
            .sessionManagement(session -> session
                .sessionFixation().migrateSession()
                .maximumSessions(1)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl(loginUrl + "?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .addLogoutHandler(logoutHandler())
            );

        return http.build();
    }

    @Bean
    public LogoutHandler logoutHandler() {
        return (request, response, authentication) -> {
            log.info("==================== USER LOGOUT ====================");
            log.info("Logout initiated");
            log.info("Session invalidated: true");
            log.info("Authentication cleared: true");
            
            if (authentication != null) {
                String principalType = authentication.getPrincipal().getClass().getSimpleName();
                log.info("Principal type: " + principalType);
                
                if (authentication.getPrincipal() instanceof OAuth2User) {
                    OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                    String accountId = oauth2User.getAttribute("account_id");
                    String email = oauth2User.getAttribute("email");
                    log.info("User account_id: " + accountId);
                    log.info("User email: " + email);
                }
            }
            
            SecurityContextHolder.clearContext();
            log.info("Security context cleared");
            log.info("=====================================================");
        };
    }
}
