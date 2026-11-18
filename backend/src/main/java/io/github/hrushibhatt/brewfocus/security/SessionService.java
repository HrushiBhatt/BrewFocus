package io.github.hrushibhatt.brewfocus.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import io.github.hrushibhatt.brewfocus.config.BrewFocusProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sign-in sessions, as JSON Web Tokens in a cookie.
 *
 * <p>Design decisions:
 *
 * <ul>
 *   <li>The JWT rides in an HttpOnly cookie, not localStorage. Page scripts can't read it, so a script
 *       injected into the page can't steal it, and the browser sends it with fetch and EventSource on
 *       its own, so the frontend has no token-handling code at all.
 *   <li>A JWT alone can't be revoked before it expires, so each token also names a row in
 *       {@code login_sessions}. The signature rejects a forgery before any database lookup, and
 *       deleting the row makes "log out" and "sign out everywhere" take effect at once.
 *   <li>The algorithm is pinned to HS256 when verifying, so a forged token can't pick its own (such as
 *       {@code none}), and an expiry is required.
 *   <li>Over HTTPS the cookie takes the {@code __Host-} prefix: browsers then insist on Secure, Path=/
 *       and no Domain, so a sibling subdomain can't plant or overwrite it.
 * </ul>
 */
@Service
public class SessionService {

    public static final String COOKIE = "bf_session";
    public static final String SECURE_COOKIE = "__Host-bf_session";
    public static final Duration LIFETIME = Duration.ofDays(14);

    private final LoginSessionRepository sessions;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final boolean secure;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SessionService(LoginSessionRepository sessions, SecretKey sessionSigningKey, BrewFocusProperties props,
            Clock clock) {
        this.sessions = sessions;
        this.secure = props.secureCookies();
        this.clock = clock;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(sessionSigningKey));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(sessionSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, SessionService::requireIdAndExpiry));
        this.decoder = nimbus;
    }

    /** The cookie's name, which takes the stricter prefix over HTTPS. */
    public String cookieName() {
        return secure ? SECURE_COOKIE : COOKIE;
    }

    /**
     * Starts a new session for {@code userId} and sets its cookie. A session this browser already held
     * is ended first, so an old cookie can't outlive the new sign-in.
     */
    public void signIn(HttpServletRequest request, HttpServletResponse response, long userId) {
        authenticate(request).ifPresent(old -> sessions.delete(old.sessionId()));
        Instant now = clock.instant();
        Instant expires = now.plus(LIFETIME);
        String id = newSessionId();
        sessions.deleteExpired(userId, now);
        sessions.insert(id, userId, expires, now);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(id)
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(expires)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        setCookie(response, token, LIFETIME);
    }

    /** Ends the request's session, if it has a valid one, and clears the cookie either way. */
    public void signOut(HttpServletRequest request, HttpServletResponse response) {
        authenticate(request).ifPresent(current -> sessions.delete(current.sessionId()));
        clearCookie(response);
    }

    /** Ends the user's sessions on every device, except {@code keep} (which may be null). */
    public void revokeAll(long userId, String keep) {
        sessions.deleteAllExcept(userId, keep);
    }

    /** True while a session exists and hasn't expired. */
    public boolean isActive(String sessionId) {
        return sessions.isActive(sessionId, clock.instant());
    }

    public void clearCookie(HttpServletResponse response) {
        setCookie(response, "", Duration.ZERO);
    }

    /**
     * The user and session the request's cookie names, or empty for anything short of a validly
     * signed, unexpired token whose session row still exists. Database errors propagate: they're a 500,
     * not a reason to treat the visitor as signed out.
     */
    public Optional<SessionPrincipal> authenticate(HttpServletRequest request) {
        String raw = readCookie(request);
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        Jwt jwt;
        long userId;
        try {
            jwt = decoder.decode(raw);
            userId = Long.parseLong(jwt.getSubject());
        } catch (JwtException | NumberFormatException e) {
            return Optional.empty();
        }
        if (!sessions.isActive(jwt.getId(), userId, clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(new SessionPrincipal(userId, jwt.getId()));
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(cookieName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(cookieName(), value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 128 random bits. Useless on its own: a cookie must also be signed with the server's key. */
    private String newSessionId() {
        byte[] id = new byte[16];
        random.nextBytes(id);
        return HexFormat.of().formatHex(id);
    }

    private static OAuth2TokenValidatorResult requireIdAndExpiry(Jwt jwt) {
        if (jwt.getId() == null || jwt.getId().isEmpty() || jwt.getExpiresAt() == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "a session token needs jti and exp", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
