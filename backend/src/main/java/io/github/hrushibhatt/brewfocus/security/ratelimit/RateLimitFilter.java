package io.github.hrushibhatt.brewfocus.security.ratelimit;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.hrushibhatt.brewfocus.web.ErrorBody;
import io.github.hrushibhatt.brewfocus.web.JsonReplies;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The per-address limits: one for the whole API, and a stricter one for anything that signs someone
 * in. A request over a limit gets a 429 with {@code Retry-After}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimits limits;
    private final ClientIpResolver ips;

    public RateLimitFilter(RateLimits limits, ClientIpResolver ips) {
        this.limits = limits;
        this.ips = ips;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = ips.clientIp(request);
        if (path.startsWith("/api/") && refused(limits.api(), "api:ip:" + ip, response)) {
            return;
        }
        if (isSignIn(request.getMethod(), path) && refused(limits.auth(), "auth:ip:" + ip, response)) {
            return;
        }
        chain.doFilter(request, response);
    }

    /** Password sign-in and sign-up, and both legs of an OAuth 2.0 handshake. */
    static boolean isSignIn(String method, String path) {
        boolean passwordForm = "POST".equals(method)
                && (path.equals("/api/auth/login") || path.equals("/api/auth/register"));
        return passwordForm || path.startsWith("/oauth2/authorization/") || path.startsWith("/login/oauth2/");
    }

    private static boolean refused(RateLimiter limiter, String key, HttpServletResponse response) throws IOException {
        RateLimiter.Decision decision = limiter.acquire(key);
        if (decision.allowed()) {
            return false;
        }
        long seconds = ErrorBody.retryAfterSeconds(decision.retryAfter());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        JsonReplies.write(response, HttpStatus.TOO_MANY_REQUESTS, "too many requests: try again in " + seconds + " seconds");
        return true;
    }
}
