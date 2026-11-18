package io.github.hrushibhatt.brewfocus.security;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * The signed-in user and the session (browser) they're using. Controllers receive it with
 * {@code @AuthenticationPrincipal} and scope every query to {@link #userId()}.
 */
public record SessionPrincipal(long userId, String sessionId) {

    /** The Spring Security token that carries a {@link SessionPrincipal} through the request. */
    static final class Token extends AbstractAuthenticationToken {
        private final SessionPrincipal principal;

        Token(SessionPrincipal principal) {
            super(List.of());
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null; // the JWT has done its job by now, and isn't kept
        }

        @Override
        public SessionPrincipal getPrincipal() {
            return principal;
        }
    }
}
