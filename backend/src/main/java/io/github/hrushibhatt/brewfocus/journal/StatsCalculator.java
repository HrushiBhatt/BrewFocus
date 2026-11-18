package io.github.hrushibhatt.brewfocus.journal;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Pure arithmetic over per-day totals, kept apart from the database so streak rules can be tested on
 * their own. Days are the user's local days, sent by the client, so "today" follows their midnight.
 */
public final class StatsCalculator {

    /** 53 weeks: enough to fill a year-long grid of week columns. */
    static final int HEATMAP_DAYS = 371;

    private StatsCalculator() {}

    public static Stats compute(List<FocusSessionRepository.DayTotals> days, LocalDate today) {
        String todayKey = today.toString();
        SortedMap<String, Integer> minutes = new TreeMap<>();
        int totalMinutes = 0;
        int totalSessions = 0;
        int totalCompleted = 0;
        int todaySessions = 0;
        for (var day : days) {
            int m = day.seconds() / 60;
            minutes.put(day.day(), m);
            totalMinutes += m;
            totalSessions += day.sessions();
            totalCompleted += day.completed();
            if (day.day().equals(todayKey)) {
                todaySessions = day.sessions();
            }
        }

        String cutoff = today.minusDays(HEATMAP_DAYS).toString();
        SortedMap<String, Integer> heatmap = new TreeMap<>(minutes.tailMap(cutoff));
        heatmap.remove(cutoff); // strictly after the cutoff

        int[] streaks = streaks(minutes, today);
        return new Stats(
                new Stats.Today(minutes.getOrDefault(todayKey, 0), todaySessions),
                streaks[0],
                streaks[1],
                new Stats.Total(totalMinutes, totalSessions, totalCompleted),
                heatmap);
    }

    /**
     * The current and best runs of consecutive active days. The current streak survives until the end
     * of today, so it counts back from yesterday when nothing has been logged yet today.
     */
    static int[] streaks(Map<String, Integer> active, LocalDate today) {
        int best = 0;
        int run = 0;
        LocalDate previous = null;
        for (String key : new TreeMap<>(active).keySet()) {
            LocalDate day = LocalDate.parse(key);
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            best = Math.max(best, run);
            previous = day;
        }

        LocalDate cursor = active.containsKey(today.toString()) ? today : today.minusDays(1);
        int current = 0;
        while (active.containsKey(cursor.toString())) {
            current++;
            cursor = cursor.minusDays(1);
        }
        return new int[] {current, best};
    }
}
