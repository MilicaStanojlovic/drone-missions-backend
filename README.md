# Drone Missions — Backend

Spring Boot REST API for a drone-mission marketplace. A **designer** publishes an aerial-survey mission with a flight plan, **pilots** bid on it, the designer awards one, the winning pilot flies and completes it, and both sides rate each other. **Admins** moderate.

Java 25 · Spring Boot 4.1 · PostgreSQL + Flyway · stateless JWT · springdoc OpenAPI.

## Requirements

- **JDK 25.** The code uses records, so an older `java` on `PATH` (often Java 8) fails to compile. Point `JAVA_HOME` at a JDK 25, and set the same SDK and language level in IntelliJ — otherwise the IDE and Maven overwrite each other's `target/classes`.
- **PostgreSQL** — database `drone-missions` on `localhost:5432`, user/password `postgres`/`postgres`. Flyway creates and migrates the schema at startup; Hibernate only validates it.

## Run

```powershell
mvnw.cmd spring-boot:run     # port 8085
mvnw.cmd clean package
mvnw.cmd test                # boots the Spring context, needs PostgreSQL
mvnw.cmd checkstyle:check
```

- Swagger UI: <http://localhost:8085/swagger-ui.html> (use **Authorize** with the token from login)
- No admin is seeded. Create the first one with a manual INSERT into the users table (role ADMIN, your own BCrypt hash); after that, admins create admins through POST /api/v1/users/admins.

## Roles

`DESIGNER` publishes missions · `PILOT` bids and flies · `ADMIN` moderates.

The role is chosen at registration and never changes. `ADMIN` cannot be self-registered — only an existing admin can create one.

## Mission lifecycle

```
DRAFT → PUBLISHED → BIDDING → AWARDED → IN_PROGRESS → COMPLETED
        (anything not yet COMPLETED) → CANCELLED
```

| Transition | Trigger |
|---|---|
| `PUBLISHED` → `BIDDING` | automatic, on the first bid |
| → `AWARDED` | designer accepts a bid; sets the awarded pilot |
| `AWARDED` → `IN_PROGRESS` | the awarded pilot starts it |
| `IN_PROGRESS` → `COMPLETED` | the awarded pilot completes it; ratings unlock |
| → `CANCELLED` | the owner cancels; all outstanding bids are rejected |

An edit never changes `status`. Admin `hide`/`unhide` is a separate axis (`moderation`), and admin *remove* is a hard delete.

## API

Everything is under `/api/v1` and needs `Authorization: Bearer <token>`, except register, login and the Swagger paths.

| Path | Access |
|---|---|
| `POST /auth/register`, `POST /auth/login` | public |
| `POST /auth/logout` | authenticated |
| `GET /users`, `POST /users/admins`, `POST /users/{id}/suspend`, `POST /users/{id}/reactivate` | `ADMIN` |
| `GET /users/me`, `GET /users/{id}` | authenticated |
| `POST /missions` | `DESIGNER` |
| `GET /missions` — open feed, with `location` / `keyword` / `date` filters | authenticated |
| `GET /missions/all`, `POST /missions/{id}/hide`, `/unhide`, `/remove` | `ADMIN` |
| `GET /missions/my-missions`, `GET /missions/{id}` | authenticated |
| `GET /missions/my-jobs` | `PILOT` |
| `PUT /missions/{id}`, `DELETE /missions/{id}`, `POST /missions/{id}/cancel` | `DESIGNER` |
| `POST /missions/{id}/start`, `POST /missions/{id}/complete` | `PILOT` |
| `POST /bids/mission/{missionId}`, `GET /bids/my`, `DELETE /bids/{id}` | `PILOT` |
| `GET /bids/mission/{missionId}` | authenticated |
| `POST /bids/{id}/accept` | `DESIGNER` |
| `POST /ratings/mission/{missionId}`, `GET /ratings/mission/{missionId}`, `GET /ratings/user/{userId}` | authenticated |
| `GET /notifications`, `/unread-count`, `POST /{id}/read`, `/read-all` | authenticated |
| `GET /audit-log`, `GET /platform-stats` | `ADMIN` |

Login returns the JWT in the **Authorization response header**; the body is the user profile.

### Flight plan

A mission carries `waypoints` and a `geofence`, stored as `jsonb`:

- **Waypoint** — `lat`, `lng`, `altitude` (positive, max **120 m** above ground), `action` (`PHOTO`, `START_RECORDING`, `STOP_RECORDING`, `HOVER`), `hoverDurationSeconds`. `HOVER` requires a positive duration; every other action forbids it. At least 2 waypoints per mission.
- **Geofence** — `CIRCLE` (center + radius) or `POLYGON` (3 or more points).

### Rules worth knowing

- One bid per pilot per mission — re-posting updates the existing pending bid. Accepting one rejects all the others.
- Ratings only on a `COMPLETED` mission, only between the two participants, one per rater, write-once.
- A mission or bid the caller may not see returns **404, not 403**, so ids cannot be probed.
- Suspension is enforced per action, not at login: a suspended user can still authenticate, but their actions are rejected.

## Errors

Every handled error returns `{ data, status, message }` from a single `@RestControllerAdvice`: 400 validation (with a field-to-message map), 404 not found, 401 bad credentials, 403 forbidden, 409 conflict, 500 catch-all.

One exception: a missing or invalid token is rejected by Spring Security **before MVC**, returning 401 with an empty body rather than that JSON.

## Configuration

In `application.properties`; values written as `${VAR:default}` are env-overridable.

| Property | Default | Env |
|---|---|---|
| `server.port` | `8085` | — |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/drone-missions` | — |
| `security.jwt.secret` | dev placeholder, min 32 bytes | `SECURITY_JWT_SECRET` |
| `security.jwt.expiration-ms` | `86400000` | `SECURITY_JWT_EXPIRATION_MS` |
| `app.mail.enabled` | `false` | `MAIL_ENABLED` |
| `app.frontend-url` | `http://localhost:4200` | `FRONTEND_URL` |
| `app.cache.mission.*` | `ttl=5m`, `max-size=1000` | `MISSION_CACHE_ENABLED` |

With `app.mail.enabled=false` the app logs rendered emails instead of sending them, so it runs with no SMTP credentials. Real credentials go in `application-local.properties`, which is gitignored.

Notifications are in-app rows plus best-effort async email. A daily job at 09:00 nudges pilots whose awarded mission is past its end time.

## Architecture

Two layers, organised by feature, under `com.project.drone_missions` (underscore, because the artifactId is not a legal package name):

- **`web`** — controllers, mappers, DTOs. Controllers own all DTO-to-entity mapping.
- **`business`** — services and domain exceptions. Services see only entities, never DTOs or servlet types.
- Shared: `data.model`, `data.repository`, `data.access`, `config`, `security`.

**Prefer built-in Spring features over hand-written equivalents** — method security, bean validation, `AuthenticationManager`, `@RestControllerAdvice`.

**Data access.** Business code depends on `data.access.MissionDao`, never on `MissionRepository` directly, because two services write missions and one cache has to observe all of it. Within that interface: read-only flows use `findById`, which may be cached and detached; **anything that will call `save` must use `findFresh`.** `Mission` has no `@Version`, so saving a stale cached copy would write back every field and silently revert `status` and `awardedPilotId`.

**Caching.** Mission reads go through a decorator chosen by profile: the default is the hand-written `CachingMissionDao`; `cache-spring` switches to `SpringCacheMissionDao` over Caffeine. Set it from the run configuration or `SPRING_PROFILES_ACTIVE`, not from `application.properties`. Setting `app.cache.mission.enabled=false` removes the decorator entirely. Both implementations assume a single application instance.

**Security.** The OAuth2 Resource Server support in Spring Security, with no custom filter. Tokens are HS256 with the user id as subject and a `role` claim mapped to `ROLE_*`; the request principal is a `Long` user id, read with `@AuthenticationPrincipal`. Authorization is layered: `SecurityConfig` authenticates, `@PreAuthorize` checks roles, services enforce ownership and state.

## Database

The schema is owned by Flyway (`src/main/resources/db/migration`, `V1` through `V18`) and Hibernate runs `ddl-auto=validate`. A schema change is **a new `V<n>__snake_case.sql` plus the matching entity annotation** — never an entity edit alone.

## Tests and style

18 test classes. All but one are pure unit tests needing no database; `DroneMissionsApplicationTests` boots the full context and requires PostgreSQL. The controller tests are plain Mockito, so there is no HTTP-layer coverage.

Checkstyle runs in the `validate` phase, so a violation fails the build before compilation. Enable the pre-commit hook once per clone:

```bash
git config core.hooksPath .githooks
```

`develop` is the integration branch; feature branches are cut from it.
