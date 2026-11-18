package io.github.hrushibhatt.brewfocus.security;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the session cookie on every request and, when it names a live session, signs the request in
 * as that user. Requests without one carry on anonymously, and the authorization rules in
 * {@link SecurityConfig} turn those away from anything that needs an account.
 */
class SessionCookieFilter extends OncePerRequestFilter {

    private final SessionService sessions;

    SessionCookieFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        sessions.authenticate(request).ifPresent(principal -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new SessionPrincipal.Token(principal));
            SecurityContextHolder.setContext(context);
        });
        chain.doFilter(request, response);
    }
}
