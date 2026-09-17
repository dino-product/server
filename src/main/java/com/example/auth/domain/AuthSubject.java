package com.example.auth.domain;

public record AuthSubject(Long userId) {
    public AuthSubject {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    public String value() {
        return "user:" + userId;
    }
}
