package io.github.hrushibhatt.brewfocus.journal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.hrushibhatt.brewfocus.events.EventHub;
import io.github.hrushibhatt.brewfocus.security.SessionPrincipal;
import io.github.hrushibhatt.brewfocus.web.ApiException;
import io.github.hrushibhatt.brewfocus.web.Days;
import jakarta.validation.constraints.Size;

/**
 * The journal (every finished focus block, with an optional reflection) and the progress numbers
 * built from it.
 */
@RestController
@RequestMapping("/api")
public class JournalController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final FocusSessionRepository sessions;
    private final EventHub hub;
    private final Clock clock;

    public JournalController(FocusSessionRepository sessions, EventHub hub, Clock clock) {
        this.sessions = sessions;
        this.hub = hub;
        this.clock = clock;
    }

    record ReflectionInput(
            @JsonProperty("reflection")
            @Size(max = 280, message = "%s must be at most {max} characters") String reflection) {}

    /** The newest finished blocks, up to {@code ?limit=} (at most 100). */
    @GetMapping("/sessions")
    List<FocusSession> list(@AuthenticationPrincipal SessionPrincipal me,
            @RequestParam(name = "limit", required = false) String limit) {
        int n;
        try {
            n = limit == null ? DEFAULT_LIMIT : Integer.parseInt(limit);
        } catch (NumberFormatException e) {
            n = DEFAULT_LIMIT;
        }
        return sessions.listDone(me.userId(), Math.clamp(n, 1, MAX_LIMIT));
    }

    /** Saves the one-sentence note written after a block. */
    @PatchMapping("/sessions/{id}")
    FocusSession reflect(@AuthenticationPrincipal SessionPrincipal me, @PathVariable("id") long id,
            @Validated @RequestBody ReflectionInput in) {
        FocusSession session = sessions.findDone(id, me.userId()).orElseThrow(ApiException::notFound);
        String note = in.reflection() == null ? "" : in.reflection().trim();
        sessions.updateReflection(id, note);
        // Every open tab showed the reflection prompt; the others can close it now.
        hub.publish(me.userId(), "session.reflected", Map.of("id", id));
        return session.withReflection(note);
    }

    /** Deletes the whole history. Tasks and custom modes stay. */
    @DeleteMapping("/sessions")
    ResponseEntity<Void> clear(@AuthenticationPrincipal SessionPrincipal me) {
        sessions.deleteDone(me.userId());
        hub.changed(me.userId(), "sessions");
        return ResponseEntity.noContent().build();
    }

    /** The client passes {@code ?today=} so "today" means the user's local day, not the server's. */
    @GetMapping("/stats")
    Stats stats(@AuthenticationPrincipal SessionPrincipal me, @RequestParam(name = "today", required = false) String today) {
        LocalDate day = today == null ? LocalDate.now(clock) : Days.parse(today);
        if (day == null) {
            throw ApiException.badRequest("today must be a YYYY-MM-DD date");
        }
        return StatsCalculator.compute(sessions.totalsByDay(me.userId()), day);
    }
}
