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

## The feedback loop

Feedback is an input to the work queue, not just a mailbox. A report filed from
the app becomes a Sentry issue, the `feedback-triage` skill turns it into an item
in [`docs/todos.md`](../todos.md), and a worker routine picks items off that
list. The whole point is that the round trip from noticing something to it being
on the list is one gesture.

### The three channels

`FeedbackKind` (`:libraries:sodogku`) names them, and its `tag` value is what
rides to Sentry as the searchable `feedback_kind` tag.

| Tag | Who | Entry point | Treated as |
| --- | --- | --- | --- |
| `owner_directive` | the owner, from a tester build | right-edge handle or swipe | an instruction: filed without debate |
| `bug_report` | a player | shake, or an error screen | a report: diagnosed, then filed |
| `feedback` | a player | Settings → feedback | read, rarely actioned |

**Why a tag and not the message.** The carrier event's message is also its issue
title, which Sentry groups on and re-summarizes, and which whoever next edits the
copy will change without knowing anything depends on it. A tag is indexed, is
queried directly (`feedback_kind:owner_directive`), and is unaffected by
grouping. `FeedbackTriageQueryContractTest` in `:apps:integration` holds the enum
and the skill's queries against each other, because renaming either end breaks
triage silently: the query still runs, still succeeds, and returns nothing, which
is indistinguishable from a quiet week.

Each report also gets a unique `feedback_event` id that `beforeSend` turns into
the event fingerprint, so **one report is one issue**. Without it every carrier
would share a title, no stacktrace, and therefore one giant issue.

### The tester entry point

`DevFeedbackHost` wraps the app in `App.kt` and is a bare `Box` unless
`BuildInfo.isTesterBuild` (debug, or TestFlight by receipt check). It offers:

- **A right-edge swipe.** The trigger the owner asked for, and the working one on
  iOS, where the right edge is free.
- **A visible handle on that edge.** Android gesture navigation claims both edges
  for system back, and it claims them for *taps* as well as drags: a tap 41px
  from the right edge backed the app out to the launcher instead of reaching the
  control under it. The handle sits inboard and calls `systemGestureExclusion`,
  which is what makes it reachable at all on Android. Measured on the emulator: a
  swipe starting on the handle opens the panel, a swipe from the bare edge
  beside it exits the app.

The panel is an overlay, not a nav destination, so it can open over a dialog or a
sheet without disturbing the back stack it is meant to describe.

### What rides along

- **The log tail.** `InMemoryLogTree` (`:libraries:core`) keeps the last 500 lines
  in memory and nowhere else. It exists because both shipping sinks drop the
  detail that explains bugs: Sentry breadcrumbs start at Info in release, Grafana
  at Warn. The buffer keeps Debug and below, costs one bounded allocation, and
  leaves the device only when someone files a report with the box ticked
  (**default on**: this surface's only users are the people building the app,
  so there is no stranger's privacy to weigh, and a directive without its logs is
  the one that costs a round trip later).

  Every line is passed through `redactSecrets` **before** it enters the buffer,
  so a credential is never resident in memory waiting to be attached. It is
  deliberately narrow: bearer and basic auth headers, JWTs, `token=`/`secret=`/
  `dsn=`-style key-value pairs, and email addresses. Session and install ids
  survive on purpose, because they are the join key to Loki and Tempo.

  The buffer used to be a field on `SentryLogTree`, which refuses every entry
  when the DSN is unset. That is exactly a local debug build, so the buffer was
  empty in the builds where reports actually get written.

- **A screenshot**, captured from the frame the panel slid over via a
  `GraphicsLayer` recording and JPEG-encoded per platform. Auto-attached and
  removable rather than picked from a gallery: it needs no permission, no picker
  plumbing, and the frame someone was looking at when they reached for the edge
  is almost always the one they mean.

### Release log levels, as they actually are

Worth stating because the intuition is close but not exact:

| Sink | Debug build | Release build | Sampled? |
| --- | --- | --- | --- |
| Sentry events | Error+ | Error+ | **No.** `options.sampleRate` is deliberately never set, and `SentryRuntimeConfigTest` pins that |
| Sentry breadcrumbs | Debug+ | Info+ | n/a |
| In-memory buffer | Verbose+ | Debug+ | never leaves the device unless attached |
| Grafana OTLP | app events at any level; plain logs Warn+ behind `telemetry.klogForwardingEnabled` | same | **Yes**, per session via `telemetry.appEventsSampleRate` (1.0 today) |

So "release ships Info and above" is right about Sentry breadcrumbs, and errors
really are 100%, but Grafana is stricter for plain logs (Warn+, not Info+) and
is the one path that *is* sampled. If a report's client events are missing from
Loki, sampling is a live explanation; a missing Sentry error is not.
