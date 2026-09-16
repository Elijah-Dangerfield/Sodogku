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
throw. Two `Won` events means `win()` ran twice, so `pendingPrompt()` returned
`StreakPrompt.None`.

## Root cause, found 2026-09-16

`StreakPrompts.promptFor` has exactly three outcomes, and on the day in question
all three were unavailable:

```kotlin
private fun celebrationIsDue(streak: Int, state: StreakPromptState): Boolean =
    streak >= FirstCelebratedStreak && streak != state.celebratedStreak
```

`FirstCelebratedStreak` is **2**. His streak was **1**. So the celebration was
never due, and the intention moment had long since been spent, because
`intentionIsDue` is `!state.intentionShown && boardsCleared >= 2` and fires once
ever.

**The exclusion is deliberate and its reasoning is written down.** From the
comment on `celebrationIsDue`: "A streak of one is excluded because the intention
moment is already that conversation, and two pages about the same day is one too
many."

That reasoning is sound for the player it was written for, who reaches a streak
of 1 within minutes of installing and gets the intention page instead. **It does
not hold for a returning player whose streak broke and restarted at 1.** They
have already seen the intention moment, months ago, and they get nothing. That
is the defect: the exclusion assumes streak 1 always means "brand new", and it
also means "started again".

So this is not a broken ceremony. It is a rule with a case it does not cover, and
the fix belongs in `promptFor`, not in `offerStreakCeremony`.

Worth noting the owner reached the same place from the other direction: his
follow-up was that the bigger gap is there being no "you lost your streak"
moment at all. A run that restarts at 1 is exactly the run that just broke. That
half is filed separately as SD-127; the two want designing together even though
only this one is a bug.

Also worth a look: `game.level_reward_granted` fired for level 15 and not for
level 16. That may well be correct, since the treat schedule pays on some levels
and not others, but it is the only other asymmetry between two otherwise
identical wins.

## Done when

Completing a board on a day that starts or extends the play streak shows the
streak ceremony, and the streak page does not say "1 days".

## Hints

- `libraries/progress/impl/.../streak/StreakPrompts.kt` owns `promptFor` and the
  two constants. It is a pure function of three arguments, so every rule in it is
  one assertion rather than a scenario, and a test for this costs nothing.
  `StreakFold.kt` beside it owns the run arithmetic.
- `StreakPromptState` is the stored half: `intentionShown` and
  `celebratedStreak`. Whatever distinguishes "new player at 1" from "returning
  player back at 1" has to come from there or from the fold, because `promptFor`
  reads no clock and no cache and should stay that way.
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
