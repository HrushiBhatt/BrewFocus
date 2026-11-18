package io.github.hrushibhatt.brewfocus.events;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.github.hrushibhatt.brewfocus.config.BrewFocusProperties;
import io.github.hrushibhatt.brewfocus.security.SessionPrincipal;
import io.github.hrushibhatt.brewfocus.security.SessionService;
import io.github.hrushibhatt.brewfocus.timer.TimerEngine;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/events}: the user's live updates as Server-Sent Events.
 *
 * <p>Each stream opens with a snapshot of the timer, so a client that (re)connects is in sync at once.
 * Then one virtual thread per stream moves events from the user's {@link Subscription} to the
 * connection. Every keepalive it also re-checks the session, so a stream opened before "sign out
 * everywhere" can't keep receiving the user's events.
 */
@RestController
public class EventStreamController {

    private final EventHub hub;
    private final TimerEngine timers;
    private final SessionService sessions;
    private final JsonMapper json;
    private final Duration keepalive;

    public EventStreamController(EventHub hub, TimerEngine timers, SessionService sessions, JsonMapper json,
            BrewFocusProperties props) {
        this.hub = hub;
        this.timers = timers;
        this.sessions = sessions;
        this.json = json;
        this.keepalive = props.keepalive();
    }

    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@AuthenticationPrincipal SessionPrincipal me, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no"); // a reverse proxy mustn't buffer the stream

        SseEmitter emitter = new SseEmitter(0L); // no timeout: streams stay open for as long as the tab does
        Subscription subscription = hub.subscribe(me.userId()); // before the snapshot, so nothing is missed
        emitter.onCompletion(subscription::close);
        emitter.onTimeout(subscription::close);
        emitter.onError(e -> subscription.close());
        Thread.ofVirtual().name("events-" + me.userId()).start(() -> pump(me, subscription, emitter));
        return emitter;
    }

    /** Sends the snapshot, then every event, until the client goes, the session ends or the hub stops. */
    private void pump(SessionPrincipal me, Subscription subscription, SseEmitter emitter) {
        try {
            send(emitter, new LiveEvent("timer", timers.current(me.userId())));
            while (true) {
                LiveEvent event = subscription.next(keepalive);
                if (Subscription.isEnd(event)) {
                    break; // dropped as too slow, or the server is shutting down
                }
                if (event == null) {
                    if (!sessions.isActive(me.sessionId())) {
                        break; // signed out since this stream opened
                    }
                    emitter.send(SseEmitter.event().comment("keepalive"));
                    continue;
                }
                send(emitter, event);
            }
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            // The client went away mid-send: nothing to tell it.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            subscription.close();
        }
    }

    /** Events are serialized here, so one with no data still arrives as valid JSON: {@code null}. */
    private void send(SseEmitter emitter, LiveEvent event) throws IOException {
        emitter.send(SseEmitter.event().name(event.type()).data(json.writeValueAsString(event.data())));
    }
}
