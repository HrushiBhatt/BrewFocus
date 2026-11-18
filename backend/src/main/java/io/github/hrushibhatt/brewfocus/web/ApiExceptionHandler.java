package io.github.hrushibhatt.brewfocus.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.DatabindException;

/**
 * Every failure inside a controller becomes the same {@code {"error": "..."}} shape, with a message
 * the UI can show. Nothing internal (stack traces, SQL, class names) reaches the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) {
        return ErrorBody.reply(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        Class<?> target = e.getParameter().getParameterType();
        return ErrorBody.reply(HttpStatus.BAD_REQUEST,
                ValidationMessages.describe(target, e.getBindingResult().getFieldErrors()));
    }

    /** A body that isn't JSON, has a field of the wrong type, or is over the size limit. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof RequestSizeLimitFilter.TooLargeException) {
                return ErrorBody.reply(HttpStatus.CONTENT_TOO_LARGE, RequestSizeLimitFilter.MESSAGE);
            }
            if (cause instanceof DatabindException mapping && !mapping.getPath().isEmpty()) {
                String field = mapping.getPath().getLast().getPropertyName();
                if (field != null) {
                    return ErrorBody.reply(HttpStatus.BAD_REQUEST, field + " has the wrong type");
                }
            }
        }
        return ErrorBody.reply(HttpStatus.BAD_REQUEST, "expected a JSON object");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> notJson() {
        return ErrorBody.reply(HttpStatus.BAD_REQUEST, "expected a JSON object");
    }

    /**
     * Unknown paths, a malformed ID ({@code /api/tasks/abc}) and the wrong method on a known path are
     * all simply things that don't exist.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class,
            MethodArgumentTypeMismatchException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<Map<String, Object>> notFound() {
        return ErrorBody.reply(HttpStatus.NOT_FOUND, "not found");
    }

    /** Anything unexpected: logged here with its stack, and a plain 500 for the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e, HttpServletRequest request) {
        // The path only: a query string could carry something sensitive.
        log.error("unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return ErrorBody.reply(HttpStatus.INTERNAL_SERVER_ERROR, ErrorBody.SERVER_ERROR);
    }
}
