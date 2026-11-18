package io.github.hrushibhatt.brewfocus.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes an error reply from a servlet filter, which runs before Spring MVC and its JSON converters.
 * The messages are fixed strings written by us, but they're escaped anyway so this can't produce
 * broken JSON.
 */
public final class JsonReplies {

    private JsonReplies() {}

    public static void write(HttpServletResponse response, HttpStatusCode status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + escape(message) + "\"}");
    }

    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
