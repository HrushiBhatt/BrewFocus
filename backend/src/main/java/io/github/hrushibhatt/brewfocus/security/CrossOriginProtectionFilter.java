package io.github.hrushibhatt.brewfocus.security;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.hrushibhatt.brewfocus.web.JsonReplies;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refuses state-changing requests that the browser says came from another site. It sits in front of
 * the CSRF token as a second, independent check, so a mistake in one doesn't open the door.
 *
 * <p>Modern browsers send {@code Sec-Fetch-Site} on every request; older ones send {@code Origin} on
 * cross-site ones. A request with neither came from something other than a browser page (curl, a
 * test), which can't ride on a visitor's cookies anyway.
 */
class CrossOriginProtectionFilter extends OncePerRequestFilter {

    static final String MESSAGE = "that request came from another site";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> SAME_SITE = Set.of("same-origin", "none");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod()) && isCrossOrigin(request)) {
            JsonReplies.write(response, HttpStatus.FORBIDDEN, MESSAGE);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isCrossOrigin(HttpServletRequest request) {
        String site = request.getHeader("Sec-Fetch-Site");
        if (site != null) {
            return !SAME_SITE.contains(site);
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            return false;
        }
        try {
            String host = request.getHeader(HttpHeaders.HOST);
            return host == null || !host.equalsIgnoreCase(URI.create(origin).getRawAuthority());
        } catch (IllegalArgumentException e) {
            return true; // "null" or garbage: not this site
        }
    }
}
