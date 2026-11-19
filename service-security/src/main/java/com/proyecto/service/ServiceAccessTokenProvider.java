package com.proyecto.service;

import com.proyecto.model.ServiceAccessToken;

@FunctionalInterface
public interface ServiceAccessTokenProvider {
    ServiceAccessToken obtainToken();
}
