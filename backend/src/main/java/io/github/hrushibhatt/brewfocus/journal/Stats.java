package io.github.hrushibhatt.brewfocus.journal;

import java.util.SortedMap;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The progress numbers: today, streaks, all-time totals and the year heatmap.
 *
 * @param streak     consecutive active days up to today (or yesterday, until today ends)
 * @param bestStreak the longest run of active days ever
 * @param heatmap    minutes focused per day, for the last 53 weeks
 */
public record Stats(
        @JsonProperty("today") Today today,
        @JsonProperty("streak") int streak,
        @JsonProperty("best_streak") int bestStreak,
        @JsonProperty("total") Total total,
        @JsonProperty("heatmap") SortedMap<String, Integer> heatmap) {

    public record Today(@JsonProperty("minutes") int minutes, @JsonProperty("sessions") int sessions) {}

    public record Total(@JsonProperty("minutes") int minutes, @JsonProperty("sessions") int sessions,
            @JsonProperty("completed") int completed) {}
}
