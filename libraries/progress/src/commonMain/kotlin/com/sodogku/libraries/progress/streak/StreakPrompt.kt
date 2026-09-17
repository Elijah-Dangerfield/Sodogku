package com.sodogku.libraries.progress.streak

/**
 * The one thing, at most, the streak is allowed to interrupt the player with.
 *
 * A sealed answer rather than a bag of booleans on purpose: the ceremonies are
 * mutually exclusive and the caller should not be the one deciding which wins.
 * [Lost] and [Celebrate] of 1 are due on exactly the same day, and that
 * collision is settled in `promptFor` rather than at three call sites.
 * Everything that could fire at a bad moment for somebody is decided here, in a
 * pure function, with a test per rule.
 */
sealed interface StreakPrompt {

    /** Nothing to say. The overwhelmingly common answer, and the default. */
    data object None : StreakPrompt

    /**
     * The full-screen "start your streak" moment, shown once ever.
     *
     * Only offered to a player who has cleared enough of the campaign to have
     * chosen to keep going. The "and has never played a daily" half went with
     * the streak no longer being the daily's — every finished board feeds it
     * now, so there is no daily to have played.
     *
     * It is also that run's celebration rather than only an ask: the page prints
     * the number, so the day it is shown on is spent and the streak does not
     * take the screen twice (SD-121).
     */
    data object Intention : StreakPrompt

    /**
     * A milestone worth a page. [streak] is the run as it stands, and is what
     * gets recorded as celebrated so the same number cannot fire twice.
     */
    data class Celebrate(val streak: Int) : StreakPrompt

    /**
     * The run that broke, named on the first day the player comes back.
     *
     * [broken] is the run they used to have, not the one they have now. The one
     * they have now is 1, and a page that prints only that is the thing SD-127
     * was filed about: a 12 became a 1 and nothing on screen admitted it.
     *
     * **This is where an offer would go.** The owner's instinct was a freeze or
     * a store at this moment, and it is deliberately not built: what a freeze
     * even covers is undecided and sits in `docs/backlog.md` as SD-28, which has
     * to be answered before anything can be sold here. The prompt carries the
     * number an offer would be priced against, and the page it opens has the
     * beat an offer would occupy, so the seam costs nothing to leave open and
     * the acknowledgement stands on its own without one.
     */
    data class Lost(val broken: Int) : StreakPrompt
}
