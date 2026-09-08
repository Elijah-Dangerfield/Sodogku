package com.sodogku.admin

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The console's last check before a write that every player feels. It is only
 * worth having if it fires on the values that matter and stays quiet on the ones
 * that don't, so both directions are tested: a `dangerousWarning` that always
 * returned a string would pass the first half of this file and fail the second,
 * and one that always returned null would do the reverse.
 *
 * Callers hand it a value in whichever spelling their field holds — the
 * kill-switch panel passes `JsonPrimitive(option).toString()`, the flag detail
 * editor passes the raw draft the operator is mid-way through typing — so the
 * cases below deliberately use both.
 */
class DangerousValuesTest {

    @Test
    fun lockouts_warn() {
        assertNotNull(dangerousWarning("upgrade.maintenanceMode", "\"blocking\""))
        assertNotNull(dangerousWarning("upgrade.maintenanceMode", "blocking"))
        assertNotNull(dangerousWarning("upgrade.maintenanceMode", "\"banner\""))
        assertNotNull(dangerousWarning("upgrade.minSupportedVersionCode", "12"))
        assertNotNull(dangerousWarning("upgrade.softUpdateVersionCode", "12"))
        assertNotNull(dangerousWarning("legal.forceReacceptBelow", "2"))
    }

    @Test
    fun monetizationKeysWarnWhenTheyWouldFailClosed() {
        assertNotNull(dangerousWarning("ads.failureMode", "\"LOCK\""))
        assertNotNull(dangerousWarning("ads.offlineGraceLevels", "0"))
        assertNotNull(dangerousWarning("ads.offlineGraceMinutes", "0"))
        assertNotNull(
            dangerousWarning("ads.rewardedPlacements", """{"continue_level": false, "skip_level": true}"""),
        )
    }

    @Test
    fun takingSomethingAwayFromEveryPlayerWarns() {
        assertNotNull(dangerousWarning("ads.enabled", "false"))
        assertNotNull(dangerousWarning("daily.enabled", "false"))
        assertNotNull(dangerousWarning("features.sharing", "false"))
        assertNotNull(dangerousWarning("features.dailyChallenge", "false"))
        assertNotNull(dangerousWarning("ads.appOpenEnabled", "true"))
        assertNotNull(dangerousWarning("ads.bannerOnLevelMap", "true"))
    }

    /**
     * The other half. On prod a warning makes the operator type the environment
     * name, so a warning on a routine retune costs more than it buys: it trains
     * them to type it without reading it.
     */
    @Test
    fun theAllClearAndTheSafeDirectionAreSilent() {
        assertNull(dangerousWarning("upgrade.maintenanceMode", "\"off\""))
        assertNull(dangerousWarning("upgrade.softUpdateVersionCode", "0"))
        assertNull(dangerousWarning("legal.forceReacceptBelow", "0"))
        assertNull(dangerousWarning("ads.failureMode", "\"CONTINUE\""))
        assertNull(dangerousWarning("ads.offlineGraceLevels", "5"))
        assertNull(dangerousWarning("ads.rewardedPlacements", """{"continue_level": true}"""))
        assertNull(dangerousWarning("ads.enabled", "true"))
        assertNull(dangerousWarning("daily.enabled", "true"))
        assertNull(dangerousWarning("features.sharing", "true"))
        assertNull(dangerousWarning("ads.appOpenEnabled", "false"))
    }

    @Test
    fun routineTuningIsSilent() {
        assertNull(dangerousWarning("ads.interstitialEveryNLevels", "1"))
        assertNull(dangerousWarning("ads.interstitialCooldownSec", "0"))
        assertNull(dangerousWarning("paywall.sessionCap", "9"))
        assertNull(dangerousWarning("scoring.comboStep", "0.2"))
        assertNull(dangerousWarning("legal.termsUrl", "\"https://example.com\""))
        assertNull(dangerousWarning("brand.newFlagNobodyHasShippedYet", "false"))
    }
}
