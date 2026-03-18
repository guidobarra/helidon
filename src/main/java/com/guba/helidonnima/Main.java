package com.guba.helidonnima;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guba.helidonnima.handler.HealthCheckHandler;
import com.guba.helidonnima.handler.UserHandler;
import com.guba.helidonnima.repository.UserRepository;
import com.guba.helidonnima.service.UserService;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

import java.lang.management.ManagementFactory;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        Config config = Config.create();
        Services.set(Config.class, config);

        Config appConfig = config.get("app");
        String environment = appConfig.get("environment").asString().orElse("dev");
        String version = appConfig.get("version").asString().orElse("unknown");
        String serviceName = appConfig.get("name").asString().orElse("helidon-nima");

        DbClient dbClient = DbClient.create(config.get("db"));
        UserRepository userRepository = new UserRepository(dbClient);
        UserService userService = new UserService(userRepository);
        UserHandler userHandler = new UserHandler(userService);
        HealthCheckHandler healthCheckHandler = new HealthCheckHandler(environment, version, serviceName);

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

        MediaContext mediaContext = MediaContext.builder()
                .addMediaSupport(JacksonSupport.create(objectMapper))
                .build();

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .mediaContext(mediaContext)
                .routing(routing -> routing(routing, userHandler, healthCheckHandler))
                .build()
                .start();

        LOG.info("HTTP server started on port " + server.port()
                + " in " + ManagementFactory.getRuntimeMXBean().getUptime() + "ms");
    }

    static void routing(HttpRouting.Builder routing,
                        UserHandler userHandler,
                        HealthCheckHandler healthCheckHandler) {
        routing
               .register("/users", userHandler)
               .register("/health-check", healthCheckHandler);
    }
}