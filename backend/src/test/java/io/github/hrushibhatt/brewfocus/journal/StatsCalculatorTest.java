package io.github.hrushibhatt.brewfocus.journal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.hrushibhatt.brewfocus.journal.FocusSessionRepository.DayTotals;

class StatsCalculatorTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-18");

    private static DayTotals day(LocalDate date, int sessions, int seconds, int completed) {
        return new DayTotals(date.toString(), sessions, seconds, completed);
    }

    @Test
    void totalsTodayAndAllTime() {
        Stats stats = StatsCalculator.compute(List.of(
                day(TODAY, 2, 3000, 2),
                day(TODAY.minusDays(1), 1, 1530, 1),
                day(TODAY.minusDays(5), 1, 600, 0)), TODAY);

        assertThat(stats.today()).isEqualTo(new Stats.Today(50, 2));
        // Minutes are floored per day, then summed: 50 + 25 + 10.
        assertThat(stats.total()).isEqualTo(new Stats.Total(85, 4, 3));
    }

    @Test
    void aStreakCountsBackFromTodayAndYesterdayStillCountsUntilTodayEnds() {
        Map<String, Integer> active = Map.of(
                TODAY.minusDays(1).toString(), 25,
                TODAY.minusDays(2).toString(), 25,
                TODAY.minusDays(5).toString(), 25,
                TODAY.minusDays(6).toString(), 25,
                TODAY.minusDays(7).toString(), 25);

        assertThat(StatsCalculator.streaks(active, TODAY)).containsExactly(2, 3);
        assertThat(StatsCalculator.streaks(active, TODAY.plusDays(1))).containsExactly(0, 3); // a day was missed
    }

    @Test
    void streaksSpanMonthAndYearBoundaries() {
        Map<String, Integer> active = Map.of("2025-12-31", 1, "2026-01-01", 1, "2026-01-02", 1);
        assertThat(StatsCalculator.streaks(active, LocalDate.parse("2026-01-02"))).containsExactly(3, 3);
    }

    @Test
    void theHeatmapCoversTheLast53Weeks() {
        Stats stats = StatsCalculator.compute(List.of(
                day(TODAY, 1, 600, 1),
                day(TODAY.minusDays(370), 1, 600, 1),
                day(TODAY.minusDays(371), 1, 600, 1),
                day(TODAY.minusDays(400), 1, 600, 1)), TODAY);

        assertThat(stats.heatmap()).containsOnlyKeys(TODAY.toString(), TODAY.minusDays(370).toString());
        assertThat(stats.total().sessions()).isEqualTo(4); // older days still count toward totals
    }

    @Test
    void anEmptyHistoryIsAllZeros() {
        Stats stats = StatsCalculator.compute(List.of(), TODAY);
        assertThat(stats.streak()).isZero();
        assertThat(stats.bestStreak()).isZero();
        assertThat(stats.heatmap()).isEmpty();
    }
}
