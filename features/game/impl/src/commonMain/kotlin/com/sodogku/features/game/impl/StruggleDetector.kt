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
 * - **A drought.** No dog placed for longer than [stallThreshold]. The one
 *   signal that is about the board rather than about the hand: whatever the
 *   player has been doing, they have not solved a square, and a run with no
 *   placement in it is the plainest description of being stuck there is.
 * - **A wrong guess.** Direct evidence the deduction went wrong. Latched until
 *   the next placement, because a wrong guess that has not yet been followed by
 *   a right one is a question still open.
 *
 * ## What changed, and what it keyed on before
 *
 * The drought used to be a *conjunction*: at least [marksBeforeStall] crosses
 * since the last commit **and** past [stallThreshold]. Both had to hold, so the
 * whole rule was gated on the player having crossed six squares off. The three
 * things it keyed on were, exactly: a latched wrong guess; [idleMs] since the
 * last input on a touched board; and that six-marks-plus-threshold conjunction.
 *
 * That left a player the detector could not see at all. Crosses are the only
 * gesture that counts toward [marksBeforeStall] — a tap the board refuses, on a
 * dog already placed or on a square that already cost a bone, deliberately does
 * not — so a player who taps around the board without crossing anything off
 * keeps restarting the idle clock with every tap while never arming the stall.
 * Idle never fires because they are touching the screen, the stall never fires
 * because they are not marking, and nothing else exists. They can sit there for
 * the length of the board and be told they are fine.
 *
 * The fix is to stop treating the crosses as a precondition and start treating
 * them as *evidence of how long to wait*. The drought fires either way; six
 * crosses since the last dog just means the player has already spent the
 * deduction and can be asked sooner, so the threshold applies as it stands
 * rather than multiplied by [droughtMultiple]. Somebody who has not crossed
 * anything off may simply be reading the board, which is why they get longer —
 * but "longer" is a number and not "never", which is what it was.
 *
 * A player reading carefully and a player with no idea do look identical for a
 * while. That is unavoidable and it is the reason the answer to a false positive
 * has to stay cheap: the most this can ever do is beat a button for seven
 * seconds and then stop on its own.
 *
 * The old code argued that one wrong guess is not struggling because everybody
 * gets one and the tutorial *instructs* one. Both are still true; neither is an
 * argument against reacting to it. The tutorial is handled by not running this
 * at all on the rehearsal board, and "everybody gets one" is handled by the
 * burst being a few pulses of a button rather than anything the player has to
 * dismiss.
 *
 * ## What stops it nagging
 *
 * Two things, and they are the whole of the frequency budget. A successful
 * placement ends the burst immediately — the player is unstuck and the button
 * stops. Otherwise the burst runs for [burstMs] and then goes quiet for
 * [quietMs] whether or not the player is still stuck, so **the most attention
 * this can ever ask for is one burst every twenty seconds.**
 *
 * That cap was thirty seconds and the owner's report on a tester build was that
 * he was not seeing the button at all. Thirty seconds of silence is a long time
 * to hold a nudge back from somebody who is, by the detector's own reckoning,
 * still stuck the whole way through it — a player stuck for a minute got two
 * chances to notice, and either of them could land while they were looking at
 * the other end of the board. Twenty gives three, and the button is still still
 * for two thirds of the time.
 *
 * Twenty is a floor rather than a number that wants to go lower. [burstMs] is
 * unchanged, so what moved is only the gap, and the gap has to stay longer than
 * the burst: a rest shorter than the movement it separates stops reading as a
 * pause and starts reading as a stutter, which is the permanent nag this cap
 * exists to prevent.
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
    private val droughtMultiple: Float = DroughtMultiple,
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

    /**
     * A fresh attempt. Everything above is about one board.
     *
     * [at] is where the attempt clock starts, which is zero for a new board and
     * the saved elapsed time for a resumed one. Both clocks below are set to it
     * rather than to zero: they are compared against the same attempt-elapsed
     * millis every entry point takes, so a board that comes back with five
     * minutes on it would otherwise open five minutes into a placement drought
     * and record its first pace gap as the whole span before the resume.
     *
     * [touched] stays false either way. A resumed board is one nobody has tried
     * *this* time round, and the idle and drought rules both wait for a first
     * input for the same reason they do on a new one.
     */
    fun reset(at: Long) {
        touched = false
        lastInputAt = at
        lastPlacementAt = at
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

    private fun stuck(now: Long): Boolean = struck || idle(now) || droughted(now)

    private fun idle(now: Long): Boolean = touched && now - lastInputAt >= idleMs

    /**
     * How long since the last dog, against how long this player is allowed.
     *
     * The crosses decide the allowance rather than whether there is one. Six of
     * them since the last commit says the player has done the work and come up
     * empty, so the threshold applies as it stands; short of that they may be
     * reading rather than failing, and get [droughtMultiple] times as long
     * before anyone assumes otherwise.
     *
     * Untouched boards are exempt for the same reason they are exempt from the
     * idle rule: a board nobody has tried is a phone on a table.
     */
    private fun droughted(now: Long): Boolean {
        if (!touched) return false
        val threshold = stallThreshold()
        val allowed = if (marksSinceCommit >= marksBeforeStall) {
            threshold
        } else {
            (threshold * droughtMultiple).toLong()
        }
        return now - lastPlacementAt >= allowed
    }

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

        /**
         * How much longer a player who has not been crossing off gets before a
         * drought counts.
         *
         * One and a half, which puts the shortest possible drought at thirty
         * seconds. It was two, and forty seconds turned out to be long enough
         * that this arm was hardly ever the one that fired — which defeats the
         * point of it, since it exists to catch the player no other arm can see.
         *
         * Thirty rather than twenty. The gap between this and [StallFloorMs] is
         * the whole of the benefit of the doubt a player gets for not having
         * crossed anything off, and collapsing it would be removing the
         * distinction rather than tuning it: somebody who has read the board for
         * half a minute without marking it is plausibly still reading, and
         * somebody who has done that for a full minute plausibly is not.
         */
        const val DroughtMultiple = 1.5f

        /**
         * How long the button is allowed to beat for.
         *
         * Unchanged at seven seconds, deliberately, because the fix for "I was
         * not seeing it" is more chances to catch one and a louder beat inside
         * it, not a button that goes on longer. How many beats fit in seven
         * seconds is `BoardControl`'s business and it now fits nearly three.
         */
        const val BurstMs = 7_000L

        /** [BurstMs] plus this is the twenty seconds in the class KDoc. */
        const val QuietMs = 13_000L

        /** Below this many gaps there is no pace, only a first impression. */
        const val PaceSampleFloor = 2
    }
}
