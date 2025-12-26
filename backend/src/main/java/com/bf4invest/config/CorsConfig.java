package com.bf4invest.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class CorsConfig {

    @Value("${cors.allowed.origins:http://localhost:4200,http://localhost:3000,http://127.0.0.1:4200}")
    private String allowedOriginsConfig;

    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateCorsConfig() {
        boolean isProd = environment.acceptsProfiles("prod");
        
        if (allowedOriginsConfig == null || allowedOriginsConfig.trim().isEmpty()) {
            if (isProd) {
                throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS est obligatoire en PRODUCTION. " +
                    "Configurez les origines autorisées via la variable d'environnement CORS_ALLOWED_ORIGINS"
                );
            }
            log.warn("CORS_ALLOWED_ORIGINS non défini - utilisation des valeurs par défaut (DEV ONLY)");
        } else {
            // Vérifier qu'on n'utilise pas "*" en production
            List<String> origins = parseOrigins(allowedOriginsConfig);
            if (isProd && origins.contains("*")) {
                throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS ne peut pas contenir '*' en PRODUCTION. " +
                    "Spécifiez explicitement les domaines autorisés (ex: https://app.example.com,https://www.example.com)"
                );
            }
            log.info("CORS configuré avec {} origine(s) autorisée(s)", origins.size());
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Parse les origines depuis la variable d'environnement
        List<String> allowedOrigins = parseOrigins(allowedOriginsConfig);
        
        // En production, ne jamais autoriser "*"
        boolean isProd = environment.acceptsProfiles("prod");
        if (isProd && (allowedOrigins.isEmpty() || allowedOrigins.contains("*"))) {
            throw new IllegalStateException(
                "CORS_ALLOWED_ORIGINS doit être défini avec des domaines explicites en PRODUCTION"
            );
        }
        
        // Si vide en dev, utiliser les valeurs par défaut locales
        if (allowedOrigins.isEmpty() && !isProd) {
            allowedOrigins = List.of(
                "http://localhost:4200",
                "http://localhost:3000",
                "http://127.0.0.1:4200"
            );
            log.warn("Utilisation des origines CORS par défaut pour le développement");
        }
        
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // allowCredentials ne peut pas être true avec "*" - mais on n'utilise plus "*" en prod
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L); // Cache preflight response for 1 hour
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> parseOrigins(String originsConfig) {
        if (originsConfig == null || originsConfig.trim().isEmpty()) {
            return List.of();
        }
        
        return Arrays.stream(originsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}


