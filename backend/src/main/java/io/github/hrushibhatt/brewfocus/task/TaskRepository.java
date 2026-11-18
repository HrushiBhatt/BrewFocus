package io.github.hrushibhatt.brewfocus.task;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Tasks. Every query names the user, so one account can never read or change another's. */
@Repository
public class TaskRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public TaskRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Open tasks first, then finished ones, each in the order they were added. */
    public List<Task> list(long userId) {
        return jdbc.sql("SELECT * FROM tasks WHERE user_id = ? ORDER BY done, id").param(userId).query(this::map).list();
    }

    public Optional<Task> find(long id, long userId) {
        return jdbc.sql("SELECT * FROM tasks WHERE id = ? AND user_id = ?").params(id, userId).query(this::map).optional();
    }

    public Task insert(long userId, String title, int estimate) {
        Instant now = Instant.ofEpochMilli(clock.millis());
        long id = jdbc.sql("INSERT INTO tasks (user_id, title, estimate, done, created_at) VALUES (?, ?, ?, 0, ?) RETURNING id")
                .params(userId, title, estimate, now.toEpochMilli())
                .query(Long.class).single();
        return new Task(id, title, estimate, false, now);
    }

    public void update(long userId, Task task) {
        jdbc.sql("UPDATE tasks SET title = ?, estimate = ?, done = ? WHERE id = ? AND user_id = ?")
                .params(task.title(), task.estimate(), task.done() ? 1 : 0, task.id(), userId)
                .update();
    }

    /** True if a task of this user was deleted. */
    public boolean delete(long id, long userId) {
        return jdbc.sql("DELETE FROM tasks WHERE id = ? AND user_id = ?").params(id, userId).update() > 0;
    }

    private Task map(ResultSet rs, int row) throws SQLException {
        return new Task(rs.getLong("id"), rs.getString("title"), rs.getInt("estimate"), rs.getInt("done") == 1,
                Instant.ofEpochMilli(rs.getLong("created_at")));
    }
}
