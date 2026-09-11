package com.sodogku.libraries.ui.components.dog

/**
 * What a looping dog does next, as a pure function of a seed and a turn number.
 *
 * A dog that plays its clips end to end, forever, in the same order reads as a
 * animation rather than as an animal — the eye learns the cycle within about
 * three passes and then stops seeing it. What makes it read as alive is that it
 * sometimes stops: a real dog holds still, glances, holds still again.
 *
 * So the schedule interleaves two things. Every turn picks a clip, and every
 * turn picks how long to sit on the first frame of that clip *before* playing
 * it. The hold is what breaks the metronome; the clip choice is what stops the
 * same gesture repeating.
 *
 * It is deterministic on purpose. "Randomish" and "untestable" are not the same
 * requirement, and a schedule seeded from the clock would make every failure a
 * story about what the dog happened to be doing. Seed it from something stable
 * per dog — a board index, a route — and two dogs on one screen still differ
 * while either one can be reproduced exactly.
 */
internal class DogLoopSchedule(
    private val seed: Int,
    private val clipCount: Int,
    private val maxHoldTurns: Int = DefaultMaxHold,
) {
    init {
        require(clipCount > 0) { "a schedule with no clips has nothing to play" }
        require(maxHoldTurns >= 0) { "a negative hold is not a pause" }
    }

    /**
     * Which clip turn [turn] plays.
     *
     * Never the same clip twice running, which is the one thing a viewer
     * reliably notices. With a single clip there is nothing to vary and the
     * caller gets it back every time.
     *
     * Each turn is folded against the one before it, so answering for turn N
     * means walking the chain from zero. Walk it, don't recurse it: [turn] is a
     * clock and has no ceiling, and a dog left composed long enough will ask for
     * a turn deeper than the stack can hold. It took about forty hours on the
     * JVM, less on iOS, which is latent rather than impossible.
     */
    fun clipAt(turn: Int): Int {
        require(turn >= 0) { "a turn is a count, not an offset: $turn" }
        if (clipCount == 1) return 0
        var clip = NoClip
        for (step in 0..turn) {
            val choice = noise(step, ClipSalt).mod(clipCount - 1)
            // Fold the previous clip out of the range rather than re-rolling, so
            // the remaining clips stay equally likely instead of the one just
            // after it getting a second chance.
            clip = if (clip == NoClip || choice < clip) choice else choice + 1
        }
        return clip
    }

    /**
     * How many frame-lengths to sit on the first frame before turn [turn] plays.
     *
     * Zero half the time, so the dog does not visibly pause between every
     * gesture, which would be its own metronome. The other half is spread over
     * `1..`[maxHoldTurns], one roll each.
     *
     * The roll is taken over twice the range and the top half collapses to zero,
     * which is what buys the even split. Collapsing at `>= maxHoldTurns` instead
     * is the off-by-one this had until 2026-09: it costs the longest hold
     * entirely and drags zero up to two turns in three.
     */
    fun holdTurnsAt(turn: Int): Int {
        if (maxHoldTurns == 0) return 0
        val roll = noise(turn, HoldSalt).mod(maxHoldTurns * 2)
        return if (roll > maxHoldTurns) 0 else roll
    }

    /**
     * A small integer hash. Not a good random source and does not need to be:
     * what it has to do is decorrelate consecutive turns, so the sequence is not
     * something a viewer can learn.
     *
     * [HoldSalt] is belt and braces. Removing it survives the tests, because the
     * two questions take different moduli of the result and come apart on their
     * own. It stays because that is a property of the current clip count and
     * hold range rather than of the design, and a third question asked of this
     * hash would not be so lucky. Worth knowing it is not load-bearing today.
     */
    private fun noise(turn: Int, salt: Int): Int {
        var x = seed * Prime1 + turn * Prime2 + salt
        x = x xor (x ushr Shift1)
        x *= Prime3
        x = x xor (x ushr Shift2)
        return x
    }

    private companion object {
        const val NoClip = -1
        const val DefaultMaxHold = 3

        const val ClipSalt = 0
        const val HoldSalt = 0x9E37

        const val Prime1 = 0x27D4EB
        const val Prime2 = 0x165667B1
        const val Prime3 = 0x85EBCA6B.toInt()
        const val Shift1 = 15
        const val Shift2 = 13
    }
}
