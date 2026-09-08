package com.sodogku.features.gate

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.AppMaintenanceMessage
import com.sodogku.libraries.config.values.AppMaintenanceMode
import com.sodogku.libraries.config.values.AppMinSupportedVersion
import com.sodogku.libraries.config.values.AppSoftUpdateVersion
import com.sodogku.libraries.config.values.LegalForceReacceptBelow
import com.sodogku.libraries.config.values.LegalPrivacyVersion
import com.sodogku.libraries.config.values.LegalTermsVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The launch gates, driven end to end from a config map.
 *
 * Every case here goes through the **real** `ConfiguredValue` classes rather than
 * hand-built inputs, so a test that says "an empty config blocks nobody" is
 * asserting about the whole path — map lookup, type coercion, declared default,
 * resolver — and not just about the last function in it. That matters because the
 * two failures this file exists to prevent live at opposite ends of it: a default
 * that ships wrong, and a resolver that reaches a wall from values that are right.
 *
 * The file is deliberately half fail-open and half the opposite. Each of the
 * "gates nobody" tests would pass against a `resolveLaunchGates` that returned
 * `LaunchGates()` unconditionally, which is exactly the trivial wrong
 * implementation the task warns about — so every one of them is paired with a
 * case where the operator deliberately asked for the wall and gets it.
 */
class LaunchGatesTest {

    // ---------------------------------------------------------------- outages

    @Test
    fun anEmptyConfigGatesNobody() {
        // What a device sees before the first fetch, and forever if the server
        // never comes back. Every one of the eight keys behind this resolves to
        // its own declared default here.
        val gates = gatesFor(emptyMap())

        assertNull(gates.blocking, "an absent config must never be able to raise a wall")
        assertNull(gates.notice)
    }

    @Test
    fun aMalformedConfigGatesNobody() {
        // Present but wrong, which is the case an absent-value test cannot see —
        // the same shape as the `"banana".toBoolean()` bug in decisions.md, where
        // a mistyped *present* value resolved rather than falling back. Numbers
        // coerce to null and fall back; the mode is not one of the three words.
        val gates = gatesFor(
            mapOf(
                "upgrade" to mapOf(
                    "minSupportedVersionCode" to "banana",
                    "softUpdateVersionCode" to mapOf("oops" to 1),
                    "maintenanceMode" to "BLOCK EVERYTHING",
                    "maintenanceMessage" to "we are down",
                ),
                "legal" to mapOf(
                    "termsVersion" to "two",
                    "privacyVersion" to true,
                    "forceReacceptBelow" to "9999",
                ),
            ),
        )

        assertNull(gates.blocking, "a malformed config must never be able to raise a wall")
    }

    @Test
    fun aConfigWhoseWriteOnlyHalfLandedGatesNobody() {
        // `maintenanceMode` and `maintenanceMessage` are two keys, so this is
        // what a partial admin write looks like from the client. The mode alone
        // would put up a wall with nothing written on it.
        val gates = gatesFor(mapOf("upgrade" to mapOf("maintenanceMode" to "blocking")))

        assertNull(gates.blocking, "a maintenance wall with nothing to say is a half-finished write")
    }

    @Test
    fun aBlankMaintenanceMessageIsNotAWall() {
        val gates = gatesFor(
            mapOf("upgrade" to mapOf("maintenanceMode" to "blocking", "maintenanceMessage" to "   ")),
        )

        assertNull(gates.blocking)
    }

    @Test
    fun aBuildThatCannotSayItsVersionIsNotBelowEverything() {
        // `installedVersionCode` comes from generated build config. A build
        // reporting 0 is below every threshold an operator could set, so the
        // guard is on our side of the comparison, not the config's.
        val gates = gatesFor(
            mapOf("upgrade" to mapOf("minSupportedVersionCode" to 500)),
            versionCode = 0,
        )

        assertNull(gates.blocking)
    }

    @Test
    fun aNegativeThresholdGatesNobody() {
        val gates = gatesFor(
            mapOf(
                "upgrade" to mapOf("minSupportedVersionCode" to -1, "softUpdateVersionCode" to -5),
                "legal" to mapOf("forceReacceptBelow" to -3),
            ),
        )

        assertNull(gates.blocking)
        assertNull(gates.notice)
    }

    @Test
    fun aFirstLaunchIsNeverGatedByLegal() {
        // Nothing accepted yet is not the same as an out-of-date acceptance, and
        // the difference is the whole reason `legalAcceptedAt` exists. A brand
        // new install meeting a re-accept wall would be walled out of a game it
        // has not played.
        val gates = gatesFor(
            mapOf("legal" to mapOf("termsVersion" to 9, "privacyVersion" to 9, "forceReacceptBelow" to 9)),
            acceptedTerms = 0,
            acceptedPrivacy = 0,
            hasEverAccepted = false,
        )

        assertNull(gates.blocking)
        assertNull(gates.notice)
    }

    @Test
    fun aReacceptFloorAboveTheVersionOnOfferIsUnsatisfiableAndIsNotRaised() {
        // The brick. `forceReacceptBelow` of 5 against a `termsVersion` of 2:
        // accepting records 2, 2 is still under 5, and the player never gets back
        // in. The floor is capped at what the accept button can actually record,
        // so every wall this raises is one the player can clear.
        val config = mapOf(
            "legal" to mapOf("termsVersion" to 2, "privacyVersion" to 2, "forceReacceptBelow" to 5),
        )

        val gates = gatesFor(config, acceptedTerms = 1, acceptedPrivacy = 1)

        assertTrue(
            gates.blocking is BlockingGate.ReacceptLegal,
            "being behind version 2 is still worth blocking on",
        )

        // And accepting the versions on offer clears it, which is the property
        // that makes the block a gate rather than a brick.
        val afterAccepting = gatesFor(config, acceptedTerms = 2, acceptedPrivacy = 2)
        assertNull(afterAccepting.blocking)
        assertNull(afterAccepting.notice)
    }

    @Test
    fun aReacceptFloorOnlyOneDocumentCanSatisfyStillClears() {
        // The asymmetric version of the case above: terms moved, privacy did
        // not. A floor applied whole would leave `acceptedPrivacy` permanently
        // short of it, because there is no privacy version 4 to accept.
        val config = mapOf(
            "legal" to mapOf("termsVersion" to 4, "privacyVersion" to 1, "forceReacceptBelow" to 4),
        )

        assertTrue(gatesFor(config, acceptedTerms = 3, acceptedPrivacy = 1).blocking is BlockingGate.ReacceptLegal)
        assertNull(gatesFor(config, acceptedTerms = 4, acceptedPrivacy = 1).blocking)
    }

    // --------------------------------------------------- the walls that do work

    @Test
    fun aDeliberateMinimumVersionBlocks() {
        val gates = gatesFor(
            mapOf("upgrade" to mapOf("minSupportedVersionCode" to 200)),
            versionCode = 199,
        )

        assertEquals(BlockingGate.ForceUpdate, gates.blocking)
    }

    @Test
    fun theMinimumVersionIsAFloorAndNotACeiling() {
        val gates = gatesFor(
            mapOf("upgrade" to mapOf("minSupportedVersionCode" to 200)),
            versionCode = 200,
        )

        assertNull(gates.blocking, "the minimum supported version is supported")
    }

    @Test
    fun aDeliberateMaintenanceWallBlocksAndCarriesTheOperatorsWords() {
        val gates = gatesFor(
            mapOf(
                "upgrade" to mapOf(
                    "maintenanceMode" to "blocking",
                    "maintenanceMessage" to "Back at 4pm. Nothing you finished is going anywhere.",
                ),
            ),
        )

        assertEquals(
            BlockingGate.Maintenance("Back at 4pm. Nothing you finished is going anywhere."),
            gates.blocking,
        )
    }

    @Test
    fun theMaintenanceModeIsForgivingAboutCasingAndNothingElse() {
        val message = mapOf("maintenanceMessage" to "back soon")

        assertTrue(
            gatesFor(mapOf("upgrade" to (mapOf("maintenanceMode" to "Blocking") + message)))
                .blocking is BlockingGate.Maintenance,
            "the console takes raw text and 'Blocking' is not a mistake worth punishing",
        )
        assertNull(
            gatesFor(mapOf("upgrade" to (mapOf("maintenanceMode" to "blocked") + message))).blocking,
            "a word that is not one of the three declared ones is off",
        )
    }

    @Test
    fun theBannerModeSaysSomethingWithoutStoppingAnyone() {
        val gates = gatesFor(
            mapOf(
                "upgrade" to mapOf(
                    "maintenanceMode" to "banner",
                    "maintenanceMessage" to "Leaderboards are having a lie down.",
                ),
            ),
        )

        assertNull(gates.blocking)
        assertEquals(NoticeGate.Maintenance("Leaderboards are having a lie down."), gates.notice)
    }

    @Test
    fun aDismissedMaintenanceBannerLetsTheNextNoticeThrough() {
        // Closing the banner must not also swallow a legal change that was
        // queued behind it.
        val gates = gatesFor(
            mapOf(
                "upgrade" to mapOf("maintenanceMode" to "banner", "maintenanceMessage" to "read me"),
                "legal" to mapOf("termsVersion" to 3),
            ),
            acceptedTerms = 1,
            dismissedBanner = "read me",
        )

        assertEquals(NoticeGate.LegalUpdated(termsVersion = 3, privacyVersion = 1), gates.notice)
    }

    @Test
    fun aDismissedBannerComesBackWhenTheOperatorRewordsIt() {
        val gates = gatesFor(
            mapOf("upgrade" to mapOf("maintenanceMode" to "banner", "maintenanceMessage" to "now with detail")),
            dismissedBanner = "read me",
        )

        assertEquals(NoticeGate.Maintenance("now with detail"), gates.notice)
    }

    @Test
    fun aDeliberateForceReacceptBlocks() {
        val gates = gatesFor(
            mapOf("legal" to mapOf("termsVersion" to 3, "privacyVersion" to 3, "forceReacceptBelow" to 3)),
            acceptedTerms = 2,
            acceptedPrivacy = 2,
        )

        assertEquals(BlockingGate.ReacceptLegal(termsVersion = 3, privacyVersion = 3), gates.blocking)
    }

    @Test
    fun anOutOfDateAcceptanceWithNoFloorIsOnlyNoted() {
        // The default `forceReacceptBelow` of 0. Publishing new terms is the
        // common case and it must not be the blocking one.
        val gates = gatesFor(
            mapOf("legal" to mapOf("termsVersion" to 3, "privacyVersion" to 2)),
            acceptedTerms = 1,
            acceptedPrivacy = 1,
        )

        assertNull(gates.blocking)
        assertEquals(NoticeGate.LegalUpdated(termsVersion = 3, privacyVersion = 2), gates.notice)
    }

    @Test
    fun theSoftUpdateSuggestionStaysDismissedAndThenComesBack() {
        val config = mapOf("upgrade" to mapOf("softUpdateVersionCode" to 300))

        assertEquals(NoticeGate.SoftUpdate(300), gatesFor(config, versionCode = 250).notice)
        assertNull(
            gatesFor(config, versionCode = 250, softUpdateDismissedFor = 300).notice,
            "a suggestion that reappears every launch is a nag",
        )

        val laterTarget = mapOf("upgrade" to mapOf("softUpdateVersionCode" to 400))
        assertEquals(
            NoticeGate.SoftUpdate(400),
            gatesFor(laterTarget, versionCode = 250, softUpdateDismissedFor = 300).notice,
            "a dismissal covers the version it was made at, not every future one",
        )
    }

    // -------------------------------------------------------------- precedence

    @Test
    fun theUpdateWallOutranksMaintenanceAndLegal() {
        val gates = gatesFor(
            mapOf(
                "upgrade" to mapOf(
                    "minSupportedVersionCode" to 200,
                    "maintenanceMode" to "blocking",
                    "maintenanceMessage" to "back soon",
                ),
                "legal" to mapOf("termsVersion" to 3, "privacyVersion" to 3, "forceReacceptBelow" to 3),
            ),
            versionCode = 100,
            acceptedTerms = 1,
            acceptedPrivacy = 1,
        )

        assertEquals(
            BlockingGate.ForceUpdate,
            gates.blocking,
            "an unsupported build sent away to wait out maintenance comes back to the same wall",
        )
    }

    @Test
    fun maintenanceOutranksLegal() {
        val gates = gatesFor(
            mapOf(
                "upgrade" to mapOf("maintenanceMode" to "blocking", "maintenanceMessage" to "back soon"),
                "legal" to mapOf("termsVersion" to 3, "privacyVersion" to 3, "forceReacceptBelow" to 3),
            ),
            acceptedTerms = 1,
            acceptedPrivacy = 1,
        )

        assertTrue(gates.blocking is BlockingGate.Maintenance)
    }

    @Test
    fun aBlockingGateSilencesEveryNotice() {
        val gates = gatesFor(
            mapOf(
                "upgrade" to mapOf("minSupportedVersionCode" to 200, "softUpdateVersionCode" to 300),
                "legal" to mapOf("termsVersion" to 3),
            ),
            versionCode = 100,
            acceptedTerms = 1,
        )

        assertEquals(BlockingGate.ForceUpdate, gates.blocking)
        assertNull(gates.notice, "a wall and a banner on top of it is two things to read and one to act on")
    }

    private class TestConfigMap(override val map: Map<String, *>) : AppConfigMap()

    private fun gatesFor(
        config: Map<String, Any>,
        versionCode: Int = 100,
        softUpdateDismissedFor: Int = 0,
        acceptedTerms: Int = 1,
        acceptedPrivacy: Int = 1,
        hasEverAccepted: Boolean = true,
        dismissedBanner: String? = null,
    ): LaunchGates {
        val map = TestConfigMap(config)
        return resolveLaunchGates(
            upgrade = UpgradeInputs(
                installedVersionCode = versionCode,
                minSupportedVersionCode = AppMinSupportedVersion(map)(),
                softUpdateVersionCode = AppSoftUpdateVersion(map)(),
                softUpdateDismissedFor = softUpdateDismissedFor,
            ),
            maintenance = MaintenanceInputs(
                mode = AppMaintenanceMode(map)(),
                message = AppMaintenanceMessage(map)(),
                dismissedBanner = dismissedBanner,
            ),
            legal = LegalInputs(
                termsVersion = LegalTermsVersion(map)(),
                privacyVersion = LegalPrivacyVersion(map)(),
                acceptedTermsVersion = acceptedTerms,
                acceptedPrivacyVersion = acceptedPrivacy,
                forceReacceptBelow = LegalForceReacceptBelow(map)(),
                hasEverAccepted = hasEverAccepted,
            ),
        )
    }
}
