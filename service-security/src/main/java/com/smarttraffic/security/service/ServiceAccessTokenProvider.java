package com.smarttraffic.security.service;

import com.smarttraffic.security.model.ServiceAccessToken;

@FunctionalInterface
public interface ServiceAccessTokenProvider {
    ServiceAccessToken obtainToken();
}
