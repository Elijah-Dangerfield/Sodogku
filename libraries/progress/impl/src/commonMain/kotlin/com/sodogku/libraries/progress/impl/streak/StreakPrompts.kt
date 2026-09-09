package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.progress.streak.StreakPrompt
import kotlinx.serialization.Serializable

/**
 * When the streak is allowed to take the screen, as a pure function.
 *
 * Every argument is something already on disk. Nothing in here reads a clock, a
 * config or a cache, so each rule below is one assertion rather than a scenario.
 */
internal fun promptFor(
    streak: Int,
    boardsCleared: Int,
    state: StreakPromptState,
): StreakPrompt = when {
    // The intention moment outranks a celebration. They cannot both be due in
    // practice, since the intention fires on the second board ever and a
    // celebration needs a run of two days, but the order says which wins rather
    // than leaving it to whichever branch is written first.
    intentionIsDue(boardsCleared, state) -> StreakPrompt.Intention

    celebrationIsDue(streak, state) -> StreakPrompt.Celebrate(streak)

    else -> StreakPrompt.None
}

/**
 * After the second board, once, ever.
 *
 * **Two, and the number is the whole design.** The first board is a practice
 * run: the player is still working out what the game *is*, and a full-screen
 * page asking them to come back every day is indistinguishable from an ad. The
 * second board is the first one they chose. That is the earliest moment the ask
 * is honest, and it is still inside the first session, which is where a habit is
 * won or lost.
 *
 * It used to be three, and it used to also require that the player had never
 * played a daily, because the streak was the daily's. Neither clause survives
 * the streak being about turning up: there is no daily to have played, and the
 * player already has a streak of one by the time they see this, because
 * finishing those two boards is what a streak is made of.
 */
private fun intentionIsDue(boardsCleared: Int, state: StreakPromptState): Boolean =
    !state.intentionShown && boardsCleared >= IntentionAfterBoards

/**
 * Every day the run grows, after the first.
 *
 * Deliberately not milestones any more. The old rule fired on 3, 7, 14 and every
 * 30, on the reasoning that the puzzle is the product and a page between the
 * player and the next board stops being a reward. That reasoning holds for a
 * page you have to *dismiss*; it does not hold for the one being built here,
 * which is the streak going up, in front of you, and then getting out of the way.
 * A run that is only acknowledged four times a month is a run nobody is keeping
 * for its own sake.
 *
 * A streak of one is excluded because the intention moment is already that
 * conversation, and two pages about the same day is one too many.
 *
 * [StreakPromptState.celebratedStreak] is what stops it firing twice for the
 * same day: the number only moves when the date does.
 */
private fun celebrationIsDue(streak: Int, state: StreakPromptState): Boolean =
    streak >= FirstCelebratedStreak && streak != state.celebratedStreak

/**
 * What has already been said, and the only streak state that is stored rather
 * than folded.
 *
 * It has to be stored: "we have shown this once" is not recoverable from the
 * play days, and the alternative, inferring it from the streak itself, re-fires
 * a celebration every time the app is reopened on the same day.
 *
 * Deliberately not part of `AppData`. These two fields are machinery and neither
 * means anything without the rules above beside it, which is the same argument
 * `SkipState` makes for living next door rather than in the shared blob.
 */
@Serializable
data class StreakPromptState(
    val intentionShown: Boolean = false,

    /**
     * The streak the last celebration was for, or `0`.
     *
     * A single number rather than a set of days already celebrated, and that is
     * a decision: a run that breaks at 30 and climbs back to 7 gets its page
     * again, because it is a different run and the player did the work twice. A
     * set would silently retire each number for the life of the install.
     */
    val celebratedStreak: Int = 0,
)

/** Boards cleared before the intention moment is offered. */
internal const val IntentionAfterBoards = 2

/** The first run worth a page. One is the intention moment's job. */
internal const val FirstCelebratedStreak = 2
