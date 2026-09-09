package com.sodogku.features.game.impl

/**
 * Whether the player looks stuck, and whether now is a reasonable moment to say
 * so by beating the Locate button.
 *
 * The gate this replaces was `strikesThisAttempt >= 2 || livesRemaining <= 1`,
 * and it was wrong in both directions. It never fired for the player who had
 * made no mistakes and simply could not see the next move — the commonest way
 * to be stuck, and the one the owner reported. And it fired *permanently* once
 * either arm went true, so a player on their last bone had a button wobbling at
 * them for the rest of the board.
 *
 * ## What counts as stuck
 *
 * Three signals, chosen because they are the three shapes being stuck actually
 * takes, and combined rather than or-ed:
 *
 * - **Idle.** [idleMs] with no input of any kind. Somebody not touching the
 *   screen is not marking and not guessing; they are staring at it. This one is
 *   only armed after the player has touched the board at least once — an
 *   untouched board is a board nobody has *tried* yet, and the honest reading of
 *   one after a minute is that the phone is on a table.
 * - **Working without progress.** At least [marksBeforeStall] crosses since the
 *   last commit, and no dog placed for longer than [stallThreshold]. This is the
 *   "crossing off but not guessing" case: the player is busy, so idle will never
 *   fire, and they are getting nowhere.
 * - **A wrong guess.** Direct evidence the deduction went wrong. Latched until
 *   the next placement, because a wrong guess that has not yet been followed by
 *   a right one is a question still open.
 *
 * The old code argued that one wrong guess is not struggling because everybody
 * gets one and the tutorial *instructs* one. Both are still true; neither is an
 * argument against reacting to it. The tutorial is handled by not running this
 * at all on the rehearsal board, and "everybody gets one" is handled by the
 * burst being three pulses of a button rather than anything the player has to
 * dismiss.
 *
 * ## What stops it nagging
 *
 * Two things, and they are the whole of the frequency budget. A successful
 * placement ends the burst immediately — the player is unstuck and the button
 * stops. Otherwise the burst runs for [burstMs] and then goes quiet for
 * [quietMs] whether or not the player is still stuck, so **the most attention
 * this can ever ask for is one burst of roughly three pulses every thirty
 * seconds.**
 *
 * ## The clock
 *
 * Every entry point takes the time rather than reading one, and the caller
 * passes attempt-elapsed millis — the clock that already stops when the app is
 * backgrounded. A player who takes a phone call mid-board comes back to the
 * board they left rather than to a button insisting they are stuck, and that
 * falls out of using the clock the attempt is already measured on instead of
 * wall time.
 */
internal class StruggleDetector(
    private val idleMs: Long = IdleMs,
    private val stallFloorMs: Long = StallFloorMs,
    private val stallPaceMultiple: Float = StallPaceMultiple,
    private val marksBeforeStall: Int = MarksBeforeStall,
    private val burstMs: Long = BurstMs,
    private val quietMs: Long = QuietMs,
) {
    private var touched = false
    private var lastInputAt = 0L
    private var lastPlacementAt = 0L
    private var marksSinceCommit = 0

    /** A wrong guess with no placement since. Cleared by the next dog. */
    private var struck = false

    /** Gaps between successive placements, which is this player's pace here. */
    private val gaps = mutableListOf<Long>()

    private var burstStartedAt: Long? = null
    private var quietUntil = 0L

    /** A fresh attempt. Everything above is about one board. */
    fun reset() {
        touched = false
        lastInputAt = 0L
        lastPlacementAt = 0L
        marksSinceCommit = 0
        struck = false
        gaps.clear()
        burstStartedAt = null
        quietUntil = 0L
    }

    fun onPlaced(now: Long) {
        // Recorded before the pace is, so the first placement of a board
        // contributes its own gap from the start rather than being skipped.
        gaps += now - lastPlacementAt
        touched = true
        lastInputAt = now
        lastPlacementAt = now
        marksSinceCommit = 0
        struck = false
        // The player just got one right. Whatever the button was saying, it has
        // been answered — this is the "clearing the struggle stops it" half, and
        // it is the reason a placement is the only thing that ends a burst early.
        burstStartedAt = null
    }

    fun onStruck(now: Long) {
        touched = true
        lastInputAt = now
        marksSinceCommit = 0
        struck = true
    }

    fun onMarked(now: Long) {
        onTouched(now)
        marksSinceCommit++
    }

    /**
     * A tap that changed nothing — on a dog already placed, or on a square that
     * already cost a bone.
     *
     * It counts as input and nothing else. Somebody tapping a refused square is
     * awake and looking at the board, so the idle clock restarts; but they have
     * not crossed anything off, so it must not feed the marks-without-progress
     * count, which would otherwise let a player fidget their way into a nudge.
     */
    fun onTouched(now: Long) {
        touched = true
        lastInputAt = now
    }

    /**
     * Whether the button should be beating right now.
     *
     * Not a pure query: asking starts and ends bursts, because the burst is a
     * fact about time passing rather than about anything the player did, and
     * there is nothing else to hang it on. Called once per tick from one place.
     */
    fun nudging(now: Long): Boolean {
        val startedAt = burstStartedAt
        if (startedAt != null) {
            if (now - startedAt < burstMs) return true
            burstStartedAt = null
            quietUntil = now + quietMs
            return false
        }
        if (now < quietUntil) return false
        if (!stuck(now)) return false
        burstStartedAt = now
        return true
    }

    private fun stuck(now: Long): Boolean = struck ||
        (touched && now - lastInputAt >= idleMs) ||
        (marksSinceCommit >= marksBeforeStall && now - lastPlacementAt >= stallThreshold())

    /**
     * How long without a dog counts as stalled, for *this* player on *this*
     * board.
     *
     * The pace can only ever raise the floor, never lower it. A deliberate
     * player who takes forty seconds a placement is not stuck at thirty, and
     * pacing off their own median is the only way to tell them apart from
     * someone who normally takes five. A fast player who suddenly freezes is
     * caught by the idle rule long before this one, so there is nothing for the
     * pace to do in the other direction.
     *
     * The median rather than the mean: two or three gaps is a small sample and
     * one long think while the player worked out the board's shape should not
     * set the bar for the rest of it.
     */
    private fun stallThreshold(): Long {
        if (gaps.size < PaceSampleFloor) return stallFloorMs
        val paced = (gaps.sorted()[gaps.size / 2] * stallPaceMultiple).toLong()
        return maxOf(stallFloorMs, paced)
    }

    private companion object {
        /** The number the owner named: ten seconds of not touching the screen. */
        const val IdleMs = 10_000L

        /**
         * The shortest a placement drought can be and still count, whatever the
         * player's pace. Long enough that ordinary deduction on a 10x10 does not
         * trip it.
         */
        const val StallFloorMs = 20_000L

        /** How far past their own median gap a player has to go. */
        const val StallPaceMultiple = 2.5f

        /**
         * Enough crosses to be sure the player is working rather than fidgeting.
         * One stroke across a row of a 10x10 makes more than this.
         */
        const val MarksBeforeStall = 6

        /** Two full pulses of the button, and part of a third. */
        const val BurstMs = 7_000L

        /** [BurstMs] plus this is the thirty seconds in the class KDoc. */
        const val QuietMs = 23_000L

        /** Below this many gaps there is no pace, only a first impression. */
        const val PaceSampleFloor = 2
    }
}
