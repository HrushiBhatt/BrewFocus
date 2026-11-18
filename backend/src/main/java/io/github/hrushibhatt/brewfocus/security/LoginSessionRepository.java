package io.github.hrushibhatt.brewfocus.security;

import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code login_sessions} table: one row per signed-in browser. A session JWT is only accepted
 * while its row exists, which is what makes signing out immediate.
 */
@Repository
public class LoginSessionRepository {

    private final JdbcClient jdbc;

    public LoginSessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String id, long userId, Instant expiresAt, Instant now) {
        jdbc.sql("INSERT INTO login_sessions (id, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)")
                .params(id, userId, expiresAt.toEpochMilli(), now.toEpochMilli())
                .update();
    }

    /** True when the session exists, belongs to {@code userId} and hasn't expired. */
    public boolean isActive(String id, long userId, Instant now) {
        return jdbc.sql("SELECT COUNT(*) FROM login_sessions WHERE id = ? AND user_id = ? AND expires_at > ?")
                .params(id, userId, now.toEpochMilli())
                .query(Long.class).single() == 1;
    }

    /** True when the session exists and hasn't expired. Long-lived event streams re-check this. */
    public boolean isActive(String id, Instant now) {
        return jdbc.sql("SELECT COUNT(*) FROM login_sessions WHERE id = ? AND expires_at > ?")
                .params(id, now.toEpochMilli())
                .query(Long.class).single() == 1;
    }

    public void delete(String id) {
        jdbc.sql("DELETE FROM login_sessions WHERE id = ?").param(id).update();
    }

    /** Ends every session of {@code userId} except {@code keep}, which may be null. */
    public void deleteAllExcept(long userId, String keep) {
        jdbc.sql("DELETE FROM login_sessions WHERE user_id = ? AND id <> ?")
                .params(userId, keep == null ? "" : keep)
                .update();
    }

    /** Tidies the user's expired rows, which can no longer sign anyone in. */
    public void deleteExpired(long userId, Instant now) {
        jdbc.sql("DELETE FROM login_sessions WHERE user_id = ? AND expires_at <= ?")
                .params(userId, now.toEpochMilli())
                .update();
    }
}
