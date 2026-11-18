package io.github.hrushibhatt.brewfocus.journal;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One finished focus block, as the journal shows it.
 *
 * @param planned   seconds the block was meant to last
 * @param focused   seconds it actually ran
 * @param completed it ran all the way to zero rather than ending early
 * @param day       the user's local calendar day when it started (YYYY-MM-DD)
 */
public record FocusSession(
        @JsonProperty("id") long id,
        @JsonProperty("task_id") Long taskId,
        @JsonProperty("mode") String mode,
        @JsonProperty("planned") int planned,
        @JsonProperty("focused") int focused,
        @JsonProperty("completed") boolean completed,
        @JsonProperty("intention") String intention,
        @JsonProperty("reflection") String reflection,
        @JsonProperty("distractions") int distractions,
        @JsonProperty("day") String day,
        @JsonProperty("created_at") Instant createdAt) {

    /** The same session with a new reflection. */
    public FocusSession withReflection(String note) {
        return new FocusSession(id, taskId, mode, planned, focused, completed, intention, note, distractions, day,
                createdAt);
    }
}
