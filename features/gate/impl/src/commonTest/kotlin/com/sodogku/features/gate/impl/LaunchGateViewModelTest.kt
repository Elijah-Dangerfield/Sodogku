package com.sodogku.features.gate.impl

import com.sodogku.features.gate.BlockingGate
import com.sodogku.features.gate.NoticeGate
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.AppConfigRepository
import com.sodogku.libraries.config.values.AppMaintenanceMessage
import com.sodogku.libraries.config.values.AppMaintenanceMode
import com.sodogku.libraries.config.values.AppMinSupportedVersion
import com.sodogku.libraries.config.values.AppSoftUpdateVersion
import com.sodogku.libraries.config.values.LegalForceReacceptBelow
import com.sodogku.libraries.config.values.LegalPrivacyUrl
import com.sodogku.libraries.config.values.LegalPrivacyVersion
import com.sodogku.libraries.config.values.LegalTermsUrl
import com.sodogku.libraries.config.values.LegalTermsVersion
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * [LaunchGateViewModel] — the wiring, not the decision.
 *
 * What the gates decide is `LaunchGatesTest` in the api module, driven from a
 * config map with no ViewModel in the way. What is left for here is everything
 * that is about *time*: seeding a first launch, recording exactly the versions
 * that were on screen, a dismissal that has to survive the next `AppData` write,
 * and an operator's change landing mid-session rather than at the next cold
 * start.
 */
@OptIn(ExperimentalTime::class)
class LaunchGateViewModelTest : CoroutineTest() {

    @Test
    fun aFirstLaunchRecordsTheVersionsInHandAndGatesNothing() = runUnitTest {
        val cache = InMemoryAppCache()
        val vm = viewModel(cache, config(legal = mapOf("termsVersion" to 4, "privacyVersion" to 3)))

        assertNull(vm.state.blocking)
        assertNull(vm.state.notice)

        val saved = cache.get()
        assertEquals(4, saved.acceptedTermsVersion)
        assertEquals(3, saved.acceptedPrivacyVersion)
        assertEquals(
            NOW,
            saved.legalAcceptedAt,
            "the timestamp is what tells a first launch apart from an acceptance of version 0",
        )
    }

    @Test
    fun aFirstLaunchCannotReachTheBlockingSheetHoweverHardTheConfigTries() = runUnitTest {
        val cache = InMemoryAppCache()
        val vm = viewModel(
            cache,
            config(legal = mapOf("termsVersion" to 9, "privacyVersion" to 9, "forceReacceptBelow" to 9)),
        )

        assertNull(vm.state.blocking, "nobody is behind terms they have never been offered")
        assertEquals(9, cache.get().acceptedTermsVersion)
    }

    @Test
    fun anOutOfDateAcceptanceIsNoted() = runUnitTest {
        val vm = viewModel(
            InMemoryAppCache(accepted(terms = 1, privacy = 1)),
            config(legal = mapOf("termsVersion" to 2, "privacyVersion" to 1)),
        )

        assertEquals(NoticeGate.LegalUpdated(termsVersion = 2, privacyVersion = 1), vm.state.notice)
        assertNull(vm.state.blocking)
    }

    @Test
    fun closingTheLegalBannerRecordsTheAcceptanceAndTakesItDown() = runUnitTest {
        val cache = InMemoryAppCache(accepted(terms = 1, privacy = 1))
        val vm = viewModel(cache, config(legal = mapOf("termsVersion" to 2, "privacyVersion" to 2)))

        vm.takeAction(LaunchGateAction.DismissNotice(NoticeGate.LegalUpdated(2, 2)))

        assertEquals(2, cache.get().acceptedTermsVersion)
        assertNull(vm.state.notice, "the record changed, so the resolve that follows finds nothing to say")
    }

    @Test
    fun acceptingRecordsTheVersionsThatWereOnScreenAndNotTheOnesConfigHasNow() = runUnitTest {
        // `state` lags `updateState` by a dispatch and config can refresh between
        // a frame and a tap, so the versions ride on the action. Re-reading them
        // here would record consent to terms nobody was ever shown.
        val cache = InMemoryAppCache(accepted(terms = 1, privacy = 1))
        val fake = config(legal = mapOf("termsVersion" to 2, "privacyVersion" to 2, "forceReacceptBelow" to 2))
        val vm = viewModel(cache, fake)

        assertEquals(BlockingGate.ReacceptLegal(2, 2), vm.state.blocking)

        fake.push(legal = mapOf("termsVersion" to 7, "privacyVersion" to 7, "forceReacceptBelow" to 7))
        vm.takeAction(LaunchGateAction.AcceptLegal(termsVersion = 2, privacyVersion = 2))

        assertEquals(2, cache.get().acceptedTermsVersion, "consent was given to version 2")
        assertEquals(
            BlockingGate.ReacceptLegal(7, 7),
            vm.state.blocking,
            "and the version that arrived afterwards is asked for separately",
        )
    }

    @Test
    fun therecordOnlyEverMovesForward() = runUnitTest {
        val cache = InMemoryAppCache(accepted(terms = 5, privacy = 5))
        val vm = viewModel(cache, config(legal = mapOf("termsVersion" to 5, "privacyVersion" to 5)))

        vm.takeAction(LaunchGateAction.AcceptLegal(termsVersion = 2, privacyVersion = 2))

        assertEquals(5, cache.get().acceptedTermsVersion, "a config that regressed must not un-accept anything")
    }

    @Test
    fun anOperatorsMaintenanceWallLandsMidSessionRatherThanAtTheNextColdStart() = runUnitTest {
        // The whole reason the config values are read at the moment of the
        // decision instead of captured at construction. Nobody force-quits the
        // app during an incident because we asked them to.
        val fake = config()
        val vm = viewModel(InMemoryAppCache(accepted()), fake)

        assertNull(vm.state.blocking)

        fake.push(upgrade = mapOf("maintenanceMode" to "blocking", "maintenanceMessage" to "back at 4"))

        assertEquals(BlockingGate.Maintenance("back at 4"), vm.state.blocking)
    }

    @Test
    fun theWallComesDownWhenTheIncidentEnds() = runUnitTest {
        val fake = config(upgrade = mapOf("maintenanceMode" to "blocking", "maintenanceMessage" to "back at 4"))
        val vm = viewModel(InMemoryAppCache(accepted()), fake)

        assertNotNull(vm.state.blocking)

        fake.push(upgrade = mapOf("maintenanceMode" to "off"))

        assertNull(vm.state.blocking)
    }

    @Test
    fun aDismissedMaintenanceBannerSurvivesTheNextWriteToAppData() = runUnitTest {
        // The trap. `AppData` is written constantly — screen visits, the board
        // snapshot — and every write re-runs the resolve. A dismissal held in
        // `state` rather than beside it would be overwritten milliseconds later
        // and the banner would be undismissable in practice.
        val cache = InMemoryAppCache(accepted())
        val vm = viewModel(
            cache,
            config(upgrade = mapOf("maintenanceMode" to "banner", "maintenanceMessage" to "read me")),
        )

        vm.takeAction(LaunchGateAction.DismissNotice(NoticeGate.Maintenance("read me")))
        assertNull(vm.state.notice)

        cache.set(cache.get().incrementVisit("board"))

        assertNull(vm.state.notice, "an unrelated AppData write must not bring it back")
    }

    @Test
    fun dismissingTheSoftUpdateSuggestionIsPersisted() = runUnitTest {
        val cache = InMemoryAppCache(accepted())
        val vm = viewModel(cache, config(upgrade = mapOf("softUpdateVersionCode" to FAR_FUTURE)))

        assertEquals(NoticeGate.SoftUpdate(FAR_FUTURE), vm.state.notice)

        vm.takeAction(LaunchGateAction.DismissNotice(NoticeGate.SoftUpdate(FAR_FUTURE)))

        assertEquals(FAR_FUTURE, cache.get().softUpdateDismissedFor)
        assertNull(vm.state.notice)
    }

    @Test
    fun theForceUpdateWallOffersTheStore() = runUnitTest {
        // Coupled to this repo's `versionCode` (1 in versions.properties), which
        // is the honest way to test the real `BuildInfo` read rather than a
        // parameter. FAR_FUTURE is above anything this app will ship.
        val vm = viewModel(
            InMemoryAppCache(accepted()),
            config(upgrade = mapOf("minSupportedVersionCode" to FAR_FUTURE)),
        )

        assertEquals(BlockingGate.ForceUpdate, vm.state.blocking)

        vm.takeAction(LaunchGateAction.OpenStore)

        val event = vm.eventFlow.first()
        assertTrue(
            event is LaunchGateEvent.OpenLink && event.url.isNotBlank(),
            "a force-update wall whose button goes nowhere is a brick",
        )
    }

    @Test
    fun theLegalLinksComeFromConfig() = runUnitTest {
        val vm = viewModel(InMemoryAppCache(accepted()), config())

        vm.takeAction(LaunchGateAction.OpenTerms)

        assertEquals(LaunchGateEvent.OpenLink(TERMS_URL), vm.eventFlow.first())
    }

    private fun viewModel(cache: AppCache, config: FakeConfig = config()) = LaunchGateViewModel(
        appCache = cache,
        appConfigRepository = config,
        minSupportedVersion = AppMinSupportedVersion(config),
        softUpdateVersion = AppSoftUpdateVersion(config),
        maintenanceMode = AppMaintenanceMode(config),
        maintenanceMessage = AppMaintenanceMessage(config),
        termsVersion = LegalTermsVersion(config),
        privacyVersion = LegalPrivacyVersion(config),
        forceReacceptBelow = LegalForceReacceptBelow(config),
        termsUrl = LegalTermsUrl(config),
        privacyUrl = LegalPrivacyUrl(config),
        clock = FixedClock,
    )

    private fun config(
        upgrade: Map<String, Any> = emptyMap(),
        legal: Map<String, Any> = emptyMap(),
    ) = FakeConfig().also { it.push(upgrade = upgrade, legal = legal) }

    /**
     * One object standing in for both halves of the production shape: a live
     * `AppConfigMap` whose `map` is re-read on every access (that is what
     * `LazyAppConfigMap` is) plus the stream that says it moved.
     */
    private class FakeConfig : AppConfigMap(), AppConfigRepository {
        private val state = MutableStateFlow<Map<String, Any>>(emptyMap())

        override val map: Map<String, *> get() = state.value
        override fun config(): AppConfigMap = this
        override fun configStream(): Flow<AppConfigMap> = state.map { this }

        fun push(upgrade: Map<String, Any> = emptyMap(), legal: Map<String, Any> = emptyMap()) {
            state.value = mapOf(
                "upgrade" to upgrade,
                "legal" to legal + mapOf("termsUrl" to TERMS_URL, "privacyUrl" to PRIVACY_URL),
            )
        }
    }

    private class InMemoryAppCache(initial: AppData = AppData()) : AppCache {
        private val data = MutableStateFlow(initial)
        override val updates: Flow<AppData> = data
        override suspend fun get(): AppData = data.value
        override suspend fun set(value: AppData) { data.value = value }
        override suspend fun clear() { data.value = AppData() }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(NOW)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val TERMS_URL = "https://sodogku.test/terms"
        const val PRIVACY_URL = "https://sodogku.test/privacy"

        /** Above any version code this app will plausibly ship. */
        const val FAR_FUTURE = 900_000

        fun accepted(terms: Int = 1, privacy: Int = 1) = AppData(
            acceptedTermsVersion = terms,
            acceptedPrivacyVersion = privacy,
            legalAcceptedAt = NOW,
        )
    }
}
