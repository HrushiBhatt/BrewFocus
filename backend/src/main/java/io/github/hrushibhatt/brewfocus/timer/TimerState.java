package io.github.hrushibhatt.brewfocus.timer;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A user's timer as clients see it. Clients derive the countdown from {@code ends_at}, corrected by
 * {@code server_time} for any clock skew, so every device shows the same seconds.
 *
 * @param durationMs  the block's full length
 * @param remainingMs time left right now
 * @param endsAt      the deadline, while running
 * @param serverTime  the server's clock when this state was taken
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimerState(
        @JsonProperty("phase") Phase phase,
        @JsonProperty("status") Status status,
        @JsonProperty("duration_ms") long durationMs,
        @JsonProperty("remaining_ms") long remainingMs,
        @JsonProperty("ends_at") Instant endsAt,
        @JsonProperty("session_id") Long sessionId,
        @JsonProperty("mode") String mode,
        @JsonProperty("intention") String intention,
        @JsonProperty("task_id") Long taskId,
        @JsonProperty("server_time") Instant serverTime) {

    public enum Phase {
        FOCUS, BREAK;

        @JsonValue
        String json() {
            return name().toLowerCase();
        }
    }

    public enum Status {
        IDLE, RUNNING, PAUSED;

        @JsonValue
        String json() {
            return name().toLowerCase();
        }
    }

    /** No timer: what a user sees between blocks. */
    public static TimerState idle(Phase phase, Instant now) {
        return new TimerState(phase, Status.IDLE, 0, 0, null, null, null, null, null, now);
    }
}
