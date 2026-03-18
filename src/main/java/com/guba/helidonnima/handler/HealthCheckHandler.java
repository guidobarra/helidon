package com.guba.helidonnima.handler;

import com.guba.helidonnima.model.HealthResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

public class HealthCheckHandler implements HttpService {

    private final String environment;
    private final String version;
    private final String serviceName;

    public HealthCheckHandler(String environment, String version, String serviceName) {
        this.environment = environment;
        this.version = version;
        this.serviceName = serviceName;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::healthCheck);
    }

    private void healthCheck(ServerRequest request, ServerResponse response) {
        response.send(HealthResponse.up(environment, version, serviceName));
    }
}
