package com.sodogku.libraries.review.impl

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.AppConfigRepository
import com.sodogku.libraries.config.values.AppReviewPromptAfterLevel
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.review.ReviewPromptCoordinator
import com.sodogku.libraries.review.ReviewTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The trigger `app.reviewPromptAfterLevel` was missing.
 *
 * The rationing itself is `RealReviewPromptCoordinatorTest`; what matters here is
 * *when* the coordinator gets asked, and the case that would otherwise be
 * invisible: a player who cleared the milestone long ago must not be asked again
 * at every launch, because `observe` replays the record it already has.
 */
class ReviewPromptOnMilestoneLevelTest : CoroutineTest() {

    @Test
    fun clearingTheConfiguredLevelAsksForAPrompt() = runUnitTest {
        val progress = FakeProgress()
        val coordinator = RecordingCoordinator()
        watcher(progress, coordinator, afterLevel = 10)
        runCurrent()

        progress.complete(10)
        runCurrent()

        assertEquals(1, coordinator.requests)
        assertEquals(ReviewTrigger.MilestoneReached, coordinator.lastTrigger)
    }

    @Test
    fun aLevelClearedBeforeThisLaunchDoesNotAskAgain() = runUnitTest {
        // `observe` replays the current record, so the naive version asks on
        // every cold start — at boot, which is the one moment the prompt is
        // explicitly not supposed to land on.
        val progress = FakeProgress(LevelRecord.unplayed(10).copy(state = LevelState.Completed))
        val coordinator = RecordingCoordinator()
        watcher(progress, coordinator, afterLevel = 10)
        runCurrent()

        assertEquals(0, coordinator.requests, "the replayed record is history, not a win")
    }

    @Test
    fun anotherLevelClearingDoesNotAsk() = runUnitTest {
        val progress = FakeProgress()
        val coordinator = RecordingCoordinator()
        watcher(progress, coordinator, afterLevel = 10)
        runCurrent()

        progress.complete(9)
        runCurrent()

        assertEquals(0, coordinator.requests)
        assertEquals(10, progress.observed, "the watcher subscribes to the configured level, not to level 1")
    }

    @Test
    fun replayingTheSameLevelOnlyAsksOnce() = runUnitTest {
        val progress = FakeProgress()
        val coordinator = RecordingCoordinator()
        watcher(progress, coordinator, afterLevel = 10)
        runCurrent()

        progress.complete(10)
        progress.complete(10)
        runCurrent()

        assertEquals(1, coordinator.requests, "a better score on a cleared level is not a new milestone")
    }

    @Test
    fun zeroTurnsThePromptOffWithoutSubscribingToAnything() = runUnitTest {
        val progress = FakeProgress()
        val coordinator = RecordingCoordinator()
        watcher(progress, coordinator, afterLevel = 0)
        runCurrent()

        progress.complete(1)
        runCurrent()

        assertEquals(0, coordinator.requests)
        assertEquals(NOTHING_OBSERVED, progress.observed, "level 0 is not a level to wait for")
    }

    @Test
    fun anAbsentConfigStillAsksAtTheShippedLevel() = runUnitTest {
        val progress = FakeProgress()
        val coordinator = RecordingCoordinator()
        val config = FakeConfigMap(emptyMap<String, Any>())
        val afterLevel = AppReviewPromptAfterLevel(config)
        assertTrue(afterLevel() > 0, "the shipped default is a real level")

        ReviewPromptOnMilestoneLevel(progress, coordinator, afterLevel, config, AppCoroutineScope(dispatchers))
        runCurrent()

        progress.complete(afterLevel())
        runCurrent()

        assertEquals(1, coordinator.requests)
    }

    private fun watcher(
        progress: ProgressRepository,
        coordinator: RecordingCoordinator,
        afterLevel: Int,
        config: FakeConfigMap = FakeConfigMap(
            mapOf("app" to mapOf("reviewPromptAfterLevel" to afterLevel)),
        ),
    ) = ReviewPromptOnMilestoneLevel(
        progress = progress,
        coordinator = coordinator,
        reviewPromptAfterLevel = AppReviewPromptAfterLevel(config),
        appConfigRepository = config,
        appScope = AppCoroutineScope(dispatchers),
    )

    /**
     * Emits once and stays open, like the real `configStream()`. The watcher
     * waits on that first emission before reading the threshold — see the class
     * KDoc for why reading it any earlier made the key inert.
     */
    private class FakeConfigMap(override val map: Map<String, *>) : AppConfigMap(), AppConfigRepository {
        override fun config(): AppConfigMap = this
        override fun configStream(): Flow<AppConfigMap> = flowOf(this)
    }

    private class FakeProgress(initial: LevelRecord? = null) : ProgressRepository {
        var observed: Int = NOTHING_OBSERVED
            private set

        private val records = MutableStateFlow(
            initial?.let { mapOf(it.levelId to it) } ?: emptyMap(),
        )

        override fun observe(levelId: Int): Flow<LevelRecord> {
            observed = levelId
            return records.map { it[levelId] ?: LevelRecord.unplayed(levelId) }
        }

        fun complete(levelId: Int) {
            val record = (records.value[levelId] ?: LevelRecord.unplayed(levelId))
                .copy(state = LevelState.Completed, attempts = 1)
            records.value = records.value + (levelId to record)
        }

        override suspend fun record(levelId: Int): LevelRecord =
            records.value[levelId] ?: LevelRecord.unplayed(levelId)

        override suspend fun all(): List<LevelRecord> = records.value.values.toList()
        override suspend fun unlockedThrough(): Int = LevelRecord.FIRST_LEVEL_ID
        override suspend fun onAttemptStarted(levelId: Int) = Unit
        override suspend fun onCompleted(levelId: Int, score: Int, paws: Int, timeMs: Long) = complete(levelId)
        override suspend fun onSkipped(levelId: Int) = Unit
        override suspend fun reset() { records.value = emptyMap() }
    }

    private class RecordingCoordinator : ReviewPromptCoordinator {
        var requests: Int = 0
            private set

        var lastTrigger: ReviewTrigger? = null
            private set

        override suspend fun requestPrompt(trigger: ReviewTrigger): Boolean {
            requests++
            lastTrigger = trigger
            return true
        }
    }

    private companion object {
        const val NOTHING_OBSERVED = -1
    }
}
