package io.github.hrushibhatt.brewfocus.account;

import java.time.Instant;

/**
 * An account, as stored. It never leaves the server as it is: {@link UserView} is what clients see,
 * so the password hash can't be serialized by accident.
 *
 * @param passwordHash bcrypt, or null for an account made with OAuth 2.0
 */
public record User(long id, String email, String name, String passwordHash, Settings settings, Instant createdAt) {

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }
}
