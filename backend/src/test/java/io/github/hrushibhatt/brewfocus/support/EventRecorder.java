package io.github.hrushibhatt.brewfocus.support;

import java.time.Duration;
import java.time.Instant;

import io.github.hrushibhatt.brewfocus.events.EventHub;
import io.github.hrushibhatt.brewfocus.events.LiveEvent;
import io.github.hrushibhatt.brewfocus.events.Subscription;

/** Listens to one user's events on a real {@link EventHub}, as a connected tab would. */
public final class EventRecorder implements AutoCloseable {

    private final Subscription subscription;

    public EventRecorder(EventHub hub, long userId) {
        this.subscription = hub.subscribe(userId);
    }

    /** The first event of {@code type}, skipping others, or an AssertionError after {@code within}. */
    public LiveEvent await(String type, Duration within) throws InterruptedException {
        Instant deadline = Instant.now().plus(within);
        while (Instant.now().isBefore(deadline)) {
            LiveEvent event = subscription.next(Duration.between(Instant.now(), deadline));
            if (event != null && !Subscription.isEnd(event) && event.type().equals(type)) {
                return event;
            }
        }
        throw new AssertionError("no " + type + " event within " + within);
    }

    /** True if an event of {@code type} arrives within {@code within}. */
    public boolean sees(String type, Duration within) throws InterruptedException {
        try {
            await(type, within);
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    @Override
    public void close() {
        subscription.close();
    }
}
