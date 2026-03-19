# Helidon SE 4.x Configuration Reference

Complete guide for Helidon Config system, profiles, and observability setup.

## Table of Contents

1. [Config Basics](#config-basics)
2. [Config Profiles](#config-profiles)
3. [Accessing Config Values](#accessing-config-values)
4. [Maven Resource Filtering](#maven-resource-filtering)
5. [Server Configuration](#server-configuration)
6. [Observability](#observability)
7. [OpenAPI](#openapi)
8. [Logging](#logging)

---

## Config Basics

Helidon automatically loads `application.yaml` (or `.conf`, `.json`, `.properties`) from the classpath:

```java
Config config = Config.create();
```

Default `application.yaml`:

```yaml
server:
  port: 9292
  host: 0.0.0.0

app:
  name: my-service
  version: 1.0.0
  environment: dev

db:
  source: jdbc
  connection:
    url: jdbc:postgresql://localhost:5432/mydb
    username: helidon
    password: helidon123
    poolName: helidonPool
    maximumPoolSize: 10
```

## Config Profiles

Helidon supports config profiles via `application-{profile}.yaml` files. The profile file overrides values from the base `application.yaml`.

### Profile file example (`application-beta.yaml`)

```yaml
app:
  environment: beta

db:
  connection:
    url: jdbc:postgresql://postgres:5432/mydb
    maximumPoolSize: 30
```

### Activating a profile

```bash
# Via system property
java -Dconfig.profile=beta -jar app.jar

# Via environment variable
HELIDON_CONFIG_PROFILE=beta ./my-app

# In Docker
ENTRYPOINT ["./app", "-Dconfig.profile=prod"]
```

### Dockerfile pattern with profile

```dockerfile
ENTRYPOINT ["/bin/bash", "-c", \
    "exec ./app ${HELIDON_CONFIG_PROFILE:+-Dconfig.profile=$HELIDON_CONFIG_PROFILE}"]
```

### Profile loading order

1. `application.yaml` — base config (always loaded)
2. `application-{profile}.yaml` — profile overrides (merged on top)
3. System properties (`-Dkey=value`) — highest priority
4. Environment variables

## Accessing Config Values

```java
Config config = Config.create();

// Navigate to a sub-tree
Config appConfig = config.get("app");

// Get typed values with defaults
String env = appConfig.get("environment").asString().orElse("dev");
int port = config.get("server.port").asInt().orElse(8080);
boolean debug = config.get("app.debug").asBoolean().orElse(false);

// Pass sub-tree to components
DbClient dbClient = DbClient.create(config.get("db"));
```

### Registering Config globally

```java
import io.helidon.service.registry.Services;

Config config = Config.create();
Services.set(Config.class, config);
```

## Maven Resource Filtering

Inject Maven project properties into `application.yaml`:

```xml
<!-- pom.xml -->
<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
            <filtering>true</filtering>
        </resource>
    </resources>
</build>
```

```yaml
# application.yaml
app:
  name: '@project.artifactId@'
  version: '@project.version@'
```

At build time, Maven replaces `@project.artifactId@` and `@project.version@` with actual values.

## Server Configuration

```yaml
server:
  port: 9292
  host: 0.0.0.0
```

```java
WebServer server = WebServer.builder()
        .config(config.get("server"))
        .routing(routing -> ...)
        .build()
        .start();

System.out.println("Started on port " + server.port());
```

### Random port (for testing)

```yaml
server:
  port: 0
```

## Observability

### Health checks

```xml
<dependency>
    <groupId>io.helidon.webserver.observe</groupId>
    <artifactId>helidon-webserver-observe-health</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.health</groupId>
    <artifactId>helidon-health-checks</artifactId>
</dependency>
```

Built-in health is available at `/observe/health` when the observe feature is on the classpath.

### Custom health handler

```java
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

    private void healthCheck(ServerRequest req, ServerResponse res) {
        res.send(Map.of(
                "status", "UP",
                "service_name", serviceName,
                "version", version,
                "environment", environment
        ));
    }
}
```

### Metrics

```xml
<dependency>
    <groupId>io.helidon.webserver.observe</groupId>
    <artifactId>helidon-webserver-observe-metrics</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.metrics</groupId>
    <artifactId>helidon-metrics-system-meters</artifactId>
    <scope>runtime</scope>
</dependency>
```

Metrics are exposed at `/observe/metrics` automatically.

## OpenAPI

```xml
<dependency>
    <groupId>io.helidon.openapi</groupId>
    <artifactId>helidon-openapi</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.integrations.openapi-ui</groupId>
    <artifactId>helidon-integrations-openapi-ui</artifactId>
</dependency>
<dependency>
    <groupId>io.smallrye</groupId>
    <artifactId>smallrye-open-api-ui</artifactId>
    <scope>runtime</scope>
</dependency>
```

Place your OpenAPI spec at `src/main/resources/META-INF/openapi.yaml`. The UI is available at `/openapi/ui`.

## Logging

Helidon SE uses `java.util.logging` by default:

```xml
<dependency>
    <groupId>io.helidon.logging</groupId>
    <artifactId>helidon-logging-jul</artifactId>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-jdk14</artifactId>
</dependency>
```

Configure via `logging.properties` in resources or programmatically:

```java
private static final Logger LOG = Logger.getLogger(Main.class.getName());
LOG.info("Server started on port " + server.port());
```
