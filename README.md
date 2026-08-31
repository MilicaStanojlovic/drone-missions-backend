# Drone Missions — Backend

A Spring Boot REST API for a **drone-mission marketplace**.

A **designer** publishes an aerial-survey mission with a flight plan — an ordered route of waypoints, a permitted flight zone, and a bidding deadline. **Pilots** bid on it. The designer awards one bid, the winning pilot flies the mission and marks it complete, and afterwards both sides rate each other. **Admins** moderate the platform: they suspend accounts, hide or remove missions, browse an audit trail of every state-changing action, and read platform-wide statistics.

Built on Spring Boot 4.1 / Java 25, PostgreSQL with a Flyway-owned schema, and stateless JWT authentication on Spring Security's OAuth2 Resource Server.

---

## Contents

- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Domain model](#domain-model)
- [Mission lifecycle](#mission-lifecycle)
- [API reference](#api-reference)
- [DTO reference](#dto-reference)
- [Business rules](#business-rules)
- [Notifications and email](#notifications-and-email)
- [Audit log](#audit-log)
- [Architecture](#architecture)
- [Configuration reference](#configuration-reference)
- [Database and migrations](#database-and-migrations)
- [Testing](#testing)
- [Code style and contributing](#code-style-and-contributing)
- [Project layout](#project-layout)

---

## Tech stack

| Area | What is used |
|---|---|
| Framework | Spring Boot **4.1.0** (`spring-boot-starter-parent`), artifact `com.project:drone-missions:0.0.1-SNAPSHOT` |
| Language | **Java 25 LTS** — records, so a JDK 25+ is mandatory |
| Build | Maven Wrapper (`mvnw` / `mvnw.cmd`) |
| Web | `spring-boot-starter-webmvc` (the Boot 4 name, not `-web`), `spring-boot-starter-validation` |
| Persistence | `spring-boot-starter-data-jpa`, PostgreSQL driver (runtime) |
| Migrations | `spring-boot-flyway` + `flyway-database-postgresql` |
| Security | `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` |
| Mail | `spring-boot-starter-mail` + `spring-boot-starter-thymeleaf` (HTML email bodies) |
| Caching | `spring-boot-starter-cache` + Caffeine (used only by the `cache-spring` profile) |
| API docs | `springdoc-openapi-starter-webmvc-ui:3.0.3` — the 3.x line targets Boot 4 |
| Codegen | Lombok (optional scope, wired through `annotationProcessorPaths`) |
| Test | `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test` |
| Style | `maven-checkstyle-plugin:3.6.0`, Checkstyle core pinned to **13.9.0** |

`spring-boot-maven-plugin` excludes Lombok from the fat jar.

---

## Getting started

### 1. JDK 25 is required

This project targets Java 25 and uses records, so both Maven and the jar must run on a JDK 25+. Point `JAVA_HOME` at your Java 25 install — `mvnw` uses it.

On many machines the default `java` on `PATH` is older (Java 8). That fails compilation with `class, interface, or enum expected` on records, and cannot run the jar (`UnsupportedClassVersionError ... version 69.0`). Invoke the JDK explicitly:

```bash
"$JAVA_HOME/bin/java" -jar target/drone-missions-0.0.1-SNAPSHOT.jar
```

In IntelliJ, also set the **Project SDK** and **language level** to 25 (File → Project Structure → Project) and check the run configuration's JRE. Otherwise the IDE and Maven overwrite each other's `target/classes` with incompatible class-file versions.

### 2. PostgreSQL

Create a database named `drone-missions` on `localhost:5432`, user/password `postgres`/`postgres` (configurable — see [Configuration reference](#configuration-reference)).

The schema is created and migrated by **Flyway at startup**; you do not need to create any tables yourself.

### 3. Run

```powershell
mvnw.cmd spring-boot:run                              # run the app (port 8085)
mvnw.cmd clean package                                # build a runnable jar into target/
mvnw.cmd test                                         # run all tests
mvnw.cmd test "-Dtest=DroneMissionsApplicationTests"  # a single test class
mvnw.cmd test "-Dtest=ClassName#methodName"           # a single test method
mvnw.cmd checkstyle:check                             # style only
```

On Unix use `./mvnw` instead of `mvnw.cmd`.

> `mvnw.cmd test` boots the full Spring context for one test class, which requires a reachable PostgreSQL. See [Testing](#testing) for what runs without a database.

### 4. API docs

| | |
|---|---|
| Swagger UI | <http://localhost:8085/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8085/v3/api-docs> |

Both are `permitAll`, so the docs load without a token. `OpenApiConfig` declares a `bearerAuth` security scheme, so the **Authorize** button lets you exercise secured endpoints — paste the token returned in login's `Authorization` response header.

### 5. First admin account

- No admin is seeded. Create the first one with a manual INSERT into the users table (role ADMIN, your own BCrypt hash); after that, admins create admins through POST /api/v1/users/admins.

> **Rotate this account's password before any real deployment.** The hash lives in a committed migration and must be treated as public.

### 6. A five-minute walkthrough

1. `POST /api/v1/auth/register` twice — once with `"role": "DESIGNER"`, once with `"role": "PILOT"`.
2. `POST /api/v1/auth/login` as each; keep the token from the `Authorization` response header.
3. As the designer, `POST /api/v1/missions` with `"status": "PUBLISHED"` and at least two waypoints.
4. As the pilot, `GET /api/v1/missions` to see it in the open feed, then `POST /api/v1/bids/mission/{missionId}`. The mission flips to `BIDDING`.
5. As the designer, `GET /api/v1/bids/mission/{missionId}`, then `POST /api/v1/bids/{bidId}/accept`. The mission becomes `AWARDED` and the pilot is notified.
6. As the pilot, `POST /api/v1/missions/{id}/start`, then `POST /api/v1/missions/{id}/complete`.
7. Both sides `POST /api/v1/ratings/mission/{missionId}`.

---

## Domain model

Six JPA entities in `data.model`.

### `User` (table `users` — `user` is reserved in PostgreSQL)

| Field | Notes |
|---|---|
| `id` | identity PK |
| `username` | not null |
| `email` | not null, **unique** — the login identifier |
| `passwordHash` | BCrypt; never leaves the server |
| `role` | `DESIGNER` / `PILOT` / `ADMIN`, fixed at registration and never changed |
| `suspended` | boolean, not null |
| `createdAt` / `updatedAt` | Hibernate timestamps |

`User` has no outgoing associations — it is the referenced side everywhere.

### `Mission`

| Field | Notes |
|---|---|
| `id`, `name`, `description` | description is 2000 chars |
| `status` | `MissionStatus`, not null — the lifecycle |
| `moderation` | `VISIBLE` / `HIDDEN`, not null, defaults `VISIBLE` — an admin axis, orthogonal to `status` |
| `designer` | `@ManyToOne User` (`user_id`), **nullable** for legacy rows created before authentication existed |
| `awardedPilot` | `@ManyToOne User` (`awarded_pilot_id`), null until a bid is accepted |
| `startTime`, `endTime` | the flight window |
| `location`, `biddingDeadline` | |
| `waypoints` | `List<Waypoint>` stored as **`jsonb`** |
| `geofence` | `Geofence` stored as **`jsonb`** |
| `createdAt` / `updatedAt` | |

`getDesignerId()` and `getAwardedPilotId()` are null-safe accessors, because both sides are legitimately nullable.

### `Bid`

`mission` and `pilot` FKs (both not null), `amount` `NUMERIC(12,2)`, optional `message` (500 chars), `status`, timestamps. A **unique constraint on `(mission_id, pilot_id)`** enforces one bid per pilot per mission. Bids cascade-delete with their mission.

### `Rating`

`mission`, `rater` and `ratee` FKs, `score` `SMALLINT` constrained 1–5, optional `comment` (500 chars), `createdAt` only — ratings are **write-once**, pinned by a unique constraint on `(mission_id, rater_id)`. The relation to `Mission` is `@ManyToOne`, not `@OneToOne`, because one mission carries two ratings — one per side.

### `Notification`

`user` FK, `type`, `title` (200), `message` (1000), optional `mission` FK, `readAt` (null while unread), timestamps. Indexed on `(user_id, created_at DESC)`.

### `AuditLog`

`actor` FK, `actorRole` (a **snapshot** of the role at the time of the action), `action`, and the target as a **`targetType` + `targetId` pair rather than an association** — so history outlives the rows it describes. Optional `details` snapshot (500 chars), `createdAt`. Immutable once written.

### Enums

| Enum | Values |
|---|---|
| `UserRole` | `DESIGNER`, `PILOT`, `ADMIN` |
| `MissionStatus` | `DRAFT`, `PUBLISHED`, `BIDDING`, `AWARDED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `MissionModeration` | `VISIBLE`, `HIDDEN` |
| `BidStatus` | `PENDING`, `ACCEPTED`, `REJECTED` |
| `NotificationType` | `BID_ACCEPTED`, `BID_REJECTED`, `MISSION_OVERDUE`, `MISSION_CANCELLED` |
| `AuditTargetType` | `MISSION`, `BID`, `USER`, `RATING` |
| `GeofenceType` | `CIRCLE`, `POLYGON` |
| `WaypointAction` | `PHOTO`, `START_RECORDING`, `STOP_RECORDING`, `HOVER` |
| `AuditAction` | 18 values — see [Audit log](#audit-log) |

### The flight plan

`Waypoint` and `Geofence` are **records serialized into `jsonb` columns**, not entities. They carry their own bean-validation constraints, so an invalid flight plan is a 400 rather than a persistence error.

**`Waypoint(lat, lng, altitude, action, hoverDurationSeconds)`**

- `lat` ∈ [-90, 90], `lng` ∈ [-180, 180] (WGS84 degrees)
- `altitude` — required, positive, **capped at 120** metres above ground level, the legal ceiling in most jurisdictions
- `action` — required; what the drone does on arrival
- `hoverDurationSeconds` — conditional, see below

A custom constraint, `@ValidWaypointAction` (`WaypointActionValidator`), ties the last two together:

| `action` | `hoverDurationSeconds` |
|---|---|
| `HOVER` | **required**, must be positive |
| `PHOTO`, `START_RECORDING`, `STOP_RECORDING` | **must be absent** |

Violations are reported against the `hoverDurationSeconds` property, so they surface as an ordinary field error.

`altitude` and `action` are nullable on the record itself so waypoints written before those fields existed still deserialize; they are made mandatory by validation on *requests* only.

**`Geofence(type, center, radiusMeters, points)`** — the permitted flight area, exactly one shape. A class-level `@AssertTrue` enforces that the fields present match the declared type:

| `type` | Requires |
|---|---|
| `CIRCLE` | `center` + `radiusMeters` (positive, metres) |
| `POLYGON` | `points` — an ordered ring of **at least 3** `GeoPoint`s |

Unused fields are omitted from the JSON. A mission request must carry **at least two waypoints**; the geofence is optional.

---

## Mission lifecycle

```
   DRAFT            (created as such; not visible to pilots)

   PUBLISHED ──first bid arrives──> BIDDING
       │                               │
       └────designer accepts a bid─────┘
                     │
                     v
                  AWARDED ──awarded pilot starts──> IN_PROGRESS
                                                         │
                                        awarded pilot completes
                                                         v
                                                     COMPLETED ──> ratings unlock

   anything except COMPLETED / CANCELLED ──owner cancels──> CANCELLED
```

| Transition | Trigger | Actor |
|---|---|---|
| → initial status | `POST /api/v1/missions` — taken from the request body | designer |
| `PUBLISHED` → `BIDDING` | **automatic**, on the first bid (`BidService.place`) | pilot |
| `PUBLISHED`/`BIDDING` → `AWARDED` | `POST /api/v1/bids/{id}/accept`; also sets `awardedPilot` | designer (owner) |
| `AWARDED` → `IN_PROGRESS` | `POST /api/v1/missions/{id}/start` | the awarded pilot |
| `IN_PROGRESS` → `COMPLETED` | `POST /api/v1/missions/{id}/complete` | the awarded pilot |
| any except `COMPLETED`/`CANCELLED` → `CANCELLED` | `POST /api/v1/missions/{id}/cancel` | designer (owner) |

Rules that hold across the whole machine:

- **An edit never changes status.** `PUT /missions/{id}` copies name, description, times, location, deadline, waypoints and geofence — and deliberately leaves `status` alone.
- **A mission never advances on its own.** Starting and completing are explicit pilot actions; no scheduler moves a mission forward.
- **Cancelling rejects every outstanding bid** — both `PENDING` and an already `ACCEPTED` one — so no pilot is left expecting to win, and the awarded pilot is notified in-app and by email.
- **Moderation is a separate axis.** `hide`/`unhide` flips `moderation` between `VISIBLE` and `HIDDEN` without touching `status`. Admin *removal* is a real hard delete, not a state.

> **Current behaviour worth knowing:** the initial status is taken straight from `MissionRequest.status` (required, but not otherwise validated) — the service neither forces `DRAFT` nor restricts the starting value. Since `update` never touches status, **there is no `DRAFT → PUBLISHED` transition**: a mission can only become published by being *created* that way.

---

## API reference

Every endpoint lives under `/api/v1`. All of them require an `Authorization: Bearer <token>` header **except** `POST /auth/register`, `POST /auth/login` and the Swagger paths.

Role gating is declared per method with `@PreAuthorize`; the "Access" column below is that expression. Rules that depend on *data* rather than role — ownership, mission state, suspension — live in the services and are listed under [Business rules](#business-rules).

### Authentication — `/api/v1/auth`

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/auth/register` | public | Self-registration as `DESIGNER` or `PILOT`. Requesting `ADMIN` is rejected. Password is BCrypt-hashed. → `201` + `UserResponse` |
| `POST` | `/auth/login` | public | Authenticates and mints a JWT. The token comes back in the **`Authorization` response header**; the body is the caller's `UserResponse` |
| `POST` | `/auth/logout` | authenticated | No-op, present for API symmetry — tokens are stateless, so the client simply discards its token. → `204` |

### Users — `/api/v1/users`

| Method | Path | Access | Description |
|---|---|---|---|
| `GET` | `/users` | `ADMIN` | Paged account listing, optional `role` filter. Default size 20, sorted `createdAt` DESC |
| `GET` | `/users/me` | authenticated | The caller's own profile |
| `GET` | `/users/{id}` | authenticated | Another account's **public** profile — `PublicUserResponse`, which deliberately omits the email |
| `POST` | `/users/admins` | `ADMIN` | The only runtime path that creates an `ADMIN`. → `201` |
| `POST` | `/users/{id}/suspend` | `ADMIN` | Suspends an account. Admins cannot be suspended |
| `POST` | `/users/{id}/reactivate` | `ADMIN` | Lifts a suspension |

### Missions — `/api/v1/missions`

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/missions` | `DESIGNER` | Creates a mission owned by the caller. → `201` + `Location` header |
| `GET` | `/missions` | authenticated | **The open marketplace.** `PUBLISHED`/`BIDDING`, `VISIBLE`, unsuspended designer. Optional `location`, `keyword` and `date` filters |
| `GET` | `/missions/all` | `ADMIN` | Paged listing of every mission regardless of status or moderation; optional `q` over name and designer |
| `GET` | `/missions/my-missions` | authenticated | Missions the caller created |
| `GET` | `/missions/my-jobs` | `PILOT` | Missions awarded to the calling pilot |
| `GET` | `/missions/{id}` | authenticated | One mission, if visible to the caller |
| `PUT` | `/missions/{id}` | `DESIGNER` | Owner-only edit. Never changes `status` |
| `DELETE` | `/missions/{id}` | `DESIGNER` | Owner-only delete. → `204` |
| `POST` | `/missions/{id}/start` | `PILOT` | Awarded pilot: `AWARDED` → `IN_PROGRESS` |
| `POST` | `/missions/{id}/complete` | `PILOT` | Awarded pilot: `IN_PROGRESS` → `COMPLETED` |
| `POST` | `/missions/{id}/cancel` | `DESIGNER` | Owner cancels; rejects outstanding bids, notifies the awarded pilot |
| `POST` | `/missions/{id}/hide` | `ADMIN` | `moderation` → `HIDDEN`; drops out of the public feed, reversible |
| `POST` | `/missions/{id}/unhide` | `ADMIN` | `moderation` → `VISIBLE` |
| `POST` | `/missions/{id}/remove` | `ADMIN` | **Permanent hard delete**; bids, notifications and ratings cascade. Only the audit row survives. → `204` |

The open-feed filters behave as follows: blank `location`/`keyword` and a null `date` mean "not filtering"; both text filters match case-insensitively; `date` selects missions **flyable on that day** — whose flight window overlaps it — with day boundaries computed in the server's local timezone so the filter matches the dates as entered and displayed.

### Bids — `/api/v1/bids`

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/bids/mission/{missionId}` | `PILOT` | Places a bid, or **updates** the caller's existing pending one. The first bid flips a `PUBLISHED` mission to `BIDDING` and emails the designer |
| `GET` | `/bids/mission/{missionId}` | authenticated | The owning designer sees every bid; anyone else sees only their own — one endpoint feeding both the designer's list and the pilot's "your bid" panel |
| `GET` | `/bids/my` | `PILOT` | Every bid the caller has placed, newest first |
| `DELETE` | `/bids/{id}` | `PILOT` | Withdraws (deletes) the caller's own pending bid. → `204` |
| `POST` | `/bids/{id}/accept` | `DESIGNER` | Awards the mission: this bid `ACCEPTED`, all others `REJECTED`, mission `AWARDED`; everyone involved is notified |

There is no reject endpoint — rejection is only ever automatic, on acceptance or on mission cancellation.

### Ratings — `/api/v1/ratings`

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/ratings/mission/{missionId}` | authenticated | Either participant rates the other side of a **completed** mission. The ratee is derived from the mission — you cannot choose who you rate |
| `GET` | `/ratings/mission/{missionId}` | authenticated | Both ratings on a mission, so a participant can see whether they have rated. Participants only |
| `GET` | `/ratings/user/{userId}` | authenticated | A user's average, count, and every rating they received, in one call |

### Notifications — `/api/v1/notifications`

| Method | Path | Access | Description |
|---|---|---|---|
| `GET` | `/notifications` | authenticated | The caller's notifications, newest first |
| `GET` | `/notifications/unread-count` | authenticated | `{"count": n}` — for the bell badge |
| `POST` | `/notifications/{id}/read` | authenticated | Marks one read; idempotent, owner only. → `204` |
| `POST` | `/notifications/read-all` | authenticated | Marks all of the caller's notifications read. → `204` |

### Audit log — `/api/v1/audit-log`

| Method | Path | Access | Description |
|---|---|---|---|
| `GET` | `/audit-log` | `ADMIN` | Paged, filterable trail of every state-changing action. Filters: `actorId`, `action`, `role`, `q` (free text over actor and details) |

### Platform statistics — `/api/v1/platform-stats`

| Method | Path | Access | Description |
|---|---|---|---|
| `GET` | `/platform-stats` | `ADMIN` | Dashboard aggregates: missions by status, active pilots, bid count and total value, suspended users, users by role, and the top six missions by bid count |

Status and role maps are **zero-filled**, so a missing key never has to be interpreted by the client. Top missions carry the mission name only, never an id.

### Paging

Paged endpoints (`/users`, `/missions/all`, `/audit-log`) accept Spring's standard `page`, `size` and `sort` parameters and return a `PagedModel` envelope. `size` is clamped to **100** by `spring.data.web.pageable.max-page-size`.

---

## DTO reference

All DTOs are records under `web.dto.<feature>`. `*Request` types carry bean validation; `*Response` types are plain carriers. Server-owned fields — ids, timestamps, ownership — are never accepted on create or update.

### Requests

| Record | Fields and constraints |
|---|---|
| `LoginRequest` | `email` `@NotBlank`; `password` `@NotBlank` |
| `RegisterRequest` | `username` `@NotBlank`; `email` `@NotBlank @Email`; `password` `@NotBlank @Size(min=8)`; `role` `@NotNull` — permanent, no endpoint changes it |
| `NewAdminRequest` | `username`, `email`, `password` — same constraints; the role is always `ADMIN`, so it is not a field |
| `MissionRequest` | `name` `@NotBlank`; `description` `@Size(max=2000)`; `status` `@NotNull`; `startTime`/`endTime` `@NotNull`; `location` `@Size(max=255)`; `biddingDeadline`; `waypoints` `@NotNull @Size(min=2) @Valid`; `geofence` `@Valid` |
| `BidRequest` | `amount` `@NotNull @Positive`; `message` `@Size(max=500)` |
| `RatingRequest` | `score` `@NotNull @Min(1) @Max(5)`; `comment` `@Size(max=500)` |

### Responses

| Record | Fields |
|---|---|
| `UserResponse` | `id`, `username`, `email`, `role`, `suspended`, `createdAt` — never the password hash |
| `PublicUserResponse` | `id`, `username`, `role`, `createdAt` — no email |
| `MissionResponse` | `id`, `name`, `description`, `status`, `moderation`, `userId`, `designerEmail`, `designerName`, `designerSuspended`, `designerRating`, `designerRatingCount`, `awardedPilotId`, `startTime`, `endTime`, `location`, `biddingDeadline`, `waypoints`, `geofence`, `createdAt`, `updatedAt` |
| `BidResponse` | `id`, `missionId`, `missionName`, `pilotId`, `pilotName`, `amount`, `message`, `status`, `createdAt`, `updatedAt` |
| `RatingResponse` | `id`, `missionId`, `missionName`, `raterId`, `raterName`, `rateeId`, `score`, `comment`, `createdAt` |
| `UserRatingsResponse` | `average`, `count`, `ratings` |
| `NotificationResponse` | `id`, `type`, `title`, `message`, `missionId`, `read`, `createdAt` |
| `AuditLogResponse` | `id`, `actorId`, `actorUsername`, `actorRole`, `action`, `targetType`, `targetId`, `details`, `createdAt` |
| `PlatformStatsResponse` | `missionsByStatus`, `activePilots`, `bidCount`, `bidAmountTotal`, `suspendedUsers`, `usersByRole`, `topMissionsByBids` (nested `TopMissionResponse(name, bids)`) |

`MissionResponse` embeds the designer's rating summary so a mission feed does not need a second round-trip per card; the summaries for a whole page are fetched in one aggregate query.

---

## Business rules

Role gating answers "may this *kind* of user call this?". Everything below answers "may *this* user do this to *this* row?", and lives in the service layer.

### Bidding

- **One bid per pilot per mission**, enforced by a unique constraint. Re-posting to the same mission **updates** the existing pending bid rather than creating a second one.
- Bids are accepted only while the mission is `PUBLISHED` or `BIDDING`.
- The **bidding deadline day itself is still open**; bidding closes once that date has passed.
- A bid that has already been decided can be neither changed nor withdrawn.
- Accepting one bid rejects every other pending bid on that mission, in a single transaction.
- **A suspended pilot's bid is frozen, not rejected.** Accepting it fails while they are suspended, and becomes possible again if they are reactivated.
- Withdrawing deletes the row, so withdrawn bids do not count toward platform bid volume.

### Ratings

- Only on a **`COMPLETED`** mission.
- Only the two participants — the designer and the awarded pilot. The counterpart is derived from the mission row, so the ratee cannot be chosen or spoofed.
- **One rating per (mission, rater)**, write-once: no update endpoint, no `updatedAt`.
- Score 1–5, validated in the DTO and constrained in the database.
- Users with no ratings are absent from summary maps rather than reported as zero, so callers decide what "unrated" should look like.

### Visibility, and 404 in place of 403

A mission is visible to its owner, to the awarded pilot, or to anyone at all once it is open for work (`PUBLISHED`/`BIDDING`, `VISIBLE`, and the designer not suspended).

A mission the caller may **not** see raises **404, not 403** — deliberately. A 403 would confirm that the id exists, which is exactly what a probe is looking for. The same masking is applied to:

- another pilot's bid (`BidNotFoundException`),
- someone else's notification (`NotificationNotFoundException`),
- a hidden mission, or one whose designer is suspended, when bidding.

### Suspension

- Blocks creating missions, placing bids, and starting or completing a mission.
- **Admins cannot be suspended.**
- Suspend and reactivate are idempotent — if the account is already in that state, nothing is written and nothing is audited.
- Suspending also invalidates the cached mission lists: a suspended designer's missions leave the public feed, but the write lands on the `users` table, which the mission cache would otherwise never observe.

> Suspension is enforced **per action, not at login**. `UserPrincipal` reports `isEnabled()` as `true` unconditionally, so a suspended user can still authenticate and hold a valid token; the services reject what they try to do with it.

### Authentication

- Login is **enumeration-safe**: every `AuthenticationException` collapses into a single `InvalidCredentialsException`, so "unknown email" and "wrong password" are indistinguishable to a caller.
- `ADMIN` cannot be obtained by self-registration.
- Passwords are BCrypt-hashed and never appear in any response.

### Search

Both the admin mission search and the audit-log free-text filter build their `%…%` LIKE pattern **in Java**, lowercased, because `lower(null)` breaks in PostgreSQL. The term is not escaped, so `%` and `_` typed by an admin act as wildcards.

---

## Notifications and email

Two delivery channels, always driven together: a persisted in-app `Notification` row, and a **best-effort** HTML email.

### In-app

`NotificationService` writes the row; the client polls `/notifications` and `/notifications/unread-count`. Four types exist: `BID_ACCEPTED`, `BID_REJECTED`, `MISSION_OVERDUE`, `MISSION_CANCELLED`.

Wording lives in one place. `NewNotification` is a record with static factories — `bidAccepted(...)`, `bidRejected(...)`, `missionCancelled(...)`, `missionOverdue(...)` — so the same message is never phrased two ways at two call sites.

### Email

Bodies are Thymeleaf templates in `src/main/resources/templates/email/`:

| Sender | Trigger | Subject | Template |
|---|---|---|---|
| `sendNewBid` | a pilot places or raises a bid | `New bid on "<mission>"` | `new-bid.html` |
| `sendBidDecision` | a bid is accepted — sent to the winner and to every loser | `Your bid on "…" was accepted` / `Update on your bid for "…"` | `bid-accepted.html` / `bid-rejected.html` |
| `sendMissionCancelled` | the designer cancels a mission that was already awarded | `Mission "…" was cancelled` | `mission-cancelled.html` |
| `sendMissionOverdue` | the daily overdue sweep | `Has your flight for "…" ended?` | `mission-overdue.html` |

Two properties of the mail path matter operationally:

- **Every send is `@Async`** (`@EnableAsync` on the application class) and **best-effort**. A template failure or an SMTP failure is logged and swallowed — it never propagates into the bidding or scheduler flow, so a broken mail server cannot fail a bid.
- **`app.mail.enabled` is `false` by default.** In that mode `EmailService` logs the rendered HTML instead of dispatching it, so the application runs correctly with no SMTP credentials at all. Flip it to `true` to send for real.

`app.mail.redirect-to` is a development convenience: when set, every message is delivered to that one inbox with the intended recipient tagged into the subject. `app.frontend-url` builds the call-to-action link, `<frontendUrl>/missions/<id>` (rejection emails link to `/missions` instead).

### The overdue sweep

`OverdueNotificationScheduler` runs on `@Scheduled(cron = "0 0 9 * * *", zone = "Europe/Belgrade")` — **once a day at 09:00**. It selects `AWARDED` and `IN_PROGRESS` missions whose flight window has ended and asks the pilot whether the flight is finished. `NotificationService.overdueExists` guards it, so each mission is nudged exactly once, ever.

The cron expression and timezone are hardcoded; no property gates this job.

---

## Audit log

Every state-changing user action is recorded, and the log is admin-readable at `/api/v1/audit-log`.

Each row stores the actor, a **snapshot of their role at the time**, the action, the target as a `(type, id)` pair, and an optional `details` snapshot — the mission name, a bid amount. Because the target is a plain pair rather than a foreign key, the history survives the deletion of whatever it describes: an admin can remove a mission and the record of the removal remains.

Entries are written as the **last** step of a successful operation, so a rejected action logs nothing.

The 18 recorded actions:

| Group | Actions |
|---|---|
| Missions | `MISSION_CREATED`, `MISSION_UPDATED`, `MISSION_DELETED`, `MISSION_STARTED`, `MISSION_COMPLETED`, `MISSION_CANCELLED` |
| Moderation | `MISSION_HIDDEN`, `MISSION_UNHIDDEN`, `MISSION_REMOVED` |
| Bids | `BID_PLACED`, `BID_WITHDRAWN`, `BID_ACCEPTED` |
| Accounts | `USER_REGISTERED`, `USER_LOGGED_IN`, `USER_SUSPENDED`, `USER_REACTIVATED`, `ADMIN_CREATED` |
| Ratings | `RATING_CREATED` |

There is **one entry per user intent, not per side effect**. Cancelling a mission rejects all of its bids, but only `MISSION_CANCELLED` is recorded — the bid rejections are consequences of that one decision, not separate ones.

---

## Architecture

### Spring-first

This is a Spring Boot application, and the governing rule is that **built-in framework features win over hand-written equivalents**. A custom class that re-implements behaviour Spring already provides is treated as a defect here, not a neutral choice — even if it works and is tested. Method security, `authorizeHttpRequests`, `@AuthenticationPrincipal`, bean validation, `AuthenticationManager`/`UserDetailsService` and `@RestControllerAdvice` are all used as-is rather than reinvented. Where custom code survives, there is a stated reason.

### Two layers, organised by feature

Base package is **`com.project.drone_missions`** — with an underscore, because the artifactId `drone-missions` is not a legal Java package name.

- **Presentation — `web`**: controllers and mappers, per feature (`web.controller.mission.MissionController`, `web.mapper.mission.MissionMapper`). Cross-cutting web infrastructure such as `GlobalExceptionHandler` sits at the `web` root. Request/response records live under `web.dto.<feature>`.
- **Business — `business`**: services and domain exceptions, per feature (`business.service.mission.MissionService`, `business.exception.mission.MissionNotFoundException`). The shared abstract exception bases sit at the `business` root.
- **Shared**: `data.model` (entities), `data.repository` (Spring Data), `data.access` (the DAL), `config`, `security`.

Separation of concerns is strict:

- **Controllers own all DTO-to-entity mapping**, delegating to the feature mapper — in via `toEntity`, out via `toResponse`.
- **Services operate exclusively on entities.** No DTOs, no servlet types, no mappers. That keeps every domain rule testable without the web layer, which is why most of the test suite needs no Spring context.

### Parameter objects

Methods with long positional parameter lists — especially adjacent same-typed parameters, which transpose silently — take a record instead: `NewNotification`, `NewBidEmail`, `NewAuditEntry`, `OpenMissionQuery`.

These records live **beside the service that consumes them**, not under `web.dto`. They are business-layer value objects, and placing them under `web` would make `business` depend on `web`. Controller handler signatures are exempt, since their parameters carry distinct Spring binding annotations a record cannot express.

### The data access layer

**Business and web code depends on `data.access.MissionDao`, never on `data.repository.MissionRepository`.** `JpaMissionDao` is the only class permitted to reference the repository.

That rule is load-bearing rather than cosmetic. Missions are written from **two** services — `MissionService`, and `BidService`, which flips a mission to `BIDDING` on the first bid and to `AWARDED` on acceptance. A cache owned by either would be stale the moment the other wrote. Routing every read *and* write through one interface means the caching decorator observes all of them, and invalidation cannot be forgotten at a call site.

#### findById versus findFresh

The distinction is a correctness requirement, not a style preference.

| Use | When |
|---|---|
| `findById` | read-only flows. May be served from cache, and may return a **detached copy** |
| `findFresh` | anything that will call `save`. Always hits the database, returns a **managed** entity, and evicts any cached copy on the way through |

`Mission` has no `@Version` column, so JPA cannot detect a stale write. If a mutating flow read a cached detached copy and passed it to `save`, `merge` would write back **every** field of that stale copy — including `status` and `awardedPilotId`, which an edit deliberately never touches. A cached copy saying `PUBLISHED`, merged over a row a bid has since moved to `BIDDING`, would silently revert it with nothing to catch the regression.

Both `MissionService` and `BidService` therefore keep a `getOrThrow` / `getFreshOrThrow` pair.

#### Query parameters are records

`findOpen` takes an `OpenMissionQuery` record rather than a `Specification` lambda. A lambda has no value equality, so it can never be a cache key. The `Specification` is built inside `JpaMissionDao`; the service keeps the domain decisions — which statuses count as open, how the date filter handles timezones.

Filter values are normalised (trimmed, lowercased, blank becomes null) before the key is built, so two requests differing only in case produce an **equal** key and land on the same cache entry instead of fragmenting it.

### Caching: two implementations, selected by profile

Mission reads are cached by a decorator over `JpaMissionDao`. There are **two** such decorators, both implementing `MissionDao`, and exactly one is active.

| Profile | Bean | Config |
|---|---|---|
| *(none — the default)* | `CachingMissionDao` — a hand-written `TtlCache` | `MissionCacheConfig` (`@Profile("!cache-spring")`) |
| `cache-spring` | `SpringCacheMissionDao` — `@Cacheable`/`@CacheEvict` over Caffeine | `SpringCacheConfig` (`@Profile("cache-spring")`) |

The two configs carry opposite `@Profile` expressions, so there is never a second `@Primary MissionDao`.

Select the profile from the **run configuration or the environment**, not from `application.properties`, so a plain run stays on the default:

```powershell
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=cache-spring"
```

```bash
SPRING_PROFILES_ACTIVE=cache-spring
SPRING_PROFILES_ACTIVE=local,cache-spring   # profiles compose
```

The name must match exactly. Spring does not warn about a profile nobody declares, so a typo such as `springcache` activates nothing and leaves you silently on the hand-written cache. Both implementations log a `mission cache: <ClassName> ...` line at startup — check that rather than waiting for the periodic report.

Setting `app.cache.mission.enabled=false` is a **third mode**: the decorator bean is never created under either profile, so `JpaMissionDao` is injected directly. No runtime branch, no no-op implementation.

**Why two exist.** The Spring-first rule says the framework should win where it fits. The hand-written cache predates that judgement and encodes behaviour the annotations cannot express. Keeping both runnable turns that into a question you answer by running the application rather than by arguing about it.

**Shared by both:** two caches (missions by id, and query to results); `findOverdue`, `searchAll` and `countByStatus` are never cached; observability is a `@Scheduled` log line on `app.cache.mission.report-interval` — there is no actuator on the classpath — and both render the same `hits misses ratio size evictions` fields so the two can be compared directly.

**`CachingMissionDao` — what the annotations cannot do:**

- Lists cache **ordered ids, never entities**, so a write discards small id arrays while the expensive rows survive. If hydration finds a member gone, it re-runs the query once.
- Entities are copied in *and* out through the all-args constructor of `Mission`, so adding a field breaks the copy at **compile time** instead of silently dropping it — and a caller cannot corrupt a cached entry by mutating what it was handed.
- Absent ids are deliberately not cached: that is the 404 path, with an unbounded key space.
- `save` never caches its own result, because `@UpdateTimestamp` has not been applied yet mid-transaction. The cache is read-through only.
- Eviction happens immediately **and** again on `afterCompletion` when a transaction is active — `afterCompletion`, not `afterCommit`, so a rollback also clears. The `isSynchronizationActive()` guard is required, since most writes here run outside a transaction.
- A full cache **refuses** the new value rather than displacing an existing entry.

**`SpringCacheMissionDao` — the costs of staying idiomatic.** All four are documented on the class and pinned by tests. They are the findings, not bugs awaiting a fix:

- `@CacheEvict(allEntries = true)` is the only way to express that any write can change which missions a query returns, so **a write clears the entity rows too**.
- Cached entities are **shared, not copied** — mutating a returned `Mission` in place would corrupt the entry. This is safe only because every write flow uses `findFresh`, which is never served from cache.
- **No transaction-aware eviction.** The `TransactionAwareCacheDecorator` from Spring does not help: it defers to `afterCommit`, which would leave a stale entry after a rollback.
- Bounding is **LRU eviction**, not refusal.

If you touch this code: `unless = "#result == null"` — not `isEmpty()` — because Spring unwraps the `Optional` before evaluating the expression; `findFresh` needs `beforeInvocation = true`; `@EnableCaching(proxyTargetClass = true)` is required because the `@Scheduled` reporter is not on the interface and a JDK proxy would drop it; and `setCacheNames(...)` must be called **before** `registerCustomCache(...)`, since it overwrites entries with default-configured, unbounded, TTL-less caches.

**`TtlCache`** is a plain data structure with no Spring, JPA or third-party dependencies: `ConcurrentHashMap`-backed, lock-free reads, and lazy expiry on read, so a stale entry is never served even if no sweep has run. Its bounding is **admission refusal rather than eviction** — when full it purges expired entries, then refuses the new value and counts a rejection. That is constant-time, keeps reads lock-free, and means a flood of one-off keys cannot evict hot entries. `get` deliberately never loads, so no bin lock is held across a database round-trip.

> **Both implementations assume a single application instance.** Two JVMs would hold divergent caches. Nothing enforces this — though the `@Scheduled` overdue sweep already carries the same assumption.

### Security

Built on the **OAuth2 Resource Server** support in Spring Security; there is no hand-rolled JWT plumbing and no custom filter.

- **Stateless JWT.** Requests are authenticated by the built-in `BearerTokenAuthenticationFilter` plus a `JwtDecoder` bean (`NimbusJwtDecoder`, HS256, symmetric secret). CSRF is disabled and sessions are `STATELESS`, since there are no cookies.
- **Tokens are minted** in `AuthService` with `NimbusJwtEncoder`: subject is the user id, plus a `role` claim, expiring after `security.jwt.expiration-ms`.
- **Open paths**, exactly: `/api/v1/auth/register`, `/api/v1/auth/login`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`. Everything else requires authentication.
- **Roles** ride in the `role` claim and are mapped to `ROLE_<role>` authorities by the `JwtGrantedAuthoritiesConverter` from Spring. Role gating uses `@PreAuthorize` (method security is on via `@EnableMethodSecurity`) rather than request matchers, keeping one source of truth per rule.
- **Passwords** are BCrypt-hashed; login credentials are checked through the built-in `AuthenticationManager`, backed by `CustomUserDetailsService` and `UserPrincipal`.

**The principal has two different types depending on phase**, which is worth knowing before reading the code:

| Phase | Principal |
|---|---|
| During login, inside `AuthenticationManager` | `UserPrincipal` — a `UserDetails` wrapping the full `User`, whose `getUsername()` returns the **email** |
| On every authenticated request | a plain **`Long` user id**, taken from the token subject |

Controllers read the latter with `@AuthenticationPrincipal Long userId`. A user id is never trusted from a request body.

> Note: `CLAUDE.md` and a javadoc comment in `SecurityConfig` both refer to a custom `@CurrentUserId` annotation. **No such annotation exists in this codebase** — those references are stale.

**Authorization is layered**, and each layer answers a different question:

1. `SecurityConfig` — is this request authenticated at all?
2. `@PreAuthorize` — may this *role* call this method?
3. the service — may this *user* do this to this *row*, in this *state*?

### Error handling

A single `@RestControllerAdvice`, `GlobalExceptionHandler`, builds every response from an immutable `ErrorResponse` record via its Lombok `@Builder`:

```json
{ "data": null, "status": "NOT_FOUND", "message": "Mission 42 not found" }
```

Handlers, most specific first:

| Exception | Status | Notes |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `data` is a field-to-message map; message is `Data validation failed` |
| `HttpMessageNotReadableException` | 400 | malformed JSON or an unknown enum value — a client error, never a 500 |
| `MethodArgumentTypeMismatchException` | 400 | `Invalid value for parameter '<name>'` |
| `NoResourceFoundException` | 404 | an unrouted URL, so unknown paths are not swallowed by the catch-all |
| `NotFoundException` | 404 | the domain message |
| `UnauthorizedException` | 401 | |
| `ForbiddenException` | 403 | |
| `AuthorizationDeniedException` | 403 | raised by `@PreAuthorize` |
| `ConflictException` | 409 | |
| `Exception` | 500 | generic message; internals are never leaked |

**Custom exceptions are domain-specific and self-documenting.** Each HTTP error class has an abstract base at the `business` root, and every domain exception extends the right one, so the *type* conveys the context without reading the message. The bases are abstract; nothing throws one directly.

| Base (status) | Domain exceptions |
|---|---|
| `NotFoundException` — **404** | `MissionNotFoundException`, `BidNotFoundException`, `UserNotFoundException`, `NotificationNotFoundException` |
| `UnauthorizedException` — **401** | `InvalidCredentialsException` |
| `ForbiddenException` — **403** | `MissionAccessDeniedException`, `AdminRegistrationNotAllowedException`, `UserSuspendedException`, `NotMissionParticipantException` |
| `ConflictException` — **409** | `EmailAlreadyExistsException`, `MissionConflictException`, `BidConflictException`, `AlreadyRatedException`, `RatingNotYetAllowedException`, `AdminCannotBeSuspendedException` |

Three of the 404s — `MissionNotFoundException`, `BidNotFoundException` and `NotificationNotFoundException` — are also used deliberately to **mask an authorization failure**, so a status code cannot confirm that a hidden id exists.

> **One response does not follow this contract.** A missing or invalid bearer token is rejected by the default `BearerTokenAuthenticationEntryPoint` from Spring Security, at the **filter layer, before MVC**. It returns 401 with a `WWW-Authenticate: Bearer` header and an **empty body** — not an `ErrorResponse`. Clients must handle that case separately.

---

## Configuration reference

Everything lives in `src/main/resources/application.properties`. Values written as `${VAR:default}` can be overridden by that environment variable.

| Concern | Property | Default | Env override |
|---|---|---|---|
| Server | `server.port` | `8085` | — |
| | `spring.application.name` | `drone-missions` | — |
| Datasource | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/drone-missions` | — |
| | `spring.datasource.username` / `.password` | `postgres` / `postgres` | — |
| JPA | `spring.jpa.hibernate.ddl-auto` | `validate` | — |
| | `spring.jpa.show-sql` | `true` | — |
| Flyway | `spring.flyway.baseline-on-migrate` | `false` | — |
| JWT | `security.jwt.secret` | dev placeholder | **`SECURITY_JWT_SECRET`** |
| | `security.jwt.expiration-ms` | `86400000` (24 h) | **`SECURITY_JWT_EXPIRATION_MS`** |
| Mail | `spring.mail.host` | `smtp.gmail.com` | `SMTP_HOST` |
| | `spring.mail.port` | `587` | `SMTP_PORT` |
| | `spring.mail.username` | *(empty)* | `SMTP_USER` |
| | `spring.mail.password` | *(empty)* | `SMTP_APP_PASSWORD` |
| | `spring.mail.properties.mail.smtp.auth` | `true` | — |
| | `spring.mail.properties.mail.smtp.starttls.enable` | `true` | — |
| | `app.mail.enabled` | `false` | `MAIL_ENABLED` |
| | `app.mail.from` | `DroneMissions <no-reply@dronemissions.app>` | `MAIL_FROM` |
| | `app.mail.redirect-to` | *(empty)* | — |
| Frontend | `app.frontend-url` | `http://localhost:4200` | `FRONTEND_URL` |
| Paging | `spring.data.web.pageable.max-page-size` | `100` | — |
| Cache | `app.cache.mission.enabled` | `true` | `MISSION_CACHE_ENABLED` |
| | `app.cache.mission.ttl` | `5m` | — |
| | `app.cache.mission.max-size` | `1000` | — |
| | `app.cache.mission.list-max-size` | `200` | — |
| | `app.cache.mission.report-interval` | `PT5M` | — |

Notes:

- **The JWT secret must be at least 32 bytes** for HS256. The committed default is a development placeholder and must be replaced in production via `SECURITY_JWT_SECRET`.
- `app.mail.redirect-to` is only ever set in the local profile; its empty default is declared inline in the `EmailService` constructor rather than in the properties file.
- `MissionCacheProperties` is a `@Validated @ConfigurationProperties("app.cache.mission")` record whose sizes are `@Positive`, so an invalid value **fails startup** rather than misbehaving later. `ttl` accepts `5m`, `300s` or `PT5M`. `report-interval` is not on the record — the two `@Scheduled` annotations read it directly with an inline `PT5M` default.
- There are **no `logging.*` properties**; logging is left at Boot defaults, and `spring.jpa.show-sql=true` is the only deliberate SQL visibility.

### Profiles

| Profile | Effect |
|---|---|
| *(none)* | hand-written mission cache |
| `cache-spring` | Spring Cache over Caffeine instead |
| `local` | local mail credentials and overrides |

They compose: `SPRING_PROFILES_ACTIVE=local,cache-spring`.

`src/main/resources/application-local.properties` is the place for real credentials. It is **untracked and gitignored**, so secrets never reach git history — keep it that way, and never commit a value from it.

### CORS

`CorsConfig` allows `/api/v1/**` from `http://localhost:4200` (the Angular frontend), methods `GET, POST, PUT, DELETE, OPTIONS`, all headers, credentials enabled, `maxAge` 3600. It **exposes the `Authorization` header**, which is what lets the browser read the token that login returns.

> The origin is hardcoded and **not** wired to `app.frontend-url`, so pointing the app at a different frontend means changing both.

---

## Database and migrations

The schema is **owned by Flyway**. Migrations in `src/main/resources/db/migration` are applied in version order at startup; Hibernate runs with `ddl-auto=validate` and never alters anything, only checking that the entities match what Flyway migrated. A mismatch fails boot.

A schema change is therefore **a new versioned migration plus the matching entity annotation** — never an entity edit alone. Name the next file `V<n>__snake_case_description.sql` and keep the `@Column` length and nullability in sync so validation passes.

| Migration | What it does |
|---|---|
| `V1__create_mission_table` | Creates `mission` with a status check over the seven lifecycle values |
| `V2__mission_status_not_null` | Makes `status` NOT NULL |
| `V3__Create_users_table` | Creates `users` with a unique email and `password_hash` |
| `V4__Add_user_id_to_missions` | Adds the nullable `mission.user_id` owner FK |
| `V5__add_user_role` | Adds `role`, backfills to `DESIGNER`, adds a `(DESIGNER, PILOT)` check |
| `V6__widen_mission_description` | Widens `description` to 2000 chars |
| `V7__add_mission_flight_plan` | Adds `location`, `bidding_deadline`, and the `waypoints` / `geofence` JSONB columns |
| `V8__create_bid_table` | Creates `bid` with a unique `(mission_id, pilot_id)`, cascade from mission, and adds `mission.awarded_pilot_id` |
| `V9__create_notification_table` | Creates `notification` with an index on `(user_id, created_at DESC)` |
| `V10__add_mission_cancelled_notification_type` | Widens the notification type check to allow `MISSION_CANCELLED` |
| `V11__create_rating_table` | Creates `rating` with a 1–5 score check and a unique `(mission_id, rater_id)` |
| `V12__add_admin_role` | Adds `ADMIN` to the role check and **seeds the bootstrap admin** |
| `V13__add_moderation_state` | Adds `users.suspended_at` and `mission.moderation`, then checked as `(VISIBLE, HIDDEN, REMOVED)` |
| `V14__create_audit_log` | Creates the immutable `audit_log` table and its indexes |
| `V15__hard_delete_removed_missions` | Deletes `REMOVED` missions and shrinks the check to `(VISIBLE, HIDDEN)` — removal becomes a hard delete |
| `V16__suspended_boolean` | Replaces `suspended_at` with a `suspended` boolean and backfills it |
| `V17__add_admin_created_audit_action` | Adds `ADMIN_CREATED` to the audit action check |
| `V18__drop_mission_restored_audit_action` | Retires `MISSION_RESTORED`, deleting its historical rows |

Constraints worth knowing, because they enforce domain rules the services also state:

- `bid` cascades on delete from `mission`, which is what makes admin removal safe.
- unique `(mission_id, pilot_id)` on `bid` — one bid per pilot per mission.
- unique `(mission_id, rater_id)` on `rating` — write-once ratings.
- `audit_log.target_type` / `target_id` are a plain pair, **not** a foreign key, so audit history outlives deleted targets.

---

## Testing

18 test classes, in three tiers. The tiers matter, because they determine what you can run without a database.

**Needs a live PostgreSQL — one class.** `DroneMissionsApplicationTests` is a plain `@SpringBootTest` context-load check. There is no `src/test/resources`, no `application-test.properties`, no H2 and no Testcontainers, so it boots against the main datasource and runs Flyway against it.

**Spring context, no database — one class.** `SpringCacheMissionDaoTest` loads only `SpringCacheConfig` plus a mocked delegate under `@ActiveProfiles("cache-spring")`, and covers the Caffeine implementation: per-key caching, key separation, the shared-instance semantics, eviction on write and on `findFresh`, the never-cached paths, and the statistics reporter.

**Pure unit tests, no Spring, no database — 16 classes.**

| Area | Classes |
|---|---|
| Services | `MissionServiceTest`, `MissionServiceModerationTest`, `BidServiceTest`, `AuthServiceTest`, `RatingServiceTest`, `UserServiceTest`, `AuditServiceTest`, `NewAuditEntryTest`, `PlatformStatsServiceTest` |
| Caching | `CachingMissionDaoTest`, `TtlCacheTest` |
| Validation | `WaypointActionValidatorTest` |
| Controllers | `MissionControllerTest`, `UserControllerTest`, `AuditLogControllerTest`, `PlatformStatsControllerTest` |

`TtlCacheTest` uses a hand-rolled advanceable `Clock` rather than sleeping, so TTL boundaries are tested exactly and the suite stays fast. `WaypointActionValidatorTest` boots a standalone `ValidatorFactory` to exercise the constraint directly.

> The controller tests are **plain Mockito, not `@WebMvcTest`** — they invoke controller methods directly. There is no MockMvc, no security filter chain and no HTTP-layer coverage, so serialization, status codes and role gating are not exercised end-to-end by the suite.

---

## Code style and contributing

Checkstyle enforces naming, formatting basics and import hygiene; the configuration is `checkstyle.xml` at the project root.

It runs in the **`validate`** phase, so **any** `test`, `package` or `verify` fails on a violation *before* compiling. Run it alone with:

```powershell
mvnw.cmd checkstyle:check
```

Details land in `target/checkstyle-result.xml`.

Key rules: no tab characters; line length at most 120 (ignoring `package`, `import` and URLs); no star imports; no unused or redundant imports; standard naming and whitespace checks. Two deliberate accommodations:

- `PackageName` carries a pinned custom format so the underscore in `com.project.drone_missions` is legal.
- `MethodName` is suppressed for `data/repository`, because Spring Data derived queries use `_` for property traversal (`findByDesigner_Id`).

Test sources are checked too (`includeTestSourceDirectory=true`).

A **pre-commit hook** in `.githooks/` runs the same check whenever staged changes include Java files. It is not active by default — enable it once per clone:

```bash
git config core.hooksPath .githooks
```

Two further conventions:

- **`develop` is the integration branch**; `main` receives merges from it. Feature branches are cut from `develop`.
- **Never delete or rewrite an existing comment**, most importantly `// TODO` markers. When refactoring surrounding code, carry the author's comments across verbatim. If a comment looks obsolete, flag it rather than removing it.

---

## Project layout

```
src/main/java/com/project/drone_missions/
├── DroneMissionsApplication.java
├── business/
│   ├── NotFoundException.java            # abstract bases: 404 / 401 / 403 / 409
│   ├── UnauthorizedException.java
│   ├── ForbiddenException.java
│   ├── ConflictException.java
│   ├── exception/<feature>/              # domain exceptions, per feature
│   └── service/
│       ├── mission/  bid/  rating/       # domain services
│       ├── auth/  user/                  # accounts and tokens
│       ├── notification/                 # in-app notifications + overdue scheduler
│       ├── mail/                         # Thymeleaf email
│       ├── audit/                        # audit trail
│       └── stats/                        # admin aggregates
├── config/
│   ├── SecurityConfig.java  OpenApiConfig.java  CorsConfig.java
│   ├── MissionCacheConfig.java           # default profile
│   ├── SpringCacheConfig.java            # cache-spring profile
│   └── MissionCacheProperties.java
├── data/
│   ├── access/                           # MissionDao + the two caching decorators
│   ├── model/                            # JPA entities, enums, jsonb value objects
│   └── repository/                       # Spring Data — reached only via data.access
├── security/                             # CustomUserDetailsService, UserPrincipal
└── web/
    ├── GlobalExceptionHandler.java
    ├── controller/<feature>/
    ├── mapper/<feature>/
    └── dto/<feature>/

src/main/resources/
├── application.properties
├── application-local.properties          # gitignored: real credentials
├── db/migration/                         # V1 … V18, Flyway-owned schema
└── templates/email/                      # five Thymeleaf email bodies
```

`scripts/multiagent-dev.sh` is internal development tooling and not part of the application.
