package io.github.hrushibhatt.brewfocus.security.oauth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A tiny OAuth 2.0 provider for tests, with a token endpoint and a user-info endpoint, so the whole
 * sign-in runs for real without calling Google or GitHub. It records the last token request so tests
 * can check what the app sent (the PKCE verifier, the client's credentials).
 */
final class FakeProvider {

    static final String CLIENT_ID = "test-client";
    static final String CLIENT_SECRET = "test-client-secret";

    private final HttpServer server;
    /** The user the next sign-in returns. */
    final AtomicReference<String> userJson = new AtomicReference<>();
    /** The form fields of the last token request. */
    final AtomicReference<Map<String, String>> lastTokenRequest = new AtomicReference<>();

    private FakeProvider() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/token", this::token);
        server.createContext("/user", this::user);
        server.start();
    }

    static FakeProvider start() {
        return new FakeProvider();
    }

    String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** The registration the app is given for this provider, shaped like GitHub's. */
    ClientRegistration registration() {
        return ClientRegistration.withRegistrationId("fake")
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("profile")
                .authorizationUri(base() + "/authorize")
                .tokenUri(base() + "/token")
                .userInfoUri(base() + "/user")
                .userNameAttributeName("id")
                .clientName("Fake")
                .build();
    }

    private void token(HttpExchange exchange) throws IOException {
        String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> fields = new HashMap<>();
        for (String pair : form.split("&")) {
            String[] kv = pair.split("=", 2);
            fields.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        fields.put("authorization", exchange.getRequestHeaders().getFirst("Authorization"));
        lastTokenRequest.set(fields);
        reply(exchange, "{\"access_token\":\"fake-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
    }

    private void user(HttpExchange exchange) throws IOException {
        reply(exchange, userJson.get());
    }

    private static void reply(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
