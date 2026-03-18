package com.guba.helidonnima.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record HealthResponse(
        String status,
        String timestamp,
        String environment,
        String version,
        @JsonProperty("service_name") String serviceName
) {

    public static HealthResponse up(String environment, String version, String serviceName) {
        return new HealthResponse("UP", Instant.now().toString(), environment, version, serviceName);
    }
}
