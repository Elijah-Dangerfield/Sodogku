# Things only you can do

Work that is blocked on a person with credentials, a browser, a password, or a
file. Nothing here can be done by an agent or a routine, which is why it is
separate from `todos.md` (the queue a worker takes from) and from
`BUILD-PLAN.md`'s punch list (features waiting to be built).

Delete an item when it is done. If something turns out not to be needed, delete
it and say why in the commit.

---

## 1. Save the art you have described into `art/source/`

**Blocking four punch-list items.** Images pasted into a chat message never reach
the filesystem, so nothing can be wired to them.

Wanted, in rough priority order:

- **The three paw-and-bone backgrounds** shown in chat on 2026-09-08. These are
  the ones blocking the welcome screen having any background at all, and the
  launch handoff behind the dog (R5 / P13, S/R12). `art/source/backgrounds/README.md`
  names the three files and says where each goes.
- **A flame icon** for the streak badge. The streak currently draws a paw print
  from the design system, which works. Swapping it is one line in
  `StreakButton.kt` once an `Icons.Flame` exists.
- **The sad dog and the bone artwork** mentioned earlier.

Put the originals in `art/source/`, and copy anything the app renders into
`libraries/resources/src/commonMain/composeResources/drawable/` as lowercase
snake_case. That is the whole pipeline: Compose generates `Res.drawable.<name>`
from the filename.

**Pasting an image into a chat message does not put it on disk.** It has to be
saved as a file, which is why this item has outlived several attempts to close
it.

---

## 2. App Store Connect: create the app record

**Blocking a TestFlight build** (R11). The beta lane cannot upload to an app
that does not exist.

Also needed before the leaderboards below can be created, since they hang off
the app record.

---

## 3. App Store Connect: two Game Center leaderboards

Under the app record, **Features → Game Center → Leaderboards**. Two **Classic**
boards:

| Reference name | Leaderboard ID | Format | Sort | Range |
|---|---|---|---|---|
| Lifetime Score | `com.sodogku.leaderboard.lifetime_score` | Integer | High to Low | from 0 |
| Longest Streak | `com.sodogku.leaderboard.longest_streak` | Integer | High to Low | 0 to ~3650 |

**The IDs have to match exactly.** They are in
`libraries/leaderboards/src/commonMain/.../Leaderboard.kt`. A mismatch fails
silently and looks like a board nobody is on.

Each board needs at least one localisation (display name and score-format
suffix) and an image.

The app side is live as of `647d4a8`: the module is in the graph, and Settings
shows a Leaderboards row on iOS once Game Center answers. Until these two
records exist, submissions go out and are dropped by the platform, which is the
designed behaviour and produces no error anywhere.

---

## 4. developer.apple.com: enable Game Center on the App ID

**Certificates, Identifiers & Profiles → the App ID → enable Game Center.**

Xcode's automatic signing usually does this itself the first time it provisions
the target. A manual provisioning profile has to be regenerated. If a device or
TestFlight build suddenly fails to sign, this is the cause.

---

## 5. Run one command in a terminal

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

`xcode-select -p` already reports this path, and iOS builds work here, but the
simulator integration refuses with an error naming this exact command. It needs
your password, so it cannot be run from a session. Without it, iOS can be built,
installed, launched and screenshotted, but **not tapped** — so every iOS gesture
so far is unverified, including the feedback panel's swipe and anything behind a
Game Center sign-in.

---

## 6. Signing credentials for the beta lane

`apps/ios/fastlane` reads these from the environment:

- `FASTLANE_APPLE_ID`
- `APPLE_TEAM_ID`
- a distribution certificate and provisioning profile

Until these exist locally, `build_only` is the furthest the lane can go here.

---

## 7. A Sentry DSN, if you want the feedback loop proven end to end

The in-app feedback panel works and the tag reaches telemetry — logcat confirms
`Telemetry: Sentry disabled, feedback dropped | tags={feedback_kind=owner_directive}`.
What is unproven is everything past `captureUserFeedback`: the fingerprint, the
tag as Sentry stores it, and the screenshot and log attachments arriving.

The `feedback-triage` skill queries Sentry for those tags, so it cannot be
exercised either until reports actually land.

---

## 8. Decisions I deferred to you

- **A weekly streak present.** Not built. With ads narrowed to bones and sniffs
  it is pure cost with no impression behind it, and the streak already pays out
  twice through the freeze and the restore. If you want one, the suggestion is 3
  bones on day 7: one board's worth of mistakes, denominated in what the ads
  already sell.
- **`com.apple.developer.applesignin`** is still in `iosApp.entitlements` from
  the identity work that C0 deleted, and nothing enables it. Removing an
  entitlement is safe where adding one is not, but it is worth a deliberate
  decision rather than a drive-by removal.
