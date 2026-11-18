package io.github.hrushibhatt.brewfocus.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The key that signs session JWTs, checked once at startup.
 *
 * <p>HS256 with one server-side secret: only this service ever verifies the tokens, so public-key
 * signing would add keys to manage for no benefit.
 */
@Configuration(proxyBeanMethods = false)
public class SigningKeyConfig {

    /** The HS256 key size: 32 random bytes, or 64 hex characters from {@code openssl rand -hex 32}. */
    public static final int MIN_SECRET_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(SigningKeyConfig.class);

    @Bean
    SecretKey sessionSigningKey(BrewFocusProperties props) {
        return signingKey(props.jwtSecret(), props.requireSecret());
    }

    /**
     * Returns the signing key. Production refuses to start without a strong one, because a guessable
     * key lets anyone forge a session for any user. Elsewhere a random key is fine, at the cost of
     * signing everyone out on each restart.
     */
    static SecretKey signingKey(String secret, boolean required) {
        byte[] bytes;
        if (secret != null && !secret.isEmpty()) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes (openssl rand -hex 32)");
            }
        } else if (required) {
            throw new IllegalStateException("JWT_SECRET is required in production (openssl rand -hex 32)");
        } else {
            bytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET not set: using a random key, so everyone is signed out when the server restarts");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
