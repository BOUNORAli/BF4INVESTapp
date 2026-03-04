package com.bf4invest.controller;

import com.bf4invest.dto.ChangePasswordRequest;
import com.bf4invest.dto.LoginRequest;
import com.bf4invest.dto.LoginResponse;
import com.bf4invest.dto.RefreshTokenRequest;
import com.bf4invest.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            LoginResponse response = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Erreur lors du refresh token: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            authService.revokeRefreshToken(request.getRefreshToken());
        }
        return ResponseEntity.ok().build();
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




