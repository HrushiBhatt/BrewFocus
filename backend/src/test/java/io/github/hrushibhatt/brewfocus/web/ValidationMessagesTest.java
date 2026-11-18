package io.github.hrushibhatt.brewfocus.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.validation.FieldError;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

class ValidationMessagesTest {

    record Sample(
            @JsonProperty("title")
            @NotEmpty(message = "%s is required")
            @Size(max = 5, message = "%s must be at most {max} characters") String title,
            @JsonProperty("task_id")
            @Min(value = 1, message = "%s must be at least {value}")
            @Max(value = 9, message = "%s must be at most {value}") Integer taskId,
            @JsonProperty("day") @IsoDate String day) {}

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private String describe(Sample sample) {
        Set<ConstraintViolation<Sample>> violations = validator.validate(sample);
        List<FieldError> errors = violations.stream()
                .map(v -> new FieldError("sample", v.getPropertyPath().toString(), v.getInvalidValue(), false,
                        new String[] {v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()},
                        null, v.getMessage()))
                .toList();
        return ValidationMessages.describe(Sample.class, errors);
    }

    @Test
    void reportsTheFirstFieldInDeclarationOrderByItsJsonName() {
        assertThat(describe(new Sample("too long", 0, "2026-02-30"))).isEqualTo("title must be at most 5 characters");
        assertThat(describe(new Sample("ok", 0, "2026-02-30"))).isEqualTo("task_id must be at least 1");
        assertThat(describe(new Sample("ok", 3, "2026-02-30"))).isEqualTo("day must be a YYYY-MM-DD date");
    }

    @Test
    void aMissingValueIsReportedBeforeAnythingElseAboutIt() {
        assertThat(describe(new Sample("", 3, null))).isEqualTo("title is required");
    }

    @Test
    void realDaysOnly() {
        assertThat(Days.parse("2026-11-18")).isNotNull();
        assertThat(Days.parse("2026-2-1")).isNull();
        assertThat(Days.parse("2026-13-01")).isNull();
        assertThat(Days.parse("+2026-11-18")).isNull();
        assertThat(Days.parse("tomorrow")).isNull();
    }

    @Test
    void escapesFilterRepliesIntoValidJson() {
        assertThat(JsonReplies.escape("a \"quote\" and \\ and \n")).isEqualTo("a \\\"quote\\\" and \\\\ and \\u000a");
    }
}
