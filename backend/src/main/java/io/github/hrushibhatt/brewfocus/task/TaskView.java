package io.github.hrushibhatt.brewfocus.task;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A task plus the focus time logged against it, for the task list.
 *
 * @param brews   full focus blocks (ones that ran to zero) for this task
 * @param minutes all focused time for this task, finished early or not
 */
public record TaskView(
        @JsonProperty("id") long id,
        @JsonProperty("title") String title,
        @JsonProperty("estimate") int estimate,
        @JsonProperty("done") boolean done,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("brews") int brews,
        @JsonProperty("minutes") int minutes) {

    static TaskView of(Task task, int brews, int seconds) {
        return new TaskView(task.id(), task.title(), task.estimate(), task.done(), task.createdAt(), brews, seconds / 60);
    }
}
