package io.github.hrushibhatt.brewfocus.support;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.github.hrushibhatt.brewfocus.journal.FocusSessionRepository;

/**
 * The base of the integration tests: the whole app on a random port, with an in-memory database,
 * a fast bcrypt cost and a short event-stream keepalive. Every test class with these settings shares
 * one running app, so tests use fresh accounts (see {@link #email()}) rather than a clean database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "brewfocus.db-path=:memory:",
        "brewfocus.jwt-secret=" + IntegrationTest.SECRET,
        "brewfocus.bcrypt-cost=4",
        "brewfocus.keepalive=300ms",
        "brewfocus.trusted-proxies=127.0.0.1,::1",
        "logging.level.brewfocus.requests=warn",
})
public abstract class IntegrationTest {

    /** The signing key the server uses, so tests can try forging tokens with and without it. */
    public static final String SECRET = "integration-test-secret-0123456789abcdef";

    @LocalServerPort
    protected int port;

    @Autowired
    protected FocusSessionRepository focusSessions;

    /** A new browser with no cookies. */
    protected Browser browser() {
        return new Browser(port);
    }

    /** A new browser signed in to a new account. */
    protected Browser signedIn() {
        Browser browser = browser();
        browser.signUp(email());
        return browser;
    }

    /** An email no other test uses. */
    protected static String email() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    /** Writes a finished focus block straight to the database, as if it had been run. */
    protected long logSession(long userId, Long taskId, String day, int seconds, boolean completed) {
        Instant now = Instant.now();
        long id = focusSessions.insertRunning(userId, taskId, "Pomodoro", 1500, "", day, now, now);
        focusSessions.complete(id, completed, seconds, 0);
        return id;
    }
}
