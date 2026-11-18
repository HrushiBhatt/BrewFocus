package io.github.hrushibhatt.brewfocus.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.hrushibhatt.brewfocus.support.TestProperties;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(TestProperties.withBcryptCost(4));

    @Test
    void theProductionCostIsTwelve() {
        assertThat(TestProperties.defaults().bcryptCost()).isEqualTo(12);
    }

    @Test
    void matchesOnlyTheRightPassword() {
        String hash = hasher.hash("plum lantern gravity orbit");
        assertThat(hash).startsWith("$2").doesNotContain("plum");
        assertThat(hasher.matches(hash, "plum lantern gravity orbit")).isTrue();
        assertThat(hasher.matches(hash, "plum lantern gravity orbi")).isFalse();
    }

    @Test
    void aMissingHashNeverMatches() {
        for (String hash : new String[] {null, ""}) {
            assertThat(hasher.matches(hash, "")).isFalse();
            assertThat(hasher.matches(hash, "anything")).isFalse();
        }
    }

    @Test
    void anOverlongAttemptIsAPlainMismatchNotAnError() {
        String hash = hasher.hash("plum lantern gravity orbit");
        assertThat(hasher.matches(hash, "x".repeat(500))).isFalse();
    }

    @Test
    void anUnknownAccountTakesAboutAsLongAsAWrongPassword() {
        PasswordHasher slow = new PasswordHasher(TestProperties.withBcryptCost(10));
        String hash = slow.hash("plum lantern gravity orbit");
        slow.matches(null, "warm up"); // the dummy hash is made on first use

        long wrong = median(() -> slow.matches(hash, "not it"));
        long unknown = median(() -> slow.matches(null, "not it"));
        // Same work factor, so within a generous factor of each other; the point is "not instant".
        assertThat(unknown).isGreaterThan(wrong / 3);
    }

    private static long median(Runnable work) {
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            work.run();
            times.add(System.nanoTime() - start);
        }
        times.sort(null);
        return times.get(2);
    }
}
