package br.com.operadorzero.identity;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GoogleAuthSuccessHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private final AuthService service;
    private final AuthCookieService cookies;
    private final AuthProperties properties;

    public GoogleAuthSuccessHandler(AuthService service, AuthCookieService cookies, AuthProperties properties) {
        this.service = service;
        this.cookies = cookies;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            OidcUser user = (OidcUser) authentication.getPrincipal();
            boolean verified = Boolean.TRUE.equals(user.getClaimAsBoolean("email_verified"));
            String intent = cookies.googleIntent(request).orElse(null);
            service.googleLogin(user.getIssuer().toString(), user.getSubject(), user.getEmail(), verified,
                user.getFullName(), intent, request, response);
            invalidateTemporarySession(request);
            response.sendRedirect(frontendRedirect("success", null));
        } catch (AuthException exception) {
            cookies.clearGoogleIntent(response);
            invalidateTemporarySession(request);
            response.sendRedirect(frontendRedirect("error", exception.code()));
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        org.springframework.security.core.AuthenticationException exception)
            throws IOException, ServletException {
        cookies.clearGoogleIntent(response);
        invalidateTemporarySession(request);
        response.sendRedirect(frontendRedirect("error", "GOOGLE_AUTH_FAILED"));
    }

    private String frontendRedirect(String status, String code) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(properties.frontendBaseUrl())
            .replaceQuery(null)
            .queryParam("auth", status);
        if (code != null) {
            builder.queryParam("code", code);
        }
        return builder.build().toUriString();
    }

    private void invalidateTemporarySession(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
    }
}
