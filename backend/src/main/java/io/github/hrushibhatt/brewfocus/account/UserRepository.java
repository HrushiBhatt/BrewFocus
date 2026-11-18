package io.github.hrushibhatt.brewfocus.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.json.JsonMapper;

/** Accounts. Emails are stored trimmed and lower-cased, so lookups are exact matches. */
@Repository
public class UserRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public UserRepository(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public Optional<User> findById(long id) {
        return jdbc.sql("SELECT * FROM users WHERE id = ?").param(id).query(this::map).optional();
    }

    public Optional<User> findByEmail(String email) {
        return jdbc.sql("SELECT * FROM users WHERE email = ?").param(email).query(this::map).optional();
    }

    public boolean emailTaken(String email) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE email = ?").param(email).query(Long.class).single() > 0;
    }

    /**
     * Creates an account and returns it. The unique index on email is what really prevents two
     * accounts sharing one: a concurrent duplicate fails here with a DuplicateKeyException.
     */
    public User insert(String email, String name, String passwordHash) {
        Instant now = clock.instant();
        long id = jdbc.sql("""
                        INSERT INTO users (email, name, password_hash, settings, created_at)
                        VALUES (?, ?, ?, ?, ?) RETURNING id""")
                .params(email, name, passwordHash, json.writeValueAsString(Settings.DEFAULTS), now.toEpochMilli())
                .query(Long.class).single();
        return new User(id, email, name, passwordHash, Settings.DEFAULTS, Instant.ofEpochMilli(now.toEpochMilli()));
    }

    public void updatePasswordHash(long id, String hash) {
        jdbc.sql("UPDATE users SET password_hash = ? WHERE id = ?").params(hash, id).update();
    }

    public void updateSettings(long id, Settings settings) {
        jdbc.sql("UPDATE users SET settings = ? WHERE id = ?").params(json.writeValueAsString(settings), id).update();
    }

    private User map(ResultSet rs, int row) throws SQLException {
        Settings saved = json.readValue(rs.getString("settings"), Settings.class);
        return new User(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("name"),
                rs.getString("password_hash"),
                saved.withDefaults(), // an account saved before a setting existed gets its default
                Instant.ofEpochMilli(rs.getLong("created_at")));
    }
}
