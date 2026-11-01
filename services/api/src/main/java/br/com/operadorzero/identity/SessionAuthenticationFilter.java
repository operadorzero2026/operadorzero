package br.com.operadorzero.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final AuthProperties properties;
    private final AuthCookieService cookies;
    private final TokenSupport tokens;
    private final IdentityRepository repository;

    public SessionAuthenticationFilter(AuthProperties properties, AuthCookieService cookies,
                                       TokenSupport tokens, IdentityRepository repository) {
        this.properties = properties;
        this.cookies = cookies;
        this.tokens = tokens;
        this.repository = repository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (properties.enabled() && SecurityContextHolder.getContext().getAuthentication() == null) {
            cookies.sessionToken(request)
                .map(tokens::hash)
                .flatMap(hash -> repository.findBySessionHash(hash, Instant.now()).map(account -> effective(account, hash)))
                .map(AuthenticatedUser::from)
                .ifPresent(principal -> SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())));
        }
        chain.doFilter(request, response);
    }

    /** Papeis ADMIN e MODERATOR so valem quando esta sessao confirmou o segundo fator. */
    private UserAccount effective(UserAccount account, String sessionHash) {
        if (!MfaService.hasPrivilegedRole(account.roles()) || repository.sessionMfaVerified(sessionHash)) {
            return account;
        }
        return new UserAccount(account.id(), account.publicId(), account.email(), account.username(), account.passwordHash(),
            account.status(), account.displayName(), account.callsign(), MfaService.withoutPrivilegedRoles(account.roles()));
    }
}
