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

- 2026-09-09 · 22f29b8d1ca742f9b0925d23db2b9a91 · owner_directive · no-action: text unrecoverable, filed from a build before 26fcba1 so neither `feedback_message` nor `feedback.txt` is on the event; screenshot is an ordinary level 2 board with nothing wrong in it · https://elijah-dangerfield.sentry.io/issues/SODOGKU-2
- 2026-09-09 · cba9f5e2dac6454293aa08c1cb43acd2 · owner_directive · no-action: text unrecoverable, same pre-26fcba1 build; screenshot is an ordinary level 2 board · https://elijah-dangerfield.sentry.io/issues/SODOGKU-4
- 2026-09-10 · b9f9aae2426c428f967ba197f81858c2 · owner_directive · todo: SD-111 Rethink what giving up on the daily board leaves behind · https://elijah-dangerfield.sentry.io/issues/SODOGKU-5
- 2026-09-10 · b8381984cf324c9f9b8562a29d1bcb84 · owner_directive · todo: SD-112 Bigger timer text, and a booster pulse you actually notice · https://elijah-dangerfield.sentry.io/issues/SODOGKU-6
- 2026-09-10 · f5c8d173fb8e46a28e435c03c4c117c1 · owner_directive · todo: SD-113 Hold back the no-starting-dog puzzles, and say so the first time · https://elijah-dangerfield.sentry.io/issues/SODOGKU-7
- 2026-09-10 · b52e55bc4b5a42a9b1b400f5e9b5f946 · owner_directive · todo: SD-114 Bring the achievements page in line with the rest of the app · https://elijah-dangerfield.sentry.io/issues/SODOGKU-8
- 2026-09-10 · 59b851e683394f3ca754257aec693f2d · owner_directive · todo: SD-115 The Levels and Start over buttons do nothing · https://elijah-dangerfield.sentry.io/issues/SODOGKU-9
- 2026-09-10 · ec3f6b49b2414de6b7f83540f7403959 · owner_directive · todo: SD-115 The Levels and Start over buttons do nothing (same defect as SODOGKU-9, folded into one item) · https://elijah-dangerfield.sentry.io/issues/SODOGKU-A
- 2026-09-10 · 232baa8ffe0e4329a0243d3ca5cba22c · owner_directive · todo: SD-116 Five paws looks unearnable · https://elijah-dangerfield.sentry.io/issues/SODOGKU-B
- 2026-09-10 · 5cba532acf834985b0bcd74ad2391b5c · owner_directive · todo: SD-116 Five paws looks unearnable (same ask as SODOGKU-B, folded into one item) · https://elijah-dangerfield.sentry.io/issues/SODOGKU-C

<!--
First run of the routine, 2026-09-14, owner directives only.

Two things the routine learned about the tooling:

1. The feedback twin is not reachable from the Sentry MCP. `search_issues` with
   `issue.category:feedback` returns nothing for the sodogku project, and
   `get_issue_user_reports` on a carrier returns nothing either. The same query
   does find feedback in the `cards` project, so it is the sodogku reports that
   are missing from that index, not the query. The skill's step 2 says to pull
   the twin for the text; use the carrier's `feedback_message` extra and its
   `feedback.txt` attachment instead, both added in 26fcba1.
2. Reports filed before 26fcba1 have neither, so their text is gone. The two
   no-action lines above are that, not a judgment about what the owner wrote.

All ten carriers were already resolved in Sentry before this run, which predates
the ledger. The eight that became TODOs were left resolved rather than reopened;
the ledger and the TODO ids are the trace. Flagged to the owner.
-->
