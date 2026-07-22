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
    public static final String GOOGLE_INTENT_COOKIE = "OZ_GOOGLE_INTENT";

    private final AuthProperties properties;

    public AuthCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public Optional<String> sessionToken(HttpServletRequest request) {
        return cookie(request, properties.cookie().name());
    }

    public Optional<String> googleIntent(HttpServletRequest request) {
        return cookie(request, GOOGLE_INTENT_COOKIE);
    }

    public void writeSession(HttpServletResponse response, String token) {
        add(response, properties.cookie().name(), token, properties.sessionDuration(), true);
    }

    public void clearSession(HttpServletResponse response) {
        add(response, properties.cookie().name(), "", Duration.ZERO, true);
    }

    public void writeGoogleIntent(HttpServletResponse response, String token) {
        add(response, GOOGLE_INTENT_COOKIE, token, Duration.ofMinutes(10), true);
    }

    public void clearGoogleIntent(HttpServletResponse response) {
        add(response, GOOGLE_INTENT_COOKIE, "", Duration.ZERO, true);
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
