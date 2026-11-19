package com.ecole.saas.identity.api;

import com.ecole.saas.identity.domain.TokenLifetimes;
import com.ecole.saas.shared.infrastructure.security.SecurityProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieFactory {

    private final SecurityProperties.Cookie cookieProperties;

    public RefreshCookieFactory(SecurityProperties securityProperties) {
        this.cookieProperties = securityProperties.cookie();
    }

    public String cookieName() {
        return cookieProperties.refreshName();
    }

    public ResponseCookie create(String value) {
        return ResponseCookie.from(cookieProperties.refreshName(), value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.path())
                .maxAge(TokenLifetimes.REFRESH_TOKEN)
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(cookieProperties.refreshName(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.path())
                .maxAge(Duration.ZERO)
                .build();
    }
}
