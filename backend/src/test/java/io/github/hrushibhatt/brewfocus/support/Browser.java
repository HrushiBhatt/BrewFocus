package io.github.hrushibhatt.brewfocus.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One browser, for integration tests: it keeps its own cookies (the session and the CSRF token) and
 * echoes the CSRF token on every change, as the React app's API client does.
 *
 * <p>Each browser gets its own made-up client address through {@code X-Forwarded-For} (the test
 * server trusts the loopback proxy), so tests don't share per-address rate limits.
 */
public final class Browser {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String base;
    private final CookieManager cookies;
    private final HttpClient http;
    private final String ip;

    public Browser(int port) {
        this("http://localhost:" + port, new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    private Browser(String base, CookieManager cookies) {
        this.base = base;
        this.cookies = cookies;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        var random = ThreadLocalRandom.current();
        this.ip = "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
    }

    /** Another device signed in to the same account: same cookies, its own connection. */
    public Browser sameCookies() {
        return new Browser(base, cookies);
    }

    public String base() {
        return base;
    }

    /** An HTTP reply, with its body as text and as JSON. */
    public record Reply(int status, String body, HttpHeaders headers) {
        public JsonNode json() {
            return JSON.readTree(body);
        }

        public <T> T as(Class<T> type) {
            return JSON.readValue(body, type);
        }

        public String error() {
            return json().path("error").asString();
        }

        public Optional<String> header(String name) {
            return headers.firstValue(name);
        }
    }

    public Reply get(String path) {
        return call("GET", path, null);
    }

    public Reply post(String path, Object body) {
        return call("POST", path, body);
    }

    public Reply put(String path, Object body) {
        return call("PUT", path, body);
    }

    public Reply patch(String path, Object body) {
        return call("PATCH", path, body);
    }

    public Reply delete(String path) {
        return call("DELETE", path, null);
    }

    /** Sends a JSON request, with the CSRF header on anything that changes state. */
    public Reply call(String method, String path, Object body) {
        HttpRequest.Builder request = request(path);
        if (!method.equals("GET") && !method.equals("HEAD")) {
            request.header("X-XSRF-TOKEN", csrfToken());
        }
        String json = body == null ? null : body instanceof String s ? s : JSON.writeValueAsString(body);
        request.header("Content-Type", "application/json");
        request.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return send(request.build());
    }

    /** A request with only this browser's address set: add headers, then {@link #send}. */
    public HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(base + path)).header("X-Forwarded-For", ip);
    }

    public Reply send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body(), response.headers());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws Exception {
        return http.send(request, handler);
    }

    /** The XSRF-TOKEN cookie, fetching a page first if this browser has none yet. */
    public String csrfToken() {
        return cookie("XSRF-TOKEN").orElseGet(() -> {
            get("/api/health");
            return cookie("XSRF-TOKEN").orElseThrow();
        });
    }

    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    // ---- shortcuts for the account every test needs -------------------------------------------------

    public static final String PASSWORD = "correct horse battery";

    /** Signs up a fresh account and returns its JSON. */
    public JsonNode signUp(String email) {
        Reply reply = post("/api/auth/register", Map.of("email", email, "password", PASSWORD));
        if (reply.status() != 201) {
            throw new AssertionError("sign-up for " + email + ": " + reply.status() + " " + reply.body());
        }
        return reply.json();
    }

    public Reply logIn(String email, String password) {
        return post("/api/auth/login", Map.of("email", email, "password", password));
    }
}
