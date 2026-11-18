package io.github.hrushibhatt.brewfocus.security.oauth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code oauth_identities} table: which provider account belongs to which of ours. */
@Repository
public class OAuthIdentityRepository {

    private final JdbcClient jdbc;

    public OAuthIdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findUserId(String provider, String subject) {
        return jdbc.sql("SELECT user_id FROM oauth_identities WHERE provider = ? AND subject = ?")
                .params(provider, subject)
                .query(Long.class).optional();
    }

    public void link(String provider, String subject, long userId, Instant now) {
        jdbc.sql("INSERT INTO oauth_identities (provider, subject, user_id, created_at) VALUES (?, ?, ?, ?)")
                .params(provider, subject, userId, now.toEpochMilli())
                .update();
    }
}
