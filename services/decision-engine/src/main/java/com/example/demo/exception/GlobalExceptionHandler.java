package com.example.demo.exception;

import com.example.demo.model.EmploymentStatus;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException invalidFormat
                    && EmploymentStatus.class.equals(invalidFormat.getTargetType())) {
                Map<String, String> errors = new HashMap<>();
                errors.put(
                        "employmentStatus",
                        "Invalid employment status. Allowed values: EMPLOYED, UNEMPLOYED, SELF_EMPLOYED");
                return ResponseEntity.badRequest().body(errors);
            }

            if (cause instanceof IllegalArgumentException illegalArgument
                    && illegalArgument.getMessage() != null
                    && illegalArgument.getMessage().toLowerCase().contains("employment status")) {
                Map<String, String> errors = new HashMap<>();
                errors.put("employmentStatus", illegalArgument.getMessage());
                return ResponseEntity.badRequest().body(errors);
            }
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Malformed JSON request"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleEnumErrors(IllegalArgumentException ex) {
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("employment status")) {
            Map<String, String> errors = new HashMap<>();
            errors.put("employmentStatus", ex.getMessage());
            return ResponseEntity.badRequest().body(errors);
        }

        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
