---
name: feedback-triage
description: Turn in-app feedback into TODO items. Scans Sentry for reports filed from the app's feedback panel, reads their log and screenshot attachments, files an item in docs/todos.md, and resolves the report. Bugs keep their Sentry issue open and get a case file the worker can act on. Use when asked to "triage feedback", "process feedback", "check what I filed", or on a schedule.
---

# Feedback triage

The app has a feedback panel (the floating feedback button, tester builds only)
and a player-facing bug report. Both land in Sentry. This routine reads
them and turns the actionable ones into `docs/todos.md` items that a worker
routine can pick up later.

## Fixed coordinates

| Thing | Value |
| --- | --- |
| Sentry org / project | `elijah-dangerfield` / `sodogku` |
| Sentry search tag | `feedback_kind` |
| Owner directives | `feedback_kind:owner_directive` |
| Player bug reports | `feedback_kind:bug_report` |
| Player feedback | `feedback_kind:feedback` |
| TODO queue | `docs/todos.md` |
| Ledger | `docs/feedback-log.md` |
| Case files | `docs/cases/SD-<n>/` |
| Enum that defines the tag values | `libraries/sodogku/src/commonMain/kotlin/com/sodogku/libraries/FeedbackKind.kt` |

Use the Sentry MCP (`search_issues`, `get_sentry_resource`, and
`execute_sentry_tool` for `get_event_attachment`, `add_issue_note`). No auth
token or `curl` needed, except to save an attachment to disk — the download URL
that `get_event_attachment` returns is signed and works with a plain `curl -o`.

## How a report is shaped in Sentry

One report is one issue. The carrier event is a `captureMessage` titled `Owner
directive`, `Bug report` or `User feedback`, fingerprinted per report, and it
holds everything you need:

- **The text**, in the `feedback_message` extra, and again as a `feedback.txt`
  attachment.
- **The tags**: `feedback_kind`, `session_id`, `install_id`, `route`,
  `commit_sha`, `commit_branch`, `environment`, `release`.
- **The attachments**: `session-log.txt` and up to three `screenshot-N.jpg`.

**Do not go looking for Sentry's user-feedback twin.** It is not reachable from
the Sentry MCP for this project: `issue.category:feedback` returns nothing for
`sodogku`, and `get_issue_user_reports` on a carrier returns nothing. Everything
is on the carrier. Reports filed before `26fcba1` have neither the extra nor the
attachment, and their text is simply gone — say so and move on.

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

From the carrier: the `feedback_message` extra, `feedback_kind`, `session_id`,
`route`, `environment`, `release`, `commit_sha`, the timestamp, and the
attachments.

**Look at the screenshot before you write anything.** Reports say "this dialog"
and "the 'you have zero'" constantly, and the screenshot is the only thing that
says which. Two reports that read as different complaints routinely turn out to
be the same screen.

### 3. Check the build before you believe the report

Resolve `commit_sha` against the log:

```shell
git log -1 --format='%h %ad %s' --date=short <commit_sha>
git rev-list --count <commit_sha>..HEAD
```

**A report describes the build it was filed from, not the code you have.** In the
2026-09-14 pass, four of six items were already fixed when they were filed,
because the reports were 160 to 177 commits behind `main` and nobody checked.

- Far behind? Check whether the behavior still exists on `HEAD` before filing.
  If a later commit fixed it, that is a no-action with the fixing sha in the
  ledger, not a TODO.
- Filing anyway because you cannot tell? Say so in the item, and put the
  distance in the provenance line.
- On `HEAD`? Say that too. It is the strongest thing you can tell a worker.

### 4. Classify: directive, bug, or no-action

**`owner_directive` is an instruction, not a data point.** The owner filed it
from a tester build, deliberately, about their own app. Do not investigate
whether it is worth doing and do not weigh it against other priorities. Write
the TODO and move on. The whole point of the channel is that the round trip from
"this bothers me" to "it is on the list" is one swipe.

Then decide whether it is a **bug**, because that is the only thing the rest of
the procedure branches on:

> A bug is the app doing something it was not built to do, or doing something it
> was built to do in a case nobody intended. A directive is the app working as
> built and the owner wanting it built differently.

"The streak did not celebrate" is a bug. "The celebration should be full screen"
is a directive. When a report contains both, the bug decides how the Sentry
issue is handled.

**`bug_report` and `feedback` come from players.** Those get the investigation in
step 5. A player's product ask that you decide not to file still gets a line in
the ledger saying so, with the reason. Never drop a player's ask silently.

### 5. Investigate

Always for bugs. Always for player reports. For a directive that is plainly a
design preference, skip it and file the item.

- **The attached log** is the highest-value artifact. It is the Debug-and-below
  detail that is never shipped as breadcrumbs, from the minutes before they hit
  send. Read it before anything else. An event that is *absent* from a log is
  often the finding: two wins and no streak event is a diagnosis.
- **The screenshot** shows the state they were describing, which is usually a
  faster diagnosis than the words.
- **Correlate by `session_id`.** The same value is a Sentry tag, an OTLP
  attribute on client logs in Grafana Loki (`{service_name="sodogku-client"}`),
  and the `X-Session-Id` header on backend calls. In Loki, `session_id` is
  structured metadata, not a stream label: match it with `| session_id="<id>"`,
  never with a line filter `|= "<id>"`, which silently returns nothing.
- **Absence of client events is weak evidence** across processes. Telemetry
  buffers to disk when offline and can arrive days late. Absence *within one
  attached session log* is strong evidence, because that file is written locally
  and shipped whole.

### 6. File it

Append a section to `docs/todos.md` in the format that file documents. Assign the
next `SD-<n>`, counting the whole history rather than the file, because completed
items are deleted:

```shell
git log --all --format=%B | grep -oE '\bSD-[0-9]+' | grep -oE '[0-9]+' | sort -n | tail -1
```

Keep the reporter's own words in **Ask** where the wording carries intent. Put
the Sentry URL, session id and date on the last line of **Hints** so the worker
can go back to the source without you.

If there is nothing to do, say so in the ledger with a reason.

### 7. Bugs get a case file

**A bug's TODO is not enough on its own.** The worker who picks it up weeks later
cannot re-derive what you are looking at right now, and the Sentry attachment
URLs expire. Write `docs/cases/SD-<n>/` containing:

- `README.md` — the report verbatim, the build and session, what the screenshot
  shows, what the log proves, where in the code it comes from, "Done when", and
  hints. Separate what you verified from what you inferred.
- `session-log.txt` — saved to disk, not linked.
- `screenshot-*.jpg` — saved to disk, renamed to say what they show.

Reference the directory from the TODO under a **Case file:** heading.

Two things belong in a case file and nowhere else. **Say when the current
behavior is deliberate** and quote the comment that argues for it, so the worker
overturns a decision knowingly instead of "fixing" it. And **say what telemetry
is missing**, because a fix nobody can measure is a fix nobody can check.

### 8. Close the loop

Append one line to `docs/feedback-log.md` for every report handled, whichever way
it went.

Then, in Sentry:

- **Filed a TODO for a directive?** Comment with the TODO id and **resolve it**.
  The TODO and the ledger are the trace; leaving it open makes the unresolved
  list a second, worse copy of the queue.
- **Filed a TODO for a bug?** Comment with the TODO id and the case file path,
  and **leave it unresolved**. It is still broken. The unresolved list is then
  exactly the list of known live defects, which is worth having.
- **No action?** Comment with the reason and resolve it.

**Whoever fixes a bug resolves its Sentry issue in the same pass**, and says
which commit fixed it. That is the other half of this rule and the only thing
that keeps the unresolved list honest. The `nightly-run` skill does this when it
completes an item; a human finishing one by hand has to do it too.

### 9. Report

Say how many reports were seen, how many became TODOs, how many were no-action,
how many Sentry issues are still open and why, and anything that needs a human.

## Guardrails

- **Idempotent.** The ledger check in step 1 is not optional.
- **This routine writes three things and nothing else.** `docs/todos.md`,
  `docs/feedback-log.md`, `docs/cases/`, plus Sentry comments and statuses. It
  does not change code. The `nightly-run` skill is the routine that does, and it
  calls this one as its first phase.
- **One report, at most one TODO.** If a directive contains three asks, file the
  one it leads with and note the others in **Hints**. Three items from one report
  become three half-remembered items. Several reports about one thing may share
  one TODO; say so in each ledger line.
- **Do not invent telemetry.** If the answer needs an event that is not emitted,
  say so and make adding the event part of the item.
