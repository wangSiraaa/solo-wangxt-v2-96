package com.teaching.match.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.getStatus()).body(body(ex.getStatus(), ex.getCode(), ex.getMessage(), req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("validation failed");
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, req));
    }

    private Map<String, Object> body(HttpStatus status, String code, String message, HttpServletRequest req) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", OffsetDateTime.now());
        map.put("status", status.value());
        map.put("error", code);
        map.put("message", message);
        map.put("path", req.getRequestURI());
        return map;
    }
}
