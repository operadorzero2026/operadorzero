package br.com.operadorzero.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenSupportTest {
    private final TokenSupport tokens = new TokenSupport();

    @Test
    void createsOpaqueUniqueTokensAndStableNonReversibleHashes() {
        String first = tokens.newToken();
        String second = tokens.newToken();
        assertThat(first).hasSizeGreaterThanOrEqualTo(40).isNotEqualTo(second);
        assertThat(tokens.hash(first)).hasSize(64).isEqualTo(tokens.hash(first)).doesNotContain(first);
    }
}
