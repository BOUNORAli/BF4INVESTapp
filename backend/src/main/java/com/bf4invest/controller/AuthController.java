package com.bf4invest.controller;

import com.bf4invest.dto.ChangePasswordRequest;
import com.bf4invest.dto.LoginRequest;
import com.bf4invest.dto.LoginResponse;
import com.bf4invest.dto.RefreshTokenRequest;
import com.bf4invest.security.AuthCookieUtil;
import com.bf4invest.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    private final AuthCookieUtil authCookieUtil;
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse httpResponse) {
        LoginResponse response = authService.login(request);
        authCookieUtil.addAuthCookies(httpResponse, response.getToken(), response.getRefreshToken());
        return ResponseEntity.ok(LoginResponse.builder()
                .user(response.getUser())
                .build());
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse httpResponse) {
        String refreshTokenValue = getRefreshTokenFromCookie(request);
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            log.warn("Refresh demandé sans cookie bf4_refresh_token");
            return ResponseEntity.status(401).build();
        }
        try {
            LoginResponse response = authService.refreshToken(refreshTokenValue);
            authCookieUtil.addAuthCookies(httpResponse, response.getToken(), response.getRefreshToken());
            return ResponseEntity.ok(LoginResponse.builder()
                    .user(response.getUser())
                    .build());
        } catch (RuntimeException e) {
            log.error("Erreur lors du refresh token: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest request,
            HttpServletResponse httpResponse) {
        String refreshTokenValue = getRefreshTokenFromCookie(request);
        if (refreshTokenValue == null && body != null && body.getRefreshToken() != null) {
            refreshTokenValue = body.getRefreshToken();
        }
        if (refreshTokenValue != null) {
            authService.revokeRefreshToken(refreshTokenValue);
        }
        authCookieUtil.clearAuthCookies(httpResponse);
        return ResponseEntity.ok().build();
    }
    
    private static String getRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> AuthCookieUtil.COOKIE_REFRESH_TOKEN.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).build();
        }
        String email = auth.getPrincipal().toString();
        try {
            authService.changePassword(email, request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.debug("Change password failed for {}: {}", email, e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }
}




