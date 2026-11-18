package io.github.hrushibhatt.brewfocus.journal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code focus_sessions} table. A block is inserted as {@code running} when it starts, updated as
 * it's paused and resumed, and marked {@code done} when it ends. Every read for the journal, stats
 * and tasks is limited to done rows of one user.
 */
@Repository
public class FocusSessionRepository {

    public static final String RUNNING = "running";
    public static final String PAUSED = "paused";
    public static final String DONE = "done";

    private final JdbcClient jdbc;

    public FocusSessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A block that was running or paused, as the timer engine needs it to carry on after a restart. */
    public record Active(long id, long userId, Long taskId, String mode, int planned, String intention, String day,
            String state, Instant endsAt, long remainingMs, int distractions, Instant createdAt) {}

    /** One day's totals, for the stats. */
    public record DayTotals(String day, int sessions, int seconds, int completed) {}

    /** One task's totals: full blocks and seconds logged against it. */
    public record TaskTotals(long taskId, int brews, int seconds) {}

    // ---- the timer engine's writes ---------------------------------------------------------------

    public long insertRunning(long userId, Long taskId, String mode, int plannedSeconds, String intention, String day,
            Instant endsAt, Instant now) {
        return jdbc.sql("""
                        INSERT INTO focus_sessions (user_id, task_id, mode, planned, intention, day, state, ends_at, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(userId, taskId, mode, plannedSeconds, intention, day, RUNNING, endsAt.toEpochMilli(),
                        now.toEpochMilli())
                .query(Long.class).single();
    }

    /** Records a running or paused block's live state, so a restart can resume it exactly. */
    public void saveProgress(long id, String state, Instant endsAt, long remainingMs, int distractions) {
        jdbc.sql("UPDATE focus_sessions SET state = ?, ends_at = ?, remaining_ms = ?, distractions = ? WHERE id = ?")
                .params(state, endsAt == null ? null : endsAt.toEpochMilli(), remainingMs, distractions, id)
                .update();
    }

    public void complete(long id, boolean completed, int focusedSeconds, int distractions) {
        jdbc.sql("""
                        UPDATE focus_sessions
                        SET state = ?, completed = ?, focused = ?, distractions = ?, ends_at = NULL, remaining_ms = 0
                        WHERE id = ?""")
                .params(DONE, completed ? 1 : 0, focusedSeconds, distractions, id)
                .update();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM focus_sessions WHERE id = ?").param(id).update();
    }

    public List<Active> findActive() {
        return jdbc.sql("SELECT * FROM focus_sessions WHERE state IN (?, ?) ORDER BY id")
                .params(RUNNING, PAUSED)
                .query((rs, row) -> new Active(
                        rs.getLong("id"), rs.getLong("user_id"), nullableLong(rs, "task_id"), rs.getString("mode"),
                        rs.getInt("planned"), rs.getString("intention"), rs.getString("day"), rs.getString("state"),
                        nullableInstant(rs, "ends_at"), rs.getLong("remaining_ms"), rs.getInt("distractions"),
                        Instant.ofEpochMilli(rs.getLong("created_at"))))
                .list();
    }

    // ---- the journal --------------------------------------------------------------------------------

    /** The user's newest finished blocks first. */
    public List<FocusSession> listDone(long userId, int limit) {
        return jdbc.sql("SELECT * FROM focus_sessions WHERE user_id = ? AND state = ? ORDER BY id DESC LIMIT ?")
                .params(userId, DONE, limit)
                .query(this::map).list();
    }

    public Optional<FocusSession> findDone(long id, long userId) {
        return jdbc.sql("SELECT * FROM focus_sessions WHERE id = ? AND user_id = ? AND state = ?")
                .params(id, userId, DONE)
                .query(this::map).optional();
    }

    public void updateReflection(long id, String reflection) {
        jdbc.sql("UPDATE focus_sessions SET reflection = ? WHERE id = ?").params(reflection, id).update();
    }

    /** Deletes the user's whole history. A block that's still running isn't history yet, so it stays. */
    public void deleteDone(long userId) {
        jdbc.sql("DELETE FROM focus_sessions WHERE user_id = ? AND state = ?").params(userId, DONE).update();
    }

    public int countCompleted(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM focus_sessions WHERE user_id = ? AND state = ? AND completed = 1")
                .params(userId, DONE)
                .query(Integer.class).single();
    }

    // ---- aggregates ------------------------------------------------------------------------------------

    public List<DayTotals> totalsByDay(long userId) {
        return jdbc.sql("""
                        SELECT day, COUNT(*) AS sessions, SUM(focused) AS seconds, SUM(completed) AS completed
                        FROM focus_sessions WHERE user_id = ? AND state = ?
                        GROUP BY day""")
                .params(userId, DONE)
                .query((rs, row) -> new DayTotals(rs.getString("day"), rs.getInt("sessions"), rs.getInt("seconds"),
                        rs.getInt("completed")))
                .list();
    }

    public Map<Long, TaskTotals> totalsByTask(long userId) {
        return jdbc.sql("""
                        SELECT task_id, SUM(completed) AS brews, SUM(focused) AS seconds
                        FROM focus_sessions WHERE user_id = ? AND state = ? AND task_id IS NOT NULL
                        GROUP BY task_id""")
                .params(userId, DONE)
                .query((rs, row) -> new TaskTotals(rs.getLong("task_id"), rs.getInt("brews"), rs.getInt("seconds")))
                .list().stream()
                .collect(Collectors.toMap(TaskTotals::taskId, t -> t));
    }

    private FocusSession map(ResultSet rs, int row) throws SQLException {
        return new FocusSession(
                rs.getLong("id"), nullableLong(rs, "task_id"), rs.getString("mode"), rs.getInt("planned"),
                rs.getInt("focused"), rs.getInt("completed") == 1, rs.getString("intention"),
                rs.getString("reflection"), rs.getInt("distractions"), rs.getString("day"),
                Instant.ofEpochMilli(rs.getLong("created_at")));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
    }
}
