package io.github.hrushibhatt.brewfocus.security.ratelimit;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

/**
 * Every rate limit in one place, so the numbers can be read and compared at a glance.
 *
 * <ul>
 *   <li>{@link #api()}: everything under /api, per address. A blunt limit against floods.
 *   <li>{@link #auth()}: sign-in, sign-up and OAuth 2.0 handshakes, per address.
 *   <li>{@link #loginAccount()}: sign-in per account being guessed at, so spreading guesses over many
 *       addresses doesn't help.
 *   <li>{@link #passwordChange()}: per signed-in user, so a stolen session can't be used to guess the
 *       current password.
 * </ul>
 */
@Component
public class RateLimits {

    private final RateLimiter api;
    private final RateLimiter auth;
    private final RateLimiter loginAccount;
    private final RateLimiter passwordChange;

    public RateLimits(Clock clock) {
        this.api = new RateLimiter(600, Duration.ofMinutes(1), clock);
        this.auth = new RateLimiter(20, Duration.ofMinutes(1), clock);
        this.loginAccount = new RateLimiter(10, Duration.ofMinutes(10), clock);
        this.passwordChange = new RateLimiter(5, Duration.ofMinutes(10), clock);
    }

    public RateLimiter api() {
        return api;
    }

    public RateLimiter auth() {
        return auth;
    }

    public RateLimiter loginAccount() {
        return loginAccount;
    }

    public RateLimiter passwordChange() {
        return passwordChange;
    }
}
