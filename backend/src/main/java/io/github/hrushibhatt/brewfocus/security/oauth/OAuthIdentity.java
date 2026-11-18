package io.github.hrushibhatt.brewfocus.security.oauth;

import java.util.Map;

import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Who a provider says signed in.
 *
 * @param provider      the registration ID, such as {@code google}
 * @param subject       the provider's stable ID for the account; its email can change, this can't
 * @param email         the account's email, or null
 * @param emailVerified the provider has confirmed the person controls {@code email}
 * @param name          a display name, or null
 */
public record OAuthIdentity(String provider, String subject, String email, boolean emailVerified, String name) {

    /**
     * Reads the standard claims: OpenID Connect's {@code sub}, {@code email}, {@code email_verified} and
     * {@code name}, falling back to GitHub's {@code id} and {@code login}.
     */
    public static OAuthIdentity from(String provider, OAuth2User user) {
        Map<String, Object> claims = user.getAttributes();
        String subject = text(claims.get("sub"));
        if (subject == null) {
            subject = text(claims.get("id"));
        }
        String name = text(claims.get("name"));
        if (name == null) {
            name = text(claims.get("login"));
        }
        Object verified = claims.get("email_verified");
        boolean isVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
        return new OAuthIdentity(provider, subject, text(claims.get("email")), isVerified, name);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
