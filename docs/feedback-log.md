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

<!--
Follow-up, 2026-09-14, after the six items were worked.

**Four of the six were already fixed before they were filed.** Every carrier
event carries `commit_sha` and `AppTelemetry` puts it there so triage can tell
whether a report is already fixed on a later commit. This run did not read it.
The reports came from `74f66d5` and `f182418`; `main` was 160 to 177 commits
past them.

- SD-114, the achievements page: fixed by `d8b2d6c` a day after the report.
- SD-116, five paws: a real bug on that build, fixed by `ab0c189` 22 hours after
  the report.
- SD-115, the two dead buttons: one half fixed by SD-54, the other by SD-49.
- SD-113's tooltip half: already shipped as SD-51.

Filed as SD-117 so the skill stops doing this. The per-report lines above are
left as written, because the ledger is append-only and what it records is what
the pass decided at the time.
-->
- 2026-09-12 · b526ab384f7f497185c02a42d5f5832a · owner_directive · todo: SD-124 Give the streak celebration the number flip it was asked for · https://elijah-dangerfield.sentry.io/issues/SODOGKU-G
- 2026-09-12 · ea426cbebe0447c9942086134a98d2fe · owner_directive · todo: SD-123 Make the win sheet a full-screen celebration, not a dialog (its dark-mode ask noted in the item, not filed) · https://elijah-dangerfield.sentry.io/issues/SODOGKU-H
- 2026-09-12 · 5d6b12c052254b2cb82fbbfde7b9155a · owner_directive · todo: SD-120 The Bones prompt opens on every board started with zero bones (bug, left unresolved, case file) · https://elijah-dangerfield.sentry.io/issues/SODOGKU-E
- 2026-09-12 · c6803e4c6cf94d5aaada86752e151361 · owner_directive · todo: SD-125 "You have 0" in the Bones dialog is too small, and maybe unneeded · https://elijah-dangerfield.sentry.io/issues/SODOGKU-F
- 2026-09-12 · 2f30d82954914d2fb497b5587c414643 · owner_directive · no-action: already fixed by SD-113 (694576d). Reported from aeae187, before the starter-dog opening run; at the shipped default of 16 opening levels, level 8 opens with a dog · https://elijah-dangerfield.sentry.io/issues/SODOGKU-K
- 2026-09-12 · bd9bd19424c740f09c7f7cbda53f8e16 · owner_directive · todo: SD-126 Review the generated daily boards for how they open · https://elijah-dangerfield.sentry.io/issues/SODOGKU-J
- 2026-09-16 · 7f68d9fd52ce45318c9c670a56bc1c38 · owner_directive · todo: SD-120 The Bones prompt opens on every board started with zero bones (bug, left unresolved, case file) · https://elijah-dangerfield.sentry.io/issues/SODOGKU-M
- 2026-09-16 · a6a6e916b1a34e50a2fc186a4d2bbf85 · owner_directive · todo: SD-122 Five paws for a solve that took a while (bug, left unresolved, case file); its full-screen-celebration half folded into SD-123 · https://elijah-dangerfield.sentry.io/issues/SODOGKU-N
- 2026-09-16 · 7f7b252b5b184b559f65dc81ff13690f · owner_directive · todo: SD-121 No streak ceremony when the streak starts or increments (bug, left unresolved, case file) · https://elijah-dangerfield.sentry.io/issues/SODOGKU-P
- 2026-09-16 · 68340f73a4424c86a2178da3a3298c59 · owner_directive · todo: SD-121 (evidence only — the streak page screenshot the owner filed for SODOGKU-P; bug, left unresolved) · https://elijah-dangerfield.sentry.io/issues/SODOGKU-Q

<!--
Run of 2026-09-16, owner directives only. Ten reports, six TODOs, one no-action.

First pass under the new rule: a directive is resolved in Sentry as soon as it
has a TODO, and only a bug stays unresolved. Three bugs stayed open, each with a
case file under `docs/cases/`: SD-120, SD-121, SD-122. All three reproduce on
`05b633b`, which is HEAD.

The staleness check that SD-117 asked for was run on all ten. Only SODOGKU-K was
stale. The Sep 12 batch came from `aeae187`, twelve commits back, and the Sep 16
batch came from `05b633b` itself.

SODOGKU-Q is a second report filed fifteen seconds after SODOGKU-P purely to
carry a screenshot for it. The ledger keeps its own line, because the ledger is
one line per report, but it produced no TODO of its own.
-->
