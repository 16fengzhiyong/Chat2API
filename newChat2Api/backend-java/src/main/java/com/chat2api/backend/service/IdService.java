package com.chat2api.backend.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class IdService {
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String id(String prefix) {
        StringBuilder builder = new StringBuilder(prefix).append("-").append(Long.toString(Instant.now().toEpochMilli(), 36)).append("-");
        for (int i = 0; i < 10; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }

    public String secret(String prefix) {
        StringBuilder builder = new StringBuilder(prefix).append("_");
        for (int i = 0; i < 48; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
