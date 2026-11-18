package io.github.hrushibhatt.brewfocus.account;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import io.github.hrushibhatt.brewfocus.events.EventHub;
import io.github.hrushibhatt.brewfocus.security.SessionService;
import io.github.hrushibhatt.brewfocus.security.ratelimit.RateLimiter;
import io.github.hrushibhatt.brewfocus.security.ratelimit.RateLimits;
import io.github.hrushibhatt.brewfocus.web.ApiException;
import io.github.hrushibhatt.brewfocus.web.ErrorBody;

/** The rules for accounts: who may sign up, sign in and change a password. */
@Service
public class AccountService {

    private final UserRepository users;
    private final PasswordPolicy policy;
    private final PasswordHasher hasher;
    private final SessionService sessions;
    private final RateLimits limits;
    private final EventHub hub;

    public AccountService(UserRepository users, PasswordPolicy policy, PasswordHasher hasher, SessionService sessions,
            RateLimits limits, EventHub hub) {
        this.users = users;
        this.policy = policy;
        this.hasher = hasher;
        this.sessions = sessions;
        this.limits = limits;
        this.hub = hub;
    }

    /** Emails are compared trimmed and lower-cased, so "Ada@Example.com " is the same account. */
    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Creates a password account. The caller signs it in. */
    public User register(AccountRequests.Registration in) {
        String email = normalize(in.email());
        if (users.emailTaken(email)) { // checked first so a taken email doesn't cost a bcrypt hash
            throw emailConflict();
        }
        String name = in.name() == null ? "" : in.name().trim();
        policy.check(in.password(), email, name);
        String hash = hasher.hash(in.password());
        if (name.isEmpty()) {
            name = email.substring(0, email.indexOf('@'));
        }
        try {
            return users.insert(email, name, hash);
        } catch (DuplicateKeyException race) {
            // Two sign-ups for one email can both pass the check above, because requests really do run
            // at once. The unique index is what decides, so losing means "taken", not a server error.
            throw emailConflict();
        }
    }

    /** Checks a password, returning the account it opens. */
    public User login(AccountRequests.Credentials in) {
        String email = normalize(in.email());
        // Per account as well as per address, so spreading guesses over many IPs doesn't help.
        RateLimiter.Decision decision = limits.loginAccount().acquire("login:" + email);
        if (!decision.allowed()) {
            throw ApiException.tooManyRequests(
                    "too many sign-in attempts for this account; try again in a few minutes", decision.retryAfter());
        }
        User user = users.findByEmail(email).orElse(null);
        // Runs bcrypt even for an unknown email, so the reply time gives nothing away.
        if (!hasher.matches(user == null ? null : user.passwordHash(), in.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "wrong email or password");
        }
        return user;
    }

    /**
     * Asks for the current password, so a borrowed session can't lock the owner out, then ends every
     * other session, so whoever had the old password is signed out too.
     */
    public void changePassword(long userId, String sessionId, AccountRequests.PasswordChange in) {
        RateLimiter.Decision decision = limits.passwordChange().acquire("password:user:" + userId);
        if (!decision.allowed()) {
            long seconds = ErrorBody.retryAfterSeconds(decision.retryAfter());
            throw ApiException.tooManyRequests("too many requests: try again in " + seconds + " seconds",
                    decision.retryAfter());
        }
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (!hasher.matches(user.passwordHash(), in.current())) {
            // 403, not 401: the session is fine, and a 401 would sign the page out.
            String reason = user.hasPassword() ? "your current password isn't right"
                    : "this account signs in with Google or GitHub, so it has no password to change";
            throw new ApiException(HttpStatus.FORBIDDEN, reason);
        }
        policy.check(in.newPassword(), user.email(), user.name());
        users.updatePasswordHash(userId, hasher.hash(in.newPassword()));
        sessions.revokeAll(userId, sessionId);
        hub.changed(userId, "account");
    }

    /** Saves preferences, filling in any that weren't sent, and tells the user's other tabs. */
    public Settings saveSettings(long userId, Settings in) {
        Settings settings = in.withDefaults();
        users.updateSettings(userId, settings);
        hub.changed(userId, "settings");
        return settings;
    }

    private static ApiException emailConflict() {
        return new ApiException(HttpStatus.CONFLICT, "that email already has an account, so try logging in");
    }
}
