package br.com.operadorzero.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {
    private static final String[] LEGACY_AUTH_COOKIES = {"OZ_GOOGLE_INTENT", "OZ_OAUTH_SESSION"};
    public static final String GOOGLE_FLOW_COOKIE = "OZ_GOOGLE_FLOW";
    public static final String GOOGLE_LINK_COOKIE = "OZ_GOOGLE_LINK";

    private final AuthProperties properties;

    public AuthCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public Optional<String> sessionToken(HttpServletRequest request) {
        return cookie(request, properties.cookie().name());
    }

    public void writeSession(HttpServletResponse response, String token) {
        add(response, properties.cookie().name(), token, properties.sessionDuration(), true);
    }

    public void clearSession(HttpServletResponse response) {
        add(response, properties.cookie().name(), "", Duration.ZERO, true);
    }

    public Optional<String> googleFlow(HttpServletRequest request) {
        return cookie(request, GOOGLE_FLOW_COOKIE);
    }

    public void writeGoogleFlow(HttpServletResponse response, String token) {
        add(response, GOOGLE_FLOW_COOKIE, token, GoogleFlowStore.FLOW_TTL, true);
    }

    public void clearGoogleFlow(HttpServletResponse response) {
        add(response, GOOGLE_FLOW_COOKIE, "", Duration.ZERO, true);
    }

    public Optional<String> googleLink(HttpServletRequest request) {
        return cookie(request, GOOGLE_LINK_COOKIE);
    }

    public void writeGoogleLink(HttpServletResponse response, String token) {
        add(response, GOOGLE_LINK_COOKIE, token, GoogleFlowStore.LINK_TTL, true);
    }

    public void clearGoogleLink(HttpServletResponse response) {
        add(response, GOOGLE_LINK_COOKIE, "", Duration.ZERO, true);
    }

    public void clearLegacyAuthentication(HttpServletResponse response) {
        for (String name : LEGACY_AUTH_COOKIES) {
            add(response, name, "", Duration.ZERO, true);
        }
    }

    private Optional<String> cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
            .filter(cookie -> name.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> !value.isBlank())
            .findFirst();
    }

    private void add(HttpServletResponse response, String name, String value, Duration maxAge, boolean httpOnly) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
            .httpOnly(httpOnly)
            .secure(properties.cookie().secure())
            .sameSite(properties.cookie().sameSite())
            .path("/")
            .maxAge(maxAge);
        if (properties.cookie().domain() != null && !properties.cookie().domain().isBlank()) {
            builder.domain(properties.cookie().domain());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}
