package io.github.hrushibhatt.brewfocus.web;

import java.util.Map;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Errors that happen outside a controller (in a filter, or in Tomcat itself) are forwarded to
 * {@code /error}. This answers them in the same JSON shape as everything else, instead of Spring
 * Boot's default page, which would include details such as the exception's class.
 */
@RestController
public class JsonErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer n ? HttpStatus.resolve(n) : null;
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = switch (status) {
            case NOT_FOUND, METHOD_NOT_ALLOWED -> "not found";
            case CONTENT_TOO_LARGE -> RequestSizeLimitFilter.MESSAGE;
            case BAD_REQUEST -> "the request couldn't be read";
            case UNAUTHORIZED -> "please log in";
            case FORBIDDEN -> "you can't do that";
            default -> status.is5xxServerError() ? ErrorBody.SERVER_ERROR : status.getReasonPhrase().toLowerCase();
        };
        return ErrorBody.reply(status == HttpStatus.METHOD_NOT_ALLOWED ? HttpStatus.NOT_FOUND : status, message);
    }
}
