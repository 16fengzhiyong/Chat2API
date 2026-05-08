package com.chat2api.backend.proxy;

public record ForwardResult(boolean success, int statusCode, String contentType, String body, String errorMessage) {
    public static ForwardResult ok(int statusCode, String contentType, String body) {
        return new ForwardResult(true, statusCode, contentType, body, null);
    }

    public static ForwardResult fail(int statusCode, String message) {
        return new ForwardResult(false, statusCode, "application/json", null, message);
    }
}
