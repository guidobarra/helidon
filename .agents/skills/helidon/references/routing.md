# Helidon SE 4.x Routing Reference

Complete guide for WebServer configuration, HttpService implementation, and routing patterns in Helidon SE 4.x (Nima).

## Table of Contents

1. [WebServer Bootstrap](#webserver-bootstrap)
2. [HttpService Interface](#httpservice-interface)
3. [Route Definitions](#route-definitions)
4. [Path Parameters](#path-parameters)
5. [Query Parameters](#query-parameters)
6. [Request Body](#request-body)
7. [Response Patterns](#response-patterns)
8. [Error Handling](#error-handling)
9. [Nested Services](#nested-services)
10. [Filters](#filters)

---

## WebServer Bootstrap

The `Main` class creates the config, wires dependencies, and starts the server:

```java
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

public class Main {
    public static void main(String[] args) {
        Config config = Config.create();

        DbClient dbClient = DbClient.create(config.get("db"));
        UserRepository userRepo = new UserRepository(dbClient);
        UserService userService = new UserService(userRepo);
        UserHandler userHandler = new UserHandler(userService);

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(routing -> routing
                        .register("/users", userHandler)
                        .register("/health-check", healthCheckHandler)
                )
                .build()
                .start();

        System.out.println("Server started on port " + server.port());
    }
}
```

### Routing with a separate method

For readability, extract routing into a static method:

```java
static void routing(HttpRouting.Builder routing,
                    UserHandler userHandler,
                    HealthCheckHandler healthCheckHandler) {
    routing
            .register("/users", userHandler)
            .register("/health-check", healthCheckHandler);
}
```

Then use it as:

```java
WebServer.builder()
        .config(config.get("server"))
        .routing(routing -> routing(routing, userHandler, healthCheckHandler))
        .build()
        .start();
```

## HttpService Interface

Every handler implements `HttpService` and defines routes in the `routing(HttpRules)` method:

```java
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

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
}
```

Handler methods signature is always `(ServerRequest, ServerResponse) -> void`.

## Route Definitions

`HttpRules` supports all standard HTTP methods:

```java
rules.get("/path", handler);       // GET
rules.post("/path", handler);      // POST
rules.put("/path", handler);       // PUT
rules.delete("/path", handler);    // DELETE
rules.patch("/path", handler);     // PATCH
rules.head("/path", handler);      // HEAD
rules.options("/path", handler);   // OPTIONS
rules.any("/path", handler);       // Any method
```

## Path Parameters

Access path parameters via `req.path().pathParameters()`:

```java
private void findById(ServerRequest req, ServerResponse res) {
    long id = Long.parseLong(req.path().pathParameters().get("id"));
    userService.findById(id)
            .ifPresentOrElse(
                    res::send,
                    () -> res.status(Status.NOT_FOUND_404).send()
            );
}
```

## Query Parameters

Access query parameters via `req.query()`:

```java
private void search(ServerRequest req, ServerResponse res) {
    String name = req.query().get("name");
    int page = req.query().all("page", Integer.class).stream()
            .findFirst().orElse(1);
    int size = req.query().all("size", Integer.class).stream()
            .findFirst().orElse(20);

    res.send(userService.search(name, page, size));
}
```

## Request Body

Deserialize JSON body using Jackson (requires `helidon-http-media-jackson`):

```java
private void create(ServerRequest req, ServerResponse res) {
    User user = req.content().as(User.class);
    User saved = userService.save(user);
    res.status(Status.CREATED_201).send(saved);
}
```

## Response Patterns

### Send JSON object

```java
res.send(user);  // Jackson serializes automatically
```

### Send with status code

```java
res.status(Status.CREATED_201).send(user);
res.status(Status.NO_CONTENT_204).send();
res.status(Status.NOT_FOUND_404).send();
```

### Send JSON list

```java
List<User> users = userService.findAll();
res.send(users);  // Jackson serializes List<User> to JSON array
```

### Common status codes

```java
import io.helidon.http.Status;

Status.OK_200
Status.CREATED_201
Status.NO_CONTENT_204
Status.BAD_REQUEST_400
Status.NOT_FOUND_404
Status.INTERNAL_SERVER_ERROR_500
```

## Error Handling

### In-handler error handling

```java
private void findById(ServerRequest req, ServerResponse res) {
    try {
        long id = Long.parseLong(req.path().pathParameters().get("id"));
        userService.findById(id)
                .ifPresentOrElse(
                        res::send,
                        () -> res.status(Status.NOT_FOUND_404).send()
                );
    } catch (NumberFormatException e) {
        res.status(Status.BAD_REQUEST_400).send();
    }
}
```

### Global error handler on routing

```java
routing.error(NotFoundException.class, (req, res, ex) ->
        res.status(Status.NOT_FOUND_404).send(Map.of("error", ex.getMessage()))
);

routing.error(Exception.class, (req, res, ex) ->
        res.status(Status.INTERNAL_SERVER_ERROR_500)
                .send(Map.of("error", "Internal server error"))
);
```

## Nested Services

Register sub-services under a parent path:

```java
@Override
public void routing(HttpRules rules) {
    rules
            .get("/", this::findAll)
            .get("/{id}", this::findById)
            .register("/{postId}/comments", commentHandler);
}
```

## Filters

Add cross-cutting concerns (logging, auth) via filters on routing:

```java
routing.addFilter((chain, req, res) -> {
    long start = System.currentTimeMillis();
    chain.proceed();
    long elapsed = System.currentTimeMillis() - start;
    System.out.println(req.prologue().method() + " " +
            req.path() + " -> " + res.status() + " (" + elapsed + "ms)");
});
```
