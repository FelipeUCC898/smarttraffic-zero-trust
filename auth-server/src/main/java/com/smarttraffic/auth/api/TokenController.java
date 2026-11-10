package com.smarttraffic.auth.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.smarttraffic.auth.security.JwtService;

@RestController
@RequestMapping("/token")
public class TokenController {
    private final JwtService jwtService;

    public TokenController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public TokenResponse issue(@Valid @RequestBody TokenRequest request) {
        return new TokenResponse(jwtService.issue(request.clientId()));
    }

    public record TokenRequest(@NotBlank String clientId, @NotBlank String clientSecret) {}

    public record TokenResponse(String accessToken) {}
}
