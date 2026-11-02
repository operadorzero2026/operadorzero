package br.com.operadorzero.identity;

import br.com.operadorzero.identity.GoogleAuthService.Outcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Recebe o retorno do Google (ja validado pelo Spring Security) e devolve a pessoa ao frontend. */
@Component
public class GoogleAuthHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(GoogleAuthHandler.class);
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final GoogleAuthService service;
    private final GoogleFlowStore store;
    private final AuthCookieService cookies;
    private final AuthProperties properties;
    private final CsrfRotation csrf;

    public GoogleAuthHandler(GoogleAuthService service, GoogleFlowStore store, AuthCookieService cookies,
                             AuthProperties properties, CsrfRotation csrf) {
        this.service = service;
        this.store = store;
        this.cookies = cookies;
        this.properties = properties;
        this.csrf = csrf;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        Optional<GoogleFlowStore.Flow> flow = takeFlow(request, response);
        Outcome outcome;
        if (flow.isEmpty()) {
            outcome = Outcome.error("GOOGLE_FLOW_EXPIRED");
        } else {
            try {
                OidcUser user = (OidcUser) authentication.getPrincipal();
                boolean verified = Boolean.TRUE.equals(user.getEmailVerified());
                outcome = service.complete(flow.get(), user.getIssuer().toString(), user.getSubject(), user.getEmail(),
                    verified, user.getFullName(), request, response);
            } catch (AuthException exception) {
                outcome = Outcome.error(exception.code());
            } catch (RuntimeException exception) {
                log.warn("Google OIDC callback failed; exceptionType={}", exception.getClass().getSimpleName());
                outcome = Outcome.error("GOOGLE_AUTH_FAILED");
            }
        }
        csrf.rotate(request, response);
        response.sendRedirect(frontendRedirect(outcome));
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String errorCode = safeErrorCode(exception);
        log.warn("Google OIDC authentication failed; errorCode={}; exceptionType={}",
            errorCode, exception.getClass().getSimpleName());
        takeFlow(request, response);
        String code = "access_denied".equals(errorCode) ? "GOOGLE_CANCELLED"
            : "authorization_request_not_found".equals(errorCode) ? "GOOGLE_FLOW_EXPIRED"
            : "GOOGLE_AUTH_FAILED";
        response.sendRedirect(frontendRedirect(Outcome.error(code)));
    }

    /** Acesso direto ou repetido a /oauth2/authorization/google (sem liberacao valida). */
    public String expiredRedirect() {
        return frontendRedirect(Outcome.error("GOOGLE_FLOW_EXPIRED"));
    }

    private Optional<GoogleFlowStore.Flow> takeFlow(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> raw = cookies.googleFlow(request);
        cookies.clearGoogleFlow(response);
        return raw.flatMap(store::takeFlow);
    }

    private String safeErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            String errorCode = oauthException.getError().getErrorCode();
            if (errorCode != null && SAFE_ERROR_CODE.matcher(errorCode).matches()) {
                return errorCode;
            }
        }
        return "unclassified";
    }

    private String frontendRedirect(Outcome outcome) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(properties.frontendBaseUrl())
            .replacePath("/")
            .replaceQuery(null)
            .queryParam("auth", "google-" + outcome.status());
        if (outcome.code() != null) {
            builder.queryParam("code", outcome.code());
        }
        return builder.build().toUriString();
    }
}
