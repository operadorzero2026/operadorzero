package br.com.operadorzero.identity;

import java.util.List;
import java.util.UUID;

public record UserAccount(
    long id,
    UUID publicId,
    String email,
    String username,
    String passwordHash,
    String status,
    String displayName,
    String callsign,
    List<String> roles
) {
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
