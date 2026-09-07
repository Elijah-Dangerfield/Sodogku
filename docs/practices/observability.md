# Observability: one id, three systems

The stack is Sentry (crashes, user feedback, stack traces), Loki (logs — client app events and
server request logs), and Tempo (server traces). What ties them together is a single correlation
id: **`session_id`**, the UUID of the current client app session.

## The session_id pivot

A "session" is a user-perceptible run of the app: it starts on cold boot and rolls over after
15 minutes in the background (`SessionTracker` in `:libraries:sodogku`). Each session mints a
fresh UUID, and that one value is stamped everywhere:

- **Client → Sentry.** `SessionTelemetryBinder` writes it onto the crash-reporting scope as a
  `session_id` tag on every rollover, so every crash, error event, and user-feedback report
  carries it. `install_id` (stable per install) rides along as a second tag.
- **Client → server.** `DefaultClientHeadersProvider` sends it on every request as `X-Session-Id`
  (plus `X-Install-Id`).
- **Server → Tempo.** `installHttpServerTracing` (apps/server `plugins/Tracing.kt`) pins
  `session_id`/`install_id` onto the HTTP root span from the headers, and carries them in OTel
  Baggage so `BaggageAttributeSpanProcessor` copies them onto **every child span** — the whole
  trace tree matches `{ .session_id = "…" }`, not just the root.
- **Server → Loki.** CallLogging lifts the same headers into MDC (`plugins/Observability.kt`),
  and the logback OTel appender forwards MDC as log attributes (`captureMdcAttributes` in
  `logback.xml`), so every backend log line for the request is filterable by `session_id`.
- **Server → Sentry.** `captureToSentry` tags server errors with the MDC `session_id`/`install_id`
  plus the active `trace_id`/`span_id`, so a backend error links to both the client session and
  its Tempo trace.
- **Client → Loki.** `GrafanaLogTree` (`:libraries:telemetry:impl`) stamps `session_id` /
  `install_id` / `is_offline` on every app-event record it exports.

The key naming rule: it is always the underscore form `session_id`, in all systems, so one query
string works everywhere. The same rule applies to any context you add — if a key exists on backend
spans and client Sentry tags, spell it identically (`Telemetry.setContext(key, value)` client-side,
`SpanAttrs` server-side).

## Loki label conventions

Stream labels are only `service_name` + `deployment_environment`. Everything else — `event_name`,
`session_id`, `install_id`, event attributes, `detected_level` — is **structured metadata**: filter
with pipes, never line filters.

```
# All app events from prod clients
{service_name="sodogku-client", deployment_environment="prod"} | event_name != ""

# One event type
{service_name="sodogku-client"} | event_name="app.launched"

# Client Warn+ logs (no event_name — that's how you tell them from events)
{service_name="sodogku-client"} | detected_level=~"warn|error"

# Server logs for one session
{service_name="sodogku-server"} | session_id="<uuid>"
```

Client records also carry resource attributes: `service.version`, `platform` (android/ios),
`build_number`, `commit_sha`, `release_channel`, and `deployment.environment` (dev for debug
builds, prod for release).

## How to find a session

Start from wherever the report landed and pivot on the id:

1. **From a Sentry issue or feedback report:** copy the `session_id` tag.
2. **Client side of the story:** `{service_name="sodogku-client"} | session_id="<uuid>"` in
   Loki — the app events and Warn+ logs for that session, each stamped with `is_offline` *at emit
   time* (a record that shipped later from the disk buffer still says what connectivity looked
   like when it happened). Feedback reports also carry a `session-log.txt` attachment — the
   in-memory ring buffer of fine-grained logs that never left the device.
3. **Backend side:** `{service_name="sodogku-server"} | session_id="<uuid>"` for logs;
   `{ .session_id = "<uuid>" }` in Tempo for every request trace the session produced.
4. **The reverse direction works too:** a server error in Sentry carries `trace_id` (paste into
   Tempo) and `session_id` (pull the client's events), so backend-first investigations reach the
   client story in one hop.

Build provenance closes the loop: client Sentry events are tagged `commit_sha`/`commit_branch`
(injected at build time — see `loadVersionMetadata` in build-logic), so a report pins to the exact
code that produced it.

## Credentials and kill switches

All client telemetry credentials are build-time injected and env-optional (`loadTelemetryMetadata`
in build-logic → `TelemetryInfo` in `:libraries:core`): blank `SENTRY_DSN` disables crash
reporting; blank Grafana values leave the OTLP pipe dormant. Nothing breaks in a fresh clone.

At runtime, remote config owns the levers (`:libraries:telemetry:impl` `TelemetryConfigValues`):
`telemetry.appEventsEnabled` (instant kill switch), `telemetry.appEventsSampleRate` (per-session
sampling, stable-hashed so a session's events are all-or-nothing), and
`telemetry.klogForwardingEnabled` (Warn+ log mirroring). The server's OTel pipeline is gated by a
single env var: `OTEL_EXPORTER_OTLP_ENDPOINT` unset → stdout exporters, set → OTLP/HTTP.

The event registry lives in [`app-events.md`](app-events.md).
