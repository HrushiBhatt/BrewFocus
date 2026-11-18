package io.github.hrushibhatt.brewfocus.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import io.github.hrushibhatt.brewfocus.support.Browser;
import io.github.hrushibhatt.brewfocus.support.IntegrationTest;
import io.github.hrushibhatt.brewfocus.web.RequestSizeLimitFilter;
import tools.jackson.databind.json.JsonMapper;

/** The protections around every request: CSRF, cross-site checks, headers, limits and forged tokens. */
@ExtendWith(OutputCaptureExtension.class)
class SecurityIT extends IntegrationTest {

    @Test
    void stateChangingRequestsNeedTheCsrfToken() {
        Browser b = browser();
        HttpRequest noToken = b.request("/api/auth/register")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email() + "\",\"password\":\"x\"}"))
                .build();
        var refused = b.send(noToken);
        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.error()).contains("security token");

        b.csrfToken(); // the browser has the cookie, but sends a different value in the header
        HttpRequest wrongToken = b.request("/api/auth/logout").header("X-XSRF-TOKEN", "forged")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(b.send(wrongToken).status()).isEqualTo(403);

        assertThat(b.get("/api/health").status()).isEqualTo(200); // reads need no token
    }

    @Test
    void crossSiteRequestsAreRefusedEvenWithAToken() {
        Browser ada = signedIn();
        HttpRequest forged = ada.request("/api/tasks")
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", ada.csrfToken())
                .header("Sec-Fetch-Site", "cross-site")
                .header("Origin", "https://evil.example")
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"forged\"}"))
                .build();
        var reply = ada.send(forged);
        assertThat(reply.status()).isEqualTo(403);
        assertThat(reply.error()).isEqualTo("that request came from another site");
        assertThat(ada.get("/api/tasks").json()).isEmpty();
    }

    @Test
    void everythingButSigningInNeedsAnAccount() {
        Browser anonymous = browser();
        for (String path : List.of("/api/me", "/api/presets", "/api/tasks", "/api/timer", "/api/events",
                "/api/sessions", "/api/stats")) {
            var reply = anonymous.get(path);
            assertThat(reply.status()).as(path).isEqualTo(401);
            assertThat(reply.error()).isEqualTo("please log in");
        }
        assertThat(anonymous.post("/api/timer/focus", Map.of()).status()).isEqualTo(401);
        assertThat(anonymous.get("/api/health").status()).isEqualTo(200);
        assertThat(anonymous.get("/api/auth/providers").json()).isEmpty();
    }

    @Test
    void usersCannotTouchEachOthersData() {
        Browser ada = signedIn();
        Browser eve = signedIn();
        long task = ada.post("/api/tasks", Map.of("title", "Ada's secret")).json().path("id").asLong();
        long preset = ada.post("/api/presets", Map.of("name", "Ada's mode", "focus", 30, "rest", 5)).json().path("id").asLong();
        long adaId = ada.get("/api/me").json().path("id").asLong();
        long session = logSession(adaId, null, "2026-11-18", 1500, true);

        assertThat(eve.get("/api/tasks").json()).isEmpty();
        assertThat(eve.get("/api/presets").json()).isEmpty();
        assertThat(eve.get("/api/sessions").json()).isEmpty();
        // Someone else's record is simply "not found", so IDs reveal nothing.
        assertThat(eve.patch("/api/tasks/" + task, Map.of("done", true)).status()).isEqualTo(404);
        assertThat(eve.delete("/api/tasks/" + task).status()).isEqualTo(404);
        assertThat(eve.delete("/api/presets/" + preset).status()).isEqualTo(404);
        assertThat(eve.patch("/api/sessions/" + session, Map.of("reflection", "mine now")).status()).isEqualTo(404);
        assertThat(eve.post("/api/timer/focus", Map.of("mode", "Pomodoro", "planned", 1500, "task_id", task,
                "day", "2026-11-18")).status()).isEqualTo(404);
        eve.delete("/api/sessions");

        assertThat(ada.get("/api/tasks").json().get(0).path("done").asBoolean()).isFalse();
        assertThat(ada.get("/api/presets").json()).hasSize(1);
        assertThat(ada.get("/api/sessions").json()).hasSize(1);
    }

    @Test
    void securityHeadersAreOnEveryResponse() {
        var reply = browser().get("/api/health");
        assertThat(reply.header("X-Content-Type-Options")).contains("nosniff");
        assertThat(reply.header("X-Frame-Options")).contains("DENY");
        assertThat(reply.header("Referrer-Policy")).contains("no-referrer");
        assertThat(reply.header("Cache-Control")).contains("no-store");
        assertThat(reply.header("Cross-Origin-Opener-Policy")).contains("same-origin");
        assertThat(reply.header("Permissions-Policy").orElseThrow()).contains("camera=()");
        String csp = reply.header("Content-Security-Policy").orElseThrow();
        assertThat(csp).contains("default-src 'none'", "script-src 'self'", "frame-ancestors 'none'", "base-uri 'self'");
        assertThat(csp).doesNotContain("script-src 'self' 'unsafe");
        assertThat(reply.header("Strict-Transport-Security")).isEmpty(); // plain HTTP here; see HttpsIT
    }

    @Test
    void theSessionCookieIsOutOfReachOfPageScripts() {
        Browser b = browser();
        String csrf = setCookie(b.get("/api/health"), "XSRF-TOKEN");
        assertThat(csrf).contains("SameSite=Lax").doesNotContain("HttpOnly"); // the page must read this one

        var reply = b.post("/api/auth/register", Map.of("email", email(), "password", Browser.PASSWORD));
        assertThat(setCookie(reply, "bf_session")).contains("HttpOnly", "SameSite=Lax", "Path=/").doesNotContain("Domain");
    }

    @Test
    void oversizedBodiesAreRefused() {
        Browser b = browser();
        String big = "x".repeat(RequestSizeLimitFilter.MAX_BODY_BYTES + 1);
        var tooBig = b.post("/api/auth/login", Map.of("email", "a@b.co", "password", big));
        assertThat(tooBig.status()).isEqualTo(413);
        assertThat(tooBig.error()).isEqualTo("that request is too large");
        assertThat(b.post("/api/auth/login", Map.of("email", "a".repeat(300), "password", "x")).status()).isEqualTo(400);
    }

    @Test
    void unknownApiPathsAreJson404sForSignedInUsers() {
        Browser ada = signedIn();
        // The removed integrations are really gone, not just hidden.
        for (String path : List.of("/api/schedule", "/api/insights", "/api/calendar/accounts", "/api/plans/live")) {
            var reply = ada.get(path);
            assertThat(reply.status()).as(path).isEqualTo(404);
            assertThat(reply.error()).isEqualTo("not found");
        }
        assertThat(ada.post("/api/assistant/plan", Map.of()).status()).isEqualTo(404);
        assertThat(ada.get("/api/tasks/not-a-number").status()).isEqualTo(404);
        assertThat(ada.post("/api/timer/explode", null).status()).isEqualTo(404);
    }

    /** The JWT is what stands between a visitor and an account, so each way of faking one must fail. */
    @Test
    void forgedTokensAreRefused() {
        Browser ada = signedIn();
        long id = ada.get("/api/me").json().path("id").asLong();
        String real = ada.cookie("bf_session").orElseThrow();
        String sessionId = decodeClaim(real, "jti");
        Instant now = Instant.now();

        var rightKey = new NimbusJwtEncoder(new ImmutableSecret<>(key(SECRET)));
        var wrongKey = new NimbusJwtEncoder(new ImmutableSecret<>(key("someone-else's-key-someone-else's-key!")));

        Map<String, String> forgeries = Map.of(
                "signed with another key", sign(wrongKey, sessionId, id, now.plusSeconds(600)),
                "already expired", sign(rightKey, sessionId, id, now.minusSeconds(60)),
                "naming a session that doesn't exist", sign(rightKey, "0".repeat(32), id, now.plusSeconds(600)),
                "for someone else's account", sign(rightKey, sessionId, id + 1, now.plusSeconds(600)),
                "with no signature at all", unsigned(sessionId, id),
                "tampered with", real.substring(0, real.length() - 4) + "AAAA",
                "not a JWT", "hello");
        forgeries.forEach((what, token) -> assertThat(replay(token)).as(what).isEqualTo(401));

        assertThat(replay(sign(rightKey, sessionId, id, now.plusSeconds(600)))).as("genuine").isEqualTo(200);
    }

    @Test
    void logsCarryNeitherCodesNorCookies(CapturedOutput output) {
        Browser ada = signedIn();
        String cookie = ada.cookie("bf_session").orElseThrow();
        ada.get("/api/health?code=SECRET-OAUTH-CODE&state=SECRET-STATE");
        ada.get("/login/oauth2/code/google?code=SECRET-OAUTH-CODE&state=SECRET-STATE");
        ada.get("/api/tasks/not-a-number?token=SECRET-TOKEN");

        assertThat(output.getAll()).doesNotContain("SECRET-OAUTH-CODE", "SECRET-STATE", "SECRET-TOKEN", cookie);
    }

    private static String setCookie(Browser.Reply reply, String name) {
        return reply.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(name + "=")).findFirst().orElseThrow();
    }

    private int replay(String token) {
        Browser thief = browser();
        return thief.send(thief.request("/api/me").header("Cookie", "bf_session=" + token).GET().build()).status();
    }

    private static SecretKeySpec key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static String sign(NimbusJwtEncoder encoder, String sessionId, long userId, Instant expires) {
        JwtClaimsSet claims = JwtClaimsSet.builder().id(sessionId).subject(Long.toString(userId))
                .issuedAt(expires.minusSeconds(3600)).expiresAt(expires).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /** {"alg":"none"}: a token that asks not to be checked at all. */
    private static String unsigned(String sessionId, long userId) {
        var b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body = b64.encodeToString(("{\"jti\":\"" + sessionId + "\",\"sub\":\"" + userId + "\",\"exp\":"
                + (Instant.now().getEpochSecond() + 600) + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + body + ".";
    }

    private static String decodeClaim(String jwt, String claim) {
        String body = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonMapper.builder().build().readTree(body).path(claim).asString();
    }
}
