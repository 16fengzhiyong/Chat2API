package com.chat2api.backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> illegalArgument(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("BAD_REQUEST", error.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> exception(Exception error) {
        return ResponseEntity.internalServerError().body(ApiResponse.fail("INTERNAL_ERROR", error.getMessage()));
    }
}
