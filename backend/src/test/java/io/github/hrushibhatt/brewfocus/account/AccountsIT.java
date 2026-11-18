package io.github.hrushibhatt.brewfocus.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.hrushibhatt.brewfocus.support.Browser;
import io.github.hrushibhatt.brewfocus.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/** Signing up, in and out, and passwords, over real HTTP. */
class AccountsIT extends IntegrationTest {

    private static final String NEW_PASSWORD = "plum lantern gravity orbit";

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordHasher hasher;

    @Test
    void signUpSignsInAndTheSessionRestoresTheUser() {
        Browser ada = browser();
        String email = email();
        JsonNode user = ada.signUp(email.toUpperCase());

        assertThat(user.path("email").asString()).isEqualTo(email); // stored lower-cased
        assertThat(user.path("name").asString()).isEqualTo(email.substring(0, email.indexOf('@')));
        assertThat(user.path("settings").path("tick").asString()).isEqualTo("off");
        assertThat(user.path("password_set").asBoolean()).isTrue();
        assertThat(user.has("password_hash") || user.has("passwordHash")).isFalse();

        assertThat(ada.get("/api/me").json().path("id").asLong()).isEqualTo(user.path("id").asLong());
    }

    @Test
    void aTakenEmailIsAConflictAndAWrongPasswordIsRefused() {
        String email = email();
        browser().signUp(email);

        Browser other = browser();
        var again = other.post("/api/auth/register", Map.of("email", email, "password", Browser.PASSWORD));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.error()).contains("already has an account");

        var wrong = other.logIn(email, "not the password");
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.error()).isEqualTo("wrong email or password");
        assertThat(other.logIn("nobody-" + email, "not the password").error()).isEqualTo("wrong email or password");
        assertThat(other.logIn(" " + email.toUpperCase() + " ", Browser.PASSWORD).status()).isEqualTo(200);
    }

    /**
     * Sign-ups for one email at the same moment all pass the "is it taken?" check, so the unique index
     * has to decide, and the losers should hear why rather than get a server error.
     */
    @Test
    void concurrentSignUpsForOneEmailMakeOneAccount() throws Exception {
        String email = email();
        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Browser b = browser();
            b.csrfToken(); // fetched before the race
            attempts.add(() -> b.post("/api/auth/register", Map.of("email", email, "password", Browser.PASSWORD)).status());
        }
        List<Integer> codes = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Integer> f : pool.invokeAll(attempts)) {
                codes.add(f.get());
            }
        }
        assertThat(codes).containsOnly(201, 409);
        assertThat(codes).filteredOn(c -> c == 201).hasSize(1);
    }

    @Test
    void registrationIsValidated() {
        Browser b = browser();
        assertThat(b.post("/api/auth/register", Map.of("password", Browser.PASSWORD)).error()).isEqualTo("email is required");
        assertThat(b.post("/api/auth/register", Map.of("email", "not-an-email", "password", Browser.PASSWORD)).error())
                .isEqualTo("that doesn't look like an email address");
        assertThat(b.post("/api/auth/register", Map.of("email", email(), "password", "short")).error())
                .isEqualTo("password must be at least 12 characters");
        assertThat(b.post("/api/auth/register", Map.of("email", email(), "password", "password1234")).error())
                .contains("too easy to guess");
        assertThat(b.post("/api/auth/register", Map.of("email", email(), "password", "é".repeat(72))).error())
                .contains("at most 72 bytes");
        assertThat(b.post("/api/auth/register", "[1, 2]").error()).isEqualTo("expected a JSON object");
        assertThat(b.post("/api/auth/register", "{\"email\": 5, \"password\": []}").status()).isEqualTo(400);
    }

    @Test
    void anAccountWhosePasswordPredatesTheRulesCanStillSignIn() {
        String email = email();
        users.insert(email, "Old", hasher.hash("short"));
        assertThat(browser().logIn(email, "short").status()).isEqualTo(200);
    }

    @Test
    void signingOutEndsTheSessionOnTheServer() {
        Browser ada = signedIn();
        String stolen = ada.cookie("bf_session").orElseThrow();
        assertThat(replay(stolen)).isEqualTo(200);

        assertThat(ada.post("/api/auth/logout", null).status()).isEqualTo(204);
        assertThat(replay(stolen)).isEqualTo(401); // a copied cookie is now worthless
        assertThat(ada.get("/api/me").status()).isEqualTo(401);
    }

    @Test
    void signingOutEverywhereEndsEverySession() {
        String email = email();
        Browser phone = browser();
        phone.signUp(email);
        Browser laptop = browser();
        assertThat(laptop.logIn(email, Browser.PASSWORD).status()).isEqualTo(200);

        assertThat(laptop.post("/api/auth/logout-all", null).status()).isEqualTo(204);
        assertThat(phone.get("/api/me").status()).isEqualTo(401);
        assertThat(laptop.get("/api/me").status()).isEqualTo(401);
    }

    @Test
    void signingInAgainEndsTheBrowsersOldSession() {
        String email = email();
        Browser ada = browser();
        ada.signUp(email);
        String old = ada.cookie("bf_session").orElseThrow();
        assertThat(ada.logIn(email, Browser.PASSWORD).status()).isEqualTo(200);
        assertThat(replay(old)).isEqualTo(401);
    }

    @Test
    void changingAPasswordSignsOutEveryOtherDevice() {
        String email = email();
        Browser ada = browser();
        ada.signUp(email);
        Browser phone = browser();
        phone.logIn(email, Browser.PASSWORD);

        var wrongCurrent = ada.put("/api/me/password", Map.of("current", "not it", "new", NEW_PASSWORD));
        assertThat(wrongCurrent.status()).isEqualTo(403); // not 401: that would sign this page out
        assertThat(ada.put("/api/me/password", Map.of("current", Browser.PASSWORD, "new", "password1234")).status())
                .isEqualTo(400);
        assertThat(ada.put("/api/me/password", Map.of("current", Browser.PASSWORD, "new", NEW_PASSWORD)).status())
                .isEqualTo(204);

        assertThat(ada.get("/api/me").status()).isEqualTo(200); // this device stays in
        assertThat(phone.get("/api/me").status()).isEqualTo(401);
        assertThat(browser().logIn(email, Browser.PASSWORD).status()).isEqualTo(401);
        assertThat(browser().logIn(email, NEW_PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void guessingTheCurrentPasswordIsSlowedDown() {
        Browser ada = signedIn();
        int last = 0;
        for (int i = 0; i < 6; i++) {
            last = ada.put("/api/me/password", Map.of("current", "a guess", "new", NEW_PASSWORD)).status();
        }
        assertThat(last).isEqualTo(429);
    }

    @Test
    void repeatedFailedSignInsForOneAccountAreSlowedDownWhicheverAddressTheyComeFrom() {
        String email = email();
        browser().signUp(email);
        Browser.Reply last = null;
        for (int i = 0; i < 11; i++) {
            last = browser().logIn(email, "guess " + i); // a new address every time
        }
        assertThat(last.status()).isEqualTo(429);
        assertThat(last.header("Retry-After")).isPresent();
        // Other accounts are unaffected.
        String other = email();
        browser().signUp(other);
        assertThat(browser().logIn(other, Browser.PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void settingsRoundTripAndFillInDefaults() {
        Browser ada = signedIn();
        var saved = ada.put("/api/me/settings", Map.of("strict", true, "tick", "ominous"));
        assertThat(saved.status()).isEqualTo(200);
        assertThat(saved.json().path("sound").asBoolean()).isTrue(); // not sent, so its default
        JsonNode settings = ada.get("/api/me").json().path("settings");
        assertThat(settings.path("strict").asBoolean()).isTrue();
        assertThat(settings.path("tick").asString()).isEqualTo("ominous");

        assertThat(ada.put("/api/me/settings", Map.of("tick", "loud")).error())
                .isEqualTo("tick must be one of: off clock ominous");
    }

    /** Sends a request carrying nothing but a copied session cookie, as a thief would. */
    private int replay(String sessionCookie) {
        Browser thief = browser();
        return thief.send(thief.request("/api/me").header("Cookie", "bf_session=" + sessionCookie).GET().build()).status();
    }
}
