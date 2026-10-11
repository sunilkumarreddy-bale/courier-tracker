package com.courier.tracker;

import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class ApiException extends RuntimeException {
  final int status;

  ApiException(int status, String message) {
    super(message);
    this.status = status;
  }
}

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> known(ApiException e) {
    return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    Map<String, String> fields = new TreeMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(Map.of("message", "Please check the form fields.", "fields", fields));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> badInput(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("message", "Invalid request value or format."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> duplicate(Exception e) {
    return ResponseEntity.status(409)
        .body(Map.of("message", "A record with these details already exists."));
  }

  @ExceptionHandler({
    OptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409)
        .body(Map.of("message", "This parcel changed. Refresh and try again."));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> forbidden(Exception e) {
    return ResponseEntity.status(403).body(Map.of("message", "You cannot perform this action."));
  }
}
