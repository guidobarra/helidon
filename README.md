# Helidon Nima

> Documentacion tecnica sobre **Helidon** y su webserver **Nima** basado en virtual threads

---

## Documentacion

- [STARTUP.md](STARTUP.md) — Como compilar, ejecutar y desplegar la aplicacion

## Tabla de contenidos

1. [Que es Helidon](#1-que-es-helidon)
   - [1.1 Los dos sabores: SE y MP](#11-los-dos-sabores-se-y-mp)
   - [1.2 Helidon SE: funcional y programatico](#12-helidon-se-funcional-y-programatico)
   - [1.3 Helidon MP: declarativo y empresarial](#13-helidon-mp-declarativo-y-empresarial)
2. [Que es Helidon Nima](#2-que-es-helidon-nima)
   - [2.1 Origen y significado del nombre](#21-origen-y-significado-del-nombre)
   - [2.2 Nima esta disponible para SE y MP](#22-nima-esta-disponible-para-se-y-mp)
   - [2.3 Que cambio de Helidon 3 a Helidon 4](#23-que-cambio-de-helidon-3-a-helidon-4)
3. [Que problematica resuelve](#3-que-problematica-resuelve)
4. [Componentes de Helidon](#4-componentes-de-helidon)
   - [4.1 WebServer (Nima)](#41-webserver-nima)
   - [4.2 Routing](#42-routing)
   - [4.3 HttpService](#43-httpservice)
   - [4.4 Config](#44-config)
   - [4.5 DbClient](#45-dbclient)
   - [4.6 Security](#46-security)
   - [4.7 Observabilidad (Health, Metrics, Tracing)](#47-observabilidad-health-metrics-tracing)
   - [4.8 Serializacion (Jackson)](#48-serializacion-jackson)
   - [4.9 WebClient](#49-webclient)
   - [4.10 Testing](#410-testing)
   - [4.11 Fault Tolerance](#411-fault-tolerance)
5. [Como funciona Helidon Nima por dentro](#5-como-funciona-helidon-nima-por-dentro)
   - [5.1 Arquitectura general](#51-arquitectura-general)
   - [5.2 Sin Netty: un webserver puro Java](#52-sin-netty-un-webserver-puro-java)
   - [5.3 Threading model: virtual threads](#53-threading-model-virtual-threads)
   - [5.4 Ciclo de vida de un request](#54-ciclo-de-vida-de-un-request)
6. [Virtual Threads en profundidad](#6-virtual-threads-en-profundidad)
   - [6.1 Virtual threads vs Platform threads](#61-virtual-threads-vs-platform-threads)
   - [6.2 Que pasa internamente durante una operacion I/O](#62-que-pasa-internamente-durante-una-operacion-io)
   - [6.3 Continuaciones: el mecanismo interno](#63-continuaciones-el-mecanismo-interno)
   - [6.4 Precauciones con virtual threads](#64-precauciones-con-virtual-threads)
7. [Comparativa con otros frameworks](#7-comparativa-con-otros-frameworks)
   - [7.1 Benchmarks oficiales y externos](#71-benchmarks-oficiales-y-externos)
8. [Ventajas y desventajas](#8-ventajas-y-desventajas)
9. [Referencias](#9-referencias)

---

## 1. Que es Helidon

Helidon es un **framework de microservicios para Java** creado y mantenido por **Oracle**. Lanzado en 2018, esta disenado para construir aplicaciones cloud-native ligeras y de alto rendimiento sobre la JVM.

### 1.1 Los dos sabores: SE y MP

Helidon ofrece dos sabores (*flavors*) que comparten el mismo motor web (Nima) pero difieren en el modelo de programacion:

| Sabor | Modelo | APIs | Ideal para |
|---|---|---|---|
| **Helidon SE** | Funcional, programatico, sin magia | APIs nativas de Helidon | Control total, microservicios ligeros |
| **Helidon MP** | Declarativo, con anotaciones y CDI | Jakarta EE / MicroProfile 6 | Equipos que vienen de Java EE / Spring |

Ambos sabores en Helidon 4 corren sobre el **mismo webserver Nima** con virtual threads. La diferencia es el modelo de programacion, no el motor.

### 1.2 Helidon SE: funcional y programatico

Helidon SE es el sabor minimalista. No usa inyeccion de dependencias, ni anotaciones magicas, ni escaneo de classpath. El desarrollador tiene control total y construye la aplicacion de forma programatica:

```java
WebServer server = WebServer.builder()
        .port(8080)
        .routing(routing -> routing
                .get("/hello", (req, res) -> res.send("Hello!")))
        .build()
        .start();
```

Caracteristicas de Helidon SE:
- APIs funcionales y fluidas
- Sin contenedor de aplicaciones
- Sin anotaciones ni reflection pesada
- Sin DI (el desarrollador gestiona las dependencias)
- Startup extremadamente rapido
- Footprint de memoria bajo

### 1.3 Helidon MP: declarativo y empresarial

Helidon MP implementa **Eclipse MicroProfile 6** sobre el mismo webserver Nima. Usa CDI (Contexts and Dependency Injection) y JAX-RS para un modelo familiar a desarrolladores de Java EE y Spring:

```java
@Path("/hello")
@ApplicationScoped
public class HelloResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello!";
    }
}
```

Caracteristicas de Helidon MP:
- Anotaciones JAX-RS (`@Path`, `@GET`, `@POST`, etc.)
- Inyeccion de dependencias via CDI
- Compatible con MicroProfile Config, Health, Metrics, OpenAPI, JWT Auth, Fault Tolerance
- JPA y JTA integrados
- Familiar para equipos con experiencia en Java EE / Spring
- Mayor footprint que SE, pero menor que Spring Boot

---

## 2. Que es Helidon Nima

### 2.1 Origen y significado del nombre

**Nima** (Νήμα) significa **"Thread" (hilo) en griego**. Es el nombre en codigo del webserver que Oracle escribio desde cero para Helidon 4, disenado especificamente para aprovechar los virtual threads de Java 21 (Project Loom).

Fue desarrollado en estrecha colaboracion con el **equipo de la JVM de Oracle**, en paralelo con el propio desarrollo de virtual threads en el JDK. Segun la pagina oficial:

> *"Helidon Nima is the first Java microservices framework based on virtual threads. It combines an easy-to-use programming model with outstanding performance."*

### 2.2 Nima esta disponible para SE y MP

Nima es el **motor web compartido** por ambos sabores de Helidon 4. No es exclusivo de SE:

```
┌─────────────────────────────────────────┐
│              Helidon 4                  │
│                                         │
│  ┌───────────┐      ┌───────────┐      │
│  │ Helidon SE│      │ Helidon MP│      │
│  │ (funcional│      │ (CDI +    │      │
│  │  + APIs   │      │  JAX-RS + │      │
│  │  nativas) │      │  MicroP.) │      │
│  └─────┬─────┘      └─────┬─────┘      │
│        │                   │            │
│        └─────────┬─────────┘            │
│                  │                      │
│        ┌─────────v─────────┐            │
│        │   NIMA WEBSERVER  │            │
│        │  (virtual threads)│            │
│        └─────────┬─────────┘            │
│                  │                      │
│        ┌─────────v─────────┐            │
│        │    Java 21+ JDK   │            │
│        │  (Project Loom)   │            │
│        └───────────────────┘            │
└─────────────────────────────────────────┘
```

En Helidon 3, Helidon MP usaba Netty + reactive engine. En Helidon 4, **ambos sabores usan Nima**. Esto significa que la mejora de rendimiento de virtual threads beneficia tanto a SE como a MP sin cambios de codigo.

Segun el articulo de InfoQ:

> *"If we compare Helidon 3 MP performance to Helidon 4 MP performance — it is measured in magnitudes."*

### 2.3 Que cambio de Helidon 3 a Helidon 4

| Aspecto | Helidon 3 | Helidon 4 |
|---|---|---|
| **Webserver** | Netty | Nima (puro Java, escrito desde cero) |
| **Motor I/O** | Non-blocking reactivo | Blocking con virtual threads |
| **APIs SE** | Reactivas (`Single<T>`, `Multi<T>`) | Imperativas (sincrono, bloqueante) |
| **APIs MP** | CDI + JAX-RS (sobre Netty) | CDI + JAX-RS (sobre Nima) |
| **Java minimo** | Java 17 | Java 21 |
| **MicroProfile** | MicroProfile 5 | MicroProfile 6 |
| **Dependencia Netty** | Si (~4MB + transitivas) | No (eliminada) |
| **Rendimiento** | Bueno | Superior (virtual threads + sin capas intermedias) |
| **Lenguaje** | Java convencional | Uso extensivo de `records`, `switch expressions`, `sealed classes` |

---

## 3. Que problematica resuelve

### El problema del modelo thread-per-request clasico

El modelo clasico de servidores Java (Servlets, Spring MVC) asigna **un platform thread por cada request entrante**:

| Problema | Descripcion |
|---|---|
| **Consumo de memoria** | Cada platform thread consume ~1MB de stack. 10,000 conexiones = ~10GB solo en stacks. |
| **Context switching** | El SO pierde tiempo alternando entre miles de hilos bloqueados. |
| **Escalabilidad limitada** | Mas hilos no significa mas rendimiento; hay un punto de retorno decreciente. |
| **Hilos ociosos** | Mientras esperan I/O (base de datos, red), los hilos no hacen nada util pero siguen ocupando recursos. |

### La solucion reactiva (Vert.x, Netty, WebFlux)

Frameworks reactivos resuelven esto usando **un event loop con I/O no bloqueante**: un solo hilo maneja miles de conexiones porque nunca se bloquea. Pero introduce un costo:

- Codigo complejo: callbacks, Futures, reactive streams.
- Debugging dificil: stack traces fragmentadas.
- Disciplina estricta: nunca bloquear el event loop.
- Librerias bloqueantes incompatibles sin wrappers.
- Curva de aprendizaje alta.

### Helidon Nima: lo mejor de ambos mundos

Helidon Nima resuelve el problema de escalabilidad **sin sacrificar la simplicidad del codigo sincrono**, gracias a los **virtual threads** de Java 21+:

- **Codigo simple**: escribes codigo Java normal, imperativo, bloqueante.
- **Alta concurrencia**: cada request corre en un virtual thread. Millones de virtual threads simultaneos.
- **Bajo costo**: un virtual thread pesa ~1KB (vs ~1MB de un platform thread).
- **Bloquear es gratis**: cuando un virtual thread hace I/O, el JDK lo "desmonta" del carrier thread, que queda libre.
- **Sin cambio de mentalidad**: si sabes Java, ya sabes usar Helidon.

---

## 4. Componentes de Helidon

Helidon es modular: agregas solo los componentes que necesitas. Estos son los principales:

### 4.1 WebServer (Nima)

El componente central. Un servidor HTTP/1.1 y HTTP/2 escrito desde cero en Java puro, sin Netty. Soporta:

- HTTP/1.1 con pipelining
- HTTP/2 via ALPN
- WebSocket (via upgrade HTTP/1.1)
- gRPC (sub-protocolo HTTP/2)
- TLS / mTLS para cualquier protocolo
- Extensible para otros protocolos TCP (no-HTTP)
- Un mismo puerto puede servir multiples protocolos simultaneamente

```java
WebServer server = WebServer.builder()
        .port(8080)
        .host("0.0.0.0")
        .routing(routing -> routing
                .get("/", (req, res) -> res.send("OK")))
        .build()
        .start();
```

**Modulo Maven**: `helidon-webserver`

### 4.2 Routing

El sistema de routing mapea URLs y metodos HTTP a handlers. Es version-agnostic (las mismas rutas sirven para HTTP/1.1 y HTTP/2), con soporte para rutas version-specific si se necesita.

```java
HttpRouting.builder()
        .get("/health", (req, res) -> res.send("UP"))
        .post("/api/items", (req, res) -> {
            Item item = req.content().as(Item.class);
            // procesar...
            res.status(Status.CREATED_201).send(item);
        })
        .register("/api/users", new UserService())
        .build();
```

Soporta:
- Rutas por metodo HTTP (`get`, `post`, `put`, `delete`, `patch`)
- Path parameters (`/items/{id}`)
- Registro de `HttpService` con prefijo
- Filters (para cross-cutting concerns como autenticacion o logging)

### 4.3 HttpService

La unidad de organizacion en Helidon SE. Una clase que implementa `HttpService` agrupa rutas relacionadas:

```java
public class ItemService implements HttpService {

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
        List<Item> items = repository.findAll();
        res.send(items);
    }

    // ... otros handlers
}
```

Se registra con un prefijo en el routing:

```java
routing.register("/api/items", new ItemService());
```

### 4.4 Config

Sistema de configuracion propio que carga de multiples fuentes con prioridad:

```
Prioridad (ultima gana):
  1. application.yaml (classpath)         ← valores base
  2. application-{profile}.yaml           ← override por perfil
  3. Variables de entorno                  ← override por ambiente
  4. System properties (-D)               ← override maximo
```

El perfil se activa con `-Dconfig.profile=prod` o la variable de entorno `HELIDON_CONFIG_PROFILE=prod`.

Ejemplo de `application.yaml`:

```yaml
server:
  port: 8080
  host: 0.0.0.0

app:
  name: my-service
  environment: dev

db:
  source: jdbc
  connection:
    url: jdbc:mariadb://localhost:3306/mydb
    username: user
    password: pass
```

Acceso en codigo:

```java
Config config = Config.create();
String dbUrl = config.get("db.connection.url").asString().orElse("jdbc:h2:mem:test");
int port = config.get("server.port").asInt().orElse(8080);
```

Conversion de claves a variables de entorno: `db.connection.url` → `DB_CONNECTION_URL` (puntos a guiones bajos, mayusculas).

**Modulo Maven**: `helidon-config` + `helidon-config-yaml`

### 4.5 DbClient

Cliente de base de datos propio que abstrae JDBC con una API fluida. Funciona directamente con virtual threads (operaciones JDBC bloqueantes se desmontan automaticamente):

```java
DbClient dbClient = DbClient.create(config.get("db"));

// Query que retorna multiples filas
Stream<DbRow> rows = dbClient.execute()
        .query("SELECT * FROM items ORDER BY id");

// Query que retorna una fila (o ninguna)
Optional<DbRow> row = dbClient.execute()
        .get("SELECT * FROM items WHERE id = ?", id);

// Insert, update, delete retornan filas afectadas
long count = dbClient.execute()
        .insert("INSERT INTO items (name) VALUES (?)", name);
```

Soporta:
- JDBC (con HikariCP como pool de conexiones)
- MongoDB (driver nativo)
- Health checks de base de datos
- Metricas de base de datos
- Tracing de queries

**Modulos Maven**: `helidon-dbclient`, `helidon-dbclient-jdbc`, `helidon-dbclient-hikari`

### 4.6 Security

Modulo de seguridad integrado con soporte para:

- Autenticacion (HTTP Basic, HTTP Digest, Bearer tokens)
- Autorizacion (RBAC, ABAC, scopes)
- Audit
- Integracion con OIDC/OAuth2
- JWT validation
- Se aplica como feature del webserver o como filtro en rutas especificas

```java
WebServer.builder()
        .addFeature(SecurityFeature.create(security))
        .routing(routing -> routing
                .get("/public", (req, res) -> res.send("OK"))
                .get("/protected", SecurityHandler.create()
                        .authenticate()
                        .authorize(),
                        (req, res) -> res.send("Secret")))
        .build();
```

**Modulo Maven**: `helidon-webserver-security`

### 4.7 Observabilidad (Health, Metrics, Tracing)

Helidon incluye endpoints de observabilidad out-of-the-box:

| Endpoint | Modulo Maven | Descripcion |
|---|---|---|
| `/observe/health` | `helidon-webserver-observe-health` | Health checks (liveness, readiness, startup) |
| `/observe/metrics` | `helidon-webserver-observe-metrics` | Metricas Prometheus/OpenMetrics |
| Tracing | `helidon-tracing-providers-*` | OpenTelemetry, Jaeger, Zipkin |

Se habilitan al agregar las dependencias correspondientes al `pom.xml`. En Helidon MP, los health checks y metricas MicroProfile se habilitan automaticamente via CDI.

### 4.8 Serializacion (Jackson)

Helidon SE 4 usa **Jackson** para serializar/deserializar JSON automaticamente:

- `res.send(objeto)` → Jackson convierte a JSON
- `req.content().as(MyClass.class)` → Jackson deserializa desde JSON

No se necesita configuracion adicional si `helidon-http-media-jackson` esta en el classpath.

**Modulo Maven**: `helidon-http-media-jackson`

### 4.9 WebClient

Cliente HTTP integrado para llamar a otros servicios. Funciona con virtual threads (las llamadas bloqueantes se desmontan del carrier thread):

```java
Http1Client client = Http1Client.builder()
        .baseUri("https://api.example.com")
        .build();

String response = client.get("/data")
        .request(String.class)
        .entity();
```

Soporta HTTP/1.1 y HTTP/2.

**Modulo Maven**: `helidon-webclient`

### 4.10 Testing

Framework de testing integrado con JUnit 5. Soporta dos modos:

```java
@RoutingTest   // sin servidor real, usa DirectClient (mas rapido)
@ServerTest    // levanta servidor real en puerto aleatorio
class MyServiceTest {

    private final Http1Client client;

    MyServiceTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.register("/api/items", new ItemService());
    }

    @Test
    void testGetAll() {
        var response = client.get("/api/items").request(String.class);
        assertThat(response.status(), is(Status.OK_200));
    }
}
```

**Modulo Maven**: `helidon-webserver-testing-junit5`

### 4.11 Fault Tolerance

Patrones de resiliencia integrados:

- **Retry**: reintentar operaciones fallidas
- **Circuit Breaker**: cortar circuito tras fallos consecutivos
- **Timeout**: limitar tiempo de ejecucion
- **Bulkhead**: limitar concurrencia a un recurso
- **Fallback**: valor alternativo en caso de fallo

**Modulo Maven**: `helidon-faulttolerance`

---

## 5. Como funciona Helidon Nima por dentro

### 5.1 Arquitectura general

```
+-------------------------------------------------------------------+
|                      Tu Aplicacion                                |
|  +---------------+  +---------------+  +------------------+      |
|  |  HttpService  |  |  HttpService  |  |  HttpService     |      |
|  |  (rutas API)  |  |  (health)     |  |  (otros)         |      |
|  +-------+-------+  +-------+-------+  +--------+---------+      |
|          |                  |                    |                 |
|  +-------v------------------v--------------------v-----------+    |
|  |                     HTTP ROUTING                          |    |
|  |     (version-agnostic: HTTP/1.1 y HTTP/2 comparten       |    |
|  |      las mismas rutas)                                    |    |
|  +---------------------------+-------------------------------+    |
|                              |                                    |
|  +---------------------------v-------------------------------+    |
|  |                  NIMA WEBSERVER                            |    |
|  |  - HTTP/1.1 con pipelining                                |    |
|  |  - HTTP/2 (via ALPN)                                      |    |
|  |  - WebSocket, gRPC                                        |    |
|  |  - TLS / mTLS                                             |    |
|  +---------------------------+-------------------------------+    |
|                              |                                    |
|  +---------------------------v-------------------------------+    |
|  |                   JAVA 21+ JDK                            |    |
|  |        Virtual Threads (Project Loom)                      |    |
|  |        java.net.ServerSocket (blocking mode)               |    |
|  +-----------------------------------------------------------+    |
+-------------------------------------------------------------------+
```

### 5.2 Sin Netty: un webserver puro Java

A diferencia de casi todos los frameworks Java (Vert.x, Micronaut, Quarkus, Spring WebFlux) que usan **Netty** como motor de I/O, Helidon 4 Nima implementa su webserver **directamente sobre las APIs del JDK**:

- `java.net.ServerSocket` en **modo bloqueante** para aceptar conexiones (el equipo de Helidon determino que los sockets bloqueantes dan mejor rendimiento que NIO con virtual threads).
- HTTP/1.1 con pipelining y HTTP/2 parseados e implementados desde cero.
- Writes asincronos al socket configurables (en Linux dan hasta **3x mejora** con HTTP/1.1 pipelining).
- Buffers **no se reutilizan** — el equipo descubrio que dejar que el GC los limpie da mejor throughput que los pools de buffers.

Segun Tomas Langer (arquitecto de Helidon en Oracle):

> *"We concluded that the best performance is achieved with blocking sockets. We use the ServerSocket in blocking mode and adopt a traditional approach of accepting a socket and initiating a new thread to handle it — with the understanding that these threads are virtual."*

Beneficios:
- **Menos dependencias**: no necesitas Netty (~4MB) ni sus dependencias transitivas. Se eliminan CVEs asociados a Netty.
- **Mejor integracion con virtual threads**: los sockets bloqueantes del JDK se integran nativamente con el scheduler de virtual threads.
- **Menos capas de abstraccion**: el request va directo del socket al handler sin pasar por channel pipelines.
- **Rendimiento superior**: en benchmarks internos, Nima iguala o supera a un servidor Netty puro minimalista.

### 5.3 Threading model: virtual threads

El modelo de threading de Helidon Nima fue documentado por Tomas Langer:

**Socket listeners**: son **platform threads** (hilos reales del OS). Hay uno por cada server socket abierto — un numero muy pequeno.

**HTTP/1.1**: por cada conexion:
- **1 virtual thread** para manejar la conexion (routing + handler)
- **1 virtual thread opcional** para writes asincronos

**HTTP/2**: por cada conexion:
- **1 virtual thread por stream** HTTP/2 (routing + handler)
- **1 virtual thread** para writes en la conexion
- **1 virtual thread** para manejar la conexion

```
                 Platform Thread (socket listener)
                          |
                   ServerSocket.accept()
                          |
            ┌─────────────┼─────────────┐
            v             v             v
      VThread #1    VThread #2    VThread #N    ← 1 por conexion (HTTP/1.1)
            |             |             |           o 1 por stream (HTTP/2)
            v             v             v
        Handler       Handler       Handler     ← tu codigo (bloqueante OK)
            |             |             |
            v             v             v
        Response      Response      Response
```

No hay event loop. No hay pool de hilos fijo. No hay reactive streams. Los virtual thread executors usan **unbounded executor services**. El JVM se encarga de montar/desmontar virtual threads sobre carrier threads.

### 5.4 Ciclo de vida de un request

Cuando llega un HTTP request a Helidon Nima:

```
1. ServerSocket.accept()        ← platform thread acepta conexion TCP
2. Crea virtual thread           ← un VThread por conexion
3. Lee bytes del socket           ← parsea HTTP request
4. HttpRouting                    ← busca el handler para la ruta
5. Filters (si hay)               ← autenticacion, logging, etc.
6. Handler.handle(req, res)       ← TU CODIGO (puede bloquear)
7. res.send(body)                 ← serializa respuesta (Jackson)
8. Escribe bytes al socket        ← envia HTTP response
9. Virtual thread termina
```

---

## 6. Virtual Threads en profundidad

### 6.1 Virtual threads vs Platform threads

| Aspecto | Platform Thread | Virtual Thread |
|---|---|---|
| **Memoria** | ~1MB de stack | ~1KB inicial (crece dinamicamente) |
| **Creacion** | Costosa (~1ms) | Barata (~1μs) |
| **Cantidad maxima** | Miles (limitado por RAM) | Millones |
| **Bloqueado en I/O** | Ocupa RAM + CPU context switch | Se desmonta del carrier, costo casi cero |
| **Gestionado por** | El sistema operativo | La JVM (user-space scheduling) |

En Helidon Nima, todo tu codigo puede bloquear libremente. JDBC, HTTP clients, file I/O, todo funciona directamente sin necesidad de `executeBlocking()` ni wrappers reactivos. El JDK desmonta el virtual thread del carrier thread durante la espera de I/O.

Esto significa que cualquier libreria bloqueante Java (HikariCP, Apache HttpClient, AWS SDK, OkHttp) funciona sin modificaciones.

### 6.2 Que pasa internamente durante una operacion I/O

Los virtual threads funcionan con un modelo **M:N** — M virtual threads se ejecutan sobre N carrier threads (platform threads):

```
Virtual Threads (millones)          Carrier Threads (pocos, ~cores)
┌──────────────┐                   ┌──────────────┐
│ VThread #1   │ ──mounted──────>  │ Carrier #1   │  (ejecutando VT #1)
│ VThread #2   │    (waiting)      │ Carrier #2   │  (ejecutando VT #5)
│ VThread #3   │    (waiting)      │ Carrier #3   │  (idle)
│ VThread #4   │    (waiting)      │ Carrier #4   │  (ejecutando VT #99)
│ VThread #5   │ ──mounted──────>  └──────────────┘
│ ...          │
│ VThread #99  │ ──mounted──────>
│ VThread #999 │    (waiting)
└──────────────┘
```

Cuando un virtual thread hace I/O bloqueante (por ejemplo, una query SQL):

```
Tiempo    Carrier Thread #1           VThread #1              Base de datos
──────    ─────────────────           ──────────              ─────────────
 0ms      Ejecuta VThread #1
 0.1ms    VT #1: socket.write()       Envia query SQL ──────> Recibe query
 0.2ms    VT #1: socket.read()
          │
          ├── JDK detecta operacion de I/O bloqueante
          ├── DESMONTA VThread #1 del carrier (guarda stack ~1KB en heap)
          ├── VThread #1 pasa a estado WAITING
          ├── Carrier #1 queda LIBRE
          │
 0.3ms    MONTA VThread #42           VT #1 esperando         Procesando...
          Ejecuta VThread #42         (en heap, ~1KB)
 5ms      MONTA VThread #77           VT #1 esperando          
          Ejecuta VThread #77         (en heap, ~1KB)
          ...                                                  Procesando...
          (ejecuta cientos de
           otros virtual threads)
                                                               
500ms     SO notifica: socket tiene datos                 <── Envia resultado
          │
          ├── JDK pone VThread #1 en cola de ejecucion
          ├── Cuando un carrier queda libre...
          │
501ms     MONTA VThread #1            Reanuda ejecucion
          VT #1: lee resultado        Deserializa ResultSet
          VT #1: retorna datos        Handler envia response
          DESMONTA VThread #1          Termina
```

**El carrier thread estuvo libre todo el tiempo para ejecutar otros virtual threads.**

### 6.3 Continuaciones: el mecanismo interno

Los virtual threads funcionan internamente con **continuaciones** (Continuation), un mecanismo del JDK:

1. **Montaje (mount)**: el JDK copia el stack del virtual thread al carrier thread y lo ejecuta.
2. **Punto de I/O**: cuando el virtual thread llama a `socket.read()`, el JDK intercepta la llamada.
3. **Desmontaje (unmount)**: el JDK guarda el stack del virtual thread en el heap (~1KB) y libera el carrier thread.
4. **Notificacion**: cuando la I/O completa (via `epoll` en Linux / `kqueue` en macOS), el JDK marca el virtual thread como ejecutable.
5. **Re-montaje**: cuando un carrier thread esta libre, monta el virtual thread y reanuda la ejecucion exactamente donde se detuvo.

El codigo del desarrollador nunca ve esto. Para el, `socket.read()` retorna el resultado normalmente, como si hubiera bloqueado.

#### Analogia: el restaurante

**Modelo platform threads (clasico):**
- Cada cliente tiene un mesero dedicado.
- El mesero lleva el pedido a cocina y **se queda parado esperando**.
- 100 clientes = 100 meseros (la mayoria parados).

**Modelo event loop (Vert.x):**
- 1 mesero para todo el salon.
- Nunca se queda parado, pero tiene que usar un protocolo complejo de notas y campanas.
- Si accidentalmente se queda parado (bloquea el event loop), todo el salon se paraliza.

**Modelo virtual threads (Helidon Nima):**
- Cada cliente tiene un "mesero virtual" (un post-it con su nombre).
- Solo hay 4 meseros reales (carrier threads).
- Cuando un mesero virtual necesita esperar (cocina), deja su post-it en la mesa y el mesero real atiende a otro cliente.
- Cuando la cocina tiene el plato listo, un mesero real disponible retoma el post-it.
- Sin protocolo complejo, sin riesgo de bloquear el salon.

#### Resumen comparativo

| Aspecto | Platform Threads | Event Loop (Vert.x) | Virtual Threads (Helidon) |
|---|---|---|---|
| **Quien espera** | Un hilo del SO | El kernel (epoll/kqueue) | El JDK scheduler |
| **Costo de esperar** | ~1MB RAM + context switch | Casi cero | ~1KB heap |
| **Codigo** | Simple, bloqueante | Complejo, callbacks/futures | Simple, bloqueante |
| **1000 queries de 10s** | 1000 hilos = ~1GB RAM | 1 hilo, 1000 file descriptors | 1000 VTs = ~1MB heap |
| **Librerias bloqueantes** | Funcionan | Necesitan wrapper | Funcionan |
| **Riesgo** | Quedarse sin hilos | Bloquear el event loop | Pinning en synchronized |

### 6.4 Precauciones con virtual threads

Documentadas por el equipo de Helidon:

**1. Pinning: Synchronized blocks y metodos nativos**

Los bloques `synchronized`, metodos nativos y foreign functions fijan (**pin**) el virtual thread al carrier thread:

> *"The virtual thread is not released from the carrier thread when it is pinned. This happens when we use Synchronized blocks as well as Native methods and foreign functions. These operations may hinder application scalability."*

El equipo de Helidon reemplazo **todo** `synchronized` por `ReentrantLock`:

```java
// EVITAR - fija el virtual thread al carrier
synchronized (lock) {
    database.query("SELECT ...");
}

// PREFERIR - como hace Helidon internamente
lock.lock();
try {
    database.query("SELECT ...");
} finally {
    lock.unlock();
}
```

**2. Obstruccion (calculos CPU-intensivos)**

> *"You should not 'obstruct' the thread. Obstruction is a long-term, full utilization of the thread. This can be resolved by off-loading the heavy load to a dedicated executor service using platform threads."*

**3. Thread locals**

Funcionan pero consumen memoria multiplicada por millones de virtual threads. Usar `ScopedValue` (preview en Java 21+) cuando sea posible.

**4. Buffer reuse**

Contra-intuitivamente, el equipo de Helidon descubrio que **no reutilizar buffers** da mejor rendimiento. Crear buffers nuevos y dejar que el GC los limpie produce mayor throughput que pools de buffers.

---

## 7. Comparativa con otros frameworks

### Helidon vs Spring Boot

| Aspecto | Helidon SE | Spring Boot |
|---|---|---|
| **Naturaleza** | Framework ligero, funcional | Framework full-stack, opinionated |
| **Modelo I/O** | Virtual threads nativos | Thread-per-request (virtual threads opt-in 3.2+) |
| **Startup** | Milisegundos | ~1-2 segundos |
| **Memoria** | Baja (~20-50MB) | Alta (~150-300MB) |
| **DI** | No incluida (SE) / CDI (MP) | Spring IoC integrado |
| **Ecosistema** | Reducido pero focalizado | El mas grande del mundo Java |
| **GraalVM native** | Soporte integrado | Soporte via Spring Native |

### Helidon vs Vert.x

| Aspecto | Helidon | Vert.x |
|---|---|---|
| **Modelo I/O** | Virtual threads (bloqueante) | Event loop (non-blocking) |
| **Codigo** | Imperativo, simple | Reactivo, callbacks/futures |
| **JDBC** | Funciona directamente | Necesita wrapper o cliente reactivo |
| **Rendimiento** | Excelente | Excelente |
| **Event bus** | No incluido | Integrado (distribuido) |
| **Webserver** | Nima (propio, puro Java) | Netty |
| **Respaldo** | Oracle | Eclipse Foundation / Red Hat |

### Helidon vs Quarkus

| Aspecto | Helidon SE | Quarkus |
|---|---|---|
| **Motor** | Nima (virtual threads) | Vert.x + Netty |
| **Modelo** | Funcional, sin DI | CDI (ArC), anotaciones |
| **MicroProfile** | Solo en Helidon MP | Si, integrado |
| **Dev mode** | Manual | Live coding integrado |
| **GraalVM** | Soporte integrado | Excelente soporte |
| **Respaldo** | Oracle | Red Hat |

### Helidon vs Micronaut

| Aspecto | Helidon SE | Micronaut |
|---|---|---|
| **DI** | No incluida | Compile-time DI |
| **Modelo** | Virtual threads | Netty + reactivo (virtual threads opt-in) |
| **AOT** | No | Si, procesamiento en compilacion |
| **Startup** | Muy rapido | Muy rapido (~500ms) |
| **GraalVM** | Soporte integrado | Excelente soporte |

### 7.1 Benchmarks oficiales y externos

**Benchmarks internos de Oracle** (documentados por Tomas Langer):
- Nima con HTTP/1.1 alcanza rendimiento comparable a un servidor Netty **puro minimalista** (sin features, sin framework).
- Con HTTP/1.1 pipelining, Nima supera a Netty puro.
- Las pruebas se hicieron con `wrk` (HTTP/1.1) y `h2load` (HTTP/2).

**TechEmpower Framework Benchmarks** ([fuente](https://www.techempower.com/benchmarks)):
- Helidon aparece en posiciones altas en TechEmpower Round 22.

**Benchmark comparativo 2026** (Java Code Geeks, REST API + PostgreSQL, 10,000 conexiones concurrentes):

| Framework | Virtual Threads | Memory | Throughput | Cold Start | DX |
|---|---|---|---|---|---|
| **Helidon 4 SE** | **Ganador** | **Ganador** | Segundo | Tercero | Segundo |
| **Quarkus 3** | Segundo | Segundo | **Ganador** | Segundo | Tercero |
| **Micronaut 4** | Tercero | Tercero | Tercero | **Ganador** | **Ganador** |

---

## 8. Ventajas y desventajas

### Ventajas

| Ventaja | Detalle |
|---|---|
| **Codigo simple** | Java normal, imperativo, bloqueante. Sin callbacks, sin reactive streams, sin futures. |
| **Virtual threads nativos** | Cada request en su propio virtual thread. Millones de conexiones con ~1KB por thread. |
| **Sin Netty** | Webserver puro Java, menos dependencias, menos CVEs, menor tamano. |
| **Rendimiento excelente** | Comparable o superior a Netty puro en benchmarks. |
| **Startup rapido** | Sin escaneo de classpath, sin reflection pesada, sin autoconfiguracion. |
| **Librerias bloqueantes compatibles** | JDBC, HikariCP, Apache HttpClient, AWS SDK, todo funciona sin wrappers. |
| **Bajo consumo de memoria** | Core ligero, aplicaciones en produccion con 20-50MB. |
| **GraalVM native image** | Soporte integrado para compilar a binario nativo. |
| **Modular** | Agregas solo lo que necesitas: web, config, DB, health, metrics. |
| **Dos sabores** | SE (funcional) y MP (MicroProfile/CDI), ambos sobre Nima. |
| **Configuracion flexible** | YAML, properties, env vars, system properties, perfiles. |
| **Observabilidad integrada** | Health checks, metricas Prometheus, tracing OpenTelemetry. |

### Desventajas

| Desventaja | Detalle |
|---|---|
| **Comunidad pequena** | Menos tutoriales, menos respuestas en StackOverflow que Spring Boot o Vert.x. |
| **Ecosistema reducido** | No tiene el equivalente a los "starters" de Spring. |
| **Sin DI integrada (SE)** | Helidon SE no incluye inyeccion de dependencias. Helidon MP si (CDI). |
| **Sin ORM integrado (SE)** | No hay Spring Data/JPA en SE. SQL directo via DbClient. En MP hay JPA disponible. |
| **Java 21+ obligatorio** | No funciona con versiones anteriores de Java. |
| **Documentacion limitada** | Menos extensa que Spring Boot o Vert.x. |
| **Pinning con synchronized** | Bloques `synchronized` degradan el rendimiento si no se identifican. |
| **Menos maduro** | Helidon 4 (Nima) se lanzo en 2023. Menos anos en produccion que Spring Boot (2014). |

### Resumen rapido

```
Helidon es ideal cuando:
  + Quieres codigo simple, sin complejidad reactiva
  + Necesitas alto rendimiento con bajo footprint
  + Usas librerias JDBC/bloqueantes existentes
  + Valoras startup rapido y bajo consumo de memoria
  + Oracle/Java 21+ son parte de tu stack
  + Necesitas elegir entre funcional (SE) o empresarial (MP)

Helidon NO es ideal cuando:
  - Necesitas el ecosistema mas grande posible (→ Spring Boot)
  - Tu equipo ya domina programacion reactiva (→ Vert.x, Quarkus)
  - Necesitas DI, ORM y convenciones integradas en SE (→ Spring Boot, Quarkus)
  - No puedes migrar a Java 21+
  - Necesitas event bus distribuido (→ Vert.x)
```

---

## 9. Referencias

### Documentacion oficial

- [Documentacion oficial Helidon 4.x](https://helidon.io/docs/v4)
- [Pagina oficial Helidon Nima](https://helidon.io/nima)
- [Helidon SE Quickstart Guide](https://helidon.io/docs/v4/se/guides/quickstart)
- [Helidon DB Client Guide](https://helidon.io/docs/v4/se/guides/dbclient)
- [Helidon GitHub Repository](https://github.com/helidon-io/helidon)
- [Helidon Starter](https://helidon.io/starter)
- [Project Loom - Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)

### Articulos tecnicos clave

- [Helidon Nima — Helidon on Virtual Threads](https://medium.com/helidon/helidon-n%C3%ADma-helidon-on-virtual-threads-130bb2ea2088) — Blog oficial de Helidon por **Tomas Langer** (Architect @ Oracle). Arquitectura interna, threading model, protocolos, benchmarks vs Netty, lecciones aprendidas.
- [Helidon 4 Adopts Virtual Threads: Increased Performance and Improved DevEx](https://www.infoq.com/articles/helidon-4-adopts-virtual-threads/) — InfoQ. Cambio de paradigma reactivo a imperativo, eliminacion de Netty, comparativas blocking vs reactive, performance.
- [Helidon 4.0 with Java 21 and Virtual Threads](https://jan-leemans.medium.com/helidon-4-0-with-java-21-and-virtual-threads-exploring-the-new-features-4c460d266c66) — Exploracion practica de Helidon 4.
- [Oracle Helidon taps virtual threads for 'pure performance'](https://www.infoworld.com/article/3691808/oracle-helidon-taps-virtual-threads-for-pure-performance.html) — InfoWorld, cobertura del lanzamiento.
- [Helidon 4 vs Quarkus 3 vs Micronaut 4: Which Framework Actually Wins With Virtual Threads?](https://www.javacodegeeks.com/2026/03/helidon-4-vs-quarkus-3-vs-micronaut-4-which-framework-actually-winswith-virtual-threads.html) — Benchmark comparativo independiente (2026).
- [TechEmpower Framework Benchmarks](https://www.techempower.com/benchmarks/#section=data-r22&hw=ph&test=composite) — Resultados independientes Round 22.
