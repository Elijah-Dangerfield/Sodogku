# Feedback ledger

One line per report the `feedback-triage` skill has handled, newest at the
bottom. Append only.

This file exists for exactly one reason: **triage must be idempotent.** The TODO
queue is not a record of what was seen, because items are deleted when they ship.
Without a ledger, the run after a fix lands re-reads the same Sentry issue, finds
no matching TODO, and files it again.

Line format:

```
- <date> · <sentry event id> · <feedback_kind> · <disposition> · <sentry url>
```

`<disposition>` is either `todo: SD-<n> <title>` or `no-action: <one-line reason>`.

Run notes for passes that found nothing go in an HTML comment, so the file stays
a list of reports while still recording what the routine learned about the
tooling.

## Entries

<!-- Nothing yet. -->
