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
    brokenStreak: Int,
    boardsCleared: Int,
    state: StreakPromptState,
): StreakPrompt = when {
    // The intention moment outranks everything below it. It cannot be due at the
    // same time as either in practice, since it fires on the second board ever,
    // but the order says which wins rather than leaving it to whichever branch
    // is written first.
    intentionIsDue(boardsCleared, state) -> StreakPrompt.Intention

    // **A loss outranks the celebration of the run that replaced it**, and this
    // is the one place the two genuinely collide. SD-121 made a run of one worth
    // a page precisely because a run that restarts at one is a run that just
    // broke, so every day a loss is due, `Celebrate(1)` is due as well.
    //
    // The loss wins because it is the same page saying strictly more. Both print
    // the 1; only this one admits it used to be a 12, which is the whole of what
    // SD-127 was filed about. Congratulating somebody on day one without naming
    // what day one cost them is the silence, dressed up.
    lossIsDue(today, streak, brokenStreak, state) -> StreakPrompt.Lost(brokenStreak)

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
 * Once, on the first day back from a break.
 *
 * Three clauses, and each of them is doing work.
 *
 * **The run is one.** That is what "first day back" is: the player finished a
 * board today and it is the only day standing. On the second day back the run is
 * two and the break is old news, which is what stops this reappearing every day
 * for the rest of the week. It is the same 1 [celebrationIsDue] keys on, for the
 * same reason, and they are due together by design rather than by accident.
 *
 * **There was a run to lose.** [ShortestMournedRun] is two, because a run of one
 * is a single day, and telling somebody who plays every other day that they lost
 * something on every single visit turns the moment into a scold. It also keeps
 * this away from a player's first week, where a broken run of one is just how
 * anybody starts.
 *
 * **It has not been said today.** The same day-scoped guard SD-121 introduced,
 * shared with the celebration on purpose: the player gets one page about a day,
 * whichever page it is, and a lost-streak screen that came back on every board
 * would be worse than the silence it replaces.
 *
 * Deliberately not gated on [StreakPromptState.intentionShown], unlike the
 * celebration of one. A broken run of two days is its own proof that this is not
 * somebody's first day, and the intention only fires off *campaign* clears, so a
 * player who has only ever played dailies can hold a long run without it ever
 * having been spent.
 *
 * There is no staleness rule either. A run that broke eight months ago is still
 * the run this player last had, and the first board back is still the only
 * moment anybody is listening.
 */
private fun lossIsDue(
    today: LocalDate,
    streak: Int,
    brokenStreak: Int,
    state: StreakPromptState,
): Boolean = streak == FirstCelebratedRestart &&
    brokenStreak >= ShortestMournedRun &&
    state.celebratedOn != today

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
 * **SD-127 added nothing here**, which is worth saying because a lost-streak
 * moment sounds like it wants a remembered "their streak was 12 yesterday". It
 * does not: the run that broke is still written down in the days that made it
 * (`brokenPlayStreakOn`), and the only thing a fold cannot know, whether the
 * player has already been told today, is the field below.
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
     * Every page writes it, including the intention and the lost-run moment.
     * The intention prints the run the player already has, so it is that run's
     * celebration; the lost-run page prints the day-one that replaced the run
     * that broke. Leaving this unset behind either would let the next board of
     * the same day say the same thing again.
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

/** The shortest run whose ending is worth telling somebody about. */
internal const val ShortestMournedRun = 2
