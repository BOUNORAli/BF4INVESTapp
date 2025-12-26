package com.bf4invest.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class JwtUtil {
    
    private static final int MIN_SECRET_LENGTH = 32; // 256 bits minimum
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private Long expiration;
    
    @Value("${jwt.refresh-expiration:604800000}")
    private Long refreshExpiration;
    
    private final Environment environment;
    
    public JwtUtil(Environment environment) {
        this.environment = environment;
    }
    
    @PostConstruct
    public void validateSecret() {
        boolean isProd = environment.acceptsProfiles("prod");
        
        if (secret == null || secret.trim().isEmpty()) {
            String errorMsg = "JWT_SECRET est obligatoire et ne peut pas être vide";
            if (isProd) {
                throw new IllegalStateException(errorMsg + " (PRODUCTION)");
            }
            log.warn(errorMsg + " - Utilisation d'une valeur par défaut non sécurisée (DEV ONLY)");
            return;
        }
        
        // Vérifier la longueur minimale (256 bits = 32 caractères)
        if (secret.length() < MIN_SECRET_LENGTH) {
            String errorMsg = String.format(
                "JWT_SECRET doit faire au moins %d caractères (256 bits) pour la sécurité. Longueur actuelle: %d",
                MIN_SECRET_LENGTH, secret.length()
            );
            if (isProd) {
                throw new IllegalStateException(errorMsg + " (PRODUCTION)");
            }
            log.warn(errorMsg + " - Accepté en développement uniquement");
        } else {
            log.info("JWT_SECRET validé (longueur: {} caractères)", secret.length());
        }
        
        // Vérifier que ce n'est pas la valeur par défaut en production
        if (isProd && secret.equals("bf4invest-secret-key-change-in-production-minimum-256-bits")) {
            throw new IllegalStateException(
                "JWT_SECRET ne peut pas utiliser la valeur par défaut en PRODUCTION. " +
                "Configurez une valeur unique et sécurisée via la variable d'environnement JWT_SECRET"
            );
        }
    }
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    public String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return createToken(claims, username, expiration);
    }
    
    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return createToken(claims, username, refreshExpiration);
    }
    
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> (String) claims.get("type"));
    }
    
    private String createToken(Map<String, Object> claims, String subject, Long expirationMs) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }
    
    public Boolean validateToken(String token, String username) {
        final String tokenUsername = extractUsername(token);
        return (tokenUsername.equals(username) && !isTokenExpired(token));
    }
}




