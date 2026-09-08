package com.sodogku.libraries.progress.impl

import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.config.values.ProgressionSkipsPerDay
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.progress.SkipRepository
import com.sodogku.libraries.progress.SkipResult
import com.sodogku.libraries.progress.daily.DeviceTimeZone
import com.sodogku.libraries.progress.impl.daily.dayOf
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The skip, end to end: the daily allowance, the ad, and the write.
 *
 * Shaped like `DailyRepositoryImpl.useFreeze` and for the same reason — the
 * three steps have to happen in one order and there is no useful state between
 * them, so a caller that assembled them itself would be a second place the
 * order could be got wrong.
 *
 * The cap applies to Pro as well (SPEC 1.6). Pro's benefit is that the skip
 * costs no ad, not that there are more of them.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SkipRepositoryImpl(
    private val progress: ProgressRepository,
    private val cache: SkipStateCache,
    private val adGate: AdGate,
    private val entitlements: Entitlements,
    private val clock: Clock,
    private val timeZone: DeviceTimeZone,
    private val skipsPerDay: ProgressionSkipsPerDay,
) : SkipRepository {

    override suspend fun remainingToday(): Int = remainingOn(today(), cache.get())

    override suspend fun skip(levelId: Int): SkipResult {
        val today = today()
        if (remainingOn(today, cache.get()) <= 0) return SkipResult.NoneLeft

        val granted = entitlements.isPro.value ||
            adGate.showRewarded(AdPlacement.SkipLevel) != RewardOutcome.Dismissed
        if (!granted) return SkipResult.Declined

        // Spent before the level is written, so a crash between the two costs
        // the player a skip rather than handing them an uncapped one.
        cache.update { it.spent(today) }
        progress.onSkipped(levelId)
        return SkipResult.Skipped(remainingOn(today, cache.get()))
    }

    /**
     * Zero rather than the cap when `progression.skipsPerDay` is nonsense.
     *
     * The one place in this file where failing toward the player would be wrong:
     * a negative cap read as "unlimited" turns a typo in the admin console into
     * an unlimited skip printer, and the player who cannot skip today can still
     * play every level in the game.
     */
    private fun remainingOn(today: LocalDate, state: SkipState): Int =
        (skipsPerDay() - state.on(today).used).coerceAtLeast(0)

    private fun today(): LocalDate = dayOf(clock.now(), timeZone.current())
}
