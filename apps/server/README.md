# `:apps:server` — Ktor backend

A small, opinionated Ktor + Postgres backend. There is no auth and no user data
(see "No accounts" in AGENTS.md); it serves health and remote config. It mirrors
the client's conventions (kotlin-inject DI, `domain/`↔`data` split, one blessed
way to do each thing) so moving between client and server is the same mental
model. This README is the detailed reference; keep `AGENTS.md` minimal and point
here.

## Quick start

The server degrades gracefully, so you can run it with **zero config**:

```bash
./gradlew :apps:server:run            # boots in "limited mode": /_health + /v1/example
curl localhost:8080/_health           # {"ok":true}
curl localhost:8080/v1/example        # {"message":"…","items":[…]}
```

To enable the database-backed routes, set env vars (copy
`apps/server/.env.example` → `apps/server/.env`, which is gitignored). Start the
bundled Postgres and point at it:

```bash
docker compose -f apps/server/docker-compose.yml up -d
# in apps/server/.env:
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/postgres
```

On boot, Flyway applies everything in `src/main/resources/db/migration`.

Boot modes (graceful degradation is a deliberate standard):

| `DATABASE_URL` | What's served |
|---|---|
| unset | `/_health`, `/v1/example` |
| set   | + `/v1/app-config`, and `/v1/admin/config` when `ADMIN_API_TOKEN` is set |

## Layout

```
config/      Env (the only env reader) + ServerConfig (typed, parsed once)
di/          ServerScope + ServerComponent (kotlin-inject + anvil)
db/          Database (Hikari + Flyway + Exposed), Tables, TimeConversions
domain/      interfaces + models + sealed outcomes (no framework imports)
data/        impls, prefixed by backing store (InMemory*, Postgres*)
plugins/     Ktor plugins: Serialization, Errors, Cors, Observability, RateLimits
routes/      one `fun Route.xRoutes(deps)` per resource + its DTO file
resources/db/migration/   Flyway V<n>__snake.sql (source of truth for the schema)
```

`Main.kt` parses config → `Application.module(config)` does production-only setup
(observability, DB connect, DI graph) → `installApp(component)` installs the
functional plugins + mounts routes. `installApp` is the seam full-stack tests
reuse (real graph, real DB).

## Conventions (copy these)

### Add a config value — `config/ServerConfig.kt`
`Env` is the only place env vars are read. Pick by criticality:
- boot-critical → `env.require("KEY")` (fail fast)
- optional w/ default → `env.int("KEY", 8080)` / `env["KEY"] ?: "x"`
- optional, degrades → nullable `env["KEY"]`, branch at the call site

Group related vars into a `data class XxxConfig` with a `fromEnv(env)` companion.
Document the var in `.env.example`.

### Add a service — `domain/` interface + `data/` impl
```kotlin
// domain/Thing.kt
interface ThingRepository { suspend fun get(id: ThingId): Thing }

// data/PostgresThingRepository.kt
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresThingRepository(private val database: Database, private val clock: Clock) : ThingRepository
```
Then expose it on `ServerComponent` as `abstract val thingRepository: ThingRepository`.
anvil + KSP wire the rest. The impl prefix names the backing store
(`InMemory*`, `Postgres*`, `Http*`).

### Add a route — `routes/XxxRoutes.kt` + `XxxDto.kt`
```kotlin
fun Route.thingRoutes(repo: ThingRepository) {
    get("/v1/thing/{id}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        call.respond(repo.get(ThingId(id)).toResponse())  // a DTO, never a domain type
    }
}
```
- real paths are versioned under `/v1`; `/_health` is the deliberate exception.
- DTOs live in `XxxDto.kt`, named `*Response` / `*Request`; map with `Thing.toResponse()`.
- routes that only an operator or a machine should reach go behind `requireAdmin`
  (`routes/AdminAuth.kt`, an `X-Admin-Token` header). There is no per-user auth.
- map domain outcomes to status codes with an exhaustive `when`; errors use the
  one `ProblemResponse` envelope (`call.respond(status, problem("code", "msg"))`).
- mount it in `installApp`.

### Add a migration — `resources/db/migration/V<n>__snake.sql`
Flyway SQL is the **source of truth** for the schema; the Exposed objects in
`db/Tables.kt` are read-side projections. Never edit an applied migration — add
the next one. Mirror schema changes into `Tables.kt`; a new table also needs a
line in `DatabaseSchemaTest`'s `PROJECTIONS`, which compares each projection
against JDBC metadata and fails on drift in either direction.
Repositories run every method in `database.transaction { }`,
take an injected `Clock`, and treat a unique-violation (SQLSTATE `23505`) as the
arbiter rather than pre-checking.

## Auth

There is none, deliberately. No JWT plugin, no user identity, no `call.userId()`.
The only gate is `requireAdmin` in `routes/AdminAuth.kt`, an `X-Admin-Token`
header for the config admin API, whose caller is a machine or the admin console
rather than a person with an account. Rate limiting keys on IP for the same
reason (`plugins/RateLimits.kt`).

The client sends no credential either. Read "No accounts" in AGENTS.md before
adding one.

## Testing

Three patterns, each with a copyable example:
- **Route test** (`routes/ExampleRoutesTest.kt`, `routes/ConfigAdminRoutesTest.kt`) —
  `testApplication` + the real plugins + a fake passed as a plain arg.
- **Repository test** (`data/PostgresAppConfigSourceTest.kt`) — real Postgres
  via Testcontainers (`DatabaseTest`), `@After` table cleanup, injected clock.
  Skips cleanly (JUnit `Assume`) when Docker is absent, except when `CI` is set,
  where an unreachable daemon fails the job rather than passing it empty.
- **Full-stack test** (`:apps:integration`) — the real client stack over real TCP
  against the real `installApp` seam on a Testcontainers Postgres.

```bash
./gradlew :apps:server:test           # add -Dsodogku.skipGitHooksCheck=true outside a hooked checkout
```

## Environment variables

| Var | Required | Default | Notes |
|---|---|---|---|
| `DATABASE_URL` | no | — | `postgresql://user:pass@host:port/db`. Unset → limited mode. URL-encode `$`→`%24`. |
| `DATABASE_POOL_MAX_SIZE` | no | 10 | |
| `DATABASE_POOL_MIN_IDLE` | no | 2 | |
| `SERVER_HOST` | no | `0.0.0.0` | |
| `SERVER_PORT` | no | 8080 | |
| `ADMIN_API_TOKEN` | no | — | Unset → the config admin API isn't mounted. |

The full list, with the rest of the optional vars, is in
[`.env.example`](.env.example).
