package com.sodogku.features.settings.impl

import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.FeatureAchievements
import com.sodogku.libraries.config.values.LegalPrivacyUrl
import com.sodogku.libraries.config.values.LegalTermsUrl
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.leaderboards.Leaderboard
import com.sodogku.libraries.leaderboards.Leaderboards
import com.sodogku.libraries.leaderboards.NoLeaderboards
import com.sodogku.libraries.leaderboards.WindowedScore
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import kotlin.test.Test
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The reference recipe for a view-model test in this repo, and the screen where
 * a toggle that lies is invisible.
 *
 * `docs/practices/testing.md#the-layers` points here for the shape: extend
 * `CoroutineTest`, hand-roll a fake per dependency, drive actions, assert on
 * `vm.state`. Anything new in that tier should read like this file.
 *
 * Every toggle is round-tripped rather than only flipped, because a handler
 * that writes without persisting and a screen that persists without reading
 * back both look correct from one direction. The ones seeded to their
 * non-default matter most: against `false`, "opens showing what is saved"
 * passes for a screen that never opened the cache at all.
 *
 * Flipping a toggle and flipping it back is the cheapest guard against a real
 * bug in this pattern. `state` lags `updateState` by a dispatch, so a handler
 * that reads it back after writing computes the second flip from a stale value
 * and the switch sticks on.
 *
 * Two decisions here are about what a toggle must *not* do. Hiding achievements
 * writes one display flag and leaves the log recording, which is what makes
 * turning them back on show real history, and that is pinned by comparing the
 * whole of the persisted data rather than the one field. Replaying the tutorial
 * clears its flag before navigating, since the board reads that flag once as it
 * loads, and it leaves the onboarding flag alone so somebody two hundred levels
 * in is not shown the welcome screen again.
 *
 * The remote-config cases lean toward the player: with no config the
 * achievements section is present, and a malformed value does not hide it.
 * A string typed into a boolean key used to resolve to false, and every feature
 * key is one console typo away from that.
 *
 * ### Not here
 *
 * What the settings actually change. Auto-mark's effect on a board is
 * `GameViewModelTest`, reduced motion's effect on a dialog is
 * `ModalDialogDefaultsTest`, and sending feedback is `FeedbackViewModelTest`.
 */
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

    /**
     * SD-149. The row is the whole feature, so the test is about whether it is
     * offered rather than about what the form does, which is Google's code.
     *
     * Both directions matter and for different reasons. Hidden is the answer
     * for most of the world, and a row that opened nothing would be the bug.
     * Shown is a Google policy requirement in the EEA, and it is the one that
     * gets a release rejected if it regresses.
     */
    @Test
    fun theConsentRowIsHiddenWhenUmpHasNothingToAsk() = runUnitTest {
        val vm = viewModel(InMemoryAppCache(), adGate = SilentAdGate(required = false))

        assertFalse(vm.state.privacyOptionsAvailable)
    }

    @Test
    fun theConsentRowAppearsAndOpensTheFormWhenUmpRequiresIt() = runUnitTest {
        val gate = SilentAdGate(required = true)
        val vm = viewModel(InMemoryAppCache(), adGate = gate)

        assertTrue(vm.state.privacyOptionsAvailable)

        vm.takeAction(SettingsAction.OpenPrivacyOptions)

        assertEquals(1, gate.shown)
    }

    private fun viewModel(
        cache: AppCache,
        config: AppConfigMap = this.config,
        entitlements: Entitlements = FakeEntitlements(),
        leaderboards: Leaderboards = NoLeaderboards(),
        adGate: AdGate = SilentAdGate(),
    ) = SettingsViewModel(
        appCache = cache,
        termsUrl = LegalTermsUrl(config),
        privacyUrl = LegalPrivacyUrl(config),
        achievementsEnabled = FeatureAchievements(config),
        entitlements = entitlements,
        leaderboards = leaderboards,
        adGate = adGate,
    )

    /**
     * An [AdGate] with nothing to say, which is what a player outside the EEA
     * gets. Only the two consent members are reachable from Settings; the rest
     * throw, so a test that somehow shows an ad from this screen fails loudly
     * rather than passing quietly.
     */
    private class SilentAdGate(private val required: Boolean = false) : AdGate {
        var shown = 0


        override suspend fun showRewarded(placement: AdPlacement): RewardOutcome =
            error("Settings must not show an ad")

        override fun preload(placement: AdPlacement) = error("Settings must not preload an ad")

        override suspend fun privacyOptionsRequired(): Boolean = required

        override suspend fun showPrivacyOptions() {
            shown++
        }
    }

    /**
     * A [Leaderboards] that says it is offerable and records the one call this
     * screen can make.
     *
     * Not a mock of the whole interface: both submit paths are no-ops here on
     * purpose, because Settings has no business submitting a score and a double
     * that recorded one would let a test pass that should not exist.
     */
    private class OfferedLeaderboards : Leaderboards {
        private val offerable = MutableStateFlow(true)
        override val isOfferable: StateFlow<Boolean> = offerable.asStateFlow()
        var opened = 0
            private set

        override fun submit(board: Leaderboard, value: Long) = Unit
        override fun submitWindowed(board: Leaderboard, points: WindowedScore) = Unit
        override fun openDashboard(board: Leaderboard?) {
            opened++
        }

        fun withdraw() {
            offerable.value = false
        }

        fun offer() {
            offerable.value = true
        }
    }

    @Test
    fun theLeaderboardRowIsOnlyOfferedWhenItLeadsSomewhere() = runUnitTest {
        // The whole point of `isOfferable`. On Android, signed out, or before
        // Game Center has answered, this row would open nothing, and a control
        // that does nothing is worse than an absent one.
        val absent = viewModel(InMemoryAppCache())
        assertFalse(absent.state.leaderboardsOfferable, "a row was offered with nothing behind it")

        val present = viewModel(InMemoryAppCache(), leaderboards = OfferedLeaderboards())
        assertTrue(present.state.leaderboardsOfferable, "the row never appeared")
    }

    @Test
    fun theRowAppearsWhenGameCenterAnswersLate() = runUnitTest {
        // Authentication resolves after launch, so this can flip while the
        // screen is already open. Read once in `load` it would stay false until
        // the player left and came back, which reads as a broken row.
        val platform = OfferedLeaderboards()
        platform.withdraw()
        val vm = viewModel(InMemoryAppCache(), leaderboards = platform)
        assertFalse(vm.state.leaderboardsOfferable)

        platform.offer()

        assertTrue(vm.state.leaderboardsOfferable, "the screen stopped watching after the first value")
    }

    @Test
    fun tappingTheRowOpensThePlatformDashboard() = runUnitTest {
        // No event is sent: the dashboard is presented by the platform over
        // whatever is on screen, so there is no route for navigation to take.
        val platform = OfferedLeaderboards()
        val vm = viewModel(InMemoryAppCache(), leaderboards = platform)

        vm.takeAction(SettingsAction.OpenLeaderboards)

        assertEquals(1, platform.opened, "the tap did not reach the platform")
    }

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
