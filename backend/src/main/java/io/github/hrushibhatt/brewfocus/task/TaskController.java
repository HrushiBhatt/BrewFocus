package io.github.hrushibhatt.brewfocus.task;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.hrushibhatt.brewfocus.events.EventHub;
import io.github.hrushibhatt.brewfocus.journal.FocusSessionRepository;
import io.github.hrushibhatt.brewfocus.security.SessionPrincipal;
import io.github.hrushibhatt.brewfocus.web.ApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** What the user is working on: add a task with an estimate of focus blocks, brew it, tick it off. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRepository tasks;
    private final FocusSessionRepository sessions;
    private final EventHub hub;

    public TaskController(TaskRepository tasks, FocusSessionRepository sessions, EventHub hub) {
        this.tasks = tasks;
        this.sessions = sessions;
        this.hub = hub;
    }

    /** A new task. The estimate defaults to one focus block. */
    record TaskInput(
            @JsonProperty("title")
            @NotEmpty(message = "%s is required")
            @Size(max = 120, message = "%s must be at most {max} characters") String title,
            @JsonProperty("estimate")
            @Min(value = 1, message = "%s must be at least {value}")
            @Max(value = 20, message = "%s must be at most {value}") Integer estimate) {}

    /** A change to a task: only the fields sent are touched. */
    record TaskPatch(
            @JsonProperty("title")
            @Size(max = 120, message = "%s must be at most {max} characters") String title,
            @JsonProperty("estimate")
            @Min(value = 1, message = "%s must be at least {value}")
            @Max(value = 20, message = "%s must be at most {value}") Integer estimate,
            @JsonProperty("done") Boolean done) {}

    /** Open tasks first, each with its totals from finished sessions. */
    @GetMapping
    List<TaskView> list(@AuthenticationPrincipal SessionPrincipal me) {
        Map<Long, FocusSessionRepository.TaskTotals> totals = sessions.totalsByTask(me.userId());
        return tasks.list(me.userId()).stream().map(task -> {
            var t = totals.get(task.id());
            return t == null ? TaskView.of(task, 0, 0) : TaskView.of(task, t.brews(), t.seconds());
        }).toList();
    }

    @PostMapping
    ResponseEntity<TaskView> create(@AuthenticationPrincipal SessionPrincipal me, @Validated @RequestBody TaskInput in) {
        String title = in.title().trim();
        if (title.isEmpty()) {
            throw ApiException.badRequest("title is required");
        }
        Task task = tasks.insert(me.userId(), title, in.estimate() == null ? 1 : in.estimate());
        hub.changed(me.userId(), "tasks");
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskView.of(task, 0, 0));
    }

    /** Renames, re-estimates or ticks off one of the user's tasks. */
    @PatchMapping("/{id}")
    Task update(@AuthenticationPrincipal SessionPrincipal me, @PathVariable("id") long id,
            @Validated @RequestBody TaskPatch in) {
        Task task = tasks.find(id, me.userId()).orElseThrow(ApiException::notFound);
        if (in.title() != null) {
            String title = in.title().trim();
            if (title.isEmpty()) {
                throw ApiException.badRequest("title is required");
            }
            task = task.withTitle(title);
        }
        if (in.estimate() != null) {
            task = task.withEstimate(in.estimate());
        }
        if (in.done() != null) {
            task = task.withDone(in.done());
        }
        tasks.update(me.userId(), task);
        hub.changed(me.userId(), "tasks");
        return task;
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal SessionPrincipal me, @PathVariable("id") long id) {
        if (!tasks.delete(id, me.userId())) {
            throw ApiException.notFound();
        }
        hub.changed(me.userId(), "tasks");
        return ResponseEntity.noContent().build();
    }
}
