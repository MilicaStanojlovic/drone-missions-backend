---
name: spring-boot-dev
description: Build, run, and test the drone-missions Spring Boot backend following this repo's conventions. Use when building the app, running tests (single or full), starting the server, adding JPA entities/repositories/controllers, or setting up the local PostgreSQL database.
---

# Spring Boot Dev Workflow

Conventions and commands for the drone-missions Spring Boot 4.1 backend (Java 17, Maven wrapper, PostgreSQL + Spring Data JPA).

## Commands

Always use the Maven wrapper. On this Windows/PowerShell environment use `mvnw.cmd`; on Unix use `./mvnw`.

```powershell
mvnw.cmd spring-boot:run                              # Run the app (port 8080)
mvnw.cmd clean package                                # Build a runnable jar into target/
mvnw.cmd test                                         # Run all tests
mvnw.cmd test "-Dtest=DroneMissionsApplicationTests"  # Run a single test class
mvnw.cmd test "-Dtest=ClassName#methodName"           # Run a single test method
```

Quote `-D` arguments in PowerShell (`"-Dtest=..."`) so they aren't parsed as PowerShell switches.

## Database prerequisite

Tests use `@SpringBootTest`, which boots the full context and needs a reachable PostgreSQL. Before running the app or tests, ensure a database named `drone-missions` exists on `localhost:5432` (user/password `postgres`/`postgres`, per `src/main/resources/application.properties`).

`ddl-auto=update` means Hibernate derives the schema from entity classes — there are no migration files. Adding or changing an `@Entity` field changes the schema on next boot. `show-sql=true` logs generated SQL to the console.

## Package convention

All code lives under `com.project.drone_missions` (underscore, not hyphen) — the `drone-missions` artifactId is not a valid Java package name. Put new classes there.

## Adding domain code

Follow standard Spring layering:
- `@Entity` classes for JPA-mapped domain objects (Lombok is available — use `@Getter`/`@Setter`/`@NoArgsConstructor` etc. rather than hand-written boilerplate).
- `JpaRepository<Entity, IdType>` interfaces for persistence.
- `@RestController` classes for HTTP endpoints, delegating to `@Service` beans.

After adding code, verify with `mvnw.cmd test` (requires the database above) or at minimum `mvnw.cmd clean package` to confirm it compiles.
