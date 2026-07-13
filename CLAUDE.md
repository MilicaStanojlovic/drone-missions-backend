# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 4.1 backend for managing drone missions. Java 25 LTS, Maven (via wrapper), PostgreSQL with Spring Data JPA. Implements Mission CRUD at `/api/v1/missions`, organized in a layered, by-feature package structure (see Conventions).

## JDK requirement (important)

This project targets Java 25 LTS and uses records, so Maven and the jar must run on a JDK 25+. Set `JAVA_HOME` to your Java 25 install — `mvnw` uses it. Note the machine's default `java` on PATH may be older (Java 8), which fails compilation ("class, interface, or enum expected" on records) and cannot run the jar ("UnsupportedClassVersionError ... version 69.0"); so invoke the JDK through `$JAVA_HOME/bin/java` rather than a bare `java`:

```bash
# ensure JAVA_HOME points to a JDK 25 install, then:
"$JAVA_HOME/bin/java" -jar target/drone-missions-0.0.1-SNAPSHOT.jar
```

In IntelliJ, also set the **Project SDK** and **language level** to 21 (File → Project Structure → Project) so the IDE matches Maven; otherwise it keeps building/running with whatever SDK the run config points at.

## Commands

Use the Maven wrapper (`./mvnw` on Unix, `mvnw.cmd` on Windows/PowerShell).

```powershell
mvnw.cmd spring-boot:run                              # Run the app (port 8085)
mvnw.cmd clean package                                # Build a runnable jar into target/
mvnw.cmd test                                         # Run all tests
mvnw.cmd test "-Dtest=DroneMissionsApplicationTests"  # Run a single test class
mvnw.cmd test "-Dtest=ClassName#methodName"           # Run a single test method
```

Note: `mvnw.cmd test` boots the full Spring context (`@SpringBootTest`), which requires a reachable PostgreSQL instance — see below.

## Database

Requires a PostgreSQL database named `drone-missions` on `localhost:5432` (user/password `postgres`/`postgres`), configured in `src/main/resources/application.properties`. Hibernate runs with `ddl-auto=update`, so entity classes drive the schema automatically — there are no migration files. `show-sql=true` logs generated SQL.

## Conventions

- **Base package is `com.project.drone_missions`** (underscore, not hyphen). The artifactId `drone-missions` is not a valid Java package name, so all code lives under the underscored package. Keep new classes there.

### Layered, by-feature package structure

Two layers, each organized by feature (`*.mission`, and `*.user`, etc. as domains are added):

- **Presentation — `web`**: controllers and mappers, per feature (`web.mission.MissionController`, `web.mission.MissionMapper`). Cross-cutting web infrastructure (e.g. `GlobalExceptionHandler`) lives at the `web` root.
- **Business — `business`**: services and custom exceptions, per feature (`business.mission.MissionService`, `business.mission.MissionNotFoundException`). Shared bases (e.g. the abstract `NotFoundException`) live at the `business` root.
- Supporting packages stay shared: `dto` (request/response records), `model` (JPA entities), `repository`, `config`.

### Strict separation of concerns

- **Controllers own all DTO↔entity mapping**, delegating to the feature's `*Mapper` — request in via `mapper.toEntity(...)`, entity out via `mapper.toResponse(...)`.
- **Services operate exclusively on entities** — no DTOs, no HTTP/servlet types, no mapper. Domain rules live here (e.g. `MissionService.update` never changes a mission's `status`). This keeps business logic testable without the web layer.
- **DTOs are records**: `*Request` for input (bean-validated with `@NotBlank`/`@NotNull`), `*Response` for output. Server-owned fields (`id`, timestamps) are never accepted on create/update.

### Lombok

- Use **`@AllArgsConstructor`** on Spring beans (controllers, services) for constructor injection instead of hand-written constructors.

### Exception handling

- A single `@RestControllerAdvice` **`GlobalExceptionHandler`** (in `web`). Every response is built from an immutable **`ErrorResponse` record** via its Lombok **`@Builder`**.
- Handlers, most-specific first: `MethodArgumentNotValidException` → 400 (per-field errors); `HttpMessageNotReadableException` → 400 (malformed body / unknown enum, so client errors never surface as 500); `NotFoundException` (base) → 404; **catch-all `Exception`** → 500 generic.
- **Custom exceptions are domain-specific and self-documenting.** `NotFoundException` is an abstract base; each domain extends it (`MissionNotFoundException`, and `UserNotFoundException` once a user domain exists). The exception *type* conveys the error context — no need to read the message. Don't throw the base directly.
