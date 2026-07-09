# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 4.1 backend for managing drone missions. Java 17, Maven (via wrapper), PostgreSQL with Spring Data JPA. Currently an early-stage scaffold — the domain (missions, drones, controllers, entities, repositories) is not yet implemented.

## JDK requirement (important)

This project targets Java 17 and uses records — but the machine's default `java` on PATH is **Java 8** (`JAVA_HOME` is unset), which fails compilation ("class, interface, or enum expected" on records) and cannot run the jar ("UnsupportedClassVersionError ... version 61.0"). A JDK 17 is installed at `C:\Users\Milica\.jdks\corretto-17.0.13`. Point Maven at it before building, and invoke that JDK's `java` binary directly to run the jar (setting `JAVA_HOME` alone does not change which `java` runs):

```bash
export JAVA_HOME="/c/Users/Milica/.jdks/corretto-17.0.13"      # for mvnw
"/c/Users/Milica/.jdks/corretto-17.0.13/bin/java" -jar target/drone-missions-0.0.1-SNAPSHOT.jar
```

## Commands

Use the Maven wrapper (`./mvnw` on Unix, `mvnw.cmd` on Windows/PowerShell).

```powershell
mvnw.cmd spring-boot:run                              # Run the app (port 8080)
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
