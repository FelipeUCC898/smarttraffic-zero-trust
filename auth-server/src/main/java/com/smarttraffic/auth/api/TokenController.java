package com.smarttraffic.auth.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/token")
public class TokenController {

    @PostMapping
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public TokenResponse issue(@Valid @RequestBody TokenRequest request) {
        return new TokenResponse("JWT implementation pending");
    }

    public record TokenRequest(@NotBlank String clientId, @NotBlank String clientSecret) {}

    public record TokenResponse(String message) {}
}
