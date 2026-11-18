package io.github.hrushibhatt.brewfocus.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.github.hrushibhatt.brewfocus.support.Browser;

/**
 * The whole OAuth 2.0 authorization-code flow against {@link FakeProvider}: the redirect out with state
 * and PKCE, the code exchange, the user lookup, and our own session cookie at the end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "brewfocus.db-path=:memory:",
        "brewfocus.jwt-secret=oauth-test-secret-0123456789abcdef01234",
        "brewfocus.bcrypt-cost=4",
})
class OAuthLoginIT {

    static final FakeProvider PROVIDER = FakeProvider.start();

    @LocalServerPort
    int port;

    @TestConfiguration
    static class UseTheFakeProvider {
        @Bean
        @Primary
        OAuthProviders fakeProviders() {
            return new OAuthProviders(List.of(PROVIDER.registration()));
        }
    }

    /** Runs a sign-in in {@code browser} for the user the provider will report, returning where it lands. */
    private String signIn(Browser browser, String userJson) {
        PROVIDER.userJson.set(userJson);
        var out = browser.get("/oauth2/authorization/fake");
        assertThat(out.status()).isEqualTo(302);
        URI authorize = URI.create(out.header("Location").orElseThrow());
        Map<String, String> query = query(authorize);

        // The provider would show its consent screen here, then send the browser back with a code.
        URI callback = URI.create(query.get("redirect_uri"));
        var back = browser.get(callback.getRawPath() + "?code=one-time-code&state="
                + URLEncoder.encode(query.get("state"), StandardCharsets.UTF_8));
        assertThat(back.status()).isEqualTo(302);
        return back.header("Location").orElseThrow();
    }

    @Test
    void theProviderIsOfferedOnTheLoginPage() {
        assertThat(new Browser(port).get("/api/auth/providers").json().get(0).asString()).isEqualTo("fake");
    }

    @Test
    void theRedirectCarriesStateAndAPkceChallenge() {
        var out = new Browser(port).get("/oauth2/authorization/fake");
        Map<String, String> query = query(URI.create(out.header("Location").orElseThrow()));
        assertThat(query).containsKeys("state", "code_challenge", "redirect_uri");
        assertThat(query.get("code_challenge_method")).isEqualTo("S256");
        assertThat(query.get("client_id")).isEqualTo(FakeProvider.CLIENT_ID);
        assertThat(query.get("redirect_uri")).endsWith("/login/oauth2/code/fake");
    }

    @Test
    void aFirstSignInCreatesTheAccountAndALaterOneReturnsToIt() {
        String user = "{\"id\": 4242, \"name\": \"Grace Hopper\", \"email\": \"grace@example.com\", \"email_verified\": true}";
        Browser laptop = new Browser(port);
        assertThat(signIn(laptop, user)).endsWith("/");

        // The app's own session: the same HttpOnly JWT cookie a password sign-in gets.
        var me = laptop.get("/api/me").json();
        assertThat(me.path("email").asString()).isEqualTo("grace@example.com");
        assertThat(me.path("name").asString()).isEqualTo("Grace Hopper");
        assertThat(me.path("password_set").asBoolean()).isFalse();
        assertThat(laptop.cookie("bf_session")).isPresent();

        // The code was exchanged with the PKCE verifier and the client's credentials.
        Map<String, String> token = PROVIDER.lastTokenRequest.get();
        assertThat(token.get("code")).isEqualTo("one-time-code");
        assertThat(token.get("code_verifier")).isNotBlank();
        assertThat(token.get("authorization")).startsWith("Basic ");

        Browser phone = new Browser(port);
        signIn(phone, user);
        assertThat(phone.get("/api/me").json().path("id").asLong()).isEqualTo(me.path("id").asLong());

        // An OAuth account has no password, so a password sign-in can't open it.
        assertThat(new Browser(port).logIn("grace@example.com", "anything at all").status()).isEqualTo(401);
    }

    @Test
    void anEmailThatAlreadyHasAPasswordAccountIsRefused() {
        new Browser(port).signUp("taken@example.com");
        String user = "{\"id\": 777, \"name\": \"Someone\", \"email\": \"Taken@Example.com\", \"email_verified\": true}";
        Browser b = new Browser(port);
        assertThat(signIn(b, user)).endsWith("/login?error=email-in-use");
        assertThat(b.get("/api/me").status()).isEqualTo(401);
    }

    @Test
    void anUnverifiedEmailIsRefused() {
        String user = "{\"id\": 888, \"email\": \"unverified@example.com\", \"email_verified\": false}";
        Browser b = new Browser(port);
        assertThat(signIn(b, user)).endsWith("/login?error=unverified-email");
        assertThat(b.get("/api/me").status()).isEqualTo(401);
    }

    @Test
    void aCallbackWithoutAMatchingStateIsRejected() {
        Browser b = new Browser(port);
        b.get("/oauth2/authorization/fake"); // starts a handshake, then the attacker swaps the state
        var forged = b.get("/login/oauth2/code/fake?code=stolen-code&state=forged-state");
        assertThat(forged.status()).isEqualTo(302);
        assertThat(forged.header("Location").orElseThrow()).endsWith("/login?error=oauth");
        assertThat(b.get("/api/me").status()).isEqualTo(401);
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> out = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            out.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return out;
    }
}
