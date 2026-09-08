package com.sodogku.libraries.leaderboards.impl

import com.sodogku.libraries.leaderboards.GameServicesStatus
import com.sodogku.libraries.leaderboards.SubmitResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The only test that can execute `GameCenterServices` at all, and it is
 * deliberately narrow about what it claims.
 *
 * **What it proves.** That the iOS implementation constructs, that asking it to
 * authenticate does not throw or block, and that a submission on a device where
 * nobody is signed in refuses locally rather than reaching for the network.
 * That last one is the fail-open path, and it is the path every player without
 * a Game Center account is permanently on.
 *
 * **What it cannot prove.** Anything that needs a signed-in player. A test
 * binary has no Game Center identity, and neither does a simulator, so the
 * authenticated branches of `submit` and `presentDashboard` are compiled and
 * never run here. They need a device and a sandbox Apple Account.
 */
class GameCenterServicesTest {

    @Test
    fun itStartsUnresolvedAndAskingToAuthenticateIsHarmless() {
        val services = GameCenterServices()

        assertEquals(GameServicesStatus.Unknown, services.status.value)

        // Idempotent by contract, so calling it twice has to be as safe as once.
        services.startAuthentication()
        services.startAuthentication()
    }

    @Test
    fun aSubmissionWithNobodySignedInRefusesWithoutTouchingTheNetwork() = runTest {
        val result = GameCenterServices().submit("com.sodogku.leaderboard.lifetime_score", 4_200L)

        assertEquals(SubmitResult.NotAuthenticated, result)
    }
}
