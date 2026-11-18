package io.github.hrushibhatt.brewfocus.journal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.hrushibhatt.brewfocus.support.Browser;
import io.github.hrushibhatt.brewfocus.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/** Custom modes, tasks, the journal and the progress numbers, over real HTTP. */
class UserDataIT extends IntegrationTest {

    @Test
    void presetsAreValidatedAndListedInOrder() {
        Browser ada = signedIn();
        record Case(Map<String, Object> body, String error) {}
        for (Case c : new Case[] {
                new Case(Map.of("focus", 30, "rest", 5), "name is required"),
                new Case(Map.of("name", "   ", "focus", 30, "rest", 5), "name is required"),
                new Case(Map.of("name", "x".repeat(33), "focus", 30, "rest", 5), "name must be at most 32 characters"),
                new Case(Map.of("name", "Long", "rest", 5), "focus is required"),
                new Case(Map.of("name", "Long", "focus", 181, "rest", 5), "focus must be at most 180"),
                new Case(Map.of("name", "Long", "focus", 30, "rest", -1), "rest must be at least 0"),
                new Case(Map.of("name", "Long", "focus", "thirty", "rest", 5), "focus has the wrong type"),
        }) {
            var reply = ada.post("/api/presets", c.body());
            assertThat(reply.status()).as(c.error()).isEqualTo(400);
            assertThat(reply.error()).isEqualTo(c.error());
        }
        var made = ada.post("/api/presets", Map.of("name", "  Thesis  ", "focus", 75, "rest", 0));
        assertThat(made.status()).isEqualTo(201);
        assertThat(made.json().path("name").asString()).isEqualTo("Thesis");
        ada.post("/api/presets", Map.of("name", "Sprint", "focus", 15, "rest", 3));

        JsonNode list = ada.get("/api/presets").json();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("name").asString()).isEqualTo("Thesis");
        assertThat(list.get(0).has("user_id")).isFalse();

        assertThat(ada.delete("/api/presets/" + list.get(0).path("id").asLong()).status()).isEqualTo(204);
        assertThat(ada.get("/api/presets").json()).hasSize(1);
    }

    @Test
    void tasksTrackTheFocusTimeLoggedAgainstThem() {
        Browser ada = signedIn();
        long me = ada.get("/api/me").json().path("id").asLong();
        long report = ada.post("/api/tasks", Map.of("title", "Write the report", "estimate", 3)).json().path("id").asLong();
        long other = ada.post("/api/tasks", Map.of("title", "Reply to email")).json().path("id").asLong();

        logSession(me, report, "2026-11-18", 1500, true);
        logSession(me, report, "2026-11-18", 600, false); // ended early: minutes, but not a brew

        JsonNode tasks = ada.get("/api/tasks").json();
        assertThat(tasks.get(0).path("brews").asInt()).isEqualTo(1);
        assertThat(tasks.get(0).path("minutes").asInt()).isEqualTo(35);
        assertThat(tasks.get(1).path("estimate").asInt()).isEqualTo(1); // the default

        // Finished tasks move to the end; open ones stay first.
        assertThat(ada.patch("/api/tasks/" + report, Map.of("done", true, "title", " Report ")).json().path("title").asString())
                .isEqualTo("Report");
        assertThat(ada.get("/api/tasks").json().get(0).path("id").asLong()).isEqualTo(other);

        assertThat(ada.patch("/api/tasks/" + report, Map.of("title", " ")).error()).isEqualTo("title is required");
        assertThat(ada.patch("/api/tasks/" + report, Map.of("estimate", 0)).error()).isEqualTo("estimate must be at least 1");
        assertThat(ada.post("/api/tasks", Map.of("title", "x".repeat(121))).error()).isEqualTo("title must be at most 120 characters");

        assertThat(ada.delete("/api/tasks/" + report).status()).isEqualTo(204);
        assertThat(ada.delete("/api/tasks/" + report).status()).isEqualTo(404);
        // The time logged against a deleted task still counts in the journal.
        assertThat(ada.get("/api/sessions").json()).hasSize(2);
    }

    @Test
    void theJournalListsNewestFirstAndKeepsReflections() {
        Browser ada = signedIn();
        long me = ada.get("/api/me").json().path("id").asLong();
        long first = logSession(me, null, "2026-11-17", 1500, true);
        long second = logSession(me, null, "2026-11-18", 1500, true);

        JsonNode journal = ada.get("/api/sessions").json();
        assertThat(journal.get(0).path("id").asLong()).isEqualTo(second);
        assertThat(ada.get("/api/sessions?limit=1").json()).hasSize(1);
        assertThat(ada.get("/api/sessions?limit=nonsense").json()).hasSize(2);

        var saved = ada.patch("/api/sessions/" + first, Map.of("reflection", "  Outlined the intro  "));
        assertThat(saved.json().path("reflection").asString()).isEqualTo("Outlined the intro");
        assertThat(ada.get("/api/sessions").json().get(1).path("reflection").asString()).isEqualTo("Outlined the intro");
        assertThat(ada.patch("/api/sessions/" + first, Map.of("reflection", "x".repeat(281))).status()).isEqualTo(400);

        assertThat(ada.delete("/api/sessions").status()).isEqualTo(204);
        assertThat(ada.get("/api/sessions").json()).isEmpty();
    }

    @Test
    void statsCountStreaksAndFillTheHeatmapForThisUserOnly() {
        Browser ada = signedIn();
        Browser other = signedIn();
        long me = ada.get("/api/me").json().path("id").asLong();
        long them = other.get("/api/me").json().path("id").asLong();
        LocalDate today = LocalDate.parse("2026-11-18");

        logSession(me, null, today.toString(), 1500, true);
        logSession(me, null, today.toString(), 900, false);
        logSession(me, null, today.minusDays(1).toString(), 1500, true);
        logSession(me, null, today.minusDays(3).toString(), 3000, true);
        logSession(them, null, today.minusDays(2).toString(), 1500, true); // must not bridge ada's gap

        JsonNode stats = ada.get("/api/stats?today=" + today).json();
        assertThat(stats.path("today").path("minutes").asInt()).isEqualTo(40);
        assertThat(stats.path("today").path("sessions").asInt()).isEqualTo(2);
        assertThat(stats.path("streak").asInt()).isEqualTo(2);
        assertThat(stats.path("best_streak").asInt()).isEqualTo(2);
        assertThat(stats.path("total").path("completed").asInt()).isEqualTo(3);
        assertThat(stats.path("heatmap").path(today.minusDays(3).toString()).asInt()).isEqualTo(50);

        // A streak survives until the end of today, and breaks after a missed day.
        assertThat(ada.get("/api/stats?today=" + today.plusDays(1)).json().path("streak").asInt()).isEqualTo(2);
        assertThat(ada.get("/api/stats?today=" + today.plusDays(2)).json().path("streak").asInt()).isZero();

        assertThat(ada.get("/api/stats?today=yesterday").error()).isEqualTo("today must be a YYYY-MM-DD date");
    }
}
