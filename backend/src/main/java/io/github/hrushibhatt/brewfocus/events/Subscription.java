package io.github.hrushibhatt.brewfocus.events;

import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * One connected client (a tab or device) of one user: a small queue of events waiting to be sent.
 *
 * <p>The queue holds at most {@link EventHub#BUFFER} events. A client that falls that far behind is
 * closed rather than waited for, so one slow connection can't hold up anyone else. Its browser
 * reconnects on its own and starts again from a fresh snapshot, so nothing is lost.
 */
public final class Subscription implements AutoCloseable {

    /** Marks the end of the stream; it always fits, because events never use the last slot. */
    static final LiveEvent CLOSED = new LiveEvent("closed", null);

    private final long userId;
    private final EventHub hub;
    private final LinkedBlockingQueue<LiveEvent> queue = new LinkedBlockingQueue<>(EventHub.BUFFER + 1);
    private boolean closed;

    Subscription(long userId, EventHub hub) {
        this.userId = userId;
        this.hub = hub;
    }

    public long userId() {
        return userId;
    }

    /** Queues an event, or closes the subscription if the client is too far behind to take it. */
    synchronized void deliver(LiveEvent event) {
        if (closed) {
            return;
        }
        if (queue.remainingCapacity() <= 1) {
            close();
            return;
        }
        queue.offer(event);
    }

    /**
     * The next event, waiting up to {@code timeout}. Returns null when nothing arrived in time, and
     * {@link #CLOSED} once the subscription has ended.
     */
    public LiveEvent next(Duration timeout) throws InterruptedException {
        return queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static boolean isEnd(LiveEvent event) {
        return event == CLOSED;
    }

    /** Ends the subscription. Safe to call more than once, from any thread. */
    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            queue.offer(CLOSED);
        }
        hub.remove(this);
    }
}
