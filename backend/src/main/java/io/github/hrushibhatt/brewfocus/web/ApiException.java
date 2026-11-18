package io.github.hrushibhatt.brewfocus.web;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/**
 * A failure the client should hear about, with a message the UI can show as it is. Controllers and
 * services throw these; {@link ApiExceptionHandler} turns them into {@code {"error": "..."}} replies.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Duration retryAfter;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    private ApiException(HttpStatus status, String message, Duration retryAfter) {
        super(message, null, false, false); // expected outcomes: no stack trace to fill in
        this.status = status;
        this.retryAfter = retryAfter;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /** A missing record. Another user's record gets this too, so IDs reveal nothing. */
    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "not found");
    }

    public static ApiException tooManyRequests(String message, Duration retryAfter) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message, retryAfter);
    }

    public HttpStatus status() {
        return status;
    }

    /** For a 429: how long until the client may try again, or null. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
