package com.orbit.auth.application.port.in.query.dto;

import java.time.Instant;

public record AccountInfo(Long accountId, Instant registeredAt) {}
