package io.github.hrushibhatt.brewfocus.preset;

import java.time.Clock;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Custom modes. Every query names the user, so one account can never read or delete another's. */
@Repository
public class PresetRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public PresetRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<Preset> list(long userId) {
        return jdbc.sql("SELECT id, name, focus_min, rest_min FROM presets WHERE user_id = ? ORDER BY id")
                .param(userId)
                .query((rs, row) -> new Preset(rs.getLong("id"), rs.getString("name"), rs.getInt("focus_min"),
                        rs.getInt("rest_min")))
                .list();
    }

    public Preset insert(long userId, String name, int focus, int rest) {
        long id = jdbc.sql("""
                        INSERT INTO presets (user_id, name, focus_min, rest_min, created_at)
                        VALUES (?, ?, ?, ?, ?) RETURNING id""")
                .params(userId, name, focus, rest, clock.millis())
                .query(Long.class).single();
        return new Preset(id, name, focus, rest);
    }

    /** True if a preset of this user was deleted. */
    public boolean delete(long id, long userId) {
        return jdbc.sql("DELETE FROM presets WHERE id = ? AND user_id = ?").params(id, userId).update() > 0;
    }
}
