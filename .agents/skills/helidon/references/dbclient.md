# Helidon SE 4.x DbClient Reference

Complete guide for database access using Helidon DbClient with JDBC and HikariCP.

## Table of Contents

1. [Maven Dependencies](#maven-dependencies)
2. [Configuration](#configuration)
3. [DbClient Creation](#dbclient-creation)
4. [Query Operations](#query-operations)
5. [Insert, Update, Delete](#insert-update-delete)
6. [Transactions](#transactions)
7. [Row Mapping](#row-mapping)
8. [Named Statements](#named-statements)
9. [Health Check](#health-check)

---

## Maven Dependencies

All versions are managed by the `helidon-se` parent BOM:

```xml
<!-- Core DbClient -->
<dependency>
    <groupId>io.helidon.dbclient</groupId>
    <artifactId>helidon-dbclient</artifactId>
</dependency>

<!-- JDBC implementation -->
<dependency>
    <groupId>io.helidon.dbclient</groupId>
    <artifactId>helidon-dbclient-jdbc</artifactId>
</dependency>

<!-- HikariCP connection pool -->
<dependency>
    <groupId>io.helidon.dbclient</groupId>
    <artifactId>helidon-dbclient-hikari</artifactId>
</dependency>

<!-- PostgreSQL driver (version not managed) -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.4</version>
</dependency>

<!-- PostgreSQL integration (optional, for pg-specific features) -->
<dependency>
    <groupId>io.helidon.integrations.db</groupId>
    <artifactId>helidon-integrations-db-pgsql</artifactId>
</dependency>
```

## Configuration

In `application.yaml`:

```yaml
db:
  source: jdbc
  connection:
    url: jdbc:postgresql://localhost:5432/mydb
    username: helidon
    password: helidon123
    poolName: helidonPool
    maximumPoolSize: 10
```

### Profile override (`application-prod.yaml`)

```yaml
db:
  connection:
    url: jdbc:postgresql://db-prod:5432/mydb
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    maximumPoolSize: 30
```

## DbClient Creation

Create from config in `Main.java`:

```java
Config config = Config.create();
DbClient dbClient = DbClient.create(config.get("db"));
```

Pass to repositories via constructor:

```java
UserRepository userRepo = new UserRepository(dbClient);
```

## Query Operations

### Query multiple rows

```java
public List<User> findAll() {
    return dbClient.execute()
            .query("SELECT * FROM users ORDER BY id")
            .map(User::fromRow)
            .toList();
}
```

### Query single row (Optional)

```java
public Optional<User> findById(long id) {
    return dbClient.execute()
            .get("SELECT * FROM users WHERE id = ?", id)
            .map(User::fromRow);
}
```

### Query with multiple parameters

```java
public List<User> findByNameAndEmail(String name, String email) {
    return dbClient.execute()
            .query("SELECT * FROM users WHERE name = ? AND email = ?", name, email)
            .map(User::fromRow)
            .toList();
}
```

### Count query

```java
public long count() {
    return dbClient.execute()
            .get("SELECT COUNT(*) AS cnt FROM users")
            .map(row -> row.column("cnt").asLong().get())
            .orElse(0L);
}
```

## Insert, Update, Delete

All return a `long` with the number of affected rows.

### Insert

```java
public long save(User user) {
    return dbClient.execute()
            .insert("INSERT INTO users (name, email) VALUES (?, ?)",
                    user.name(), user.email());
}
```

### Insert and retrieve the saved entity

```java
public User save(User user) {
    long count = dbClient.execute()
            .insert("INSERT INTO users (name, email) VALUES (?, ?)",
                    user.name(), user.email());
    if (count == 0) {
        throw new IllegalStateException("Failed to insert user");
    }
    return dbClient.execute()
            .get("SELECT * FROM users WHERE email = ?", user.email())
            .map(User::fromRow)
            .orElseThrow();
}
```

### Update

```java
public Optional<User> update(long id, User user) {
    long count = dbClient.execute()
            .update("UPDATE users SET name = ?, email = ? WHERE id = ?",
                    user.name(), user.email(), id);
    if (count == 0) {
        return Optional.empty();
    }
    return findById(id);
}
```

### Delete

```java
public boolean delete(long id) {
    long count = dbClient.execute()
            .delete("DELETE FROM users WHERE id = ?", id);
    return count > 0;
}
```

## Transactions

Use `dbClient.execute()` with transaction methods for multi-statement atomicity:

```java
public void transferCredits(long fromId, long toId, int amount) {
    dbClient.execute()
            .transaction(tx -> {
                tx.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", amount, fromId);
                tx.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, toId);
            });
}
```

## Row Mapping

### Using a static factory method (recommended)

Define a `fromRow(DbRow)` method on the model:

```java
public class User {
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;

    public static User fromRow(DbRow row) {
        User user = new User();
        user.setId(row.column("id").asLong().get());
        user.setName(row.column("name").asString().get());
        user.setEmail(row.column("email").asString().get());
        row.column("created_at").as(LocalDateTime.class).ifPresent(user::setCreatedAt);
        return user;
    }

    // getters and setters...
}
```

### Column access methods

```java
row.column("id").asLong().get();            // Long (required)
row.column("name").asString().get();        // String (required)
row.column("active").asBoolean().orElse(false); // Boolean with default
row.column("score").as(BigDecimal.class).get(); // Any type
row.column("created_at").as(LocalDateTime.class).ifPresent(...); // Optional column
```

## Named Statements

Define SQL in `application.yaml` to keep Java code clean:

```yaml
db:
  source: jdbc
  connection:
    url: jdbc:postgresql://localhost:5432/mydb
    username: user
    password: secret
  statements:
    find-all-users: "SELECT * FROM users ORDER BY id"
    find-user-by-id: "SELECT * FROM users WHERE id = ?"
    insert-user: "INSERT INTO users (name, email) VALUES (?, ?)"
    update-user: "UPDATE users SET name = ?, email = ? WHERE id = ?"
    delete-user: "DELETE FROM users WHERE id = ?"
```

Use named statements in the repository:

```java
public List<User> findAll() {
    return dbClient.execute()
            .namedQuery("find-all-users")
            .map(User::fromRow)
            .toList();
}

public Optional<User> findById(long id) {
    return dbClient.execute()
            .namedGet("find-user-by-id", id)
            .map(User::fromRow);
}

public long save(User user) {
    return dbClient.execute()
            .namedInsert("insert-user", user.name(), user.email());
}
```

## Health Check

Add DbClient health check to the server:

```xml
<dependency>
    <groupId>io.helidon.dbclient</groupId>
    <artifactId>helidon-dbclient-health</artifactId>
</dependency>
```

Register in routing or observability setup as needed. The health check verifies the database connection is alive.
