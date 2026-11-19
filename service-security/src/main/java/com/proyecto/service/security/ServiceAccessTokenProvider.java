package com.proyecto.service.security;

import com.proyecto.model.security.ServiceAccessToken;

@FunctionalInterface
public interface ServiceAccessTokenProvider {
    ServiceAccessToken obtainToken();
}
