# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 4.1 backend for managing drone missions. Java 25 LTS, Maven (via wrapper), PostgreSQL with Spring Data JPA. Implements Mission CRUD at `/api/v1/missions` and stateless JWT authentication / account management at `/api/v1/auth`, organized in a layered, by-feature package structure (see Conventions).

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

Requires a PostgreSQL database named `drone-missions` on `localhost:5432` (user/password `postgres`/`postgres`), configured in `src/main/resources/application.properties`. `show-sql=true` logs generated SQL.

**The schema is owned by Flyway migrations** in `src/main/resources/db/migration` (`V1__…` … `V6__…`, applied in version order at startup). Hibernate runs with `ddl-auto=validate` — it never alters the schema, only checks that the entities match what Flyway migrated (a mismatch fails boot). So a schema change is a **new versioned migration file plus the matching entity/column annotation** — never an entity edit alone. Name the next file `V<n>__snake_case_description.sql` and keep the entity's `@Column` (length/nullability) in sync so validation passes.

## Conventions

### Spring-first — use the framework, don't reinvent it (read before writing any code)

This is a Spring Boot application. **Prefer Spring Boot / Spring Security built-in features over hand-written code, always** — for security and for everything else. Before adding a class, filter, resolver, annotation, or helper, assume Spring already provides it and go find that feature first.

- **Do not build something new or more complex when Spring already offers it and it fits.** A custom class that re-implements framework behaviour is a defect here, not a neutral choice — even if it works and is tested.
- **Research before you implement.** Consult the official Spring reference documentation and, when useful, a web search for the current idiomatic approach for the Spring version in use (Spring Boot 4.1 / Spring Security 6+). Do not rely on memory or on older patterns; verify against the docs. Cite what you found when proposing an approach.
- **Reach for the framework's own mechanisms**, e.g. method security (`@EnableMethodSecurity`, `@PreAuthorize`/`@PostAuthorize`), `authorizeHttpRequests` rules, `@AuthenticationPrincipal`, argument resolvers, bean validation, `AuthenticationManager`/`UserDetailsService`, exception handling via `@RestControllerAdvice` — rather than bespoke equivalents.
- **When a built-in and a custom approach both work, choose the built-in** and keep the custom surface as small as possible. If custom code is genuinely unavoidable, say explicitly why the Spring feature does not cover the case before writing it.

### Comments and TODOs — never delete mine

- **Never delete, move, or rewrite a comment the author added** — most importantly `// TODO` comments, but any comment. Treat them as fixed markers.
- When refactoring or replacing surrounding code, **carry the author's comments across verbatim**, keeping them attached to the line or block they annotate.
- If a comment looks obsolete or wrong, **leave it in place and flag it** in your summary — do not remove it on your own initiative. Removal only ever happens when the author explicitly asks.

- **Base package is `com.project.drone_missions`** (underscore, not hyphen). The artifactId `drone-missions` is not a valid Java package name, so all code lives under the underscored package. Keep new classes there.

### Layered, by-feature package structure

Two layers, each organized by feature (`*.mission`, and `*.user`, etc. as domains are added):

- **Presentation — `web`**: controllers and mappers, per feature (`web.mission.MissionController`, `web.mission.MissionMapper`). Cross-cutting web infrastructure (e.g. `GlobalExceptionHandler`) lives at the `web` root.
- **Business — `business`**: services and custom exceptions, per feature (`business.mission.MissionService`, `business.mission.MissionNotFoundException`). Shared bases (e.g. the abstract `NotFoundException`) live at the `business` root.
- Supporting packages stay shared: `data.model` (JPA entities), `data.repository`, `config`, and `security` (JWT/authentication infrastructure). Request/response records live under `web.dto.<feature>`.

### Strict separation of concerns

- **Controllers own all DTO↔entity mapping**, delegating to the feature's `*Mapper` — request in via `mapper.toEntity(...)`, entity out via `mapper.toResponse(...)`.
- **Services operate exclusively on entities** — no DTOs, no HTTP/servlet types, no mapper. Domain rules live here (e.g. `MissionService.update` never changes a mission's `status`). This keeps business logic testable without the web layer.
- **DTOs are records**: `*Request` for input (bean-validated with `@NotBlank`/`@NotNull`), `*Response` for output. Server-owned fields (`id`, timestamps) are never accepted on create/update.

### Lombok

- Use **`@AllArgsConstructor`** on Spring beans (controllers, services) for constructor injection instead of hand-written constructors.

### Exception handling

- A single `@RestControllerAdvice` **`GlobalExceptionHandler`** (in `web`). Every response is built from an immutable **`ErrorResponse` record** via its Lombok **`@Builder`**.
- Handlers, most-specific first: `MethodArgumentNotValidException` → 400 (per-field errors); `HttpMessageNotReadableException` → 400 (malformed body / unknown enum, so client errors never surface as 500); `NotFoundException` → 404; `UnauthorizedException` → 401; `ForbiddenException` → 403; `ConflictException` → 409; **catch-all `Exception`** → 500 generic.
- **Custom exceptions are domain-specific and self-documenting.** Each HTTP error class has an abstract base at the `business` root — `NotFoundException` (404), `UnauthorizedException` (401), `ForbiddenException` (403), `ConflictException` (409) — and every domain extends the right one (e.g. `MissionNotFoundException`, `UserNotFoundException`, `InvalidCredentialsException`, `MissionAccessDeniedException`, `EmailAlreadyExistsException`). The exception *type* conveys the error context — no need to read the message. Don't throw a base directly.

### Security & authentication

Built on Spring Security's **OAuth2 Resource Server** — use its built-ins, don't hand-roll JWT plumbing (see Spring-first above). Config is `config.SecurityConfig`.

- **Stateless JWT.** All `/api/v1/**` endpoints require a valid `Authorization: Bearer <token>` **except** `POST /api/v1/auth/register` and `POST /api/v1/auth/login` (and the Swagger paths, below). Requests are authenticated by Spring's built-in `BearerTokenAuthenticationFilter` + a `JwtDecoder` bean (`NimbusJwtDecoder`, HS256, symmetric secret) — there is no custom filter.
- **Tokens are minted** in `business.auth.AuthService` with Spring's `JwtEncoder` (`NimbusJwtEncoder`): subject = user id, plus a `role` claim. The HS256 secret and expiry come from `security.jwt.*` in `application.properties` (override via `SECURITY_JWT_SECRET` / `SECURITY_JWT_EXPIRATION_MS` in prod). Login returns the token in the `Authorization` **response header** and the profile in the body.
- **Principal is the user id (`Long`)**, set by the JWT→authentication converter in `SecurityConfig` (a private method, not a class). Read it with the custom `@CurrentUserId Long userId` param (a meta-annotation over `@AuthenticationPrincipal`). Never trust a user id from the request body.
- **Roles** (`DESIGNER` / `PILOT`, fixed at registration) ride in the token's `role` claim → mapped to a `ROLE_<role>` authority by Spring's `JwtGrantedAuthoritiesConverter`. **Role gating uses `@PreAuthorize`** (method security is on via `@EnableMethodSecurity`) — e.g. `@PreAuthorize("hasRole('DESIGNER')")` on mission create. Prefer `@PreAuthorize` over `SecurityConfig` request-matchers for role rules, and keep a single source (no duplicate rule in both places).
- **Authorization is layered:** authentication rules in `SecurityConfig`; role checks via `@PreAuthorize`; **data-dependent rules in the service.** Mission visibility is role-free — `MissionService` decides by ownership + status: the open marketplace (`PUBLISHED`/`BIDDING`) is visible to all, `my-missions` is the caller's own, a single mission is visible if owned or open. **Ownership** for edit/delete is enforced there too (`MissionAccessDeniedException` → 403).
- **Passwords** are BCrypt-hashed (`PasswordEncoder` bean) and never returned — `UserResponse` excludes the hash. Login credential checks go through the built-in `AuthenticationManager`, backed by `security.CustomUserDetailsService` + `security.UserPrincipal`.
- **Error responses:** missing/invalid token → 401 via Spring Security's **default `BearerTokenAuthenticationEntryPoint`** (fires at the filter layer, before MVC — it returns a `WWW-Authenticate: Bearer` header with an empty body, **not** an `ErrorResponse` JSON body). Role/permission denials → 403 via `GlobalExceptionHandler` (`AuthorizationDeniedException` from `@PreAuthorize`, and the `ForbiddenException` family).

### API documentation (Swagger / OpenAPI)

- **springdoc-openapi** (`springdoc-openapi-starter-webmvc-ui`, the 3.x line for Spring Boot 4). Swagger UI at `/swagger-ui.html`, the OpenAPI JSON at `/v3/api-docs`. These paths are `permitAll` in `SecurityConfig` so the docs load without a token (dev convenience — lock down in prod if needed).
- `config.OpenApiConfig` declares a `bearer`/JWT security scheme so the Swagger UI **Authorize** button lets you test secured endpoints (paste the token from login's `Authorization` header).
