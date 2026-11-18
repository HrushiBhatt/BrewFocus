package io.github.hrushibhatt.brewfocus.web;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The one shape every error reply takes: {@code {"error": "a sentence the UI can show"}}. The
 * frontend displays {@code error} directly, so these messages are written for people.
 */
public final class ErrorBody {

    public static final String SERVER_ERROR = "something went wrong on our side";

    private ErrorBody() {}

    public static ResponseEntity<Map<String, Object>> reply(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(Map.of("error", message));
    }

    static ResponseEntity<Map<String, Object>> reply(ApiException e) {
        var reply = ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON);
        if (e.retryAfter() != null) {
            reply.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds(e.retryAfter())));
        }
        return reply.body(Map.of("error", e.getMessage()));
    }

    /** Whole seconds, rounded up, and never zero: "Retry-After: 0" invites an immediate retry. */
    public static long retryAfterSeconds(Duration wait) {
        long seconds = (wait.toMillis() + 999) / 1000;
        return Math.max(1, seconds);
    }
}
