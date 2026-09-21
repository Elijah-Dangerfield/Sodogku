# What Sodogku offers

Every player-facing capability, as the app behaves today. Written for somebody deciding
whether they want this app: what it does, what it can be tuned to do, and what it
deliberately does not do.

Sodogku is the Queens ruleset with dogs. An `N x N` grid is split into `N` coloured
regions and the player places `N` dogs on it, one per region, row and column, none
touching. There are no accounts, no cloud save, and no server the game needs to run.

**How to read this.** Each section says what the feature does and the rules it keeps.
Where a number is tunable without shipping a new build it is called out as config. Code
paths appear only where naming one is the clearest way to say what happens.
`docs/decisions.md` holds the reasoning behind the choices; this file holds the behaviour.

## At a glance

| | |
|---|---|
| **Genre** | Logic puzzle, single player, offline |
| **Platforms** | iOS and Android from one Kotlin Multiplatform codebase |
| **Content** | 1,000 hand-verified campaign levels, plus a 730-board daily pool (two years) |
| **Progression** | Campaign ladder, daily challenge, two streak systems, 73 achievements |
| **Monetization** | Rewarded ads only, plus one non-consumable "Pro" unlock |
| **Accounts** | None. Progress is device-local by design |
| **Live-ops** | 62 remote config keys, an admin console, kill switches, force-update and maintenance gates |
| **Not built** | Sound, cosmetics, sharing, cloud save, push notifications |

**Two things a buyer should know up front.** Remote config is fully built on both ends and
**cannot currently reach a shipped build**, because the client's server base URL is empty;
it is a one-line fix and until then every key resolves to its compiled default. And the ad
units are Google's published test IDs, so the app earns nothing until real AdMob IDs are
filled in. Both are tracked in `docs/todos.md`.

## The board

**The rules.** Exactly one dog per region, one per row, one per column, and no two dogs
touching, including diagonally. A board is won when every row holds a dog and no rule is
broken.

**Sizes** run 4x4 to 10x10. Below four there is no legal placement at all; ten is where
the solver's bitmasks and the region alphabet stop, and a measured spike concluded an
11x11 plays as *longer* rather than harder.

**Gestures.** One tap crosses a square off or clears the cross, and is always free. Two
taps inside 320ms commit a guess: right places a dog, wrong costs a life. Dragging across
the grid paints crosses in whichever direction the first square implies, and never places
a dog. There is no long press for sighted players; a screen reader gets one.

**Auto-mark** is the quality-of-life feature that makes a 9x9 tractable. Placing a dog
rules out its row, column, region and eight neighbours. The deduction is **always
computed**; the Settings toggle only decides whether it is drawn. Hints, difficulty rating
and level verification all read the full deduction, so turning the crosses off changes the
picture and never buys worse help. It ships off by default.

**Manual crosses** sit on top, letting a careful player record their own reasoning. A
square that cost a life can never be un-crossed.

**Answer checking is a lookup**, because the solution ships with each level. A tap is an
array index rather than a solve, so there is no runtime cost on a cold tap.

**There is no free first mistake.** The only board where a wrong guess costs nothing is
the tutorial's rehearsal board.

## Bones

Bones are lives. There are three, and they are **one count across the whole game**, shared
by the campaign and the daily. Starting a level does not top them up and a retry hands
none back.

A wrong committed guess costs one. At zero the attempt ends, the board dims, the answer is
**not** revealed, and the lose sheet appears. Opening a board at zero shows the refill
offer immediately rather than waiting for the guess that would end it.

**The lose sheet** reports dogs placed and time on the board, and offers four routes out:

| Option | Cost |
|---|---|
| Watch an ad to refill and keep the board | Rewarded ad, free for Pro |
| Start over | Free, grants no bones |
| Skip the level | Rewarded ad, and only when a skip is offered |
| Back to the level list | Free |

**There is one revive and it restores the whole set.** Handing back a single life puts the
player on the same sheet one guess later.

**The revive cannot fail closed.** No fill, no network, an SDK that threw, ads switched
off, a config server nobody can reach: all grant the reward. The only way to leave with
nothing is to deliberately close the ad, and the offer is still there on the next tap.

**Scoring prices the attempt, not the holding**, so a mid-board refill is never a score
multiplier.

## Sniffs and treats

Three consumables share one shape, because three economies would be three things to learn
before the puzzle.

| Consumable | What it does | How it is spent |
|---|---|---|
| **Bone** | A wrong guess costs one. Out of bones ends the attempt. | By being wrong, never by tapping |
| **Sniff** | A hint. Shows squares where a dog *cannot* go. | The Sniff button |
| **Treat** | Places one correct dog, free, with no life at risk. | The Treat button |

All three start at 3, refill to 3 for a rewarded ad, persist across boards, and may be
held **above** 3, because clearing levels grants extra and a refill is a floor rather than
a reset.

**The sniff shows where a dog cannot go, never where one does.** It picks the single
shallowest technique that proves something and lights up to four squares that technique
alone found. The result is a proposal the player keeps or discards.

**A booster is never spent for nothing.** If deduction has nothing to add, the sniff
declines and keeps the charge. The treat does the same when there is no provable cell, and
the streak freeze and restore offers only appear when using one would actually help.

**First use always explains.** The first tap of each booster opens a card with three
choices: use one, watch an ad for three, or not now. Spending cannot be undone, so nobody
discovers the cost after paying it.

**Level rewards.** Clearing a campaign level on the treat schedule grants a Treat, first
clear only. The schedule is dense early where a Treat teaches something and sparse later
where the player is holding several: 54 Treats across 1,000 levels. The level list marks
every row that pays and marks the collected ones as spent.

**The free dog.** Levels 1 to 16 open with one dog already placed, as does the start of
each new grid size. So the first genuinely empty board is level 17, which lands after the
4x4 band is finished and before the difficulty ramp steps up. The first empty board also
gets a one-time "Empty on purpose" note, because an empty grid otherwise reads as a board
that failed to load.

**Pro** opens every attempt with a floor of three sniffs and three treats. A floor, never
an assignment, so it cannot be farmed by restarting.

## Score and paws

**The header shows one lifetime score**, folded from every banked board on demand rather
than kept as a stored tally. A tally that drifts has no witness and no server copy to
rebuild from.

**Per placement**, points scale with grid size, a combo multiplier that grows with
consecutive correct placements, and a speed multiplier that decays linearly over a window
proportional to the board. A 4x4 gets 16 seconds per placement, a 10x10 gets 40.

**A completion bonus** is priced per cell and scales with difficulty and lives remaining,
so both halves of a run grow with the board at the same rate. That is what lets one set of
thresholds describe a 4x4 and a 10x10 alike.

**Boosters cost points, not paws.** Each one spent keeps 85% of the run, multiplicatively,
so nothing can drive a score negative and the cost is the same wherever the help was
taken. The paw rating is measured on what the run earned *before* the cost, which keeps
the top rungs reachable for anyone who took a hint, including everyone following the
tutorial. Paws say how the board was solved; the score says what the help was worth.

**Paws run 0 to 5** and are score-based rather than strike-based. Finishing earns one; the
other four are fractions of par, and par is derived at runtime rather than stored, so
retuning what a five-paw clear means is a config change.

| Paws | Fraction of par |
|---|---|
| 5 | 0.89 |
| 4 | 0.77 |
| 3 | 0.64 |
| 2 | 0.53 |
| 1 | finished |

The rungs are placed in the measured gaps between real runs rather than chosen for
roundness, and a test holds the measurement. **Compressing the range is the failure mode
to watch** on any retune: two separate rounds have shipped a rating that carried no
information, once because the speed term was dead on large boards and once because no
clean run could score low enough to distinguish the cuts.

**Praise text** floats over the board on high-multiplier placements. Purely cosmetic,
every cutoff in config.

**Time to beat.** A cleared level reopens with its previous best as a target, reported
live under the board. **Records kept per level** are best score, best time and best paws,
each an independent best, so a fast replay never costs a three-paw clear.

All 17 scoring keys are remote config, applied as a whole set rather than field by field.

## Skip

After repeated failed attempts on a campaign level, the lose sheet offers a skip. It is
the last control on the sheet, because it is a rescue rather than an invitation to stop
thinking.

A skipped level records as skipped: no score, no paws, no time. It stays on the list and
can be cleared properly later, it does not count as a clear for achievements, and it
unlocks the next level normally.

**The cap is three a day and it applies to everyone including Pro**, otherwise a Pro
player reaches the end of a thousand levels in an afternoon. Pro's benefit is that the
skip costs no ad.

The allowance is checked **before** the ad, so nobody watches thirty seconds for a skip
they cannot have. With it spent, the control stays on screen, disabled, saying why:
removing it is how a player learns that a feature they used yesterday has silently gone.

**The daily counter only ever moves forward**, so winding a clock back cannot recover a
spent skip.

## The campaign

**1,000 verified levels**, bundled, ordered into bands.

| Levels | Grid | Difficulty |
|---|---|---|
| 1 to 10 | 4x4 | tiers 1 to 2 |
| 11 to 40 | 5x5 | tiers 2 to 4 |
| 41 to 100 | 6x6 | tiers 2 to 4 |
| 101 to 180 | 7x7 | tiers 3 to 4 |
| 181 to 280 | 8x8 | tiers 3 to 4 |
| 281 to 390 | 9x9 | tiers 3 to 4 |
| 391 to 500 | 10x10 | tiers 3 to 4 |
| 501 to 1000 | 10x10 | five further bands |

**Difficulty is not grid size.** It is the deepest reasoning technique the solver had to
reach, from single-candidate elimination up to a contradiction step. Each band opens a
tier *below* where the last one closed, deliberately: the grid just grew, which is its own
difficulty jump, so the reasoning gets a breather while the player learns a wider board.

**Every board in the pack is distinct** up to the eight grid symmetries and any renaming
of regions, asserted over the whole pack by an automated test. That is a claim the genre's
best-known incumbent cannot make.

**Every level is verified on every build.** Regions contiguous, region count equal to
size, and exactly one solution which matches the shipped answer. A malformed pack fails
compilation rather than the app. Generation is an offline JVM tool using the same solver
that runs the hint on device; nothing is generated on the phone.

**Levels are appended and never reordered**, because progress is keyed on level id.

**The level list** is a drawer over the board rather than a separate screen, since the
puzzle is the home screen. All 1,000 rows in one list, scrolled to where the player is.
Locked levels are shown rather than hidden, because seeing that level 200 is an 8x8 you
have not reached is the reason to scroll. Pro can jump to any level.

**The ending.** Clearing the last level presents a campaign-end summary with total levels,
paws and score, and offers the daily as its next action. It does not promise more content.

## The daily

One board per calendar day, identical for every player, drawn from a separate pool of
**730** boards so it never spoils a campaign level. The index is computed from the local
date with no server involved.

**The daily pool is not a ramp.** Every player meets the same board whatever level they
are on, so it stays at 6x6 to 8x8 and stays shuffled, with no easiest tier at all and
about a fifth at the ceiling. It is a three-to-five-minute habit, and it is deliberately
not *easier* than the campaign level its player is on.

**38% of dailies open with no forced move.** Nothing places a dog for free, so the first
move is a cross rather than a dog. None is unstartable, every board has a deduction
available on the empty grid and none ever needs a guess, but the first dog can be a long
way off. The starter dog is campaign-only and stays that way, because the daily is one
shared board and a free dog would move par for everybody at once.

**One attempt per day**, enforced by the database primary key rather than by a check.

**A loss writes nothing**, so the day stays open and the player can revive or start over.
The lost board is kept, so reopening hands back the position rather than a fresh run.

**A spent day still opens**, on its result rather than its board, as a review.

**The attempt is keyed to the date the board opened**, so a session running through
midnight counts for the day it started.

**Timezones.** The day boundary is the start of the next local day rather than plus 24
hours, because a daylight-saving day is 23 hours and midnight can be missing entirely. The
zone is re-read continuously, so a player who flies gets the right board at their new
midnight.

**The daily card** sits at the top of the level list with the date, streak, state, paw
rating, a countdown to the next board, and the freeze or restore offer when there is one.

Two independent kill switches close it.

## The streak

**There are two streak numbers and they measure different things.**

**The play streak** is the one the flame and the streak page show. Any day with a finished
board counts, campaign or daily, because the streak is about turning up rather than about
which puzzle. One row per day.

Today not being played does not break the run: a player opening the app at breakfast on
day nine has a streak of eight, not zero. Nothing is stored but the days themselves, so
the number cannot drift out of step with the history it summarises. The calendar shows
five whole weeks, and a future-dated day is drawn as future even if a row exists, so a
clock set forward cannot draw a lived-in day.

**The daily streak** is consecutive days with a cleared daily. Frozen and restored days
bridge it without counting. A day the player attempted and lost is not a missed day.

**Streak freezes and restores.** One rewarded ad covers one missed day, capped at two a
month. A restore is the bigger hammer on the same placement: it bridges a run of
consecutive missed days, up to three, capped at three bridged days a month. Together they
forgive at most three consecutive missed days; past that the daily streak really does end.
Pro gets both without the ad. This is the single most reliable ad impression in the app.

**Three moments can take the screen**, at most one per day:

| Moment | When |
|---|---|
| Commitment screen | Once ever, after the second board cleared |
| Celebration | Every day the run grows |
| Lost run | The first day back after a run of two or more broke |

The lost-run moment names the run that ended rather than silently showing a 1 where a 12
used to be, and it leaves a seam for a future offer without making one. Each is recorded
as shown on arrival rather than on dismissal, so a force-quit is not an exit, and the
guard is keyed to the day rather than to the run's length, so a player who turns up once a
week is congratulated every time rather than once ever.

**Known gaps.** A freeze bridges the daily streak and not the play streak, so the two can
disagree after one is used. A board finished just after midnight counts for the new day in
the play streak and the old day in the daily. A grace window after midnight is specified
and not built. All three are tracked in `docs/todos.md`.

## Achievements

**73 badges across 9 shelves**, all local.

| Shelf | n | What is on it |
|---|---|---|
| The campaign | 13 | Levels cleared, from 1 to 1,000, plus milestones on larger boards |
| Clean play | 8 | Clears costing no lives, including consecutive runs |
| The hard way | 9 | Clears after losing two lives, and clears with no help taken |
| Speed | 6 | Clears inside 30 seconds, and large boards inside a minute |
| Score and combos | 6 | Three score rungs, and runs of consecutive correct placements |
| Paws | 9 | Levels taken to three paws, including consecutive runs |
| Daily challenge | 11 | Dailies cleared and day-streak milestones up to 100 |
| Time on the boards | 3 | 1, 10 and 50 hours summed across every recorded attempt |
| Secrets | 9 | All hidden until earned |

**Every criterion is one shape: a counter reached a number.** That keeps progress-toward-
unlock a division rather than a special case.

**The three score targets are derived from par**, not typed by hand, because a badge
nobody can earn looks exactly like a working one.

**What is stored is the facts, not the counters.** One append-only row per finished
attempt, deduped per attempt, with every counter folded back out on demand. There is no
second copy of progress to drift, and **an achievement added in a later release back-fills
from history**, dated to the attempt that really earned it, rather than starting everyone
at zero. A separate record of what has already been announced means a catalog change
cannot re-toast a two-month-old badge, and moving a target out of reach never retracts a
badge somebody holds.

**Hidden badges show no progress at all** until earned, because a counter under a mystery
badge narrows down the condition.

**Two independent gates.** A player-facing toggle hides badges but keeps recording, so
re-enabling shows accurate history, and a remote kill switch turns the whole thing off.

## Leaderboards

Three boards, hosted by the platform. Nothing about them reaches a server of ours: the
platform owns the identity, the scores, the UI and the display-name moderation.

| Board | What is submitted |
|---|---|
| Lifetime score | The banked lifetime total, on every clear |
| Longest streak | The longest run of days with any board finished, on every clear |
| Weekly score | Points banked since the start of the current window |

Three rather than more, because a leaderboard is a shared room and splitting a small
player base across several empties all of them. Per-level, per-daily and total-paws boards
were each considered and rejected.

**iOS is Game Center and works today.** The weekly window is asked of the platform per
submission rather than decided on the device.

**Android is Play Games Services and is inert**, because the board IDs do not exist yet.
The platform reports itself unavailable, the Settings row is not drawn, and nothing is
submitted. Play has no recurring board, so the weekly board is iOS-only by design.

**Failing open is structural.** The interface has no suspending method and no method that
returns a result, so there is no way to write a call site that waits on a leaderboard or
branches on one. A score earned before sign-in resolves is held and flushed once the
platform decides who the player is.

**The sign-in sheet never arrives on its own**; it appears only when the player opens a
leaderboard themselves.

Because the solution ships with each level, the leaderboards are not cheat-proof and are
not claimed to be.

## Pro

One non-consumable in-app purchase, identical on both stores. It is a one-time payment and
the copy says so. **The price comes from the store and only from the store**, per
storefront; it is never hardcoded and never in config.

| Benefit | How it works |
|---|---|
| No ads, ever | Every gate short-circuits on the entitlement first |
| Unlimited offline play | The offline block is never raised for Pro |
| Continues, skips and freezes with no ad | Every one of those call sites treats Pro as a granted reward |
| Boosters topped up each attempt | A floor of 3 sniffs and 3 treats, never an assignment |
| Every level open from the start | The level list unlocks and the jump is allowed |

**The entitlement is treated as true until proven false.** If the store is unreachable at
launch, a paying customer must not see ads, so store ownership is three-valued precisely
so "we could not ask" is distinguishable from "no". Only a deliberate restore that comes
back "not owned" clears it.

**Restore Purchases is in Settings and always drawn**, because Apple rejects a
non-consumable app with no visible restore control.

**The paywall** is a bottom sheet with four triggers: the offline block, a continue, a
skip, and a direct tap from Settings. A session cap limits how often it can be offered.

**The Go Pro button is one tap to the store, no sheet first.** It stands in three places
a player already passes: beside the streak button at the top of the level pane, as a line
under Next level on the cleared screen, and beside the rewarded continue on the lose
sheet. It carries the store's price once the store has answered and no number before.
It is hidden for Pro, hidden inside the new-user grace (a player who has not been shown
an ad is not sold "no ads"), and switched off by dropping `direct_button` from
`paywall.triggers`. It is not a sheet, so the session cap does not count it. A purchase
that lands shows the same toast a badge does; one that fails says so in a dialog; a
cancelled one says nothing.

**The ad stand-in.** When a gate asked and the network had nothing, the player used to
carry on with no sign anything was attempted. That case now raises a Pro sheet that holds
its own close controls briefly, and system back always gets through.

Android is Play Billing 8 and iOS is StoreKit 2, with a narrow platform seam under a
common Kotlin entitlement layer, which is what makes the gates testable without a store.

## Ads

**Five placements: four rewarded, one floor.** Nothing on a board in play, ever.

| Placement | Trigger |
|---|---|
| Continue | Third strike: restore the lives, keep the board |
| Booster grant | Refill a sniff, a treat or lives on a board still in play |
| Skip | The skip offer |
| Streak freeze | Cover a missed daily, or restore a run of them |
| Level complete | Next level, when a free player has cleared `ads.interstitialEveryLevels` boards (5) without an ad of any kind |

**The floor and the ceiling are one counter.** Every board finished moves it along; every
ad on screen, rewarded or not, watched out or closed early, puts it back to zero. So a
careful player who never needs a continue still sees an ad every fifth level, and a
struggling player who watches a continue every other board is never shown one on top.
The interstitial pays nothing and withholds nothing: closing it opens the next board, a
failure to serve opens the next board, and it never raises the Pro sheet (the Pro answer
to it is the Go Pro line on the same screen). It never shows on the daily, offline (and
spends no offline grace, which is about rewards the player was owed), for Pro, or inside
the new-user grace.

**Every other placement being rewarded is policy, not an accident of what got built.** An
interstitial, an app-open ad and a banner were all specified, built, and deleted on
2026-09-14, having produced no impressions because nothing ever called them. The
interstitial came back on 2026-09-21 with exactly one caller; app-open and banner stay
gone. `AdPolicyTest` names the one placement that is not rewarded, so a second has to be
argued for in that file rather than merely added.

**New-user grace: no ads before level 5 or the first 5 minutes**, and both legs have to be
past. Day-zero ad exposure is the biggest single driver of first-session churn in this
genre.

**A failed ad grants the reward.** No fill, offline, not shown, an SDK that threw: all
grant. Only a deliberate dismissal withholds.

**The Pro offer sits below every free path**, so a day-zero player is never sold to.

**Kill switches are read at the point of use**, so switching ads off takes effect on the
next config refresh rather than after a force-quit nobody performs mid-incident. There is
a global switch, a per-placement map (the interstitial is in it as `level_complete`), and
`ads.interstitialEveryLevels`, where zero turns the floor off.

**Ad unit IDs are in the binary, never in config**, because a config outage that blanked
them would take ads and purchases down together. **The app currently ships Google's
published test IDs**, so it earns nothing until real ones are filled in.

**Never on the board.** No banner over a grid whose squares are already under 44pt.

## Offline

Play is fully local. Both level packs are bundled and nothing needs the network to solve,
so the only thing going offline stops is ads, and therefore the ad-funded grants. Pro is
unaffected.

**The grace.** A free player gets three levels or twenty minutes, whichever comes first,
counted from the first ad gate that could not be served rather than from going offline.
The reward is paid on every unservable gate either way; what changes past the grace is
that a block screen goes up behind them. **The grace resets on a successful ad view, never
on reconnecting**, so coming back online without watching anything leaves the debt owed.

**The block screen** requires all four of: no network, grace spent, the feature enabled,
and the player not Pro. It is a full screen rather than a dismissible sheet, it swallows
the back gesture, and it **dismisses itself** the moment connectivity returns or the
entitlement arrives. It is the highest-intent paywall moment in the app and is
instrumented separately.

**Config is offline-first.** The first frame never blocks on the network even on a fresh
install: the stream starts from the bundled defaults and a synchronous read returns
whatever is in hand.

**Nothing queues a player write, because there are none to queue.** The client never sends
player state anywhere, so there is no outbox, no pending-writes table, and nothing needing
an idempotency key. Two things do leave the device: telemetry, which is written to disk
before export and survives process death, and leaderboard scores, held in memory and
flushed on authentication.

## Audience and consent

**The app is general audience, not child-directed, and the code implements that branch.**
"Big bubbly kids themed" is an art direction with an expensive policy consequence: a
child-directed classification restricts Play to certified ad SDKs with no personalised ads
and no ad ID, and Apple's Kids Category bans third-party analytics and advertising
outright. That would remove the ad network, crash reporting and the telemetry pipeline
together, and take the whole rewarded-ad economy with them.

So: 13+ on the Play questionnaire, not in Designed for Families, not in the Kids Category,
and the ad network's child-directed tag set to not-child-directed. A heavily kid-appealing
icon plus a 13+ declaration can still draw a review flag, so the art should read "cute"
rather than "preschool".

**Settled in practice on 2026-09-21**, when both stores' forms were filed on this branch:
Play's target audience is 13-15, 16-17 and 18+, Apple's rating calculates 4+, and neither
Designed for Families nor the Kids Category was entered. The branch is still reversible, and
§6 of `docs/store/data-safety.md` still describes what reversing it would cost, but it is no
longer an open question blocking anything.

Two ad-request settings follow from it, and both are now explicit in code rather than
default:

- **`MAX_AD_CONTENT_RATING_G`**, set on both platforms. A target audience starting at 13-15
  brings the ads part of Families policy into scope, and G is also the only rating that
  cannot contradict a 4+ App Store rating. It caps what Google may serve, and says nothing
  about who the player is.
- **`tagForUnderAgeOfConsent` stays unset.** Ruled on by the owner on 2026-09-21 rather than
  left alone: the flag is all-or-nothing, so setting it would drop personalised ads for the
  entire audience and not only for under-16s in the EEA, and the UMP form already collects
  consent where the law requires it. The residual risk is named rather than hidden: an
  under-16 EEA player who consents through UMP can receive personalised ads.

**Consent.** Google's UMP form for the EEA and UK, and Apple's App Tracking Transparency
on iOS. Both are raised **before the first ad request and not at launch**, at a moment
where the value is legible. The order is enforced in one place per platform: consent, then
tracking permission, then SDK init, then the first request.

## Launch gates and legal

Force update, maintenance and legal re-acceptance are one decision, resolved at the moment
it is made rather than captured at startup, so an operator's change lands on the next
config refresh.

A **blocking** gate is rendered instead of the navigation host, so there is no back stack
entry to pop and deep links are dropped while it is up. A **notice** is a dismissible
banner over whatever the player was doing.

| | Order | Why |
|---|---|---|
| Blocking | force update, then maintenance, then legal | An update is the only permanent fix, and it replaces the client reading this config |
| Notice | maintenance, then legal, then soft update | The incident outranks the paperwork, which outranks the suggestion |

**Force update** fires below a minimum version code. A build that cannot report its own
version blocks nobody, since it would otherwise be below every threshold.

**Maintenance** needs both a mode and a non-blank message, because "mode without message"
is what a half-finished write looks like. The operator's text is the whole content of the
screen. The banner's dismissal is session-scoped, so a running incident says so again next
launch.

**Legal.** Versions and URLs come from config, so publishing new terms is a config change
rather than a release. Three rules each exist because the straightforward reading bricks
somebody: a first launch is seeded rather than prompted, the re-acceptance floor is capped
at the version actually on offer so it cannot be unsatisfiable, and closing the
non-blocking banner *is* the acceptance, which the copy says.

**Everything here fails open.** Every key defaults to zero or off, so a missing, partial or
unreachable config blocks nobody, and the whole resolution is wrapped so a resolver that
throws lets the player play. A force-update gate is the only config value that can brick
every install at once, which is why it is guarded this carefully.

Note that none of these gates can fire today, because config cannot reach a shipped build.

## Onboarding and the tutorial

**The welcome screen** is one screen: a looping dog, the tagline, the three rules in a line
each, and the legal links. Two exits, both into the game: start the tutorial, or skip it.

**The tutorial teaches on a board of its own**, a hand-authored 5x5 rehearsal board that is
in no pack and cannot be finished. It replaced a guided run over the first three real
levels, which spent a player's first three boards under a scrim and had to point at
whatever square the generator happened to produce. This one is chosen so each lesson has a
clean example, and its uniqueness is checked with the same solver the packs get.

**Nothing on it counts.** No attempt recorded, no level record touched, no achievement, no
life spent, no event fired.

**Fourteen steps**, or twelve for a player who has turned auto-mark off, because teaching a
feature somebody disabled is worse than not teaching it. In order: the free dog, the three
rules read off the board around it, the two gestures, lives, a placement with the auto-mark
cascade lit through the scrim, the sniff and the treat, a deliberate wrong guess that costs
nothing, and the sign-off.

**A step is one of two shapes**, and the difference is what stops it becoming a dead end. A
step you *read* dismisses on a tap anywhere. A step you *do* keeps its lit square live,
ignores taps elsewhere, has no confirm button, and advances only on the gesture it asked
for, and not until that gesture has finished drawing itself.

**Every step carries a skip**, which leaves the rehearsal for level 1 rather than dropping
the player onto a demo board they can never finish. It can be replayed from Settings
without putting the welcome screen in front of somebody 200 levels in.

**A screen-reader player can complete the gated steps.**

## Settings

A full screen reached from the board header. Every row writes straight through on tap,
anywhere on the row rather than only on the switch, and there is no save button.

**Playing**

| Row | What it does | Default |
|---|---|---|
| Cross off squares for me | Draws the auto-mark cascade. Presentation only. | off |
| Vibration | Buzz on mark, place and strike. | on |
| Reduce animations | Still dogs, shorter entrances, dialogs fade rather than spring. | off |
| Shapes on colors | Colourblind mode. | off |
| Replay the tutorial | Returns to the rehearsal board. | n/a |

**Achievements**, each row conditional on the feature being visible and enabled, plus a
leaderboards row drawn only when the platform offers one.

**Pro**, tappable only when the player is not Pro, and **Restore purchases**, always drawn.

**About** holds send feedback, terms of service, privacy policy, and the version as text.

**A footer, outside any card**, saying that progress lives on this device and that levels,
streaks and badges do not survive a reinstall or a move to a new phone. That is the honest
cost of having no accounts and it is stated where somebody will read it.

**A QA menu** exists on tester builds only, gated by a runtime receipt check on iOS. It
carries a shiftable clock and streak seeding on debug builds. The shake-to-report dialog
ships in release with the player-facing action only.

## Accessibility

The core mechanic is colour, so this is a design constraint rather than a checkbox.
Roughly 8% of men have red-green colour vision deficiency and no ten-colour palette
survives deuteranopia.

**Colourblind mode** overlays each region with a distinct glyph, ten of them, one per
region colour. There is deliberately no X or cross in the set, because that reads as the
player's own mark. The base palette is picked for lightness separation as well as hue.

**Nothing is encoded only in colour.** Rule chips, strike feedback and region highlight all
carry a shape or a motion component.

**Screen readers.** The board is playable with one, and this is a real implementation
rather than a set of labels:

- A square says its position and region, with its state announced separately, because
  state is the half a reader re-announces on its own when it changes.
- In colourblind mode the label names the glyph rather than the hue, because the glyph is
  the region's identity to that player.
- Crossing off is ordinary activation; **placing a dog is a long press and a named custom
  action**, because a second tap inside 320ms is consumed by the reader and never reaches
  the app. Without this the board could be marked and never played.
- **The board vanishes from the accessibility tree while anything covers it**, because a
  scrim that swallows a sighted player's taps is only a drawing.
- **The tutorial's lit square is a real control**, since a spotlight hole is invisible to
  the semantics tree.

**Touch targets.** 44pt at 10x10 is geometrically impossible: ten columns of 44 is wider
than any phone. Measured on device, a board square is 32.2dp at the narrowest supported
width, which clears WCAG 2.2 AA and its exception for an essential presentation. It does
not clear AAA or Apple's 44pt and cannot while keeping ten columns. **Everything that is
not a board square clears 44dp.**

**Dynamic type** is verified at font scale 2.0 on the board, Settings and every dialog.

**Reduce animations** reaches the design system as one composition local provided at the
app root, so a new screen honours the setting without its author knowing it exists.

**Still unverified: VoiceOver on iOS.** The semantics are platform-independent, but no pass
has been run.

## The dog

The playing piece, and the app's character.

**Poses split into board weight and hero weight**, and the split is a performance boundary
rather than a stylistic one. Up to a hundred board dogs can be on screen at once, so they
ship small; hero dogs ship large and appear one at a time. Shipping the originals
everywhere would be 8.3MB of assets and roughly 28MB of decoded bitmaps for one puzzle.

**Animation ships as sprite sheets rather than animated images**, because animated WebP
does not render on Compose Multiplatform iOS and would have meant an animation that works
on Android and silently freezes on iOS. One sheet is decoded once and shared by every dog
on the board. There is no third-party image library in the app at all.

**A placed dog keeps itself company.** It plays a loop, holds still a moment, then plays a
different one. A single loop on repeat stops being seen after about three passes; what
reads as alive is that it sometimes stops. The schedule is deterministic and seeded per
dog, and each cell starts at a different frame, or a board of dogs blinks in unison and
reads as a rendering glitch rather than a row of animals.

**One component draws every dog**, and it is what decides whether the dog may move: never
in previews or tests, and never when the player has asked for fewer animations. A build
test fails if anything outside that component names a dog image.

## Feedback

**Send feedback** is its own page under Settings: a text field, a send button, and a
confirmation panel that replaces the form. It forwards the note to the crash-reporting
service as a user feedback report. There is no feedback backend of our own and the
no-accounts rule means there will not be one.

**A send is never reported as failed.** Telling somebody their complaint failed to send is
a second thing to complain about.

**Report a bug** is reachable from a shake gesture, from error screens, and from a
home-screen quick action. It carries a log ID, an error code and a context message.

**Both player-facing forms say what they attach**, in as many words: the note is sent with
a log of what the app did this session, which is how the problem gets found without making
the player describe it.

**A floating feedback button** exists on tester builds only and does not exist in a player
build at all. Its panel captures a screenshot before the panel covers the screen, using the
composition's own graphics layer rather than a platform capture API, so it needs no
permission.

**Home-screen quick actions** are the daily challenge and report a bug. Report a bug is
second deliberately, because that row sits directly above Delete App.

## Saved progress

**Progress is device-local. It does not survive a reinstall, and Settings says so.**

Progress lives in a **Room database**, currently at version 11, with real auto-migrations
and **no destructive fallback**, because there is no account and no server copy, so a wipe
is unrecoverable.

| Table | Holds |
|---|---|
| `level_progress` | Per level: state, best score, best paws, best time, attempts, timestamps |
| `daily_result` | One row per local date. Insert-only; the primary key is the one-attempt-per-day lock |
| `play_day` | One column, the local date. The play streak, and nothing else |
| `score_event` | Append-only points with timestamps, pruned on a retention window. This is what lets a weekly leaderboard price a window |
| `achievement_fact` | One row per finished attempt, append-only, deduped |
| `achievement_unlock` | What has already been announced, and when it was really earned |

**Nothing derived is stored.** The lifetime score, both streaks, every achievement counter
and both freeze allowances are folded out of those rows on every read. A stored tally has
no witness and no server copy to rebuild from, and a fold makes a past bug retroactively
fixable.

**Settings, flags and light counters** live separately in a serialised record backed by
DataStore, which holds the value in memory and serves reads from there. That covers
onboarding and tutorial flags, the Settings toggles, consumable counts, the install ID, the
Pro entitlement, the accepted legal versions, and the in-progress board.

**The in-progress board** is one slot, and the newest real board takes it. It stores
placements, marks, strikes, score, combo and elapsed time, and is written after every move
rather than on a lifecycle callback, because a force-quit skips the callback. Elapsed time
is stored as a duration rather than a start timestamp, so hours the app spent closed are
not charged to the player.

**Switching phones** carries the Pro entitlement via store restore and strands the
progress. The cheap fix, if it is ever wanted, is an export/import code the player pastes,
not a server.

## Remote config

Postgres-backed, edited through a web admin console, with a bundled fallback map and an
offline-first client. This is intended as the live-ops lever and is the reason the server
exists at all.

**It cannot reach a shipped build today.** The client's network base URL is empty and
nothing replaces it, so every key resolves to its compiled default and the console edits
values no device will fetch. It is a one-line fix and it is tracked as a P1. Everything
below describes what the system does once that is connected.

**The rule: config owns numbers and switches, the binary owns content and logic shape.** If
changing it needs a new asset, a new string or a new code path, it belongs in the binary.
If it is a threshold, a cap, a frequency, a URL or an on/off, it belongs in config.

**Targeting** supports platform, version-code range, version range, country, locale, a
rollout percentage, and an install-ID allowlist, so a value can be rolled out gradually or
aimed at a single device.

### Two hard constraints, both held by tests

1. **Every declared key has a bundled fallback.** The app has to be fully playable,
   correctly monetized and legally compliant on a first launch with no network, forever, if
   the server never comes back. A test checks that every declared path resolves, that the
   fallback equals the declared default, and that no orphan key sits in the map.
2. **Monetization keys fail open toward the player.** A config outage must produce *fewer*
   ads and *fewer* blocks, never more. A server problem must never lock a player out of a
   game they already paid for. A test reads every value against an empty map and against
   mistyped values and asserts the resolved behaviour.

Both constraints are about **defaults**. Tightening a number in config is a live-ops
decision and is allowed; it is the shipped fallback that has to be generous, because that
is what an outage resolves to. The console puts a confirm sheet in front of writes that
block a player or make a monetization key fail closed, and on production that sheet
requires typing the environment name. It does not refuse them.

**One sharp edge.** A mistyped value on a boolean key does not fall back, because any
non-`true` string reads as `false`. The server's type check is what prevents this, which is
why the uploaded manifest must list every declared key and a test fails the build when it
does not.

**Kill switches are read at the point of use**, never captured when a screen opens.

**A declared key that nothing reads fails a test**, against a list that can only shrink. It
currently holds three.

### The keys

62 keys in the manifest, grouped by namespace. Defaults are the shipped fallbacks.

| Namespace | Controls |
|---|---|
| `ads.*` | Master switch, new-user grace, offline grace, per-placement enablement |
| `progression.*` | Skips per day, skip threshold, starter-dog windows |
| `boosters.*` | Starting and refill amounts, the treat schedule, Pro per-attempt floors |
| `scoring.*` | 17 keys: the whole scoring formula and the paw ladder |
| `daily.*` | Master switch, freezes and restores per month, restore reach, pool offset |
| `paywall.*` | Which triggers are live, the offline block, the ad stand-in, session cap |
| `legal.*` | Terms and privacy versions and URLs, the re-acceptance floor |
| `upgrade.*` | Minimum supported version, soft-update version, maintenance mode and message |
| `app.*` | Which level prompts for a store review |
| `features.*` | One flag per shippable-but-hideable feature, so anything can be dark-launched |
| `telemetry.*` | Event enablement, sample rate, log forwarding |
| `config.*` | The minimum gap between config fetches |

**The price is never in config**; it comes from the store, per storefront.

### What stays in the binary, and why

| Thing | Why not config |
|---|---|
| Level packs | Content. Needs generation and CI verification. A bad pack is worse than a stale one |
| Achievement definitions | Each needs a glyph and copy, so a new one needs a release anyway |
| Game rules and the scoring formula shape | Only the coefficients are tunable, not the formula |
| Ad unit IDs and the store product ID | Changing one is a store operation, and an outage that blanked them would take ads and purchases down together |
| The Pro price | Per-storefront, and only the store knows it |
| Anything needed before the first config fetch | Onboarding, the tutorial, level 1 |

**So new levels always need a release.** The campaign appends and never reorders. The daily
pool cannot be regenerated at all, because each played day records the pool index it drew,
so reshuffling would rewrite which board every past day was.

## Telemetry

Crash reporting plus a logs-and-traces pipeline, pivoting on a session ID, with an
authoritative event registry held against the code by a contract test.

**No per-tap event.** Forty taps per level across a thousand levels is a volume and a cost
problem, so taps are aggregated into the completion event.

Broad shape: game lifecycle, the daily, ads, purchases, onboarding and tutorial, the launch
gates, the offline banner, and leaderboard submission.

**The only identifier is an install ID**, a per-install UUID minted on first read that dies
with an uninstall. The app never shows it to anyone. There is deliberately no way to attach
a user or an email to a report: both seams existed, compiled, had no caller, and would have
made two published sentences in the privacy policy false, so they were deleted rather than
left dormant.

**Six dashboards** ship as committed JSON: level drop-off, difficulty calibration, the ad
funnel, paywall conversion, daily retention, and the tutorial funnel. A contract test holds
their queries against the event registry.

**There is no in-app analytics opt-out.** The telemetry switch is an operator control, not a
player one, and how a player asks for their data to be deleted is an open question.

## What the game does not have

Not oversights. Each of these was decided, and most of them were built and then removed.

- **No accounts, no sign-in, no cloud save, no user-scoped server state.** The identity
  stack was removed rather than disabled. Progress is device-local.
- **No per-player support actions.** With no account and no server-side progress, there is
  no way to restore one player's streak or grant one player anything. The nearest available
  lever is a config value aimed at an install ID.
- **No push notifications.** No APNs entitlement, no messaging SDK, nothing. A player who
  is not in the app cannot be reached.
- **No sharing.** The library, both platform launchers, the button, the sheet, the flag, the
  event and the strings are all gone.
- **No give-up on the daily.** It spent the day, ended the streak, could not afterwards be
  frozen or restored, and the player got nothing back for any of it.
- **No interstitials, no banners, no app-open ads.**
- **No free first mistake.**
- **No progressive disclosure on the level list.** Every level is visible, locked or not.
- **No sound.** There is no audio anywhere: no clips, no player, no toggle.
- **No server-delivered level packs.** Generation stays offline; only delivery could move.
- **No cosmetics economy or dog skins.**
- **No leaderboard on Android today**, because the Play Console board IDs do not exist yet.
  The code is there and inert.
- **No mirrored platform achievements.** It would be 73 console forms and 73 images the
  game does not have, and they cannot be taken back.
- **No in-app analytics opt-out**, and no way to request deletion in the app.
- **No A/B testing framework.** What exists is a deterministic rollout percentage, which is
  a staged-rollout primitive with no holdback and no metric attribution.
- **No outbox.** Nothing the player does produces a write that has to reach a server.

**Not in this list, because it is not settled:** the kids-versus-general-audience decision.
