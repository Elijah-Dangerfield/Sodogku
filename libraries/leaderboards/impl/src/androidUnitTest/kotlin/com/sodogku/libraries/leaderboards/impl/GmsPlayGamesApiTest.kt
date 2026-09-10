package com.sodogku.libraries.leaderboards.impl

import android.app.Activity
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.leaderboards.Leaderboard
import com.sodogku.libraries.sodogku.ActivityProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The one part of `GmsPlayGamesApi` that runs without a phone: which of the two
 * ids on a board is the Play Games one.
 *
 * Everything else in that class needs a foreground Activity and a Google
 * `Task`, so it is compiled and never executed here, exactly as
 * `GameCenterServices` is on the iOS side. This is worth carving out anyway,
 * because reading `appleId` here would compile, would look right in review, and
 * would fail only as a leaderboard nobody is ever on.
 *
 * The assertions are `null` today rather than a string, and that is not a
 * weaker test. The Play Console has not minted anything yet, so `null` is the
 * correct answer for all three; what it kills is the substitution, because every
 * board does have a Game Center id and reading that one would produce a value.
 */
class GmsPlayGamesApiTest : CoroutineTest() {

    private val api by lazy { GmsPlayGamesApi(activityProvider = NoActivity, dispatchers = dispatchers) }

    @Test
    fun aBoardWithNoMintedIdHasNoPlayIdRatherThanItsGameCenterOne() {
        assertEquals(3, Leaderboard.entries.size)
        Leaderboard.entries.forEach { board ->
            assertNull(
                api.leaderboardId(board),
                "${board.name} resolved to an id before the Play Console minted one",
            )
        }
    }

    @Test
    fun aBlankIdIsNeverHandedToTheSdk() {
        // Submitting an empty string is an SDK error rather than a no-op, and
        // an SDK error is one more thing to be loud about on a device where the
        // player has done nothing wrong.
        Leaderboard.entries.forEach { board ->
            assertNull(api.leaderboardId(board)?.takeIf { it.isBlank() })
        }
    }
}

private object NoActivity : ActivityProvider {
    override fun currentActivity(): Activity? = null
}
