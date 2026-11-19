package com.smarttraffic.security.model;

import java.time.Instant;

public record ServiceAccessToken(String value, String principal, Instant issuedAt, Instant expiresAt) {}
