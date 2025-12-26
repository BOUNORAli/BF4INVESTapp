package com.bf4invest.service;

import com.bf4invest.model.RefreshToken;
import com.bf4invest.model.User;
import com.bf4invest.repository.RefreshTokenRepository;
import com.bf4invest.repository.UserRepository;
import com.bf4invest.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user-1")
                .email("test@example.com")
                .name("Test User")
                .role(User.Role.ADMIN)
                .enabled(true)
                .build();
    }

    @Test
    void testLogin_Success() {
        // Given
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            token.setId("refresh-token-id");
            return token;
        });

        // When
        com.bf4invest.dto.LoginRequest request = new com.bf4invest.dto.LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        var response = authService.login(request);

        // Then
        assertNotNull(response);
        assertEquals("access-token", response.getToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertNotNull(response.getUser());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void testRefreshToken_Success() {
        // Given
        RefreshToken refreshToken = RefreshToken.builder()
                .id("refresh-token-id")
                .token("refresh-token")
                .userId("user-1")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(jwtUtil.extractUsername("refresh-token")).thenReturn("test@example.com");
        when(jwtUtil.extractTokenType("refresh-token")).thenReturn("refresh");
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("new-access-token");

        // When
        var response = authService.refreshToken("refresh-token");

        // Then
        assertNotNull(response);
        assertEquals("new-access-token", response.getToken());
        assertEquals("refresh-token", response.getRefreshToken());
    }

    @Test
    void testRefreshToken_ThrowsException_WhenTokenInvalid() {
        // Given
        when(jwtUtil.extractUsername("invalid-token")).thenReturn("test@example.com");
        when(jwtUtil.extractTokenType("invalid-token")).thenReturn("access"); // Pas un refresh token

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            authService.refreshToken("invalid-token");
        });
    }

    @Test
    void testRefreshToken_ThrowsException_WhenTokenRevoked() {
        // Given
        RefreshToken refreshToken = RefreshToken.builder()
                .id("refresh-token-id")
                .token("refresh-token")
                .userId("user-1")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(true) // Révoqué
                .build();

        when(jwtUtil.extractUsername("refresh-token")).thenReturn("test@example.com");
        when(jwtUtil.extractTokenType("refresh-token")).thenReturn("refresh");
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(refreshToken));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            authService.refreshToken("refresh-token");
        });
    }
}

