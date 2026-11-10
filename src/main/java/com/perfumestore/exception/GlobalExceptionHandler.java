package com.perfumestore.exception;

import com.perfumestore.dto.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception,HttpServletRequest request) {

        return buildResponse(
                exception.getStatus(),
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            String message = error.getDefaultMessage();

            if (message == null) {
                message = "Invalid value";
            }

            fieldErrors.putIfAbsent(error.getField(), message);
        }

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class,
        ServletRequestBindingException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleInvalidRequest(Exception exception,HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameters or JSON body.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception,HttpServletRequest request) {

        String message = exception.getReason();

        if (message == null || message.isBlank()) {
            message = "The request could not be completed.";
        }

        return buildResponse(
                exception.getStatusCode(),
                message,
                request,
                Map.of(),
                exception.getHeaders()
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception,HttpServletRequest request) {

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception,HttpServletRequest request) {

        return buildResponse(
                HttpStatus.FORBIDDEN,
                "You are not allowed to perform this action.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict(DataIntegrityViolationException exception,HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "The request conflicts with existing data.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler({OptimisticLockingFailureException.class,PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleConcurrentUpdate(
            Exception exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "The data changed during this operation. Refresh and retry.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler({NoResourceFoundException.class,NoHandlerFoundException.class})
    public ResponseEntity<ApiError> handleMissingEndpoint(Exception exception,HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Endpoint not found.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception,HttpServletRequest request) {

        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not supported for this endpoint.",
                request,
                Map.of(),
                exception.getHeaders()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception,HttpServletRequest request) {

        return buildResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Use Content-Type: application/json for request bodies.",
                request,
                Map.of(),
                exception.getHeaders()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleUnacceptableMediaType(HttpMediaTypeNotAcceptableException exception,HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_ACCEPTABLE,
                "The requested response format is not supported.",
                request,
                Map.of(),
                exception.getHeaders()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedError(Exception exception,HttpServletRequest request) {

        log.error("Unexpected server error", exception);

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatusCode status,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {

        return buildResponse(
                status,
                message,
                request,
                fieldErrors,
                new HttpHeaders()
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatusCode status,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors,
            HttpHeaders headers) {

        HttpStatus knownStatus = HttpStatus.resolve(status.value());

        String errorName = "HTTP Error";

        if (knownStatus != null) {
            errorName = knownStatus.getReasonPhrase();
        }

        ApiError response = new ApiError(
                Instant.now(),
                status.value(),
                errorName,
                message,
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.status(status).headers(headers).body(response);
    }
}