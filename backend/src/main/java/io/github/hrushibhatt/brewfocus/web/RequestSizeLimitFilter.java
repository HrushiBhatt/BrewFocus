package io.github.hrushibhatt.brewfocus.web;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bounds every request body. Real requests are a few kilobytes at most, so this stops memory
 * exhaustion without ever being reached in normal use. A declared length over the limit is refused
 * before anything is read; a chunked body is cut off as soon as it passes the limit.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    public static final int MAX_BODY_BYTES = 256 * 1024;
    static final String MESSAGE = "that request is too large";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            JsonReplies.write(response, HttpStatus.CONTENT_TOO_LARGE, MESSAGE);
            return;
        }
        chain.doFilter(new Limited(request), response);
    }

    /** Thrown mid-read when a body without a declared length goes past the limit. */
    static final class TooLargeException extends IOException {
        TooLargeException() {
            super(MESSAGE);
        }
    }

    /** A request whose body stream stops at the limit. */
    private static final class Limited extends HttpServletRequestWrapper {
        private ServletInputStream stream;

        Limited(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new CountingStream(super.getInputStream());
            }
            return stream;
        }
    }

    private static final class CountingStream extends ServletInputStream {
        private final ServletInputStream in;
        private long read;

        CountingStream(ServletInputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = in.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws TooLargeException {
            read += n;
            if (read > MAX_BODY_BYTES) {
                throw new TooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return in.isFinished();
        }

        @Override
        public boolean isReady() {
            return in.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            in.setReadListener(listener);
        }
    }
}
