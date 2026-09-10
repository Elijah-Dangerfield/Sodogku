# Backlog

Ideas and gaps that are real but not being worked. Deliberately separate from
[todos.md](todos.md), which is a queue a worker takes from: anything in here has
been looked at and set down, and nothing should pick it up without a person
saying so first.

Move an item back into `todos.md` when it is actually next. Delete it when it
stops being a good idea, and say why in the commit.

## SD-10 [P1] — The game makes no sound

**Ask:** Haptics ship (`AppCache.hapticsEnabled`, `rememberHaptics`), audio does
not exist anywhere: no clips, no player, no `soundEnabled`, and SPEC 11 still
lists the Settings row as outstanding. Audio is one of the two things
Meowdoku's reviewers praise unprompted, the other being its hint.

**Done when:** Dog placed, strike, level win, praise sting, button tap and
achievement unlock all play; a Settings row silences them; and nothing plays
over the iOS silent switch.

**Hints:** SPEC 20 lists the six clips under "Art and audio" and they are still
unordered. Follow the haptics shape exactly: a flag in `AppCache`, a toggle in
Settings, and playback at the screen rather than in the ViewModel.

