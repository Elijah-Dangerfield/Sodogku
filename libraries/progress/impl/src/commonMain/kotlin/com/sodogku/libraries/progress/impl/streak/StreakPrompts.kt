package com.sodogku.libraries.progress.impl.streak

import com.sodogku.libraries.progress.streak.StreakPrompt
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * When the streak is allowed to take the screen, as a pure function.
 *
 * Every argument is something already on disk. Nothing in here reads a clock, a
 * config or a cache, so each rule below is one assertion rather than a scenario.
 */
internal fun promptFor(
    today: LocalDate,
    streak: Int,
    boardsCleared: Int,
    state: StreakPromptState,
): StreakPrompt = when {
    // The intention moment outranks a celebration. They cannot both be due in
    // practice, since the intention fires on the second board ever and a
    // celebration needs a run of two days, but the order says which wins rather
    // than leaving it to whichever branch is written first.
    intentionIsDue(boardsCleared, state) -> StreakPrompt.Intention

    celebrationIsDue(today, streak, state) -> StreakPrompt.Celebrate(streak)

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
 * A streak of one counts once the intention moment has been spent, and not
 * before. Until then it is the intention's to talk about, and two pages about
 * the same day is one too many. After it, a run of one is not a player starting
 * out, it is a player who came back to a run that had broken, which is the one
 * day they are most likely to break it again (SD-121). Saying nothing to them
 * was the old rule reading every 1 as "brand new".
 *
 * [StreakPromptState.celebratedOn] is what stops it firing twice for the same
 * day. A date rather than the run's length, and the difference is the whole of
 * SD-121's second half: a player who turns up once a week restarts at one every
 * time, so "celebrate when the number changes" congratulates them once and never
 * again. The number is the same every visit. The day never is.
 */
private fun celebrationIsDue(
    today: LocalDate,
    streak: Int,
    state: StreakPromptState,
): Boolean = streak >= firstCelebratedStreak(state) && state.celebratedOn != today

private fun firstCelebratedStreak(state: StreakPromptState): Int =
    if (state.intentionShown) FirstCelebratedRestart else FirstCelebratedStreak

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
     * The day the streak last took the screen, or `null`.
     *
     * The intention moment writes it too. That page prints the run the player
     * already has, so it is that run's celebration, and leaving this unset
     * behind it would let the next board of the same day say the same thing
     * again.
     *
     * One date rather than the set of every day already celebrated, which is
     * all the rule needs: the prompt is only ever asked for on a day the player
     * has just finished a board, so "not today" is the whole of "not again".
     */
    val celebratedOn: LocalDate? = null,
)

/** Boards cleared before the intention moment is offered. */
internal const val IntentionAfterBoards = 2

/** The first run worth a page before the intention. One is its job. */
internal const val FirstCelebratedStreak = 2

/** And after it, when a run of one is a run that started over. */
internal const val FirstCelebratedRestart = 1
