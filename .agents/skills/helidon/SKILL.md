---
name: helidon
description: Develop Helidon SE 4.x applications with Nima (virtual threads). Use when building REST services, configuring DbClient, routing, config profiles, observability, or working with Helidon SE APIs. Triggers include "helidon", "helidon SE", "helidon nima", "HttpService", "HttpRules", "DbClient", "helidon config", "helidon webserver", "helidon routing", "helidon test".
---

# Helidon SE 4.x with Nima (Virtual Threads)

Expert skill for building high-performance microservices with Helidon SE 4.x, which runs on virtual threads (Project Loom) via the Nima web server — no reactive APIs needed.

## Overview

Helidon SE 4.x (codename Nima) replaces the Netty-based reactive web server with a virtual-thread-based blocking model. This means you write straightforward synchronous code while getting the concurrency benefits of virtual threads. No `CompletionStage`, no `Mono`/`Flux` — just plain Java.

Key characteristics:
- **Blocking I/O on virtual threads** — simple, debuggable code with massive concurrency
- **No dependency injection framework** — wire dependencies manually in `Main`
- **DbClient over JPA** — lightweight, GraalVM-compatible database access
- **Config profiles** — `application.yaml` + `application-{profile}.yaml`
- **Native image ready** — designed for GraalVM from day one

## When to Use

Use this skill when:
- Creating a new Helidon SE 4.x REST service or endpoint
- Implementing `HttpService` with routing rules
- Configuring `DbClient` for PostgreSQL/MySQL JDBC access
- Setting up `application.yaml` config and profiles
- Writing tests with `@RoutingTest`, `@ServerTest`, and `DirectClient`
- Adding health checks, metrics, or OpenAPI
- Structuring the project (handler → service → repository layers)
- Building GraalVM native images of Helidon apps

## Instructions

### 1. Project Structure

Follow a layered architecture without DI — wire dependencies manually in `Main`:

```
src/main/java/
├── Main.java                    # WebServer bootstrap, wiring, routing
├── handler/                     # HttpService implementations (REST layer)
│   └── UserHandler.java
├── service/                     # Business logic
│   └── UserService.java
├── repository/                  # DbClient data access
│   └── UserRepository.java
└── model/                       # Records or POJOs with fromRow()
    └── User.java

src/main/resources/
├── application.yaml             # Default config
└── application-{profile}.yaml   # Profile overrides (beta, prod)
```

### 2. WebServer and Routing

See the [Routing Reference](references/routing.md) for complete patterns.

The entry point bootstraps config, creates dependencies, and starts the web server:

```java
public class Main {
    public static void main(String[] args) {
        Config config = Config.create();

        DbClient dbClient = DbClient.create(config.get("db"));
        UserRepository userRepository = new UserRepository(dbClient);
        UserService userService = new UserService(userRepository);
        UserHandler userHandler = new UserHandler(userService);

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(routing -> routing
                        .register("/users", userHandler)
                )
                .build()
                .start();
    }
}
```

Handlers implement `HttpService` and define routes via `HttpRules`:

```java
public class UserHandler implements HttpService {
    private final UserService userService;

    public UserHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void routing(HttpRules rules) {
        rules
            .get("/", this::findAll)
            .get("/{id}", this::findById)
            .post("/", this::create)
            .put("/{id}", this::update)
            .delete("/{id}", this::delete);
    }

    private void findAll(ServerRequest req, ServerResponse res) {
        res.send(userService.findAll());
    }

    private void findById(ServerRequest req, ServerResponse res) {
        long id = Long.parseLong(req.path().pathParameters().get("id"));
        userService.findById(id)
                .ifPresentOrElse(
                        res::send,
                        () -> res.status(Status.NOT_FOUND_404).send()
                );
    }

    private void create(ServerRequest req, ServerResponse res) {
        User user = req.content().as(User.class);
        User saved = userService.save(user);
        res.status(Status.CREATED_201).send(saved);
    }
}
```

### 3. DbClient (Database Access)

See the [DbClient Reference](references/dbclient.md) for complete patterns.

Helidon DbClient provides a blocking API on virtual threads — no reactive streams:

```java
public class UserRepository {
    private final DbClient dbClient;

    public UserRepository(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public List<User> findAll() {
        return dbClient.execute()
                .query("SELECT * FROM users ORDER BY id")
                .map(User::fromRow)
                .toList();
    }

    public Optional<User> findById(long id) {
        return dbClient.execute()
                .get("SELECT * FROM users WHERE id = ?", id)
                .map(User::fromRow);
    }

    public long save(User user) {
        return dbClient.execute()
                .insert("INSERT INTO users (name, email) VALUES (?, ?)",
                        user.name(), user.email());
    }
}
```

Model classes map rows with a static `fromRow(DbRow)` method:

```java
public static User fromRow(DbRow row) {
    return new User(
            row.column("id").asLong().get(),
            row.column("name").asString().get(),
            row.column("email").asString().get()
    );
}
```

### 4. Configuration

See the [Config Reference](references/config.md) for complete patterns.

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
    username: user
    password: secret
    poolName: helidonPool
    maximumPoolSize: 10
```

Config profiles override values per environment. Use `application-beta.yaml`, `application-prod.yaml`, etc. Activate with:

```bash
java -Dconfig.profile=beta -jar app.jar
```

### 5. Testing

See the [Testing Reference](references/testing.md) for complete patterns.

Helidon provides two test modes:

- **`@RoutingTest`** — direct invocation without sockets (unit test, fast)
- **`@ServerTest`** — real server on random port (integration test)

```java
@RoutingTest
class UserHandlerTest {
    private final Http1Client client;

    UserHandlerTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.register("/users", new UserHandler(new UserService(mockRepo)));
    }

    @Test
    void testGetUsers() {
        ClientResponseTyped<String> response = client.get("/users").request(String.class);
        assertThat(response.status(), is(Status.OK_200));
    }
}
```

### 6. Maven Setup

Use the `helidon-se` parent BOM — it manages all Helidon dependency versions:

```xml
<parent>
    <groupId>io.helidon.applications</groupId>
    <artifactId>helidon-se</artifactId>
    <version>4.4.0</version>
    <relativePath/>
</parent>
```

Core dependencies (versions managed by parent):

```xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-yaml</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.http.media</groupId>
    <artifactId>helidon-http-media-jackson</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.dbclient</groupId>
    <artifactId>helidon-dbclient</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.dbclient</groupId>
    <artifactId>helidon-dbclient-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.dbclient</groupId>
    <artifactId>helidon-dbclient-hikari</artifactId>
</dependency>
```

### 7. GraalVM Native Image

Helidon SE 4.x parent POM includes a `native-image` profile. Build with:

```bash
mvn package -Pnative-image -DskipTests
./target/my-app
```

For Docker multi-stage builds, see the project's `Dockerfile.native`.

## Best Practices

1. **Use blocking code** — Helidon 4 runs on virtual threads; no need for reactive APIs
2. **No DI framework** — wire dependencies manually in `Main.java`
3. **DbClient over JPA** — lighter, GraalVM-compatible, no reflection-heavy ORM
4. **Records for DTOs** — immutable, concise, serialization-friendly
5. **`fromRow(DbRow)` pattern** — static factory method on model classes for row mapping
6. **Config profiles** — use `application-{profile}.yaml` for environment-specific config
7. **ReentrantLock over synchronized** — avoid virtual thread pinning
8. **Test with `@RoutingTest`** — fast, no socket overhead
9. **Layered architecture** — handler → service → repository, wired in `Main`
10. **Jackson for JSON** — use `helidon-http-media-jackson` for automatic serialization

## Constraints

- **Helidon SE has no CDI/injection** — all wiring is manual
- **DbClient is not an ORM** — you write SQL and map rows manually
- **Config profiles** are file-based overrides, not Spring-style `@Profile` annotations
- **Helidon 4.x requires Java 21+** for virtual threads
- **Native image** may need reflection config for Jackson-serialized classes

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Port already in use | Change `server.port` in `application.yaml` or use `-Dserver.port=0` for random port |
| DbClient connection fails | Verify `db.connection.url`, credentials, and that the database is running |
| Jackson serialization error | Ensure model has default constructor and getters, or use `@JsonProperty` |
| Native image reflection error | Add class to `reflect-config.json` in `META-INF/native-image/` |
| Config profile not loading | Verify file is named `application-{profile}.yaml` and `-Dconfig.profile=` is set |
| Virtual thread pinning | Replace `synchronized` blocks with `ReentrantLock` |
| Test client 404 | Check `@SetUpRoute` registers the same path prefix as the handler |
