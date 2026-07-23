package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

@ExtendWith(OutputCaptureExtension.class)
class GoogleAuthSuccessHandlerTest {
    @Test
    void failureLogsOnlySanitizedClassification(CapturedOutput output) throws Exception {
        AuthService service = mock(AuthService.class);
        AuthCookieService cookies = mock(AuthCookieService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuthProperties properties = new AuthProperties(
            true,
            URI.create("https://operadorzero.vercel.app"),
            "Operador Zero",
            Duration.ofDays(7),
            Duration.ofMinutes(30),
            new AuthProperties.Cookie("OZ_SESSION", true, "None", ""),
            new AuthProperties.Mail(false, "no-reply@example.test"),
            new AuthProperties.Google(true, "client", "secret", "https://api.example.test/callback")
        );
        GoogleAuthSuccessHandler handler = new GoogleAuthSuccessHandler(service, cookies, properties);
        OAuth2AuthenticationException failure = new OAuth2AuthenticationException(
            new OAuth2Error("invalid_token_response", "sensitive-provider-description", null),
            "sensitive-exception-message"
        );

        handler.onAuthenticationFailure(request, response, failure);

        verify(cookies).clearGoogleIntent(response);
        verify(response).sendRedirect(
            "https://operadorzero.vercel.app?auth=error&code=GOOGLE_AUTH_FAILED"
        );
        assertThat(output).contains("errorCode=invalid_token_response");
        assertThat(output).doesNotContain("sensitive-provider-description", "sensitive-exception-message");
    }
}
