package br.com.operadorzero.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * Guarda state, nonce e code_verifier no Redis, ligados ao cookie do fluxo, em vez da sessao HTTP.
 * Assim um reinicio da API durante o consentimento nao perde o pedido.
 */
@Component
public class GoogleAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private final GoogleFlowStore store;
    private final AuthCookieService cookies;

    public GoogleAuthorizationRequestRepository(GoogleFlowStore store, AuthCookieService cookies) {
        this.store = store;
        this.cookies = cookies;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return cookies.googleFlow(request).flatMap(store::loadAuthorizationRequest).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
                                         HttpServletResponse response) {
        String flow = cookies.googleFlow(request).orElse(null);
        if (flow == null) {
            return;
        }
        if (authorizationRequest == null) {
            store.takeAuthorizationRequest(flow);
            return;
        }
        store.saveAuthorizationRequest(flow, authorizationRequest);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        return cookies.googleFlow(request).flatMap(store::takeAuthorizationRequest).orElse(null);
    }
}
