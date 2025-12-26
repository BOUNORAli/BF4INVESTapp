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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    
    @Value("${jwt.refresh-expiration:604800000}")
    private Long refreshExpirationMs;
    
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        
        User user = userRepository.findByEmail(request.getEmail())
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
}




