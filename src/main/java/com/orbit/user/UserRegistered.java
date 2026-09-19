package com.orbit.user;

import java.time.Instant;

public record UserRegistered(Long userId, Instant occurredAt) {}
