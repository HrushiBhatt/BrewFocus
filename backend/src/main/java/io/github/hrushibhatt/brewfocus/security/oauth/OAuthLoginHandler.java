package io.github.hrushibhatt.brewfocus.security.oauth;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import io.github.hrushibhatt.brewfocus.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * The end of an OAuth 2.0 handshake. Spring Security has already checked the {@code state} and PKCE
 * verifier, exchanged the code and (for OpenID Connect) validated the ID token. This turns the
 * provider's identity into one of our sessions, exactly as a password sign-in would, so everything
 * after this point is the same however someone signed in.
 */
public class OAuthLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginHandler.class);

    private final OAuthAccountService accounts;
    private final SessionService sessions;

    public OAuthLoginHandler(OAuthAccountService accounts, SessionService sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        String destination = "/";
        try {
            var token = (OAuth2AuthenticationToken) authentication;
            OAuthIdentity identity = OAuthIdentity.from(token.getAuthorizedClientRegistrationId(), token.getPrincipal());
            long userId = accounts.signIn(identity);
            sessions.signIn(request, response, userId);
        } catch (OAuthAccountService.RefusedException refused) {
            destination = "/login?error=" + refused.code();
        } finally {
            forgetHandshake(request);
        }
        response.sendRedirect(destination);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        // The reason only, never the request: its query string holds the authorization code.
        log.info("OAuth 2.0 sign-in failed: {}", exception.getMessage());
        forgetHandshake(request);
        response.sendRedirect("/login?error=oauth");
    }

    /**
     * Drops the servlet session that held the handshake's state, and the provider's authentication.
     * From here on the JWT cookie is the only thing that signs this browser in.
     */
    private static void forgetHandshake(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
