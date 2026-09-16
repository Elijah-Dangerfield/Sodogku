---
name: nightly-run
description: The unattended overnight pass. Triages every new Sentry report into docs/todos.md, then works the queue with subagents, escalates anything that needs a person into docs/OWNER-TODO.md, commits and pushes each finished item, and writes a recap to docs/nightly/. Use when asked to "run the nightly", "do the overnight pass", or on a schedule.
---

# Nightly run

One pass, five phases, in order. Triage is uncapped. Code work stops at **15
items**. Everything that lands is committed and pushed to `main` as it lands, so
a run that dies halfway still leaves finished work on the remote and an accurate
queue behind it.

You are the orchestrator. You spawn subagents and you own the files. Subagents
investigate and write code; only you write `docs/todos.md`,
`docs/feedback-log.md`, `docs/OWNER-TODO.md`, and the recap. Two agents appending
to the same markdown file at the same time is how a queue loses an item.

## Fixed coordinates

| Thing | Value |
| --- | --- |
| Repo | `/Users/elijahdangerfield/Workspace/Sodogku` |
| TODO queue | `docs/todos.md` |
| Human-only queue | `docs/OWNER-TODO.md` |
| Triage ledger | `docs/feedback-log.md` |
| Recap | `docs/nightly/<YYYY-MM-DD>.md` |
| Triage procedure | the `feedback-triage` skill |
| Repo conventions | `AGENTS.md` |
| Branch | `main`, committed and pushed directly |

## Default: decide, don't ask

Nobody is awake. Every judgment call is yours, and the bias is toward doing the
work. A question you would have asked becomes either an assumption you write down
in the recap, or an item in `docs/OWNER-TODO.md`. It never becomes a stall.

Escalate to `docs/OWNER-TODO.md` only when the blocker is a *person*, not a
difficulty: credentials, a paid account, a store console, a browser session, a
payment method, a file only they have, a legal or copy decision that is theirs to
make, or a product direction where guessing wrong wastes more than asking. "This
is hard" and "I am not sure this is the best design" are not escalations. Pick the
smaller reversible option and say so in the recap.

## Phase 0. Preflight

1. `cd` to the repo. `git status --porcelain` and `git log --oneline -1`.
2. **If the working tree is dirty, do Phase 1 and Phase 5 only.** Uncommitted
   changes are a human mid-thought. Do not stash them, do not commit them, do not
   build on top of them. Say so in the recap and skip the code work entirely.
3. `git pull --rebase origin main`. If the rebase conflicts, abort it, do triage
   only, and put the conflict in the recap.
4. Note the start time. Create `docs/nightly/<today>.md` with the header and fill
   it as you go, so a run that is killed still leaves a partial recap.

## Phase 1. Triage, uncapped

Follow the `feedback-triage` skill for what a report is and how to read one. This
phase adds the fan-out.

1. **Enumerate, yourself.** Query Sentry for each kind separately
   (`feedback_kind:owner_directive`, `feedback_kind:bug_report`,
   `feedback_kind:feedback`), then drop every event id already in
   `docs/feedback-log.md`. The ledger check is not optional. Do this in the
   orchestrator, not in a subagent, so one list exists and nothing is triaged
   twice.
2. **One subagent per remaining report**, in parallel. Give each the issue id, the
   kind, and this instruction set:
   - Read the carrier event: the `feedback_message` extra, the tags, the attached
     `session-log.txt` and screenshots. There is no reachable feedback twin.
   - **Resolve `commit_sha` against the log and say how far behind `HEAD` the
     report is.** If a later commit already fixed it, that is a no-action naming
     the fixing sha, not a TODO.
   - `owner_directive` is an instruction, not a data point. Do not investigate
     whether it is worth doing. Restate it as a TODO and stop.
   - `bug_report` and `feedback` get the investigation in the triage skill:
     the log first, then the screenshot, then `session_id` correlation in Loki.
   - Say whether it is a **bug** (the app doing what it was not built to do) or a
     **directive** (the app working as built, wanted different). For a bug, also
     return the case-file material: what the screenshot shows, what the log
     proves, where in the code it comes from, and any telemetry that is missing.
   - **Write no files and touch no Sentry state.** Return a single block:
     proposed title, priority, `bug` or `directive`, `Ask`, `Done when`, `Hints`,
     the provenance line, and either `file` or `no-action: <reason>`.
3. **You write the results, serially.** Assign each `SD-<n>` yourself by
   incrementing the highest across the whole git history, append to
   `docs/todos.md` in the format that file documents, write `docs/cases/SD-<n>/`
   for every bug (saving the log and screenshots to disk, since the Sentry URLs
   expire), and append one ledger line per report either way.
4. **Then set Sentry state, per the triage skill's rule.** A directive is
   commented with its TODO id and **resolved**. A bug is commented with its TODO
   id and case-file path and **left unresolved**. A no-action is commented and
   resolved. The unresolved list is then exactly the list of known live defects.
5. Commit: `docs: triage <n> feedback reports`. Push.

An owner directive that is plainly a person-only job (buy the account, sign the
policy) goes straight to `docs/OWNER-TODO.md` instead of `docs/todos.md`, with its
provenance line intact.

## Phase 2. Work the queue, capped at 15

**Order:** `P0` first, then `P1`, then `P2`; within a priority, lowest `SD-` id
first. Count only items you actually attempt against the cap.

**Skip, and say why in the recap:**

- Items whose only remaining work is a person's (they belong in Phase 3).
- Items marked as a spike or an open question where the first real step is a
  decision, unless the decision is small and reversible, in which case make it.
- Items that need a device, a store account, or a signing secret to verify.

**One subagent per item, one item at a time.** Sequential, not parallel: they all
share one working tree, and two agents editing it at once produces a commit that
belongs to neither. Each worker gets:

- The full TODO section verbatim.
- `AGENTS.md` as the law, plus `docs/practices/testing.md` before it writes tests.
- The scope rule: change what the item asks for and what that change forces.
  Nothing else. No drive-by refactors, no reformatting, no version bumps.
- The finish line: the `Done when` condition, verified by a build or a test that
  the worker actually ran, with the command and its result reported back. An item
  is not done because the code looks right.

After each worker returns:

1. Verify yourself. Run the build or tests the item touches. Do not take the
   worker's word for green.
2. If green: `git add -A` the whole tree (never a subset of hunks, and never
   `git add -p`), commit with a conventional message naming the item
   (`fix: a sniff says what proved it (SD-11)`), delete the item's section from
   `docs/todos.md` in that same commit, and push.

   **Then close the bug's Sentry issue, if it had one.** An item with a
   `docs/cases/SD-<n>/` directory came from a report that was deliberately left
   unresolved because it was still broken. It is not broken now, so comment the
   fixing commit sha on every Sentry issue the case file names and resolve them.
   Delete the case directory in the same commit as the fix; it is scaffolding for
   the work, not a record of it, and the commit is the record. Skipping this is
   how the unresolved list rots into a list of things that were fixed months ago.
3. If red and the fix is close, send the worker back once with the failure. If it
   is still red, `git checkout -- . && git clean -fd`, leave the item in the
   queue, and record the failure and what was learned in the item's `Hints` and
   in the recap. A half-finished item on `main` is worse than an untouched one.

Between items the tree must be clean. Check `git status --porcelain` before
starting the next one; if it is not empty, something leaked, so reset and stop
Phase 2 there.

## Phase 3. Escalate what needs a person

For every item you skipped or discovered to be person-blocked:

1. Append it to `docs/OWNER-TODO.md` in that file's style: what is blocked, the
   exact thing only they can do, and the paths that prove it. Keep the `SD-` id in
   the heading so the two files can be cross-read.
2. Delete it from `docs/todos.md`. It lives in one queue, not two.
3. Commit: `docs: move SD-<n> to the owner queue`. Push.

## Phase 4. Keep the queue clean

`docs/todos.md` is a queue, not a changelog. Before writing the recap:

- Delete any item whose work is already in the tree, and say so in the recap.
- Merge duplicates into the lower id, folding the loser's `Hints` in.
- Update an item whose `Hints` you proved wrong tonight, including paths that
  moved.
- Never leave a ticked item behind. A tick is an item the next worker reads and
  skips.

Commit any hygiene edits: `docs: prune the TODO queue`. Push.

## Phase 5. Recap

Write `docs/nightly/<YYYY-MM-DD>.md`:

```markdown
# Nightly run, <YYYY-MM-DD>

Started <HH:MM>, finished <HH:MM>. Base commit <sha>, head <sha>.

## Triage

<n> new reports. <n> filed, <n> no action, <n> straight to the owner queue.
One line each: what it said, and where it went.

## Shipped

One line per item, `SD-<n> — <what changed>`, with the commit sha and the
command that proved it.

## Not shipped

Every item attempted and abandoned, or skipped, with the actual reason. Failures
go here in full, including the error, because this is the only place they are
written down.

## Escalated

What went to `docs/OWNER-TODO.md` and what it is waiting on.

## Decisions I made for you

Every judgment call where a reasonable person could have gone the other way, and
which way I went. This is the section to read first.

## Queue

<n> items left: <n> P0, <n> P1, <n> P2.
```

Be blunt. This file is read once, in the morning, by someone deciding whether to
trust the run. Overstating what happened is worse than a thin night. Then commit
(`docs: nightly recap for <date>`) and push.

## Guardrails

- **Never force-push, never rebase pushed commits, never touch another branch.**
- **Never `git push --tags`, never run release tooling.** Release-please owns
  versions.
- **No dependency upgrades**, no `versions.properties` edits, unless an item
  explicitly asks for one.
- **Never edit `docs/BUILD-PLAN.md`'s punch list.** That table is the owner's.
- **Do not delete a ledger line, ever.** The ledger is append-only and it is what
  makes triage idempotent.
- **A build you cannot run is not a pass.** iOS `xcodebuild` and anything needing
  a device are unverifiable here; if an item's `Done when` depends on one, it goes
  to Phase 3, not to a hopeful commit.
- **If Sentry is unreachable**, skip Phase 1, do the queue work, and say so.
- **If the push fails**, pull with rebase and retry once. If it fails again, stop
  pushing, keep the commits local, and lead the recap with it.
