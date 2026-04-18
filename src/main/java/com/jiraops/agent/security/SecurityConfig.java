package com.jiraops.agent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

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
    public HttpSessionOAuth2AuthorizationRequestRepository authorizationRequestRepository() {
        return new HttpSessionOAuth2AuthorizationRequestRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String dashboardUrl = frontendUrl + "/dashboard";
        String loginUrl = frontendUrl + "/login";

        AuthenticationFailureHandler failureHandler = new SimpleUrlAuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                    org.springframework.security.core.AuthenticationException exception) throws IOException, ServletException {
                log.error("OAuth FAILED: {}", exception.getMessage());
                HttpSession session = request.getSession(false);
                log.error("Session ID: {}", session != null ? session.getId() : "none");
                log.error("Cookie: {}", request.getHeader("Cookie"));
                getRedirectStrategy().sendRedirect(request, response, loginUrl + "?error=oauth_failed");
            }
        };

        http
            .cors(Customizer.withDefaults()) // Use the CorsFilter bean with highest precedence
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login", 
                    "/error", 
                    "/swagger-ui/**", 
                    "/v3/api-docs/**", 
                    "/v1/auth/debug",
                    "/v1/auth/me",
                    "/v1/session/test",
                    "/login/oauth2/**", 
                    "/oauth2/**",
                    "/actuator/health"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .defaultSuccessUrl(dashboardUrl, true)
                .failureHandler(failureHandler)
                .authorizationEndpoint(authorization -> authorization
                    .authorizationRequestRepository(authorizationRequestRepository())
                )
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/logout", "/v1/**", "/login/oauth2/**", "/oauth2/**")
            )
            .sessionManagement(session -> session
                .sessionFixation().none()
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
            log.info("USER LOGOUT");
            if (authentication != null && authentication.getPrincipal() instanceof OAuth2User) {
                OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                log.info("User: {}", oauth2User.getAttribute("email").toString());
            }
            SecurityContextHolder.clearContext();
        };
    }
}
