package io.github.hrushibhatt.brewfocus.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SigningKeyConfigTest {

    @Test
    void productionRefusesToStartWithoutASecret() {
        assertThatThrownBy(() -> SigningKeyConfig.signingKey("", true)).hasMessageContaining("JWT_SECRET is required");
        assertThatThrownBy(() -> SigningKeyConfig.signingKey(null, true)).hasMessageContaining("JWT_SECRET is required");
    }

    @Test
    void aShortSecretIsRefusedEverywhere() {
        assertThatThrownBy(() -> SigningKeyConfig.signingKey("hunter2", false)).hasMessageContaining("at least 32 bytes");
    }

    @Test
    void aStrongSecretIsUsedAsIs() {
        String secret = "0123456789abcdef0123456789abcdef";
        assertThat(SigningKeyConfig.signingKey(secret, true).getEncoded()).isEqualTo(secret.getBytes());
    }

    @Test
    void developmentFallsBackToARandomKey() {
        byte[] first = SigningKeyConfig.signingKey("", false).getEncoded();
        byte[] second = SigningKeyConfig.signingKey("", false).getEncoded();
        assertThat(first).hasSize(32).isNotEqualTo(second);
    }
}
