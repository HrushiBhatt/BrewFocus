package io.github.hrushibhatt.brewfocus.account;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.nulabinc.zxcvbn.Zxcvbn;

import io.github.hrushibhatt.brewfocus.web.ApiException;

/**
 * The rules for a new password, following NIST SP 800-63B: length and unguessability rather than
 * composition rules. A few unrelated words beat "P@ssw0rd!", and are easier to remember.
 *
 * <p>Existing passwords aren't re-checked at sign-in, so tightening a rule never locks anyone out.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    /** bcrypt's limit. It counts bytes, so an accented or emoji password reaches it in fewer characters. */
    public static final int MAX_BYTES = 72;
    /**
     * zxcvbn's "safely unguessable" band: roughly 10^8 guesses or more. Rate limits make that hopeless
     * online, and at bcrypt cost 12 it is about a year of one CPU core per password offline.
     */
    static final int MIN_SCORE = 3;

    /** Words an attacker would try first against any account here. */
    private static final List<String> SITE_WORDS = List.of("brew", "focus", "brewfocus");

    private final Zxcvbn zxcvbn = new Zxcvbn();

    /**
     * Throws a 400 with a readable reason when {@code password} is too weak. {@code personal} are this
     * account's own details, such as its email and name, which make poor passwords.
     */
    public void check(String password, String... personal) {
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw ApiException.badRequest("password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw ApiException.badRequest(
                    "password must be at most " + MAX_BYTES + " bytes (accents and emoji count as more than one)");
        }
        if (zxcvbn.measure(password, guessableWords(personal)).getScore() < MIN_SCORE) {
            throw ApiException.badRequest("that password is too easy to guess: a few unrelated words work well");
        }
    }

    /** The site's name plus each detail whole and in parts: "ada.lovelace@example.com" gives "ada" too. */
    static List<String> guessableWords(String... personal) {
        List<String> words = new ArrayList<>(SITE_WORDS);
        for (String detail : personal) {
            if (detail == null || detail.isBlank()) {
                continue;
            }
            String lower = detail.toLowerCase(Locale.ROOT);
            words.add(lower);
            for (String part : lower.split("[@. ]+")) {
                if (!part.isEmpty()) {
                    words.add(part);
                }
            }
        }
        return words;
    }
}
