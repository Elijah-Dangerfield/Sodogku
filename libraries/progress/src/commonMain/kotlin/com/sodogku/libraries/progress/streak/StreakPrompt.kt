package com.sodogku.libraries.progress.streak

/**
 * The one thing, at most, the streak is allowed to interrupt the player with.
 *
 * A sealed answer rather than two booleans on purpose: the two ceremonies are
 * mutually exclusive and the caller should not be the one deciding which wins.
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
     * chosen to keep going, and who has never played a daily. Telling somebody
     * to start a streak they are already three days into is a lie, and they
     * would be right to trust the next thing we say less.
     */
    data object Intention : StreakPrompt

    /**
     * A milestone worth a page. [streak] is the run as it stands, and is what
     * gets recorded as celebrated so the same number cannot fire twice.
     */
    data class Celebrate(val streak: Int) : StreakPrompt
}
