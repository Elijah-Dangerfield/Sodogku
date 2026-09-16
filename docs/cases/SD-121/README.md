# SD-121 case file — no streak ceremony when the streak starts or increments

On current `main`. The owner filed the report and then, fifteen seconds later,
filed a second one carrying the streak page as evidence.

## The reports

| | The report | The evidence |
| --- | --- | --- |
| Sentry | [SODOGKU-P](https://elijah-dangerfield.sentry.io/issues/SODOGKU-P) | [SODOGKU-Q](https://elijah-dangerfield.sentry.io/issues/SODOGKU-Q) |
| Event id | `7f7b252b5b184b559f65dc81ff13690f` | `68340f73a4424c86a2178da3a3298c59` |
| Filed | 2026-09-16 15:45:29Z | 2026-09-16 15:45:44Z |
| Route | GameRoute | StreakRoute |

Both from build `05b633b96a09`, session `1ba50ef1-4f55-4832-870f-ce8b91da99c3`.

SODOGKU-P: "I just came back every one day and completed a puzzle which
should've increment in my streak, but I didn't see it. Increment like I didn't
get a celebration page or anything like that."

SODOGKU-Q: "this might be helpful to have for the streak problem. I just filed."

## What the evidence shows

`screenshot-streak-page.jpg` is the streak page taken right after the complaint:

- "1 day streak"
- The week row has Monday and Wednesday filled, nothing else
- "Longest run: 1 days"
- The calendar highlights 14 and 16, with 16 outlined as today

So he played Mon the 14th, missed Tue the 15th, and played Wed the 16th. **The
streak reading 1 is arithmetically correct** — the missed Tuesday broke the run.
What is missing is the ceremony: going from no streak to a streak of 1 produced
no celebration at all.

Do not "fix" this by making a skipped day keep the run. Read his words as the
complaint about the ceremony, which is what the second report and the screenshot
are both about, and what SODOGKU-N asks for again in its second half.

Note also "Longest run: 1 days", which is a plural bug in the copy.

## What the log proves

`session-log.txt` covers the whole session, from cold boot at 15:42:21 to
15:45:06. He completed two campaign levels:

```
15:42:22.324  game.level_started        level 15
15:42:47.825  game.level_completed
15:42:47.890  game.level_reward_granted
15:42:47.890  Sending event Won
15:42:49.291  game.level_started        level 16
15:44:15.957  game.level_completed
15:44:16.003  Sending event Won
```

**There is not one streak event in the entire log.** `grep -ci streak` returns 0.
No ceremony, no prompt, no failure.

That matters because of how the code is shaped. `GameViewModel.offerStreakCeremony()`
runs inside `win()` and is the only place the ceremony is offered:

```kotlin
val prompt = Catching { streak.pendingPrompt() }
    .logOnFailure { "Failed to read the streak prompt" }
    .getOrNull()
    ?: return
```

"Failed to read the streak prompt" is **not** in the log, so the call did not
throw. Two `Won` events means `win()` ran twice. So the most likely reading is
that `pendingPrompt()` returned `StreakPrompt.None` on a day the streak went
from nothing to 1. Verify that before changing anything; the alternative is that
`offerStreakCeremony` is not reached from the path these wins took.

Also worth a look: `game.level_reward_granted` fired for level 15 and not for
level 16. That may well be correct, since the treat schedule pays on some levels
and not others, but it is the only other asymmetry between two otherwise
identical wins.

## Done when

Completing a board on a day that starts or extends the play streak shows the
streak ceremony, and the streak page does not say "1 days".

## Hints

- `libraries/progress/impl/.../streak/StreakPrompts.kt` owns `pendingPrompt()`.
  `StreakFold.kt` beside it owns the run arithmetic.
- `GameViewModel.offerStreakCeremony()` is the single call site, and its doc
  comment explains why it is called from the finished board and nowhere else.
  Keep that.
- `GameEvent.OpenStreak(streak)` carries the number being celebrated.
  `GameAction.OpenStreak` deliberately sends 0, because a page the player asked
  for should not animate. Do not collapse the two.
- The streak is a **play** streak, fed by any board, not a daily-puzzle streak.
  The doc comment on `offerStreakCeremony` says the daily used to be excluded
  and no longer is.
- This is a view-model-level claim, so a `GameViewModelTest` should be able to
  express it. Mutation-check it.
