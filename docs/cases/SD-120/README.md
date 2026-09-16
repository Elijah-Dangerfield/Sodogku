# SD-120 case file — the Bones prompt opens on every board started with zero bones

Filed twice, four days apart, on two different builds. The second one is on
current `main`, so this is live.

## The reports

| | Sep 12 | Sep 16 |
| --- | --- | --- |
| Sentry | [SODOGKU-E](https://elijah-dangerfield.sentry.io/issues/SODOGKU-E) | [SODOGKU-M](https://elijah-dangerfield.sentry.io/issues/SODOGKU-M) |
| Event id | `5d6b12c052254b2cb82fbbfde7b9155a` | `7f68d9fd52ce45318c9c670a56bc1c38` |
| Build | `aeae187170d5` | `05b633b96a09` |
| Session | `bec35582-715e-4374-98b3-19ad3ac2e472` | `1ba50ef1-4f55-4832-870f-ce8b91da99c3` |
| Route | GameRoute, level 8 | GameRoute, level 16 |

Sep 12: "we show this dialog way too much. is it every on resume of every puzzle.
it's popping up a lot"

Sep 16: "I get the out of bones message at the start of every game. idk if that
makes any sense. maybe we need to not do that. maybe just when you run out or
make a mistake after running out idk"

## What the screenshots show

`screenshot-sep12-level8.jpg` and `screenshot-sep16-level16.jpg` are the same
dialog on two different levels. Title "Bones", body "A wrong guess costs a bone.
The same three carry across every board, so a new puzzle does not hand you any.
Run out and the attempt ends." Then "You have 0", a blue WATCH AN AD FOR 3, and
NOT NOW. The bone pills in the HUD behind it are empty in both.

Neither board has been played yet. Sep 16 is level 16 at 1/5 dogs with the
starter dog placed and nothing else; Sep 12 is level 8 at 0/4.

## Where it comes from

`features/game/impl/.../GameViewModel.kt`, in `startAttempt`:

```kotlin
boosterPrompt = if (!rehearsal && it.livesRemaining <= 0) Consumable.Bone else null,
```

**This is deliberate, and the reasoning is in the comment above it:** "A board
opened with nothing to spend meets the offer straight away rather than on the
guess that ends it." Bones carry across boards and a new puzzle does not hand
out any, so a player at zero is going to hit the wall on their first mistake,
and the argument was that meeting them at the door is kinder than meeting them
at the end.

The owner has now played it and disagrees. Treat the existing behavior as a
decision being overturned rather than as a defect, and keep the reasoning it was
built on: whatever replaces it still has to leave the player a way to get bones
before the attempt dies.

His own suggestion, and it is only a suggestion: show it when they actually run
out, or on the first mistake after running out.

## The session log

`session-log-sep16.txt`, from the Sep 16 build. The relevant stretch:

```
15:42:47.825  game.level_completed      level 15
15:42:49.291  game.level_started        level 16
15:43:45.937  Feedback forwarded to Sentry (owner_directive)
```

The prompt is not in the log at all. **There is no telemetry for the booster
prompt being shown or dismissed**, so there is no way to answer "how often does
this actually fire" from data. Adding `ads.gate_shown`-style events for the
booster prompt is worth doing as part of this, and is the only way the fix can
be judged by anything other than the owner playing again.

## Done when

A player who starts a board with zero bones is not shown the dialog before they
have done anything, and still finds out about bones before an attempt ends
because of them.

## Hints

- `GameViewModel.startAttempt` is the trigger. `BoosterPrompt.kt` is the
  composable. `GameAction.DismissBoosterPrompt` and `GameAction.RefillBones`
  are the two exits.
- The prompt is suppressed on the rehearsal board already; that guard stays.
- Watch the ad path. `refillBones` goes through the same fail-open ad gate as
  every other refill, so a network with no fill must not become a lock.
- Mutation-check whatever test you add. The condition is one line and it is easy
  to write a test that passes against both versions of it.
