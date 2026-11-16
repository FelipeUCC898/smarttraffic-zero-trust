package com.smarttraffic.security;

@FunctionalInterface
public interface ServiceAccessTokenProvider {
    ServiceAccessToken obtainToken();
}
