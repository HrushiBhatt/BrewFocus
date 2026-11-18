package io.github.hrushibhatt.brewfocus.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * A real calendar day written as YYYY-MM-DD: "2026-02-30" fails as well as "tomorrow". The client
 * sends its own local day, so streaks follow the user's midnight rather than the server's. Null
 * passes; pair it with {@code @NotEmpty} when the day is required.
 */
@Documented
@Constraint(validatedBy = IsoDate.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IsoDate {

    String message() default "%s must be a YYYY-MM-DD date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<IsoDate, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Days.parse(value) != null;
        }
    }
}
