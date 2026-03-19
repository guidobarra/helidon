# Helidon Nima Microservice - AI Agent Guide

## Project Type
Cloud-native Java microservice built with Helidon SE 4.4.0 (Nima) and GraalVM native compilation.

## Tech Stack
- Java 21+ with virtual threads (Nima)
- Framework: Helidon SE 4.4.0
- Database: PostgreSQL via DbClient (JDBC + HikariCP)
- JSON: Jackson
- Build: Maven + GraalVM Native Image
- Observability: Health checks, Metrics, OpenAPI

## Skills
Use `/skill <name>` for specific guidance:

- `/helidon` — Helidon SE 4.x routing, DbClient, config profiles, testing
- `/graalvm-native-image` — Native image compilation, reflection config, tracing agent
- `/java-add-graalvm-native-image-support` — Add native image support to a Java project

## Quick Commands

### Development
```bash
mvn package && java -jar target/helidon-nima.jar
mvn package -DskipTests                              # Skip tests
java -Dconfig.profile=beta -jar target/helidon-nima.jar  # Run with profile
```

### Database
```bash
docker compose up -d postgres
```

### Testing
```bash
mvn test       # Unit tests (@RoutingTest)
mvn verify     # Integration tests (@ServerTest)
```

### Docker Compose
```bash
docker compose up -d --build                          # JVM (default .env)
# Editar .env: DOCKERFILE=Dockerfile.native           # Para native
docker compose down
```

### Native Build (local)
```bash
mvn package -Pnative-image -DskipTests
./target/helidon-nima
```

## Code Standards
- Blocking I/O on virtual threads — no reactive APIs
- No DI framework — wire dependencies manually in `Main.java`
- DbClient over JPA (GraalVM compatible, no reflection-heavy ORM)
- Use Records for DTOs when possible
- `fromRow(DbRow)` static factory for row mapping
- `ReentrantLock` over `synchronized` to avoid thread pinning
- Config profiles via `application-{profile}.yaml`

## Project Structure
```
src/main/java/com/guba/helidonnima/
├── Main.java              # WebServer bootstrap, dependency wiring, routing
├── handler/               # HttpService implementations (REST layer)
├── service/               # Business logic
├── repository/            # DbClient data access
└── model/                 # POJOs with fromRow(DbRow)

src/main/resources/
├── application.yaml           # Default config
└── application-beta.yaml      # Beta profile override

src/test/java/
├── AbstractMainTest.java      # Shared test base
├── MainTest.java              # @RoutingTest (unit, no sockets)
└── MainIT.java                # @ServerTest (integration, real port)
```

## Key Endpoints
- `GET/POST /users` — User CRUD
- `GET/PUT/DELETE /users/{id}`
- `GET /health-check` — Custom health endpoint
- `GET /observe/metrics` — Metrics (auto-configured)
- `GET /openapi` — OpenAPI spec

See `/helidon` skill for detailed framework guidance.
