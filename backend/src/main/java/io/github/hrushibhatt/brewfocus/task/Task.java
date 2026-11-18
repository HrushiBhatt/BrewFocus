package io.github.hrushibhatt.brewfocus.task;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Something to get done over one or more focus blocks.
 *
 * @param estimate the planned number of focus blocks
 */
public record Task(
        @JsonProperty("id") long id,
        @JsonProperty("title") String title,
        @JsonProperty("estimate") int estimate,
        @JsonProperty("done") boolean done,
        @JsonProperty("created_at") Instant createdAt) {

    Task withTitle(String value) {
        return new Task(id, value, estimate, done, createdAt);
    }

    Task withEstimate(int value) {
        return new Task(id, title, value, done, createdAt);
    }

    Task withDone(boolean value) {
        return new Task(id, title, estimate, value, createdAt);
    }
}
