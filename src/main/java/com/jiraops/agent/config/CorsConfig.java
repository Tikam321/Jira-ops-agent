package com.jiraops.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsConfig {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Use the FRONTEND_URL from environment variable
        config.setAllowedOrigins(List.of(
            frontendUrl, 
            "http://localhost:5173", 
            "https://jira-ops-frontend-latest.onrender.com"
        ));
        
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type", 
            "X-Requested-With", 
            "Accept", 
            "Origin", 
            "Access-Control-Request-Method", 
            "Access-Control-Request-Headers",
            "Cookie"
        ));
        
        // Critical for sending JSESSIONID/Session Cookie back and forth
        config.setAllowCredentials(true);
        
        // Expose Authorization and Set-Cookie headers
        config.setExposedHeaders(List.of("Authorization", "Set-Cookie", "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));

        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
