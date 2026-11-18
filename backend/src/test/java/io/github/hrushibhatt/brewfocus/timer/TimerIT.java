package io.github.hrushibhatt.brewfocus.timer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.hrushibhatt.brewfocus.support.Browser;
import io.github.hrushibhatt.brewfocus.support.EventStream;
import io.github.hrushibhatt.brewfocus.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/** The server-run timer over HTTP, and the live stream that keeps every tab in step. */
class TimerIT extends IntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    private static Map<String, Object> focus(Object taskId) {
        var body = new HashMap<String, Object>(Map.of("mode", "Pomodoro", "planned", 1500, "intention", "",
                "day", "2026-11-18"));
        body.put("task_id", taskId);
        return body;
    }

    @Test
    void aBlockRunsPausesResumesAndIsAbandoned() {
        Browser ada = signedIn();
        long task = ada.post("/api/tasks", Map.of("title", "Write the report")).json().path("id").asLong();

        assertThat(ada.get("/api/timer").json().path("status").asString()).isEqualTo("idle");
        JsonNode running = ada.post("/api/timer/focus", focus(task)).json();
        assertThat(running.path("status").asString()).isEqualTo("running");
        assertThat(running.path("intention").asString()).isEqualTo("Write the report"); // the task's title by default
        assertThat(running.path("duration_ms").asLong()).isEqualTo(1_500_000);
        assertThat(running.has("ends_at")).isTrue();

        var busy = ada.post("/api/timer/break", Map.of("length", 300));
        assertThat(busy.status()).isEqualTo(409);
        assertThat(busy.json().path("timer").path("status").asString()).isEqualTo("running");

        JsonNode paused = ada.post("/api/timer/pause", null).json();
        assertThat(paused.path("status").asString()).isEqualTo("paused");
        assertThat(paused.has("ends_at")).isFalse();
        assertThat(ada.post("/api/timer/pause", null).status()).isEqualTo(409);
        assertThat(ada.post("/api/timer/resume", null).json().path("status").asString()).isEqualTo("running");

        // Under a minute, so finishing early logs nothing.
        assertThat(ada.post("/api/timer/finish", null).json().path("status").asString()).isEqualTo("idle");
        assertThat(ada.get("/api/sessions").json()).isEmpty();
        assertThat(ada.post("/api/timer/cancel", null).error()).isEqualTo("no timer is running");
    }

    @Test
    void startingABlockIsValidated() {
        Browser ada = signedIn();
        assertThat(ada.post("/api/timer/focus", Map.of("planned", 1500, "day", "2026-11-18")).error()).isEqualTo("mode is required");
        assertThat(ada.post("/api/timer/focus", Map.of("mode", "Pomodoro", "planned", 30, "day", "2026-11-18")).error())
                .isEqualTo("planned must be at least 60");
        assertThat(ada.post("/api/timer/focus", Map.of("mode", "Pomodoro", "planned", 1500, "day", "18/11/2026")).error())
                .isEqualTo("day must be a YYYY-MM-DD date");
        assertThat(ada.post("/api/timer/focus", focus(999_999)).status()).isEqualTo(404);
        assertThat(ada.post("/api/timer/break", Map.of("length", 4000)).error()).isEqualTo("length must be at most 3600");
    }

    /** The stream opens with a snapshot, then pushes changes made from any other tab or device. */
    @Test
    void theEventStreamKeepsEveryDeviceInStep() throws Exception {
        Browser phone = signedIn();
        Browser laptop = phone.sameCookies();
        try (EventStream stream = new EventStream(phone)) {
            assertThat(stream.status()).isEqualTo(200);
            assertThat(stream.await("timer", WAIT).data().path("status").asString()).isEqualTo("idle");

            laptop.post("/api/timer/focus", focus(null));
            assertThat(stream.await("timer", WAIT).data().path("status").asString()).isEqualTo("running");

            laptop.post("/api/tasks", Map.of("title", "From the laptop"));
            assertThat(stream.await("changed", WAIT).data().path("kind").asString()).isEqualTo("tasks");

            // An event with no data still arrives as JSON (null), or the page would drop it.
            laptop.post("/api/timer/finish", null);
            assertThat(stream.await("session.discarded", WAIT).data().isNull()).isTrue();
        }
    }

    @Test
    void aStreamEndsOnceItsSessionIsRevoked() throws Exception {
        Browser ada = signedIn();
        try (EventStream stream = new EventStream(ada)) {
            stream.await("timer", WAIT);
            ada.sameCookies().post("/api/auth/logout-all", null);
            // The next keepalive notices (300 ms in tests) and closes it.
            assertThat(stream.endsWithin(WAIT)).isTrue();
        }
    }

    @Test
    void reflectionsCloseThePromptOnEveryTab() throws Exception {
        Browser ada = signedIn();
        long me = ada.get("/api/me").json().path("id").asLong();
        long session = logSession(me, null, "2026-11-18", 1500, true);
        try (EventStream stream = new EventStream(ada.sameCookies())) {
            stream.await("timer", WAIT);
            ada.patch("/api/sessions/" + session, Map.of("reflection", "Done"));
            assertThat(stream.await("session.reflected", WAIT).data().path("id").asLong()).isEqualTo(session);
        }
    }
}
