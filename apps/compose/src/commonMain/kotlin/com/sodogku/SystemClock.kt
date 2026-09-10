package com.sodogku

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Real wall time, and the only `Clock` a release build can resolve.
 *
 * A contributed class rather than a `@Provides` on [AppComponent] so the debug
 * build can take it out with `replaces` — see
 * `com.sodogku.qa.QaShiftedClock`. A second `@Provides` would have been a
 * duplicate binding, and a `@Provides` that branched on `BuildInfo.isDebug`
 * would have put the QA override in the release graph and merely made it
 * unreachable.
 *
 * Every date in the app is derived from this: the daily's board and countdown,
 * the streak fold, the calendar grid, the skip allowance. There is one binding
 * on purpose, so moving it moves all of them at once.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = Clock::class)
@Inject
class SystemClock : Clock {
    override fun now(): Instant = Clock.System.now()
}
