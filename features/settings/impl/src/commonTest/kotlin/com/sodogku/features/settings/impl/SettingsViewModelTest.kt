package com.sodogku.features.settings.impl

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.FeatureAchievements
import com.sodogku.libraries.config.values.LegalPrivacyUrl
import com.sodogku.libraries.config.values.LegalTermsUrl
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import kotlin.test.Test
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SettingsViewModelTest : CoroutineTest() {

    @Test
    fun opensShowingWhatIsAlreadySaved() = runUnitTest {
        val cache = InMemoryAppCache(
            AppData(hapticsEnabled = false, reduceAnimations = true, colorblindMode = true),
        )

        val vm = viewModel(cache)

        assertFalse(vm.state.hapticsEnabled)
        assertTrue(vm.state.reduceAnimations)
        assertTrue(vm.state.colorblindMode)
    }

    @Test
    fun autoMarkIsOffByDefaultAndTogglesAndPersists() = runUnitTest {
        // Off by default is the load-bearing half. Crossing a square off is the
        // deduction, so a board that does it unasked has taken the step the
        // player is meant to make.
        val cache = InMemoryAppCache()
        val vm = viewModel(cache)
        assertFalse(vm.state.autoMarkEnabled, "the board crossed squares off nobody asked it to")
        assertFalse(cache.get().autoMarkEnabled, "and a fresh install is stored that way")

        vm.takeAction(SettingsAction.ToggleAutoMark)

        assertTrue(vm.state.autoMarkEnabled)
        assertTrue(cache.get().autoMarkEnabled)
    }

    @Test
    fun autoMarkOpensShowingWhatIsAlreadySaved() = runUnitTest {
        // Seeded to the *non*-default, which is the whole test: against `false`
        // this would pass just as well if the screen never read the cache.
        val cache = InMemoryAppCache(AppData(autoMarkEnabled = true))

        val vm = viewModel(cache)

        assertTrue(vm.state.autoMarkEnabled, "the screen forgot a choice the player made")
    }

    @Test
    fun hapticsTogglesAndPersists() = runUnitTest {
        val cache = InMemoryAppCache()
        val vm = viewModel(cache)
        assertTrue(vm.state.hapticsEnabled, "vibration is on by default")

        vm.takeAction(SettingsAction.ToggleHaptics)

        assertFalse(vm.state.hapticsEnabled)
        assertFalse(cache.get().hapticsEnabled)
    }

    @Test
    fun reduceAnimationsTogglesAndPersists() = runUnitTest {
        val cache = InMemoryAppCache()
        val vm = viewModel(cache)
        assertFalse(vm.state.reduceAnimations)

        vm.takeAction(SettingsAction.ToggleReduceAnimations)

        assertTrue(vm.state.reduceAnimations)
        assertTrue(cache.get().reduceAnimations)
    }

    @Test
    fun colorblindModeTogglesAndPersists() = runUnitTest {
        val cache = InMemoryAppCache()
        val vm = viewModel(cache)

        vm.takeAction(SettingsAction.ToggleColorblind)

        assertTrue(vm.state.colorblindMode)
        assertTrue(cache.get().colorblindMode)
    }

    @Test
    fun aToggleFlipsBackOffAgain() = runUnitTest {
        // `state` lags `updateState` by a dispatch, so a handler that reads it
        // back after writing computes the second toggle off a stale value and
        // the switch sticks. One round trip is the cheapest guard against that.
        val cache = InMemoryAppCache()
        val vm = viewModel(cache)

        vm.takeAction(SettingsAction.ToggleHaptics)
        vm.takeAction(SettingsAction.ToggleHaptics)

        assertTrue(vm.state.hapticsEnabled)
        assertTrue(cache.get().hapticsEnabled)
    }

    @Test
    fun theAchievementToggleWritesTheDisplayFlagAndNothingElse() = runUnitTest {
        // The whole point of this toggle is what it does *not* touch. It writes
        // one boolean into AppData; the achievement log carries on recording,
        // which is what makes turning badges back on show real history. If this
        // ever grows a second effect, the equality below is what catches it.
        val cache = InMemoryAppCache()
        val vm = viewModel(cache)
        assertTrue(vm.state.achievementsVisible, "badges are shown by default")

        vm.takeAction(SettingsAction.ToggleAchievements)

        assertFalse(vm.state.achievementsVisible)
        assertEquals(AppData(achievementsVisible = false), cache.get())
    }

    @Test
    fun theAchievementGridIsItsOwnDestination() = runUnitTest {
        val vm = viewModel(InMemoryAppCache())

        vm.takeAction(SettingsAction.OpenAchievements)

        assertEquals(SettingsEvent.OpenAchievements, vm.eventFlow.first())
    }

    @Test
    fun theAchievementToggleOpensShowingWhatIsAlreadySaved() = runUnitTest {
        val vm = viewModel(InMemoryAppCache(AppData(achievementsVisible = false)))

        assertFalse(vm.state.achievementsVisible)
    }

    @Test
    fun theVersionIsOnScreenFromTheFirstFrame() = runUnitTest {
        // Part of the initial state rather than loaded, because it is the one
        // thing on this screen a support conversation asks for.
        val vm = viewModel(InMemoryAppCache())

        assertTrue(vm.state.appVersion.isNotBlank())
        assertTrue(
            vm.state.appVersion.contains(BuildInfo.versionName),
            "expected the marketing version in \"${vm.state.appVersion}\"",
        )
    }

    @Test
    fun termsAndPrivacyOpenTheConfiguredUrls() = runUnitTest {
        // Read from config, not from a constant: a policy page that moves has to
        // be a config change rather than a store release.
        val vm = viewModel(InMemoryAppCache())

        vm.takeAction(SettingsAction.OpenTerms)
        assertEquals(SettingsEvent.OpenLink(TERMS_URL), vm.eventFlow.first())

        vm.takeAction(SettingsAction.OpenPrivacy)
        assertEquals(SettingsEvent.OpenLink(PRIVACY_URL), vm.eventFlow.first())
    }

    @Test
    fun replayingTheTutorialClearsTheFlagBeforeItNavigates() = runUnitTest {
        // Order is the whole of it. The board reads `hasCompletedTutorial` once
        // as its ViewModel loads, so a write that landed after the navigation
        // would open level 1 with nothing to teach.
        val cache = InMemoryAppCache(AppData(hasCompletedTutorial = true))
        val vm = viewModel(cache)

        vm.takeAction(SettingsAction.RerunTutorial)

        assertFalse(cache.get().hasCompletedTutorial)
        assertEquals(SettingsEvent.RerunTutorial, vm.eventFlow.first())
    }

    @Test
    fun replayingTheTutorialLeavesOnboardingAlone() = runUnitTest {
        // The reason the two flags are separate: somebody 200 levels in who
        // wants a refresher must not be shown the welcome screen again.
        val cache = InMemoryAppCache(
            AppData(hasUserOnboarded = true, hasCompletedTutorial = true),
        )
        val vm = viewModel(cache)

        vm.takeAction(SettingsAction.RerunTutorial)

        assertTrue(cache.get().hasUserOnboarded)
    }

    @Test
    fun switchingAchievementsOffTakesTheWholeSectionAway() = runUnitTest {
        val vm = viewModel(
            InMemoryAppCache(),
            FakeConfigMap(mapOf("features" to mapOf("achievements" to false))),
        )

        assertFalse(vm.state.achievementsAvailable)
        assertTrue(
            vm.state.achievementsVisible,
            "the player's own toggle is untouched, so the setting is still there " +
                "when the feature comes back",
        )
    }

    @Test
    fun withNoConfigTheAchievementSectionIsPresent() = runUnitTest {
        val vm = viewModel(InMemoryAppCache(), FakeConfigMap(emptyMap<String, Any>()))

        assertTrue(vm.state.achievementsAvailable)
    }

    @Test
    fun aMalformedAchievementFlagDoesNotHideTheFeature() = runUnitTest {
        // A string typed into a boolean key used to resolve to `false`. Every
        // `features.*` key is one console typo away from this.
        val vm = viewModel(
            InMemoryAppCache(),
            FakeConfigMap(mapOf("features" to mapOf("achievements" to "banana"))),
        )

        assertTrue(vm.state.achievementsAvailable)
    }

    @Test
    fun feedbackIsItsOwnDestination() = runUnitTest {
        val vm = viewModel(InMemoryAppCache())

        vm.takeAction(SettingsAction.OpenFeedback)

        assertEquals(SettingsEvent.OpenFeedback, vm.eventFlow.first())
    }

    private fun viewModel(
        cache: AppCache,
        config: AppConfigMap = this.config,
        entitlements: Entitlements = FakeEntitlements(),
    ) = SettingsViewModel(
        appCache = cache,
        termsUrl = LegalTermsUrl(config),
        privacyUrl = LegalPrivacyUrl(config),
        achievementsEnabled = FeatureAchievements(config),
        entitlements = entitlements,
    )

    @Test
    fun restoringReportsEveryOutcomeIncludingTheBoringOne() = runUnitTest {
        // A restore that silently does nothing is the commonest reason this
        // control gets reported as broken: the player cannot tell "you never
        // bought it" from "we could not ask". Every branch has to say something.
        val seen = listOf(
            RestoreOutcome.Restored to RestoreMessage.Restored,
            RestoreOutcome.NothingToRestore to RestoreMessage.NothingToRestore,
            RestoreOutcome.Failed("store") to RestoreMessage.Failed,
        ).map { (outcome, expected) ->
            val vm = viewModel(InMemoryAppCache(), entitlements = FakeEntitlements(restore = outcome))
            vm.takeAction(SettingsAction.RestorePurchases)
            vm.state.restoreMessage to expected
        }

        seen.forEach { (actual, expected) -> assertEquals(expected, actual) }
    }

    @Test
    fun aSuccessfulRestoreTurnsProOn() = runUnitTest {
        val entitlements = FakeEntitlements(restore = RestoreOutcome.Restored, proAfterRestore = true)
        val vm = viewModel(InMemoryAppCache(), entitlements = entitlements)
        assertFalse(vm.state.isPro, "the fixture has to start un-Pro or this proves nothing")

        vm.takeAction(SettingsAction.RestorePurchases)

        assertTrue(vm.state.isPro)
    }

    @Test
    fun theStoreRowIsReachableWhetherOrNotYouAlreadyPaid() = runUnitTest {
        // Apple rejects a non-consumable app with no visible restore control, so
        // this is a submission requirement rather than a nicety.
        val vm = viewModel(InMemoryAppCache())

        vm.takeAction(SettingsAction.OpenPaywall)

        assertEquals(SettingsEvent.OpenPaywall, vm.eventFlow.first())
    }

    private class FakeEntitlements(
        private val restore: RestoreOutcome = RestoreOutcome.NothingToRestore,
        private val proAfterRestore: Boolean = false,
    ) : Entitlements {
        private val pro = MutableStateFlow(false)
        override val isPro: StateFlow<Boolean> = pro
        override suspend fun purchasePro(trigger: String?) = PurchaseOutcome.Unavailable
        override suspend fun restore(): RestoreOutcome {
            if (proAfterRestore) pro.value = true
            return restore
        }
    }

    private val config = FakeConfigMap(
        mapOf("legal" to mapOf("termsUrl" to TERMS_URL, "privacyUrl" to PRIVACY_URL)),
    )

    private class FakeConfigMap(override val map: Map<String, *>) : AppConfigMap()

    private class InMemoryAppCache(initial: AppData = AppData()) : AppCache {
        private val data = MutableStateFlow(initial)
        override val updates: Flow<AppData> = data
        override suspend fun get(): AppData = data.value
        override suspend fun set(value: AppData) { data.value = value }
        override suspend fun clear() { data.value = AppData() }
    }

    private companion object {
        const val TERMS_URL = "https://sodogku.test/terms"
        const val PRIVACY_URL = "https://sodogku.test/privacy"
    }
}
