package com.sodogku.libraries.leaderboards

/**
 * Every board this game keeps, and the id it is filed under in the store's
 * console. Three, deliberately.
 *
 * A leaderboard is a shared room, and splitting a small player base across many
 * rooms empties all of them. So the test each candidate had to pass was not
 * "could this be a leaderboard" but "would a stranger's name be next to yours
 * on it in week one". Three survived:
 *
 * - **[LifetimeScore]** is the headline. It already exists as one number folded
 *   out of every board the player has finished (`LifetimeScore.banked`), it only
 *   ever goes up, and it is comparable between two people who have never played
 *   the same level.
 * - **[LongestStreak]** measures the other axis. Score rewards volume, streak
 *   rewards turning up, and a player who cannot win the first can plausibly win
 *   the second.
 * - **[WeeklyScore]** is the one anybody can win. The two above are records, and
 *   a record is a wall a newcomer looks at; a week is short enough that turning
 *   up beats having been here since launch.
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
 * ## How the weekly one differs, and why that is the platform's problem
 *
 * [WeeklyScore] is the only **recurring** board here, and the only one whose
 * value is not a running total of everything. Two things follow from that, and
 * both are load bearing.
 *
 * **Nothing on the device knows when the week turns.** Game Center is told the
 * start, the duration and the repeat interval in App Store Connect, and it
 * files a submission into whichever occurrence is open when it arrives. There
 * is no local week number, no stored "current window" and nothing to reset, so
 * there is nothing to be wrong after a reboot, a time-zone change or a clock
 * the player set by hand.
 *
 * **The value is points banked inside that window, not the lifetime total.** A
 * recurring board keeps the *best single submission* it received during an
 * occurrence; it does not add them up. Sending the lifetime total would rank
 * everyone by lifetime total under a weekly heading, which is the all-time board
 * again with a shorter memory. So the number sent is
 * `ScoreLedger.bankedSince(windowStart)`, and the window start is asked of the
 * platform ([GameServices.currentWindowStart]) rather than worked out here —
 * the daily above is what happens when a client invents a window.
 *
 * ## The ids, two per board
 *
 * They are committed constants rather than remote config, for the reason
 * `AdUnits` gives: an id is a store operation, and a config outage that blanked
 * one would take the board down with it. Getting one wrong is silent, and it
 * looks like a board nobody is on.
 *
 * [appleId] is the Game Center id, typed by hand into App Store Connect and
 * matched by string. [playId] is the Play Games id for the same board, and it
 * is not typed by hand: the Play Console mints an opaque id (`CgkI…`) when the
 * board is created, so the value has to be copied back out of the console into
 * this file. Until somebody does that it is empty, which every Android caller
 * reads as "no board here" and skips — see `PlayGamesServices`.
 *
 * The two id spaces never meet. Submitting a Game Center id to Play is a
 * rejection Play does not explain, so `PlayGamesServicesTest` pins which of the
 * two reaches the platform.
 */
enum class Leaderboard(val appleId: String, val playId: String) {

    /** Every point the player has ever banked, campaign and daily together. */
    LifetimeScore(
        appleId = "com.sodogku.leaderboard.lifetime_score",
        playId = "",
    ),

    /**
     * The longest run of days the player has ever finished a board on, of any
     * kind. Fed by `StreakSummary.longest`, which folds `play_day`.
     *
     * It said "consecutive dailies" until 2026-09-20, which stopped being true
     * when the streak moved off `daily_result`: a player who only ever clears
     * campaign levels has been ranked on this board the whole time.
     */
    LongestStreak(
        appleId = "com.sodogku.leaderboard.longest_streak",
        playId = "",
    ),

    /**
     * Points banked inside the window Game Center currently has open, campaign
     * and daily together.
     *
     * The only entry here backed by a **recurring** board in App Store Connect,
     * and the only one submitted through [Leaderboards.submitWindowed]. Both of
     * those are the same fact: the value depends on when the window opened, and
     * only the platform knows that.
     *
     * **[playId] is empty here permanently, and that is the answer rather than
     * a gap.** Play Games has no recurring board. It splits one board into
     * daily, weekly and all-time views by the time a score was submitted
     * (`LeaderboardVariant.TIME_SPAN_WEEKLY`), so the weekly standing an Android
     * player wants is already a tab on the lifetime board and a second board
     * would only halve the room. There is also nothing to ask for a window
     * start, which is why `GameServices.currentWindowStart` answers `null` on
     * Android and this board is never submitted there at all.
     */
    WeeklyScore(
        appleId = "com.sodogku.leaderboard.weekly_score",
        playId = "",
    ),
}
