package br.com.operadorzero.identity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AuthenticatedUser(
    long internalId,
    UUID publicId,
    String email,
    String username,
    String displayName,
    String callsign,
    List<String> roles
) implements UserDetails {
    public static AuthenticatedUser from(UserAccount account) {
        return new AuthenticatedUser(account.id(), account.publicId(), account.email(), account.username(),
            account.displayName(), account.callsign(), account.roles());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }
}
