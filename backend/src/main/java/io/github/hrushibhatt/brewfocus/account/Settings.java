package io.github.hrushibhatt.brewfocus.account;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Pattern;

/**
 * A user's preferences, stored on the server so they follow them to every device.
 *
 * <p>Every field is nullable on the way in: a field missing from a request, or from JSON saved before
 * the field existed, takes its default rather than false. {@link #withDefaults()} fills them in.
 *
 * @param sound     an 8-bit chime when a block or break runs out
 * @param notifications a browser notification when time is up and the tab is in the background
 * @param autoBreak begin the break as soon as the reflection is saved
 * @param strict    log tab switches during focus as distractions
 * @param breathe   offer box breathing before a block
 * @param tick      the optional ticking clock: off, clock or ominous
 */
public record Settings(
        @JsonProperty("sound") Boolean sound,
        @JsonProperty("notify") Boolean notifications,
        @JsonProperty("autoBreak") Boolean autoBreak,
        @JsonProperty("strict") Boolean strict,
        @JsonProperty("breathe") Boolean breathe,
        @JsonProperty("tick")
        @Pattern(regexp = "off|clock|ominous", message = "%s must be one of: off clock ominous") String tick) {

    /** What a new account starts with. */
    public static final Settings DEFAULTS = new Settings(true, false, true, false, true, "off");

    /** These settings with every missing field set to its default. */
    public Settings withDefaults() {
        return new Settings(
                sound != null ? sound : DEFAULTS.sound,
                notifications != null ? notifications : DEFAULTS.notifications,
                autoBreak != null ? autoBreak : DEFAULTS.autoBreak,
                strict != null ? strict : DEFAULTS.strict,
                breathe != null ? breathe : DEFAULTS.breathe,
                tick != null ? tick : DEFAULTS.tick);
    }
}
