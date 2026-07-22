package br.com.operadorzero.identity;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public final class PasswordPolicy {
    public void validate(String password, String email) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw AuthException.invalidPassword("A senha deve ter entre 12 e 128 caracteres.");
        }
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!upper || !lower || !digit) {
            throw AuthException.invalidPassword("Use letras maiusculas, minusculas e numeros.");
        }
        String localPart = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@", 2)[0];
        if (localPart.length() >= 4 && password.toLowerCase(Locale.ROOT).contains(localPart)) {
            throw AuthException.invalidPassword("A senha nao pode conter a parte principal do e-mail.");
        }
    }
}
