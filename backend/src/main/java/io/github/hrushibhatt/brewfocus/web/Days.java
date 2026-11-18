package io.github.hrushibhatt.brewfocus.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** Calendar days as the API writes them: YYYY-MM-DD, in the user's own time zone. */
public final class Days {

    /** Strict ISO days: a four-digit year, two-digit month and day, and the day must exist. */
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private Days() {}

    /** The day {@code text} names, or null if it isn't a real YYYY-MM-DD day. */
    public static LocalDate parse(String text) {
        try {
            return text == null ? null : LocalDate.parse(text, FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
