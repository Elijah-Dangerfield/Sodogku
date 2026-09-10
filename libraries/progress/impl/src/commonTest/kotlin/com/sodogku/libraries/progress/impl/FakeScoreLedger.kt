package com.sodogku.libraries.progress.impl

import com.sodogku.libraries.progress.ScoreLedger

/**
 * Records every [bank] call **verbatim**, including the zeroes and negatives a
 * real ledger would throw away.
 *
 * That is the point of it. What the two repositories are responsible for is the
 * arithmetic — how much this clear actually earned — and what the ledger is
 * responsible for is refusing to write a number that is not a gain. A fake that
 * quietly applied the second rule would let a repository hand over a whole best
 * score where it owed a difference and still look right.
 */
internal class FakeScoreLedger : ScoreLedger {

    val calls = mutableListOf<Int>()

    var bankedSince: Int = 0

    override suspend fun bank(points: Int) {
        calls += points
    }

    override suspend fun bankedSince(startMillis: Long): Int = bankedSince
}
