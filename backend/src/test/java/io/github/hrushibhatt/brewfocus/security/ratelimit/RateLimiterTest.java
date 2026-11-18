package io.github.hrushibhatt.brewfocus.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.hrushibhatt.brewfocus.support.MutableClock;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T12:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1), clock);

    @Test
    void allowsUpToTheLimitThenRefusesWithTheWait() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.acquire("ip:a").allowed()).isTrue();
        }
        clock.advance(Duration.ofSeconds(20));
        RateLimiter.Decision refused = limiter.acquire("ip:a");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfter()).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void eachKeyHasItsOwnWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire("ip:a");
        }
        assertThat(limiter.acquire("ip:a").allowed()).isFalse();
        assertThat(limiter.acquire("ip:b").allowed()).isTrue();
    }

    @Test
    void aNewWindowStartsFresh() {
        for (int i = 0; i < 4; i++) {
            limiter.acquire("ip:a");
        }
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.acquire("ip:a").allowed()).isTrue();
    }

    @Test
    void sweepsExpiredKeysSoMemoryStaysBounded() {
        for (int i = 0; i <= 10_001; i++) {
            limiter.acquire("ip:" + i);
        }
        clock.advance(Duration.ofMinutes(2));
        assertThat(limiter.trackedKeys()).isGreaterThan(10_000);
        limiter.acquire("ip:new"); // over the threshold: expired windows are dropped first
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }
}
