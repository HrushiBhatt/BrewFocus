package io.github.hrushibhatt.brewfocus.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows {@code limit} requests per {@code window} for each key, in fixed windows held in memory.
 *
 * <p>In memory is right for one process. Several replicas would need a shared store such as Redis,
 * and this class is the only thing that would change.
 */
public final class RateLimiter {

    /** Above this many keys, expired windows are swept so the map can't grow without bound. */
    private static final int SWEEP_ABOVE = 10_000;

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();

    public RateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /** One key's requests in its current window. */
    private static final class Bucket {
        final Instant start;
        int count = 1;

        Bucket(Instant start) {
            this.start = start;
        }
    }

    /** The outcome of a request: allowed, or refused with the time until its window resets. */
    public record Decision(boolean allowed, Duration retryAfter) {
        static final Decision ALLOW = new Decision(true, Duration.ZERO);
    }

    /** Records a request for {@code key} and reports whether it's within the limit. */
    public synchronized Decision acquire(String key) {
        Instant now = clock.instant();
        if (buckets.size() > SWEEP_ABOVE) {
            buckets.values().removeIf(b -> expired(b, now));
        }
        Bucket bucket = buckets.get(key);
        if (bucket == null || expired(bucket, now)) {
            buckets.put(key, new Bucket(now));
            return Decision.ALLOW;
        }
        if (bucket.count >= limit) {
            return new Decision(false, Duration.between(now, bucket.start.plus(window)));
        }
        bucket.count++;
        return Decision.ALLOW;
    }

    /** How many keys are being tracked. */
    synchronized int trackedKeys() {
        return buckets.size();
    }

    private boolean expired(Bucket bucket, Instant now) {
        return !now.isBefore(bucket.start.plus(window));
    }
}
