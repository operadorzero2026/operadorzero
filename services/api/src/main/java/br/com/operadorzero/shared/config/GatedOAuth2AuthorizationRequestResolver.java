package br.com.operadorzero.shared.config;

import br.com.operadorzero.identity.AuthCookieService;
import br.com.operadorzero.identity.GoogleFlowStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Somente aloca um pedido OAuth quando /api/auth/google/start (CSRF + rate limit) liberou o fluxo,
 * e uma unica vez. Acesso direto ou repetido a /oauth2/authorization/google nao chega ao Google.
 */
final class GatedOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final OAuth2AuthorizationRequestResolver delegate;
    private final GoogleFlowStore store;
    private final AuthCookieService cookies;

    GatedOAuth2AuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate, GoogleFlowStore store,
                                            AuthCookieService cookies) {
        this.delegate = delegate;
        this.store = store;
        this.cookies = cookies;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return gate(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return gate(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest gate(HttpServletRequest request, OAuth2AuthorizationRequest resolved) {
        if (resolved == null) {
            return null;
        }
        boolean allowed = cookies.googleFlow(request).map(store::consumeGate).orElse(false);
        return allowed ? resolved : null;
    }
}
