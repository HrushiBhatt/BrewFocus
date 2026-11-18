package io.github.hrushibhatt.brewfocus.timer;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.hrushibhatt.brewfocus.security.SessionPrincipal;
import io.github.hrushibhatt.brewfocus.task.Task;
import io.github.hrushibhatt.brewfocus.task.TaskRepository;
import io.github.hrushibhatt.brewfocus.web.ApiException;
import io.github.hrushibhatt.brewfocus.web.IsoDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The timer over HTTP. {@link TimerEngine} owns all timer state; these handlers only check the request
 * and pass it on, and the event stream carries what happens back to every open tab.
 */
@RestController
@RequestMapping("/api/timer")
public class TimerController {

    private final TimerEngine engine;
    private final TaskRepository tasks;

    public TimerController(TimerEngine engine, TaskRepository tasks) {
        this.engine = engine;
        this.tasks = tasks;
    }

    record FocusInput(
            @JsonProperty("mode")
            @NotEmpty(message = "%s is required")
            @Size(max = 32, message = "%s must be at most {max} characters") String mode,
            @JsonProperty("planned")
            @NotNull(message = "%s is required")
            @Min(value = 60, message = "%s must be at least {value}")
            @Max(value = 10_800, message = "%s must be at most {value}") Integer planned,
            @JsonProperty("intention")
            @Size(max = 140, message = "%s must be at most {max} characters") String intention,
            @JsonProperty("task_id") Long taskId,
            @JsonProperty("day")
            @NotEmpty(message = "%s is required")
            @IsoDate String day) {}

    record BreakInput(
            @JsonProperty("length")
            @NotNull(message = "%s is required")
            @Min(value = 60, message = "%s must be at least {value}")
            @Max(value = 3_600, message = "%s must be at most {value}") Integer length) {}

    @GetMapping
    TimerState current(@AuthenticationPrincipal SessionPrincipal me) {
        return engine.current(me.userId());
    }

    /** Starts a focus block. A block for a task takes the task's title when no intention is given. */
    @PostMapping("/focus")
    TimerState focus(@AuthenticationPrincipal SessionPrincipal me, @Validated @RequestBody FocusInput in) {
        String intention = in.intention() == null ? "" : in.intention().trim();
        if (in.taskId() != null) {
            Task task = tasks.find(in.taskId(), me.userId()).orElseThrow(ApiException::notFound);
            if (intention.isEmpty()) {
                intention = task.title();
            }
        }
        var request = new FocusRequest(in.mode().trim(), Duration.ofSeconds(in.planned()), intention, in.taskId(), in.day());
        return engine.startFocus(me.userId(), request);
    }

    @PostMapping("/break")
    TimerState rest(@AuthenticationPrincipal SessionPrincipal me, @Validated @RequestBody BreakInput in) {
        return engine.startBreak(me.userId(), Duration.ofSeconds(in.length()));
    }

    /** pause, resume, finish, cancel or distraction. Anything else isn't a route. */
    @PostMapping("/{action}")
    TimerState control(@AuthenticationPrincipal SessionPrincipal me, @PathVariable("action") String action) {
        TimerAction parsed = TimerAction.parse(action).orElseThrow(ApiException::notFound);
        return engine.control(me.userId(), parsed);
    }

    /** 409 with the timer's real state, so an out-of-date client can catch up from the reply. */
    @ExceptionHandler(TimerConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(TimerConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage(), "timer", e.state()));
    }
}
