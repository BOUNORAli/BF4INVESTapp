package com.bf4invest.service;

import com.bf4invest.dto.LoginRequest;
import com.bf4invest.dto.LoginResponse;
import com.bf4invest.model.RefreshToken;
import com.bf4invest.model.User;
import com.bf4invest.repository.RefreshTokenRepository;
import com.bf4invest.repository.UserRepository;
import com.bf4invest.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    /** email -> (failed count, lockout until epoch millis) */
    private final Map<String, LockoutEntry> failedAttempts = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-expiration:604800000}")
    private Long refreshExpirationMs;

    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail();
        checkLockout(email);

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (org.springframework.security.core.AuthenticationException e) {
            recordFailedLogin(email);
            throw e;
        }

        clearFailedAttempts(email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Générer access token (court)
        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        
        // Générer refresh token (long)
        String refreshTokenValue = jwtUtil.generateRefreshToken(user.getEmail());
        
        // Stocker le refresh token en base
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000);
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .userId(user.getId())
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        
        log.info("Login réussi pour utilisateur: {}", user.getEmail());
        
        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshTokenValue)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .build())
                .build();
    }
    
    @Transactional
    public LoginResponse refreshToken(String refreshTokenValue) {
        // Vérifier que le token est valide (signature, expiration)
        try {
            String username = jwtUtil.extractUsername(refreshTokenValue);
            String tokenType = jwtUtil.extractTokenType(refreshTokenValue);
            
            if (!"refresh".equals(tokenType)) {
                throw new RuntimeException("Token invalide: ce n'est pas un refresh token");
            }
            
            // Vérifier en base que le token existe et n'est pas révoqué
            RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                    .orElseThrow(() -> new RuntimeException("Refresh token introuvable"));
            
            if (!refreshToken.isValid()) {
                throw new RuntimeException("Refresh token expiré ou révoqué");
            }
            
            // Récupérer l'utilisateur
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
            
            // Générer un nouveau access token
            String newAccessToken = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
            
            log.info("Token rafraîchi pour utilisateur: {}", user.getEmail());
            
            return LoginResponse.builder()
                    .token(newAccessToken)
                    .refreshToken(refreshTokenValue) // Garder le même refresh token
                    .user(LoginResponse.UserInfo.builder()
                            .id(user.getId())
                            .name(user.getName())
                            .email(user.getEmail())
                            .role(user.getRole().name())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Erreur lors du refresh token: {}", e.getMessage());
            throw new RuntimeException("Refresh token invalide", e);
        }
    }
    
    @Transactional
    public void revokeRefreshToken(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Refresh token révoqué pour utilisateur: {}", token.getUserId());
        });
    }
    
    @Transactional
    public void revokeAllUserTokens(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
        log.info("Tous les refresh tokens révoqués pour utilisateur: {}", userId);
    }

    public void changePassword(String userEmail, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Mot de passe actuel incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Mot de passe modifié pour utilisateur: {}", userEmail);
    }

    private void checkLockout(String email) {
        LockoutEntry entry = failedAttempts.get(email);
        if (entry != null && entry.lockoutUntilEpochMs > System.currentTimeMillis()) {
            long minutesLeft = (entry.lockoutUntilEpochMs - System.currentTimeMillis()) / 60_000;
            throw new RuntimeException("Compte temporairement verrouillé après trop de tentatives. Réessayez dans " + minutesLeft + " minute(s).");
        }
        if (entry != null && entry.lockoutUntilEpochMs <= System.currentTimeMillis()) {
            failedAttempts.remove(email);
        }
    }

    private void recordFailedLogin(String email) {
        failedAttempts.compute(email, (k, v) -> {
            int next = (v == null) ? 1 : v.failedCount + 1;
            long lockoutUntil = next >= MAX_FAILED_ATTEMPTS
                ? System.currentTimeMillis() + LOCKOUT_MINUTES * 60_000L
                : 0;
            return new LockoutEntry(next, lockoutUntil);
        });
        log.warn("Échec de connexion pour {} (tentatives enregistrées)", email);
    }

    private void clearFailedAttempts(String email) {
        failedAttempts.remove(email);
    }

    private record LockoutEntry(int failedCount, long lockoutUntilEpochMs) {}
}




