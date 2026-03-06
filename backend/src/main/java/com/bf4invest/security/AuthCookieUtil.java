package com.bf4invest.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieUtil {

    public static final String COOKIE_ACCESS_TOKEN = "bf4_token";
    public static final String COOKIE_REFRESH_TOKEN = "bf4_refresh_token";
    private static final String COOKIE_PATH = "/";
    private static final Duration ACCESS_MAX_AGE = Duration.ofSeconds(3600);   // 1 hour
    private static final Duration REFRESH_MAX_AGE = Duration.ofDays(7);

    @Value("${cookie.secure:false}")
    private boolean secure;

    /**
     * Add httpOnly auth cookies to the response (login / refresh).
     */
    public void addAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        ResponseCookie accessCookie = ResponseCookie.from(COOKIE_ACCESS_TOKEN, accessToken)
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(ACCESS_MAX_AGE)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", accessCookie.toString());

        ResponseCookie refreshCookie = ResponseCookie.from(COOKIE_REFRESH_TOKEN, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(REFRESH_MAX_AGE)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", refreshCookie.toString());
    }

    /**
     * Clear auth cookies (logout).
     */
    public void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie clearAccess = ResponseCookie.from(COOKIE_ACCESS_TOKEN, "")
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", clearAccess.toString());

        ResponseCookie clearRefresh = ResponseCookie.from(COOKIE_REFRESH_TOKEN, "")
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", clearRefresh.toString());
    }
}
