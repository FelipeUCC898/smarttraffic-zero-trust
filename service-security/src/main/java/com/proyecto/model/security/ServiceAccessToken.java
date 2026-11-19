package com.proyecto.model.security;

import java.time.Instant;

public record ServiceAccessToken(String value, String principal, Instant issuedAt, Instant expiresAt) {}
