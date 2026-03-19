# Helidon SE 4.x Testing Reference

Complete guide for testing Helidon SE applications with JUnit 5.

## Table of Contents

1. [Maven Dependencies](#maven-dependencies)
2. [RoutingTest (Unit Tests)](#routingtest-unit-tests)
3. [ServerTest (Integration Tests)](#servertest-integration-tests)
4. [SetUpRoute](#setuproute)
5. [Client Usage](#client-usage)
6. [Testing Patterns](#testing-patterns)
7. [Integration Tests with Maven](#integration-tests-with-maven)

---

## Maven Dependencies

```xml
<!-- Direct client for unit tests -->
<dependency>
    <groupId>io.helidon.webserver.testing.junit5</groupId>
    <artifactId>helidon-webserver-testing-junit5</artifactId>
    <scope>test</scope>
</dependency>

<!-- HTTP client for integration tests -->
<dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-api</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.hamcrest</groupId>
    <artifactId>hamcrest-all</artifactId>
    <scope>test</scope>
</dependency>
```

## RoutingTest (Unit Tests)

`@RoutingTest` invokes routing directly without opening sockets. Fast and ideal for unit testing handlers.

```java
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;

@RoutingTest
class UserHandlerTest {
    private final Http1Client client;

    UserHandlerTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        // Wire up the handler with dependencies
        builder.register("/users", new UserHandler(new UserService(mockRepo)));
    }

    @Test
    void testFindAll() {
        ClientResponseTyped<String> response = client.get("/users").request(String.class);
        assertThat(response.status(), is(Status.OK_200));
    }
}
```

## ServerTest (Integration Tests)

`@ServerTest` starts a real server on a random port. Use for full integration tests:

```java
import io.helidon.webserver.testing.junit5.ServerTest;

@ServerTest
class UserHandlerIT {
    private final Http1Client client;

    UserHandlerIT(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.register("/users", new UserHandler(new UserService(realRepo)));
    }

    @Test
    void testCreateUser() {
        try (Http1ClientResponse response = client.post("/users")
                .submit("{\"name\":\"Alice\",\"email\":\"alice@example.com\"}")) {
            assertThat(response.status(), is(Status.CREATED_201));
        }
    }
}
```

## SetUpRoute

`@SetUpRoute` is a static method annotation that configures the routing for the test server:

```java
@SetUpRoute
static void routing(HttpRouting.Builder builder) {
    builder.register("/health-check",
            new HealthCheckHandler("test", "1.0-TEST", "helidon-nima"));
    builder.register("/users", userHandler);
}
```

## Client Usage

### GET request — typed response

```java
ClientResponseTyped<String> response = client.get("/users").request(String.class);
assertThat(response.status(), is(Status.OK_200));
assertThat(response.entity(), containsString("\"name\""));
```

### GET request — raw response (auto-closeable)

```java
try (Http1ClientResponse response = client.get("/users").request()) {
    assertThat(response.status(), is(Status.OK_200));
}
```

### POST request with JSON body

```java
try (Http1ClientResponse response = client.post("/users")
        .submit("{\"name\":\"Bob\",\"email\":\"bob@example.com\"}")) {
    assertThat(response.status(), is(Status.CREATED_201));
}
```

### PUT request

```java
try (Http1ClientResponse response = client.put("/users/1")
        .submit("{\"name\":\"Updated\",\"email\":\"updated@example.com\"}")) {
    assertThat(response.status(), is(Status.OK_200));
}
```

### DELETE request

```java
try (Http1ClientResponse response = client.delete("/users/1").request()) {
    assertThat(response.status(), is(Status.NO_CONTENT_204));
}
```

### Checking headers

```java
try (Http1ClientResponse response = client.get("/users").request()) {
    assertThat(response.headers().get(HeaderNames.CONTENT_TYPE).get(),
            is("application/json"));
}
```

## Testing Patterns

### Abstract base test class

Share routing and tests between `@RoutingTest` and `@ServerTest`:

```java
abstract class AbstractMainTest {
    private final Http1Client client;

    protected AbstractMainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.register("/health-check",
                new HealthCheckHandler("test", "1.0-TEST", "helidon-nima"));
    }

    @Test
    void testHealthCheck() {
        ClientResponseTyped<String> response = client.get("/health-check")
                .request(String.class);
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), containsString("\"status\":\"UP\""));
    }
}

// Unit test
@RoutingTest
class MainTest extends AbstractMainTest {
    MainTest(DirectClient client) {
        super(client);
    }
}

// Integration test
@ServerTest
class MainIT extends AbstractMainTest {
    MainIT(Http1Client client) {
        super(client);
    }
}
```

### Testing metrics endpoint

```java
@Test
void testMetricsObserver() {
    try (Http1ClientResponse response = client.get("/observe/metrics").request()) {
        assertThat(response.status(), is(Status.OK_200));
    }
}
```

### Testing 404 responses

```java
@Test
void testNotFound() {
    try (Http1ClientResponse response = client.get("/users/99999").request()) {
        assertThat(response.status(), is(Status.NOT_FOUND_404));
    }
}
```

## Integration Tests with Maven

By convention, classes ending in `*IT.java` run during the `verify` phase:

```bash
mvn test       # Unit tests (*Test.java) — @RoutingTest
mvn verify     # Integration tests (*IT.java) — @ServerTest
```

The `maven-failsafe-plugin` (included in the `helidon-se` parent) handles `*IT.java` execution during the `integration-test` phase.
