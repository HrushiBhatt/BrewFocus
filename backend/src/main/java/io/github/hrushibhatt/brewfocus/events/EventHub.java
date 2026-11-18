package io.github.hrushibhatt.brewfocus.events;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Fans events out to each user's connected clients (tabs and devices), which is what keeps a timer in
 * sync everywhere.
 *
 * <p>Design decisions:
 *
 * <ul>
 *   <li>Server-Sent Events, not WebSockets. Clients only need to listen, since commands go over
 *       ordinary HTTP requests, and SSE is plain HTTP that browsers reconnect to on their own.
 *   <li>{@link #publish} never blocks: it drops an event into each subscriber's queue and returns, so
 *       a timer expiring or a request finishing never waits on someone's slow network.
 *   <li>On shutdown the hub closes every stream before the web server stops ({@link #getPhase}), so
 *       a graceful shutdown isn't held up by connections that would otherwise stay open for hours.
 * </ul>
 */
@Component
public class EventHub implements SmartLifecycle {

    /** How many undelivered events a client may fall behind by before it's disconnected. */
    public static final int BUFFER = 16;

    private final Map<Long, Set<Subscription>> subscribers = new ConcurrentHashMap<>();
    private volatile boolean running;

    /** Registers a client for {@code userId}'s events. Close it when the client disconnects. */
    public Subscription subscribe(long userId) {
        Subscription subscription = new Subscription(userId, this);
        if (!running) {
            subscription.close();
            return subscription;
        }
        subscribers.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(subscription);
        return subscription;
    }

    /** Sends an event to every client of {@code userId}. */
    public void publish(long userId, LiveEvent event) {
        Set<Subscription> set = subscribers.get(userId);
        if (set != null) {
            for (Subscription subscription : set) {
                subscription.deliver(event);
            }
        }
    }

    public void publish(long userId, String type, Object data) {
        publish(userId, new LiveEvent(type, data));
    }

    /** Tells the user's other tabs and devices to fetch a kind of data again, such as "tasks". */
    public void changed(long userId, String kind) {
        publish(userId, "changed", Map.of("kind", kind));
    }

    /** How many clients are connected, across all users. */
    public int connections() {
        return subscribers.values().stream().mapToInt(Set::size).sum();
    }

    void remove(Subscription subscription) {
        subscribers.computeIfPresent(subscription.userId(), (id, set) -> {
            set.remove(subscription);
            return set.isEmpty() ? null : set;
        });
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        subscribers.values().forEach(set -> set.forEach(Subscription::close));
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** The highest phase stops first: before the web server's graceful shutdown waits on requests. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
