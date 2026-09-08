package com.sodogku.libraries.leaderboards

/**
 * Every board this game keeps, and the id it is filed under in the store's
 * console. Two, deliberately.
 *
 * A leaderboard is a shared room, and splitting a small player base across many
 * rooms empties all of them. So the test each candidate had to pass was not
 * "could this be a leaderboard" but "would a stranger's name be next to yours
 * on it in week one". Two survived:
 *
 * - **[LifetimeScore]** is the headline. It already exists as one number folded
 *   out of every board the player has finished (`LifetimeScore.banked`), it only
 *   ever goes up, and it is comparable between two people who have never played
 *   the same level.
 * - **[LongestStreak]** measures the other axis. Score rewards volume, streak
 *   rewards turning up, and a player who cannot win the first can plausibly win
 *   the second.
 *
 * ## What was rejected, and why it stays rejected
 *
 * **Per-level best score or best time, 500 of them.** Game Center allows 100
 * leaderboards per app, so this does not fit even arithmetically, and if it did
 * each board would hold the handful of people who happened to replay level 312.
 *
 * **The daily challenge, as a recurring leaderboard.** This is the one that
 * looks right and is not. Game Center's recurring boards roll on a fixed
 * instant; our daily rolls at device-local midnight (`DailyCalendar`). A player
 * in Auckland is on tomorrow's puzzle while the board still says today, so the
 * board would be ranking scores from two different puzzles against each other.
 * Fixing that means either giving up the local-midnight daily or submitting into
 * a window we compute ourselves, and neither is worth a board.
 *
 * **Total paws.** A monotone function of score. Two boards ranking the same
 * players in nearly the same order is one board and one distraction.
 *
 * **A weekly score board** is the real gap. An all-time score board is
 * unwinnable for a newcomer, and the standard answer is a rolling window that
 * everyone starts level in. Game Center supports exactly that natively, but it
 * needs "points earned since a date", and `:libraries:progress` folds a lifetime
 * total rather than a windowed one. That is a small addition there and a
 * one-entry addition here, in that order.
 *
 * ## The ids
 *
 * They are committed constants rather than remote config, for the reason
 * `AdUnits` gives: an id is a store operation, and a config outage that blanked
 * one would take the board down with it. They are typed by hand into App Store
 * Connect and must match exactly. Getting one wrong is silent, and it looks like
 * a board nobody is on.
 *
 * [id] is the Game Center id. Google Play Games is out of scope, and when it is
 * not, its console mints its own ids; that arrives as a second property here and
 * a resolver like `AdUnits.android(format)`, not as a different interface.
 */
enum class Leaderboard(val id: String) {

    /** Every point the player has ever banked, campaign and daily together. */
    LifetimeScore("com.sodogku.leaderboard.lifetime_score"),

    /** The longest run of consecutive dailies the player has ever finished. */
    LongestStreak("com.sodogku.leaderboard.longest_streak"),
}
