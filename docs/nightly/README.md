# Nightly recaps

One file per overnight run, `<YYYY-MM-DD>.md`, written by the `nightly-run` skill
just before it pushes. Each is a record of what an unattended agent did to `main`
while nobody was watching: what it triaged out of Sentry, what it shipped, what it
tried and abandoned, what it handed back to a person, and every judgment call it
made on your behalf.

Read the **Decisions I made for you** section first. That is where a run tells you
where it guessed.

These are append-only history, not a queue. Delete old ones when they stop being
useful; nothing reads them.
