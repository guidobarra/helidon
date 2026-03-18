# Startup

## Requisitos

- Java 21+
- Maven 3.8+
- Docker y Docker Compose

## 1. Base de datos

Levantar PostgreSQL con Docker Compose:

```bash
docker compose up -d postgres
```

Esto crea el contenedor `postgres` con la base de datos inicializada (`docker/init.sql`).

Verificar que este healthy:

```bash
docker compose ps
```

## 2. Ejecucion local

### Compilar

```bash
mvn clean compile
```

### Ejecutar

```bash
mvn clean compile exec:java
```

La app arranca en `http://localhost:9292` (perfil `dev` por defecto).

### Compilar JAR

```bash
mvn clean package -DskipTests
```

### Ejecutar JAR

```bash
java -jar target/helidon-nima.jar
```

## 3. Ejecucion con Docker Compose

Levanta la app y PostgreSQL juntos:

```bash
docker compose up -d --build
```

Reconstruir solo la app (sin cache):

```bash
docker compose build --no-cache helidon-nima-app
```

Reconstruir y levantar:

```bash
docker compose up -d --build helidon-nima-app
```

Ver logs:

```bash
docker compose logs -f helidon-nima-app
```

Bajar todo:

```bash
docker compose down
```

Bajar todo y borrar volumenes (resetea la DB):

```bash
docker compose down -v
```

## 4. Ejecucion con Docker (standalone)

### Los 3 Dockerfiles

El proyecto incluye 3 Dockerfiles, cada uno genera un tipo de imagen distinto:

| Dockerfile | Imagen runtime | Tamano aprox. | Startup | Uso recomendado |
|---|---|---|---|---|
| `Dockerfile` | JRE completo (`eclipse-temurin:21-jre`) | ~250MB | ~500ms | **Default**. Desarrollo y produccion general. |
| `Dockerfile.jlink` | JRE custom con `jlink` (`debian:bookworm-slim`) | ~100MB | ~300ms | Produccion optimizada. Solo incluye los modulos Java que la app necesita. |
| `Dockerfile.native` | Binario nativo GraalVM (`oraclelinux:9-slim`) | ~80MB | ~50ms | Maximo rendimiento. Compila la app a un binario nativo sin JVM. |

**`Dockerfile`** (default) — JRE completo

Usa un JRE standard. Es el mas simple y compatible. Funciona con cualquier libreria Java sin restricciones.

```
Build: maven:3.9-eclipse-temurin-21
Runtime: eclipse-temurin:21-jre
Artefacto: helidon-nima.jar + libs/
```

**`Dockerfile.jlink`** — JRE custom con jlink

Usa `jlink` para crear un Java Runtime Image (JRI) que solo contiene los modulos del JDK que la app necesita (por ejemplo, `java.base`, `java.sql`, `java.net.http`). Reduce el tamano de la imagen significativamente.

```
Build: maven:3.9-eclipse-temurin-21 (con perfil -Pjlink-image)
Runtime: debian:bookworm-slim (solo OS, sin JDK instalado)
Artefacto: helidon-nima-jri/ (JRE custom + app empaquetados)
```

**`Dockerfile.native`** — Binario nativo GraalVM

Compila la app a un binario nativo con GraalVM Native Image. No necesita JVM para ejecutarse. Startup en milisegundos y consumo de memoria minimo, pero el build es lento (~5-10 min) y algunas librerias con reflection pueden necesitar configuracion adicional.

```
Build: ghcr.io/graalvm/graalvm-community:21 (con perfil -Pnative-image)
Runtime: oraclelinux:9-slim (OS minimo con glibc)
Artefacto: helidon-nima (binario ejecutable)
```

### Build de la imagen

```bash
# Default (JRE completo)
docker build -t helidon-nima-app .

# JRE custom con jlink
docker build -f Dockerfile.jlink -t helidon-nima-app .

# Binario nativo GraalVM
docker build -f Dockerfile.native -t helidon-nima-app .
```

### Run

```bash
docker run -p 9292:9292 helidon-nima-app
```

### Con variables de entorno

```bash
docker run -p 9292:9292 \
  -e HELIDON_CONFIG_PROFILE=beta \
  -e DB_CONNECTION_URL=jdbc:postgresql://host.docker.internal:5432/sales \
  -e DB_CONNECTION_USERNAME=helidon \
  -e DB_CONNECTION_PASSWORD=helidon123 \
  helidon-nima-app
```

## 5. Perfiles de configuracion

El perfil se selecciona con la variable de entorno `HELIDON_CONFIG_PROFILE` o el system property `-Dconfig.profile`:

| Valor | Archivo | Uso |
|-------|---------|-----|
| `dev` (default) | `application.yaml` | Desarrollo local |
| `beta` | `application-beta.yaml` | Docker Compose / staging |

```bash
# Local con system property
java -Dconfig.profile=beta -jar target/helidon-nima.jar

# Docker con variable de entorno
docker run -e HELIDON_CONFIG_PROFILE=beta helidon-nima-app
```

La configuracion se carga en este orden (el ultimo gana):

1. `application.yaml` — valores base
2. `application-{profile}.yaml` — valores del perfil
3. Variables de entorno
4. System properties (`-D`)

## 6. Variables de entorno

Helidon convierte las claves YAML a variables de entorno siguiendo esta convencion:

- Puntos (`.`) → guiones bajos (`_`)
- Todo en mayusculas

| Variable de entorno | Clave en YAML |
|---------------------|---------------|
| `SERVER_PORT` | `server.port` |
| `DB_CONNECTION_URL` | `db.connection.url` |
| `DB_CONNECTION_USERNAME` | `db.connection.username` |
| `DB_CONNECTION_PASSWORD` | `db.connection.password` |
| `DB_CONNECTION_MAXIMUMPOOLSIZE` | `db.connection.maximumPoolSize` |
| `APP_ENVIRONMENT` | `app.environment` |

## 7. OpenAPI y Swagger UI

La especificacion OpenAPI se sirve automaticamente al tener las dependencias en el classpath.

| URL | Descripcion |
|-----|-------------|
| `http://localhost:9292/openapi` | Especificacion OpenAPI (YAML) |
| `http://localhost:9292/openapi/ui` | Swagger UI interactivo |

La spec se define en el archivo estatico `src/main/resources/META-INF/openapi.yaml`.

## 8. Endpoints

| Metodo | URL | Descripcion |
|--------|-----|-------------|
| `GET` | `/health-check` | Health check |
| `GET` | `/users` | Listar usuarios |
| `GET` | `/users/{id}` | Obtener usuario por ID |
| `POST` | `/users` | Crear usuario |
| `PUT` | `/users/{id}` | Actualizar usuario |
| `DELETE` | `/users/{id}` | Eliminar usuario |
| `GET` | `/openapi` | Especificacion OpenAPI |
| `GET` | `/openapi/ui` | Swagger UI |

### Ejemplos

Health check:

```bash
curl http://localhost:9292/health-check
```

Listar usuarios:

```bash
curl http://localhost:9292/users
```

Obtener usuario por ID:

```bash
curl http://localhost:9292/users/1
```

Crear usuario:

```bash
curl -X POST http://localhost:9292/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Juan", "email": "juan@mail.com"}'
```

Actualizar usuario:

```bash
curl -X PUT http://localhost:9292/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "Juan Perez", "email": "juan.perez@mail.com"}'
```

Eliminar usuario:

```bash
curl -X DELETE http://localhost:9292/users/1
```
