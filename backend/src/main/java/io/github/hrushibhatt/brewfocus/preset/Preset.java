package io.github.hrushibhatt.brewfocus.preset;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A saved custom timing mode, such as "Thesis 75/15".
 *
 * @param focus minutes of focus
 * @param rest  minutes of break; 0 means no break
 */
public record Preset(
        @JsonProperty("id") long id,
        @JsonProperty("name") String name,
        @JsonProperty("focus") int focus,
        @JsonProperty("rest") int rest) {}
