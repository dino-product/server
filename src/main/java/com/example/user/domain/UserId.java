package com.example.user.domain;

public record UserId(Long value) {

    public UserId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }
}
