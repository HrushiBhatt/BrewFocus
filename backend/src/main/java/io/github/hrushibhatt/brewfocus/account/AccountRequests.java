package io.github.hrushibhatt.brewfocus.account;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * The bodies the account endpoints accept. The size limits are generous caps against abuse; the real
 * password rules are in {@link PasswordPolicy}.
 */
final class AccountRequests {

    private AccountRequests() {}

    record Registration(
            @JsonProperty("email")
            @NotEmpty(message = "%s is required")
            @Email(message = "that doesn't look like an email address")
            @Size(max = 254, message = "%s must be at most {max} characters") String email,
            @JsonProperty("password")
            @NotEmpty(message = "%s is required")
            @Size(max = 1024, message = "%s must be at most {max} characters") String password,
            @JsonProperty("name")
            @Size(max = 60, message = "%s must be at most {max} characters") String name) {}

    record Credentials(
            @JsonProperty("email")
            @NotEmpty(message = "%s is required")
            @Size(max = 254, message = "%s must be at most {max} characters") String email,
            @JsonProperty("password")
            @NotEmpty(message = "%s is required")
            @Size(max = 1024, message = "%s must be at most {max} characters") String password) {}

    record PasswordChange(
            @JsonProperty("current")
            @NotEmpty(message = "%s is required")
            @Size(max = 1024, message = "%s must be at most {max} characters") String current,
            @JsonProperty("new")
            @NotEmpty(message = "%s is required")
            @Size(max = 1024, message = "%s must be at most {max} characters") String newPassword) {}
}
