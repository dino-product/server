package com.orbit.auth.application.port.in.command.dto;

import java.time.Instant;

public record LoginNonceInfo(String nonce, Instant expiresAt) {}
