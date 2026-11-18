package io.github.hrushibhatt.brewfocus.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.hrushibhatt.brewfocus.support.Browser;

/** One process serves the API and the built React app, with client-side routes falling back to index.html. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "brewfocus.db-path=:memory:",
        "brewfocus.jwt-secret=static-test-secret-0123456789abcdef0123",
        "brewfocus.bcrypt-cost=4",
})
class StaticFilesIT {

    private static final Path SITE = builtSite();

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void staticDir(DynamicPropertyRegistry registry) {
        registry.add("brewfocus.static-dir", SITE::toString);
    }

    /** A stand-in for frontend/dist: a page, a hashed bundle and an icon, plus a file outside it. */
    private static Path builtSite() {
        try {
            Path root = Files.createTempDirectory("brewfocus-site");
            Files.writeString(root.resolve("index.html"), "<!doctype html><title>Brew Focus</title>");
            Files.createDirectories(root.resolve("assets"));
            Files.writeString(root.resolve("assets/main-abc123.js"), "console.log('brew')");
            Files.writeString(root.resolve("favicon.svg"), "<svg/>");
            Files.writeString(root.getParent().resolve("brewfocus-outside.txt"), "not part of the site");
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void servesTheAppWithItsSecurityPolicy() {
        var page = new Browser(port).get("/");
        assertThat(page.status()).isEqualTo(200);
        assertThat(page.body()).contains("<title>Brew Focus</title>");
        assertThat(page.header("Content-Security-Policy").orElseThrow()).contains("script-src 'self'");
        assertThat(page.header("Cache-Control")).contains("no-cache");
    }

    @Test
    void clientSideRoutesGetTheAppAndApiPathsDont() {
        Browser b = new Browser(port);
        for (String route : new String[] {"/progress", "/login", "/some/deep/link"}) {
            assertThat(b.get(route).body()).as(route).contains("<title>Brew Focus</title>");
        }
        var api = b.get("/api/nothing-here");
        assertThat(api.header("Content-Type").orElseThrow()).contains("application/json");
        assertThat(api.body()).doesNotContain("<title>");
    }

    @Test
    void hashedBundlesAreCachedForGoodAndMissingOnesAre404s() {
        Browser b = new Browser(port);
        var bundle = b.get("/assets/main-abc123.js");
        assertThat(bundle.status()).isEqualTo(200);
        assertThat(bundle.header("Cache-Control").orElseThrow()).contains("immutable");
        assertThat(b.get("/favicon.svg").body()).isEqualTo("<svg/>");
        assertThat(b.get("/assets/main-missing.js").status()).isEqualTo(404);
    }

    @Test
    void filesOutsideTheBuildCantBeReached() {
        Browser b = new Browser(port);
        for (String path : new String[] {"/../brewfocus-outside.txt", "/%2e%2e/brewfocus-outside.txt",
                "/assets/..%2f..%2fbrewfocus-outside.txt"}) {
            assertThat(b.get(path).body()).as(path).doesNotContain("not part of the site");
        }
    }
}
