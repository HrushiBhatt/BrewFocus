package io.github.hrushibhatt.brewfocus.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventHubTest {

    private static final Duration SOON = Duration.ofMillis(200);
    private final EventHub hub = new EventHub();

    @BeforeEach
    void start() {
        hub.start();
    }

    @AfterEach
    void stop() {
        hub.stop();
    }

    @Test
    void deliversToEveryTabOfThatUserOnly() throws Exception {
        Subscription phone = hub.subscribe(1);
        Subscription laptop = hub.subscribe(1);
        Subscription stranger = hub.subscribe(2);

        hub.changed(1, "tasks");

        assertThat(phone.next(SOON).data()).isEqualTo(Map.of("kind", "tasks"));
        assertThat(laptop.next(SOON).type()).isEqualTo("changed");
        assertThat(stranger.next(SOON)).isNull();
    }

    @Test
    void aClientThatFallsTooFarBehindIsDroppedNotWaitedFor() throws Exception {
        Subscription slow = hub.subscribe(1);
        Subscription fine = hub.subscribe(1);
        for (int i = 0; i < EventHub.BUFFER + 5; i++) {
            hub.publish(1, "timer", i);
            fine.next(SOON); // this one keeps up
        }
        // The slow one got its buffer's worth, then the end of its stream.
        int received = 0;
        LiveEvent event;
        while (!Subscription.isEnd(event = slow.next(SOON))) {
            assertThat(event).isNotNull();
            received++;
        }
        assertThat(received).isEqualTo(EventHub.BUFFER);
        assertThat(hub.connections()).isEqualTo(1);
    }

    @Test
    void stoppingEndsEveryStream() throws Exception {
        Subscription tab = hub.subscribe(1);
        hub.stop();
        assertThat(Subscription.isEnd(tab.next(SOON))).isTrue();
        assertThat(hub.connections()).isZero();
        assertThat(Subscription.isEnd(hub.subscribe(1).next(SOON))).isTrue(); // nothing new once stopped
    }

    @Test
    void closingIsIdempotent() {
        Subscription tab = hub.subscribe(1);
        tab.close();
        tab.close();
        assertThat(hub.connections()).isZero();
    }
}
