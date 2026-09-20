# Streak freeze and streak restore

A design doc. Nothing here is built. Written 2026-09-20 against `main`, and it
answers SD-28 in `docs/backlog.md` and SD-138 in `docs/todos.md`.

**The owner took the load-bearing decision on 2026-09-20: one freeze economy, on
the play streak, and the daily's freeze and restore retire.** So everything below
that was conditional on that answer is now the plan rather than a recommendation,
and option B in the options table is off the table. `docs/decisions.md` carries
the call. Three smaller questions are still open and are listed under "What the
owner decides".

## Recommendation, in four sentences

Build **one** item, a freeze, held as a balance, spent against the **play streak**. It
spends itself automatically when the player comes back, and the same item applied to a gap
already behind them is what a "restore" is, so freeze and restore are one mechanic with
two spend moments rather than two features. The balance is fed by three routes that do not
compete: a starting handful, campaign first-clear rewards, and a rewarded ad at the break.
**Do not create a consumable store product in phase 1**, because the paid route that
already exists is Pro, a consumable cannot be restored after a reinstall on a device-local
game, and a cash offer at the break would be the third offer at one moment, competing with
your own ad.

And retire the daily's freeze and restore as player-facing offers when the play-streak one
ships. Two freeze economies is one too many, which is the question SD-28 asks and this is
the answer.

## What was verified by reading code

1. **A freeze and a restore already exist, for the daily, behind a rewarded ad.**
   `DailyRepositoryImpl.useFreeze()` and `restoreStreak()`, both gated on
   `isPro || showRewarded(AdPlacement.StreakFreeze) != Dismissed`, wired to
   `GameAction.UseFreeze` / `RestoreStreak` and drawn as two buttons on `DailyCard`.
   Config keys `daily.freezesPerMonth` (2), `daily.restoreDaysPerMonth` (3),
   `daily.restoreMaxDays` (3), all read, all in the manifest.
2. **There are two streaks and they disagree.** The daily's folds from `daily_result` over
   four outcomes. The play streak folds from `play_day` over a set of dates and counts any
   finished board. `playCalendarOn` only ever emits `Future`, `Completed` and `Missed`;
   `StreakDayState.Bridged` exists, is styled, and nothing produces it. A daily freeze
   writes a `daily_result` row and touches `play_day` not at all. That is SD-138.
3. **Both numbers are on screen at once, under the same string.** `LevelDrawer.kt:178`
   labels the streak button with the play streak through `Res.string.daily_streak`.
   Seventy lines later the same file passes `status.streak`, the daily-only fold, to
   `DailyCard`, which renders it at display size through the same string resource. Two
   different numbers, one string, one screen. Filed separately as SD-142.
4. **Billing is entirely non-consumable-shaped.** `StoreBilling` has ownership, purchase,
   restore and price, and **no consume method**. Android queries `INAPP` and never calls
   `consumeAsync`; iOS reads `currentEntitlements`, which does not return consumables.
5. **Campaign prizes are one line to extend.** `GameViewModel.grantLevelReward` checks
   `isDaily`, first clear, then the treat schedule, grants one, persists, logs.
   `LevelRewardChip` already takes a label and a colour.
6. **There is a streak leaderboard.** `Leaderboard.LongestStreak` submits
   `StreakSummary.longest`, which since the `play_day` split is the **play** streak's
   all-time longest. Live on Game Center, inert on Play because the board id is empty. Its
   KDoc said "consecutive dailies" and was stale; corrected in the same change as this
   document. This matters for anti-abuse below.

**Inferred, not verified.** That the rewarded freeze is a meaningful revenue line. The
config KDoc calls it "the single most reliable ad impression in the app" and `features.md`
repeats it, but nothing measures it and SD-131 means the key has never been tuned in
production. Treat it as design intent rather than as data.

## Freeze versus restore: one item, two spend moments

Duolingo has both and the difference is real. A freeze is acquired in advance and spends
itself on a day you missed. A repair is bought after the fact against a break you already
have. The first is insurance, the second is a refund, and the second is the one that puts
a shop in front of somebody at the moment they feel worst.

**They collapse into one item here.** An item that auto-spends leaves nothing for a
restore to do for any player who held one, so the restore path exists only for a player
who held zero when the day went by. The difference is not the item, it is whether the
balance was empty at the time. Model it as two products and you get two inventories, two
budgets, two sets of copy and two chances for them to disagree, all for one real
behavioural difference: whether the player is told before or after.

That also settles where a sale would go without designing anything. The moment is exactly
where the balance is empty and a run just ended, which is `StreakPrompt.Lost`, whose KDoc
already says the offer belongs there and is deliberately unbuilt pending SD-28. The seam
is cut and waiting.

**The one thing that is genuinely two things is the bound.** Auto-spend wants a small
bound on how many days one absence may eat, so a fortnight away does not silently drain
five credits and hand back a lie. Retroactive application wants a reach bound, because a
gap longer than a few days is somebody who stopped playing. The daily already got both
right and `decisions.md` records the reasoning. Reuse the numbers: reach of three, and no
more than two consecutive days auto-covered.

## Which streak it applies to

**The play streak.** Three reasons.

It is the streak the app shows. The drawer button, the streak page, the celebration, the
lost-run page and the Game Center board all read `StreakSummary`, which folds `play_day`.
The daily's own fold survives on exactly one surface, the daily card, where it is a second
number wearing the first one's label.

It is the streak a freeze is *for*. The daily fold's own docblock says a frozen day
bridges without counting because "the streak is days you played". The play streak is
literally days you played.

And it is the cheap one. The play fold is a pure function of a set of dates, so covering a
day is a second set walked alongside the first. The daily's fold would need a fifth
outcome and a rethink of its missed-day arithmetic.

**What happens to the daily's offers.** Remove the two buttons and stop populating their
offers. Keep `DailyOutcome.Frozen` and `Restored` parseable forever, the way `Failed` is
kept recognisable, because existing installs have those rows and a name that stops parsing
drops a day from somebody's history. Retire the three `daily.*` keys once nothing reads
them, or `ConfigValuesAreReadTest` fails, which is that test doing its job.

Losing the daily's placement is not losing the ad. `AdPlacement.StreakFreeze` is reused
unchanged, so `ads.rewardedPlacements` keeps one switch for one idea.

## Where the balance lives, and what a reinstall does

**`AppData`, as `streakFreezes: Int?`.** Not a Room table.

The bridged *days* go in Room as rows, because a fold must not be handed an input it
cannot derive. But the *credits* are not a fold input. They are inventory, identical in
kind to sniffs and treats, which live in `AppData` as `Int?` where null means "never
granted, use the config key". Copy that shape exactly, including the nullability, because
a record that already says 3 is indistinguishable from a player who spent down to three.

So: a new insert-only `streak_bridge(date, source)` table, and one nullable int in
`AppData`. Both, not either.

**A reinstall wipes both.** Progress is device-local and Settings says so. A player who
bought freezes and reinstalled has lost them with no restore path, because a consumable is
by definition not restorable. That is the strongest argument against selling them for
cash, and why the paid path should run through Pro, which *is* restorable through the
store's own mechanism.

**Two devices get two independent balances**, and two independent streaks, because
`play_day` is device-local too. That is already true of the streak and nobody has
complained. Do not build for it.

## Can this be done with no backend

**Yes, completely, for phases 1 and 2.** Nothing about a device-local freeze needs a
server. The fold is local, the inventory is local, both grant routes are local.

**The only thing a backend buys is receipt validation on a consumable**, and it is not
worth it. Both stores verify locally: Play reads its own signed on-device cache, and
StoreKit 2 results are verified or unverified with the Swift layer already branching on
that. What a server adds is protection against a replayed purchase on a rooted device, and
the prize for that attack is free freezes in a game with no shared economy.

**There is already a server and it changes nothing.** `:apps:server` is Ktor and Postgres
on Fly.io, and it already receives `X-Install-Id` and buckets on it for config targeting.
So "hold the balance server-side" is technically a small step. Do not take it:

- It would not survive a reinstall either, because the install id dies with the install.
- It reintroduces user-scoped server state, which C0 deleted deliberately.
- It creates an outbox. A server-held balance spent offline is exactly a pending write,
  and `features.md` currently states there is no such queue anywhere in the app.
- It puts the streak behind the network, which SD-131 makes unreachable in every shipped
  build, so it would ship broken.

**Plainly: no Supabase, no new backend, no change to the existing one.**

## Purchase, ads and prizes as three routes

The failure mode to avoid is three offers at one moment. There are already two at the
break, an ad and Pro standing behind it, and adding a cash consumable there makes the ad
look like the sucker's option and the consumable look like a worse Pro.

Split them by moment instead of stacking them.

| Route | When | Grants | Why it does not cannibalise |
|---|---|---|---|
| Starting balance | First launch | 3 | Spent before the player has a reason to buy |
| Campaign first clear | On a level the schedule pays | 1 | Rewards play, never available on demand |
| Rewarded ad | At the break, balance empty | 1, applied at once | The free path, reachable every time |
| Pro | Already built | Freezes never cost an ad | The existing paid path, and restorable |
| Consumable pack | Phase 3 at the earliest | 5 | A bundle, so it is not the same purchase as the ad |

The paid route is not made worthless by the free one, because the free one is thirty
seconds of video per freeze and the paid one is five at once with no video ever. That is a
convenience sale, which is the only honest kind here. Selling one freeze for cash next to
a button that gives one freeze for an ad is selling a player their own time back at a
price they can compute, and they will compute it.

**Campaign prizes feed the same pool**, which is the point of one inventory. A
`streak.freezeSchedule` of the same type as the treat schedule, tuned much sparser, means
the level pane marks both kinds of paying row and the grant path is already written. It is
also what the owner picked in SD-28 on 2026-09-16: *"bones shouldnt be the only gift
right? streak freezes too"*.

## A starting balance

**Yes, and it costs almost nothing.** One config key, one nullable int, one read.

It is also right for a reason beyond cost. A freeze that only arrives after you have
broken a streak teaches the player that the app sells insurance after the fire. A freeze
you already hold is what makes the *first* missed day a non-event, and the first missed
day is what decides whether a nine-day run becomes a ninety-day one. Duolingo gives you
one free and shows it to you before you need it, and that is the part that works.

Three, matching the starting sniffs, starting treats, the bone count and the refill size.
A fourth consumable that starts at a different number is a fourth thing to learn.

## Anti-abuse

**There is a streak leaderboard**, so a freeze is not purely self-affecting.
`Leaderboard.LongestStreak` is live on Game Center and inert on Play.

**Not newly exploitable.** `decisions.md` already settled clock manipulation: nothing
detects or punishes a moved clock, future-dated rows are invisible to the walk until the
date arrives, and rows are insert-only so no clock setting leaves a permanently wrong
state. A freeze changes none of that. You cannot mint a `play_day` row without finishing a
board, and you cannot mint credits without a grant.

**Newly exploitable, mildly.** The balance is a counter in a DataStore blob, editable on a
rooted device, so a determined player can give themselves a hundred freezes. Two honest
observations: sniffs, treats and bones already have exactly this property and nobody has
treated it as a problem, and the bound that actually protects the leaderboard is not the
credit count but `streak.freezeMaxConsecutiveDays`. A player with a hundred freezes still
cannot bridge a four-day gap if the bound is two.

**What matters.** Somebody inflating their own number on a Game Center board of a few
hundred people is a nuisance, not a threat, and defending it costs a server and an
account, which is the trade C0 already refused. Do not defend it. Do put the bound in,
because the bound is what stops an *honest* player ending up with a number that is a lie
about them.

Worth doing for free: record `source` on each bridge row (`auto`, `ad`, `reward`, `pro`).
One text column, and it is the only way to learn later whether the ad route or the reward
route is carrying the feature.

## The realistic options

| Option | What it is | Cost | Verdict |
|---|---|---|---|
| **A. Nothing** | A streak with no freeze | 0 | A working streak, and SD-138 stays a live contradiction the app has already sold an ad on |
| **B. Bridge from the daily's rows** | The play fold reads `daily_result` bridges alongside `play_day` | ~1 day, no schema, no keys, no UI | Closes SD-138 cheaply and answers nothing SD-28 asks. Leaves two economies and the two-numbers bug |
| **C. Freeze as a balance on the play streak** | New table, `AppData` credit, three grant routes, daily's offers retired | ~3 to 4 days with tests, 1 schema bump, 5 config keys, 0 store products | **Recommended.** Answers SD-28 and SD-138 together, one economy, no backend |
| **D. C plus a consumable IAP** | A five-freeze pack | C plus ~2 to 3 days, a consume path on both platforms, two store products created and reviewed, and a permanent "I reinstalled and lost what I bought" support line | Phase 3 at the earliest, only if C's data says the ad route is not enough |
| **E. Server-held balance** | Ktor and Postgres keyed on install id | ~1 week, an outbox, and a reversal of C0 | No. Buys nothing that survives a reinstall, and ships broken under SD-131 |

## What the owner decides

1. ~~Does the daily keep a player-facing freeze and restore?~~ **Answered 2026-09-20:
   they retire. One economy, on the play streak.**
2. Does the daily card stop showing its own streak number? The same decision wearing a
   smaller hat, and it is the bug a player would actually report.
3. Is a cash purchase wanted at all, given Pro already grants ad-free freezes and is built,
   restorable, and not yet created in either store?
4. The numbers: starting balance (3), holding cap (5), auto-cover bound (2 consecutive
   days), retroactive reach (3 days), and which campaign levels pay.

**Everything else follows and needs no further decision:** that the balance lives in
`AppData` and the bridged days in Room, that `StreakDayState.Bridged` is what gets drawn,
that the ad reuses the existing placement, that the offer surface is `StreakPrompt.Lost`,
and that the fold stays pure with the write at one named place.

## Phased plan

### Phase 1: the freeze works, no purchase, no new store product

The smallest thing that ships and is useful. A player who misses a day and holds a credit
keeps their run, and sees why.

1. **`streak_bridge` table**, `(date PRIMARY KEY, source)`, dao shaped like `PlayDayDao`.
   Database to version 12 with an auto-migration; a new table is one Room writes itself.
2. **The fold takes two sets.** The play-streak walk steps through a bridged day without
   incrementing, exactly as the daily's does. The calendar emits `Bridged`, tested in the
   same order as `Future` before `Completed` for the same clock-goes-backwards reason.
   `longestPlayStreak` and `brokenPlayStreakOn` both need the bridged set too, and the
   second is the one that closes SD-138's specific complaint.
3. **One writer, two edges.** The spend lives in `StreakRepositoryImpl` and nowhere else:
   on the day-rollover edge and in `onBoardCompleted()`. It walks back from yesterday and
   bridges a day only when there is a credit, the run behind it is non-zero, the day is at
   or after the player's first `play_day`, and fewer than the bound have been bridged this
   pass. Mark the repository `AutoInit` so the boot edge actually runs.
4. **The balance.** `AppData.streakFreezes: Int?`, seeded from config at the same read
   site that seeds sniffs and treats.
5. **Grant on first clear.** Extend `grantLevelReward` to a second schedule. Its `Boolean`
   return and the single reward flag on state become a small sealed result or a second
   field.
6. **Draw it.** The calendar renders `Bridged`; the streak page says how many are held.
7. **Retire the daily's offers**, and stop the daily card printing its own streak number.
8. **Docs in the same commit.** Close SD-28 and SD-138, write the decision, correct
   `features.md`.

Phase 1 deliberately excludes any purchase, any store product, and any retroactive restore.

### Phase 2: the ad at the break

9. On `StreakPrompt.Lost`, when the balance is zero and the gap is within the reach bound,
   offer one rewarded ad at the existing placement, granting a credit and applying it
   immediately. Pro skips the ad on the same line the daily already uses. Fail open: only
   a deliberate dismissal withholds.
10. The `Lost` page's beat already exists. This is a button and a result, not a screen.

### Phase 3, only if phase 2's data says so

11. A consume path: `StoreBilling.consume`, Android `consumeAsync`, an iOS branch that
    credits at purchase and does not consult `currentEntitlements`. Two products created,
    priced per storefront, reviewed. A support answer for "I reinstalled".
12. Sell a pack of five, never a single, and never in the same sheet as the ad.

## What it costs

**Two new files**, a dao and a config values file. **About sixteen modified**, across the
progress library and its impl, the app cache, the config fallback map and manifest, the
game view model and level drawer, the streak feature, three design-system components,
strings, and four docs.

**One new table.** Schema 11 to 12, one auto-migration, no spec needed because a new table
has no wrong default. **One new `AppData` field**, additive, so the versioned serializer
round-trips existing blobs unchanged. **Zero store products** in phases 1 and 2.

**Five new config keys**, each needing a declaration, a fallback entry, a manifest entry
and a reader, or three existing tests fail.

| Key | Default | What it does |
|---|---|---|
| `features.streakFreeze` | `false` | Rollout flag |
| `streak.freezeStartingBalance` | 3 | What a new player opens with. 0 turns the grant off |
| `streak.freezeMaxHeld` | 5 | Holding cap, so the schedule cannot print forever |
| `streak.freezeMaxConsecutiveDays` | 2 | How many days one absence may auto-cover. 0 turns auto-spend off |
| `streak.restoreMaxDays` | 3 | Reach of a retroactive application |

Three `daily.*` keys retire once nothing reads them. Net key count: plus two.

**The defaults are the product until SD-131 is fixed.** No shipped build can read remote
config, so every one of these resolves to its fallback and stays there. Pick them as if
they cannot be changed, because today they cannot. The rollout flag in particular means
nothing: shipping `features.streakFreeze = false` ships a dark feature no console write can
light, so it has to ship `true` or the feature has to not ship. Worth knowing before
writing the key rather than after.

**Tests** go in the two existing streak test classes, one assertion per rule.
Mutation-check each: a fold that always returns zero satisfies "a missed day breaks the
streak", which is the exact trap `decisions.md` names. The one easiest to write so that it
passes for the wrong reason is "a freeze does not bridge a day with no run behind it",
because the naive fixture has a run behind every day.

## Open questions

1. **Is the rewarded freeze actually the reliable ad impression the docs claim?** Nothing
   measures it and SD-131 means it has never been tuned in production. If it is not, the
   case for an ad route weakens and the case for a purchase strengthens. The single most
   useful thing to learn before phase 3.
2. **What a freeze should do about SD-137 and SD-141.** Both change what counts as a
   missed day, which is the input to every rule here, and a freeze spent on a day SD-141
   would have saved is a credit burned for nothing. **Land SD-141 first.**
3. **Whether the campaign schedule and the treat schedule can share a curve.** The band
   type is reusable and a freeze is a much rarer prize, so the numbers differ by an order
   of magnitude. A modelling call to make while writing it, not before.
4. **What the Play leaderboard does when its ids exist.** Android inherits whatever the
   freeze does to the number, on a much larger player base. Not a reason to change
   anything now, and a reason to have the `source` column when it happens.
5. **Whether SD-139's daily-only player needs a route of their own.** They feed `play_day`,
   so the freeze works for them, but the campaign grant route is closed to them by
   construction, leaving the ad and the starting three.
