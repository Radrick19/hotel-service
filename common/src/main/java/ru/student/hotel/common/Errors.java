package ru.student.hotel.common;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class Errors {
  public static ErrorDto body(int status, String message) {
    return new ErrorDto(Instant.now(), status, message, MDC.get("traceId"));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorDto> api(ApiException e) {
    return ResponseEntity.status(e.status()).body(body(e.status().value(), e.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    org.springframework.web.bind.ServletRequestBindingException.class
  })
  ResponseEntity<ErrorDto> validation(Exception e) {
    return ResponseEntity.badRequest()
        .body(body(400, "Invalid request: check required fields and date range"));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ErrorDto> notFound(Exception e) {
    return ResponseEntity.status(404).body(body(404, "Resource not found"));
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorDto> method(Exception e) {
    return ResponseEntity.status(405).body(body(405, "Method not allowed"));
  }

  @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
  ResponseEntity<ErrorDto> locked(Exception e) {
    return ResponseEntity.status(409).body(body(409, "Concurrent operation; retry request"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorDto> duplicate(Exception e) {
    return ResponseEntity.status(409).body(body(409, "Operation conflicts with existing data"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorDto> forbidden(Exception e) {
    return ResponseEntity.status(403).body(body(403, "Access denied"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorDto> unexpected(Exception e) {
    LoggerFactory.getLogger(Errors.class).error("event=unhandled_error", e);
    return ResponseEntity.internalServerError().body(body(500, "Internal error"));
  }
}
