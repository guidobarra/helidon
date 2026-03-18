package com.guba.helidonnima;

import com.guba.helidonnima.handler.HealthCheckHandler;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.CoreMatchers.containsString;

abstract class AbstractMainTest {
    private final Http1Client client;

    protected AbstractMainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.register("/health-check", new HealthCheckHandler("test", "1.0-TEST", "helidon-nima"));
    }

    @Test
    void testMetricsObserver() {
        try (Http1ClientResponse response = client.get("/observe/metrics").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    void testHealthCheck() {
        ClientResponseTyped<String> response = client.get("/health-check").request(String.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), containsString("\"status\":\"UP\""));
        assertThat(response.entity(), containsString("\"service_name\":\"helidon-nima\""));
    }
}
