package io.github.hrushibhatt.brewfocus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Brew Focus: a pomodoro timer and tracker whose timers run on the server, so a block keeps going
 * when the tab closes and every signed-in device shows the same clock.
 *
 * <p>One process serves the JSON API under {@code /api}, the live event stream, the OAuth 2.0
 * sign-in endpoints and, in production, the built React app. The browser sees a single origin, so
 * no cross-origin setup is needed. Packages are organized by feature:
 *
 * <ul>
 *   <li>{@code account}: sign-up, sign-in, passwords and settings
 *   <li>{@code security}: the filter chain, JWT sessions, rate limits, OAuth 2.0
 *   <li>{@code timer}: the server-run timers
 *   <li>{@code events}: live updates to every open tab
 *   <li>{@code preset}, {@code task}, {@code journal}: the user's data
 *   <li>{@code web}, {@code config}: errors, validation, static files and wiring
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BrewFocusApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrewFocusApplication.class, args);
    }
}
