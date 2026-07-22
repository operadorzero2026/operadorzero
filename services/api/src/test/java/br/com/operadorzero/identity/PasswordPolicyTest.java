package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsLongMixedPasswordThatDoesNotContainEmail() {
        assertThatCode(() -> policy.validate("CampoSeguro2026!", "operador@example.com")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortOrEmailDerivedPasswords() {
        assertThatThrownBy(() -> policy.validate("Curta1A", "operador@example.com"))
            .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> policy.validate("Operador2026A", "operador@example.com"))
            .isInstanceOf(AuthException.class);
    }
}
