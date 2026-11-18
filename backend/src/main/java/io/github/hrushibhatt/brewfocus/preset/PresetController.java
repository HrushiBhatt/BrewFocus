package io.github.hrushibhatt.brewfocus.preset;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.hrushibhatt.brewfocus.events.EventHub;
import io.github.hrushibhatt.brewfocus.security.SessionPrincipal;
import io.github.hrushibhatt.brewfocus.web.ApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The user's custom timing modes. Each change is announced to their other tabs and devices. */
@RestController
@RequestMapping("/api/presets")
public class PresetController {

    private final PresetRepository presets;
    private final EventHub hub;

    public PresetController(PresetRepository presets, EventHub hub) {
        this.presets = presets;
        this.hub = hub;
    }

    /** Boxed numbers, so a missing value is "required" rather than silently 0. */
    record PresetInput(
            @JsonProperty("name")
            @NotEmpty(message = "%s is required")
            @Size(max = 32, message = "%s must be at most {max} characters") String name,
            @JsonProperty("focus")
            @NotNull(message = "%s is required")
            @Min(value = 1, message = "%s must be at least {value}")
            @Max(value = 180, message = "%s must be at most {value}") Integer focus,
            @JsonProperty("rest")
            @NotNull(message = "%s is required")
            @Min(value = 0, message = "%s must be at least {value}")
            @Max(value = 60, message = "%s must be at most {value}") Integer rest) {}

    @GetMapping
    List<Preset> list(@AuthenticationPrincipal SessionPrincipal me) {
        return presets.list(me.userId());
    }

    @PostMapping
    ResponseEntity<Preset> create(@AuthenticationPrincipal SessionPrincipal me, @Validated @RequestBody PresetInput in) {
        String name = in.name().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("name is required");
        }
        Preset preset = presets.insert(me.userId(), name, in.focus(), in.rest());
        hub.changed(me.userId(), "presets");
        return ResponseEntity.status(HttpStatus.CREATED).body(preset);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal SessionPrincipal me, @PathVariable("id") long id) {
        if (!presets.delete(id, me.userId())) {
            throw ApiException.notFound();
        }
        hub.changed(me.userId(), "presets");
        return ResponseEntity.noContent().build();
    }
}
