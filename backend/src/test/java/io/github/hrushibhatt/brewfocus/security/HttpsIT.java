package io.github.hrushibhatt.brewfocus.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.github.hrushibhatt.brewfocus.support.Browser;

/**
 * With SECURE_COOKIES=true (served over HTTPS, perhaps behind a TLS-terminating proxy): HSTS, the
 * stricter cookie prefix, and an upgrade of any stray http:// subresource.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "brewfocus.db-path=:memory:",
        "brewfocus.jwt-secret=https-test-secret-0123456789abcdef0123",
        "brewfocus.bcrypt-cost=4",
        "brewfocus.secure-cookies=true",
})
class HttpsIT {

    @LocalServerPort
    int port;

    @Test
    void hstsAndTheHostPrefixWhenServedOverHttps() {
        Browser b = new Browser(port);
        var health = b.get("/api/health");
        assertThat(health.header("Strict-Transport-Security")).contains("max-age=31536000 ; includeSubDomains");
        assertThat(health.header("Content-Security-Policy").orElseThrow()).endsWith("upgrade-insecure-requests");

        // Java's cookie jar won't return a Secure cookie over plain http, so the token is set by hand.
        String token = "0123456789012345678901234567890123456789012345678901234567890123";
        var request = b.request("/api/auth/register")
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + token)
                .header("X-XSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"https@example.com\",\"password\":\"" + Browser.PASSWORD + "\"}"))
                .build();
        var reply = b.send(request);
        assertThat(reply.status()).isEqualTo(201);
        String cookie = reply.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("__Host-bf_session=")).findFirst().orElseThrow();
        assertThat(cookie).contains("Secure", "HttpOnly", "Path=/").doesNotContain("Domain");
    }
}
