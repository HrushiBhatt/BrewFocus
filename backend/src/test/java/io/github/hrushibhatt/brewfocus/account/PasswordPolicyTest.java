package io.github.hrushibhatt.brewfocus.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.hrushibhatt.brewfocus.web.ApiException;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"plum lantern gravity orbit", "correct horse battery", "Zk8#vQ2!mP9$xL4"})
    void acceptsLongUnguessablePasswords(String password) {
        assertThatCode(() -> policy.check(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "short                          | at least 12 characters",
            "password1234                   | too easy to guess",
            "brewfocus2026                  | too easy to guess",
            "111111111111                   | too easy to guess",
    })
    void refusesShortOrGuessablePasswords(String password, String reason) {
        assertThatThrownBy(() -> policy.check(password)).isInstanceOf(ApiException.class).hasMessageContaining(reason);
    }

    @Test
    void countsBytesAgainstBcryptsLimit() {
        // 72 characters, but 144 bytes: bcrypt would silently ignore half of it.
        assertThatThrownBy(() -> policy.check("é".repeat(72))).hasMessageContaining("at most 72 bytes");
        assertThatThrownBy(() -> policy.check("ab cd ".repeat(13))).hasMessageContaining("at most 72 bytes");
    }

    @Test
    void countsCharactersNotUtf16UnitsForTheMinimum() {
        // Six emoji are twelve UTF-16 units, but only six characters.
        assertThatThrownBy(() -> policy.check("☕🍵☕🍵☕🍵")).hasMessageContaining("at least 12");
    }

    @Test
    void refusesTheAccountsOwnDetails() {
        assertThatThrownBy(() -> policy.check("ada.lovelace@example.com", "ada.lovelace@example.com", "Ada Lovelace"))
                .hasMessageContaining("too easy to guess");
    }

    @Test
    void splitsPersonalDetailsIntoTheWordsAnAttackerWouldTry() {
        assertThat(PasswordPolicy.guessableWords("Ada.Lovelace@Example.com", "Ada Lovelace", null))
                .contains("brew", "focus", "brewfocus", "ada.lovelace@example.com", "ada", "lovelace", "example", "com");
    }
}
