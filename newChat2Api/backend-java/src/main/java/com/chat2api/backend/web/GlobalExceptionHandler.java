package com.chat2api.backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> illegalArgument(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("BAD_REQUEST", error.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> responseStatus(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(ApiResponse.fail("HTTP_ERROR", error.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> exception(Exception error) {
        return ResponseEntity.internalServerError().body(ApiResponse.fail("INTERNAL_ERROR", error.getMessage()));
    }
}
