package io.github.hrushibhatt.brewfocus.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The app's settings, bound from {@code brewfocus.*} in application.yml, which reads them from
 * environment variables. Anything invalid stops the server at startup (see {@link SigningKeyConfig}
 * and {@code ClientIpResolver}), so a mistake can't surface later as a confusing failure.
 *
 * @param jwtSecret      the key that signs session JWTs; empty means a random one per run
 * @param requireSecret  refuse to start without {@code jwtSecret} (the prod profile)
 * @param secureCookies  the app is served over HTTPS
 * @param trustedProxies addresses allowed to report a client's IP, for rate limiting
 * @param dbPath         the SQLite file, or {@code :memory:}
 * @param staticDir      the built frontend to serve; empty for API only
 * @param publicUrl      the public address, when it differs from what the server sees
 * @param bcryptCost     the bcrypt work factor
 * @param keepalive      how often an event stream pings and re-checks its session
 * @param oauth          OAuth 2.0 client credentials; a provider is on only when both are set
 */
@ConfigurationProperties("brewfocus")
public record BrewFocusProperties(
        String jwtSecret,
        boolean requireSecret,
        boolean secureCookies,
        @DefaultValue List<String> trustedProxies,
        @DefaultValue("brewfocus.db") String dbPath,
        String staticDir,
        String publicUrl,
        @DefaultValue("12") int bcryptCost,
        @DefaultValue("25s") Duration keepalive,
        @DefaultValue OAuth oauth) {

    /** Client credentials for each supported provider. */
    public record OAuth(@DefaultValue Client google, @DefaultValue Client github) {}

    /** One provider's OAuth 2.0 client, registered in its developer console. */
    public record Client(String clientId, String clientSecret) {

        /** Both halves are present, so the provider can be offered. */
        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    /** True when a built frontend should be served. */
    public boolean servesFrontend() {
        return staticDir != null && !staticDir.isBlank();
    }
}
