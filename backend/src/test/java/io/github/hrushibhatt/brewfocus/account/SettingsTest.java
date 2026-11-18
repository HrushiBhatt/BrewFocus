package io.github.hrushibhatt.brewfocus.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SettingsTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void newAccountsGetTheDefaults() {
        assertThat(Settings.DEFAULTS).isEqualTo(new Settings(true, false, true, false, true, "off"));
    }

    @Test
    void missingFieldsTakeTheirDefaultsNotFalse() {
        // Saved before "breathe" and "tick" existed.
        Settings old = json.readValue("{\"sound\":false,\"strict\":true}", Settings.class).withDefaults();
        assertThat(old).isEqualTo(new Settings(false, false, true, true, true, "off"));
    }

    @Test
    void usesTheApisFieldNames() {
        String written = json.writeValueAsString(Settings.DEFAULTS);
        assertThat(written).contains("\"notify\":false", "\"autoBreak\":true", "\"tick\":\"off\"");
    }
}
