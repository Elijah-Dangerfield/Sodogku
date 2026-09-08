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
    campaignClears: Int,
    dailyPlayedEver: Boolean,
    dailyEnabled: Boolean,
    state: StreakPromptState,
): StreakPrompt = when {
    // A ceremony for a feature that is switched off is the worst interruption
    // available: it asks for a commitment and then leads nowhere.
    !dailyEnabled -> StreakPrompt.None

    // The intention moment outranks a celebration, and the two cannot both be
    // due anyway, because a player with a streak has played a daily.
    intentionIsDue(campaignClears, dailyPlayedEver, state) -> StreakPrompt.Intention

    isMilestone(streak) && streak != state.celebratedStreak -> StreakPrompt.Celebrate(streak)

    else -> StreakPrompt.None
}

/**
 * After [IntentionAfterClears] cleared campaign levels, once, and only to a
 * player who has never played a daily.
 *
 * **Three, and the number is the whole design.** One clear is the first thirty
 * seconds of the app, before the game has earned the right to ask for anything,
 * and a full-screen interruption there is indistinguishable from an ad. Ten is
 * past the point where a player has decided; whatever they were going to do
 * about coming back, they have already done it. Three is the first moment the
 * player has *chosen* to keep playing twice, is still inside their first
 * session, and still has time to spend the streak they just started.
 *
 * The "never played a daily" clause is not belt and braces. The daily card sits
 * in the level pane from level one, so a curious player can be four days into a
 * streak before they clear their third campaign board, and "tap to start your
 * streak" would be telling them something they can see is false.
 */
private fun intentionIsDue(
    campaignClears: Int,
    dailyPlayedEver: Boolean,
    state: StreakPromptState,
): Boolean = !state.intentionShown && !dailyPlayedEver && campaignClears >= IntentionAfterClears

/**
 * Which runs get a page: 3, 7, 14, 30, and every 30 after that.
 *
 * Thinning out fast is the point. Duolingo can celebrate every week because the
 * streak *is* its product; here the puzzle is the product and the streak is a
 * reason to come back, so a page that appears on day 4, 5 and 6 stops being a
 * reward and becomes something standing between the player and the next board.
 *
 * Three is the first run worth noticing and lands inside the first week, which
 * is where a habit is won or lost. Seven is the number people say out loud.
 * After thirty the run is its own reward and a month is a decent wait for the
 * next page.
 */
internal fun isMilestone(streak: Int): Boolean =
    streak in EarlyMilestones || (streak >= MonthlyMilestoneFrom && streak % MonthlyMilestoneFrom == 0)

/**
 * What has already been said, and the only streak state that is stored rather
 * than folded.
 *
 * It has to be stored: "we have shown this once" is not recoverable from the
 * daily rows, and the alternative, inferring it from the streak itself,
 * re-fires a celebration every time the page is opened on a milestone day.
 *
 * Deliberately not part of `AppData`. These two fields are machinery and neither
 * means anything without [isMilestone] beside it, which is the same argument
 * `SkipState` makes for living next door rather than in the shared blob.
 */
@Serializable
data class StreakPromptState(
    val intentionShown: Boolean = false,

    /**
     * The streak the last celebration was for, or `0`.
     *
     * A single number rather than a set of milestones already seen, and that is
     * a decision: a run that breaks at 30 and climbs back to 7 gets its page
     * again, because it is a different run and the player did the work twice. A
     * set would silently retire each milestone for the life of the install.
     */
    val celebratedStreak: Int = 0,
)

/** Cleared campaign levels before the intention moment is offered. */
internal const val IntentionAfterClears = 3

private val EarlyMilestones = setOf(3, 7, 14)

private const val MonthlyMilestoneFrom = 30
