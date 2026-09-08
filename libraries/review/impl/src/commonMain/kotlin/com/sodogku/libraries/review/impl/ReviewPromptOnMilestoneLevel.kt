package com.sodogku.libraries.review.impl

import com.sodogku.libraries.config.AppConfigRepository
import com.sodogku.libraries.config.values.AppReviewPromptAfterLevel
import com.sodogku.libraries.core.AutoInit
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.review.ReviewPromptCoordinator
import com.sodogku.libraries.review.ReviewTrigger
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Asks for a store review the moment `app.reviewPromptAfterLevel` is cleared.
 *
 * The prompt itself already existed — [ReviewPromptCoordinator] rations it
 * against install age and a 30-day floor — and had nothing calling it. This is
 * the trigger, and it lives here rather than in the game because the coordinator
 * owns eligibility and the game should not have to know a threshold exists.
 *
 * **It fires on the transition, not on the state.** `observe` replays the current
 * record first, so a player who cleared the level months ago would otherwise be
 * asked on every launch — at boot, which is the opposite of the "right after a
 * win" moment the whole feature is for. The first emission is dropped, so only a
 * clear that happens *while the app is running* counts.
 *
 * **It waits for the first config emission before reading the threshold.** This
 * is an [AutoInit], so it constructs in `Application.onCreate` — before the
 * config stream has emitted anything, where `AppConfigMap` still answers from the
 * bundled fallback. Reading the value in the constructor therefore looked wired
 * and would have been inert: `app.reviewPromptAfterLevel` could never have been
 * anything but 10, on every cold start, forever. Found on a device, by an
 * override that changed nothing.
 *
 * **Two things it deliberately does not do.** It watches exactly the configured
 * level, so a player who skips past it is never asked; and it reads the threshold
 * once per launch rather than re-subscribing on every config refresh, which would
 * re-arm the drop below and could swallow the clear it was meant to catch.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class ReviewPromptOnMilestoneLevel(
    private val progress: ProgressRepository,
    private val coordinator: ReviewPromptCoordinator,
    private val reviewPromptAfterLevel: AppReviewPromptAfterLevel,
    private val appConfigRepository: AppConfigRepository,
    appScope: AppCoroutineScope,
) : AutoInit {

    private val logger = KLog.withTag("ReviewPrompt")

    init {
        appScope.launch { watch() }
    }

    private suspend fun watch() {
        appConfigRepository.configStream().first()
        val level = reviewPromptAfterLevel()

        if (level <= 0) {
            // Zero is off, and it is the value an operator reaches for to stop
            // asking. Nothing to watch.
            logger.d { "Review prompt disabled (app.reviewPromptAfterLevel=$level)" }
            return
        }

        logger.d { "Watching level $level for a review prompt" }
        progress.observe(level)
            .map { it.state == LevelState.Completed }
            .distinctUntilChanged()
            .drop(1)
            .filter { completed -> completed }
            .collect {
                logger.d { "Level $level cleared; asking the coordinator for a review prompt" }
                Catching { coordinator.requestPrompt(ReviewTrigger.MilestoneReached) }
                    .logOnFailure { "Review prompt request failed" }
            }
    }
}
