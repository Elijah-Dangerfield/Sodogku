package com.sodogku.features.onboarding.impl

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.LegalPrivacyUrl
import com.sodogku.libraries.config.values.LegalTermsUrl
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.flowroutines.testing.recordingEvents
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The welcome screen's one measurable moment.
 *
 * `onboarding.completed` is how we would know what fraction of installs get
 * through onboarding, and until SD-87 it went out once per tap rather than once
 * per player. The same shape had already been fixed in the tutorial (SD-85), so
 * these tests are here as much to hold the shape as to hold this screen.
 */
class OnboardingViewModelTest : CoroutineTest() {

    @Test
    fun aSecondPlayDoesNotReportASecondCompletion() = recordingEvents { events ->
        runUnitTest {
            // `isFinishing` disables both buttons, which is why this was harder
            // to reach than the tutorial's version. It is not a guard, though:
            // `state` lags `updateState` by a dispatch, so two taps inside one
            // frame are two actions in the channel before the disabled pass
            // renders, and `finish` ran whole for each of them.
            val vm = viewModel()

            vm.takeAction(OnboardingAction.Start)
            vm.takeAction(OnboardingAction.Start)
            settle()

            assertEquals(
                1,
                events.attributesOf("onboarding.completed").size,
                "the second tap reported its own completion",
            )
        }
    }

    @Test
    fun aSkipAfterAPlayDoesNotReportASecondCompletion() = recordingEvents { events ->
        runUnitTest {
            // The other door. Play and Skip both funnel through `finish`, and a
            // Skip landing on the frame after Play arrives with
            // `skipped_tutorial = true` next to the false the player earned,
            // which reads as a drop-off into a tutorial funnel they are in.
            val vm = viewModel()

            vm.takeAction(OnboardingAction.Start)
            vm.takeAction(OnboardingAction.SkipTutorial)
            settle()

            val completions = events.attributesOf("onboarding.completed")
            assertEquals(1, completions.size, "the late Skip reported its own completion")
            assertEquals(
                false,
                completions.single()["skipped_tutorial"],
                "a player who chose the tutorial was recorded as skipping it",
            )
        }
    }

    @Test
    fun theFirstTapDecidesWhatTheTutorialFlagSays() = recordingEvents {
        runUnitTest {
            // The write behind the event, for the same reason: a second `finish`
            // would have overwritten `hasCompletedTutorial` with the other
            // ending's answer and sent the player past the lesson they asked for.
            val cache = InMemoryAppCache()
            val vm = viewModel(cache)

            vm.takeAction(OnboardingAction.Start)
            vm.takeAction(OnboardingAction.SkipTutorial)
            settle()

            val data = cache.current()
            assertTrue(data.hasUserOnboarded, "onboarding never recorded itself as done")
            assertEquals(
                false,
                data.hasCompletedTutorial,
                "the second tap marked the tutorial seen behind the player's back",
            )
        }
    }

    @Test
    fun aReturningPlayerReportsNoCompletionAndNoAbandonment() = recordingEvents { events ->
        runUnitTest {
            // The bounce path, which is neither ending. Without this the guard
            // could be satisfied by a `finish` that fires on entry, and the
            // funnel would count every relaunch as an onboarding.
            viewModel(InMemoryAppCache(AppData(hasUserOnboarded = true)))
            settle()

            assertEquals(emptyList(), events.attributesOf("onboarding.completed"))
            assertEquals(emptyList(), events.attributesOf("onboarding.step_viewed"))
        }
    }

    private fun viewModel(cache: AppCache = InMemoryAppCache()): OnboardingViewModel =
        OnboardingViewModel(
            appCache = cache,
            termsUrl = LegalTermsUrl(config),
            privacyUrl = LegalPrivacyUrl(config),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun settle() {
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private val config = FakeConfigMap(
        mapOf("legal" to mapOf("termsUrl" to "https://sodogku.test/terms", "privacyUrl" to "https://sodogku.test/privacy")),
    )

    private class FakeConfigMap(override val map: Map<String, *>) : AppConfigMap()

    private class InMemoryAppCache(initial: AppData = AppData()) : AppCache {
        private val data = MutableStateFlow(initial)
        override val updates: Flow<AppData> = data
        override suspend fun get(): AppData = data.value
        override suspend fun set(value: AppData) { data.value = value }
        override suspend fun clear() { data.value = AppData() }

        fun current(): AppData = data.value
    }
}
