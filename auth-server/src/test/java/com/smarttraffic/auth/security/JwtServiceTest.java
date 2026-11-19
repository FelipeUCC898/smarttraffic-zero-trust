package com.smarttraffic.auth.security;

import com.smarttraffic.auth.config.*;
import com.smarttraffic.auth.controller.*;
import com.smarttraffic.auth.model.*;
import com.smarttraffic.auth.service.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    @Test
    void issuesTokenWithClientAsSubject() {
        String secret = "VGhpc0lzQVN1ZmZpY2llbnRseUxvbmdTZWNyZXRLZXlGb3JUZXN0aW5n";
        JwtService service = new JwtService(secret, 900);

        String token = service.issue("traffic-simulator");

        assertTrue(token.split("\\.").length == 3);
    }
}
