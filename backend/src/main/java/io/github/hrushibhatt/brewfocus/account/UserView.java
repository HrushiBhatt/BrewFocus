package io.github.hrushibhatt.brewfocus.account;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An account as the client sees it.
 *
 * @param passwordSet false for an account made with OAuth 2.0, so the UI can hide the password form
 */
public record UserView(
        @JsonProperty("id") long id,
        @JsonProperty("email") String email,
        @JsonProperty("name") String name,
        @JsonProperty("settings") Settings settings,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("password_set") boolean passwordSet) {

    static UserView of(User user) {
        return new UserView(user.id(), user.email(), user.name(), user.settings(), user.createdAt(), user.hasPassword());
    }
}
