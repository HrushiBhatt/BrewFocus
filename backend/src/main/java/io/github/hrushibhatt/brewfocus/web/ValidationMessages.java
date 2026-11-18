package io.github.hrushibhatt.brewfocus.web;

import java.lang.reflect.RecordComponent;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.validation.FieldError;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Turns Bean Validation failures into one sentence the UI can show, such as "title must be at most 120
 * characters".
 *
 * <p>Request records declare their messages next to each constraint, with {@code %s} standing for the
 * field's JSON name ({@code @Size(max = 120, message = "%s must be at most {max} characters")}).
 * When several fields fail, the first one in the record's declaration order is reported, and a
 * missing value is reported before anything else about the same field, so the message is stable.
 */
public final class ValidationMessages {

    private static final Set<String> REQUIRED = Set.of("NotNull", "NotEmpty", "NotBlank");

    private ValidationMessages() {}

    /** The message for the most relevant of {@code errors} on a request of type {@code target}. */
    public static String describe(Class<?> target, List<FieldError> errors) {
        if (errors.isEmpty()) {
            return "expected a JSON object";
        }
        FieldError first = errors.stream()
                .min(Comparator.comparingInt((FieldError e) -> position(target, e.getField()))
                        .thenComparing(e -> REQUIRED.contains(e.getCode()) ? 0 : 1))
                .orElseThrow();
        String template = first.getDefaultMessage() == null ? "%s is invalid" : first.getDefaultMessage();
        return template.contains("%s") ? template.formatted(jsonName(target, first.getField())) : template;
    }

    /** Where a field is declared in the request record, so errors come out in reading order. */
    private static int position(Class<?> target, String field) {
        RecordComponent[] components = target.getRecordComponents();
        if (components != null) {
            for (int i = 0; i < components.length; i++) {
                if (components[i].getName().equals(field)) {
                    return i;
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * The name the client sent: {@code task_id}, not {@code taskId}. {@code @JsonProperty} can't target
     * a record component, so Java places it on the component's field, which is where this looks.
     */
    static String jsonName(Class<?> target, String field) {
        try {
            JsonProperty json = target.getDeclaredField(field).getAnnotation(JsonProperty.class);
            return json != null && !json.value().isEmpty() ? json.value() : field;
        } catch (NoSuchFieldException e) {
            return field;
        }
    }
}
