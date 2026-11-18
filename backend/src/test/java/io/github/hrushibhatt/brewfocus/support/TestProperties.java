package io.github.hrushibhatt.brewfocus.support;

import java.time.Duration;
import java.util.List;

import io.github.hrushibhatt.brewfocus.config.BrewFocusProperties;

/** {@link BrewFocusProperties} for unit tests, without starting Spring. */
public final class TestProperties {

    public static final String SECRET = "unit-test-secret-unit-test-secret-0123";

    private TestProperties() {}

    public static BrewFocusProperties defaults() {
        return build(12, false, List.of(), null);
    }

    public static BrewFocusProperties withBcryptCost(int cost) {
        return build(cost, false, List.of(), null);
    }

    public static BrewFocusProperties withTrustedProxies(String... proxies) {
        return build(4, false, List.of(proxies), null);
    }

    public static BrewFocusProperties withOAuth(BrewFocusProperties.Client google, BrewFocusProperties.Client github,
            String publicUrl) {
        return new BrewFocusProperties(SECRET, false, false, List.of(), ":memory:", null, publicUrl, 4,
                Duration.ofSeconds(25), new BrewFocusProperties.OAuth(google, github));
    }

    private static BrewFocusProperties build(int cost, boolean secure, List<String> proxies, String publicUrl) {
        var none = new BrewFocusProperties.Client(null, null);
        return new BrewFocusProperties(SECRET, false, secure, proxies, ":memory:", null, publicUrl, cost,
                Duration.ofSeconds(25), new BrewFocusProperties.OAuth(none, none));
    }
}
