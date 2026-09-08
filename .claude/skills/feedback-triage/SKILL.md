---
name: feedback-triage
description: Turn in-app feedback into TODO items. Scans Sentry for reports filed from the app's feedback panel, reads their log and screenshot attachments, and either files an item in docs/todos.md or resolves the report as no-action. Use when asked to "triage feedback", "process feedback", "check what I filed", or on a schedule.
---

# Feedback triage

The app has a feedback panel (right-edge swipe or the edge handle, tester builds
only) and a player-facing bug report. Both land in Sentry. This routine reads
them and turns the actionable ones into `docs/todos.md` items that a worker
routine can pick up later.

## Fixed coordinates

| Thing | Value |
| --- | --- |
| Sentry search tag | `feedback_kind` |
| Owner directives | `feedback_kind:owner_directive` |
| Player bug reports | `feedback_kind:bug_report` |
| Player feedback | `feedback_kind:feedback` |
| TODO queue | `docs/todos.md` |
| Ledger | `docs/feedback-log.md` |
| Enum that defines the tag values | `libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/FeedbackKind.kt` |

Use the Sentry MCP (`search_issues`, `search_events`, `get_sentry_resource`).
No auth token or `curl` needed.

## How a report is shaped in Sentry

Submitting feedback produces **two linked issues**, and you need both:

1. **The carrier event.** A `captureMessage` whose title is `Owner directive`,
   `Bug report` or `User feedback`. It carries the tags (`feedback_kind`,
   `session_id`, `install_id`, `route`, `commit_sha`, `commit_branch`), the
   breadcrumbs, and the attachments: `session-log.txt` and up to three
   `screenshot-N.jpg`. It does **not** carry the written text.
2. **The feedback twin.** Sentry's own user-feedback record, linked by
   `associated_event_id`. This is where the words are.

Every carrier gets a unique fingerprint (`["feedback", <uuid>]`), so **one report
is one issue**. If you see many reports crammed into a single issue, that issue
predates the fingerprinting and each event in it must be read individually.

## Procedure

### 1. List unhandled reports

Query each kind separately, because Sentry's issue search has no boolean `OR`:

- `feedback_kind:owner_directive`
- `feedback_kind:bug_report`
- `feedback_kind:feedback`

Then **check every event id against `docs/feedback-log.md` and skip the ones
already there.** This is the step that keeps the routine idempotent; a fixed and
deleted TODO leaves no other trace that its report was handled.

### 2. Read the report

Pull the twin for the text, the carrier for context. Extract: the message,
`feedback_kind`, `session_id`, `route`, `environment`, `release`, `commit_sha`,
timestamp, and the attachments.

Sentry's resource renderer sometimes puts the feedback comment under an unrelated
heading. Read the whole body rather than trusting the heading.

### 3. Classify before investigating

**`owner_directive` is an instruction, not a data point.** The owner filed it
from a tester build, deliberately, about their own app. Do not investigate
whether it is worth doing and do not weigh it against other priorities. Write the
TODO and move on. The whole point of the channel is that the round trip from
"this bothers me" to "it is on the list" is one swipe.

**`bug_report` and `feedback` come from players.** Those get the full treatment
below. A player's product ask that you decide not to file still gets a line in
the ledger saying so, with the reason. Never drop a player's ask silently.

### 4. Investigate (player reports only)

- **The attached log** is the highest-value artifact. It is the Debug-and-below
  detail that is never shipped as breadcrumbs, from the minutes before they hit
  send. Read it before anything else.
- **The screenshot** shows the state they were describing, which is usually a
  faster diagnosis than the words.
- **Correlate by `session_id`.** The same value is a Sentry tag, an OTLP
  attribute on client logs in Grafana Loki (`{service_name="sodogku-client"}`),
  and the `X-Session-Id` header on backend calls. In Loki, `session_id` is
  structured metadata, not a stream label: match it with `| session_id="<id>"`,
  never with a line filter `|= "<id>"`, which silently returns nothing.
- **Absence of client events is weak evidence.** Telemetry buffers to disk when
  offline and can arrive days late.

### 5. File it

Append a section to `docs/todos.md` in the format that file documents. Assign the
next `SD-<n>`:

```shell
grep -oE '\bSD-[0-9]+' docs/todos.md | grep -oE '[0-9]+' | sort -n | tail -1
```

Keep the reporter's own words in **Ask** where the wording carries intent. Put
the Sentry URL, session id and date on the last line of **Hints** so the worker
can go back to the source without you.

If there is nothing to do, say so in the ledger with a reason.

### 6. Close the loop

Append one line to `docs/feedback-log.md` for every report handled, whichever way
it went.

Then, in Sentry:

- **Filed a TODO?** Comment on the issue with the TODO id and leave it
  unresolved. It is not fixed yet. Resolving at triage time is how a dropped TODO
  disappears with no trace anywhere.
- **No action?** Resolve it.

### 7. Report

Say how many reports were seen, how many became TODOs, how many were no-action,
and anything that needs a human.

## Guardrails

- **Idempotent.** The ledger check in step 1 is not optional.
- **This routine writes two files and nothing else.** `docs/todos.md`,
  `docs/feedback-log.md`, plus Sentry comments and statuses. It does not change
  code. A separate worker routine does that.
- **One report, at most one TODO.** If a directive contains three asks, file the
  one it leads with and note the others in **Hints**. Three items from one report
  become three half-remembered items.
- **Do not invent telemetry.** If the answer needs an event that is not emitted,
  say so and file adding the event as the TODO.
