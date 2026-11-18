package io.github.hrushibhatt.brewfocus.web;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * One log line per request: method, path, status and time taken. The query string and headers are
 * left out on purpose. OAuth callbacks carry one-time codes in the query, and headers carry the
 * session cookie, and neither belongs in a log file.
 */
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("brewfocus.requests");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (log.isInfoEnabled()) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                log.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), ms);
            }
        }
    }
}
