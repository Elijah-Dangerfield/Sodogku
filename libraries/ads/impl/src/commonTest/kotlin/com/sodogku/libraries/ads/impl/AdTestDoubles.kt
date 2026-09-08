package com.sodogku.libraries.ads.impl

import com.sodogku.libraries.ads.AdFormat
import com.sodogku.libraries.ads.AdNetwork
import com.sodogku.libraries.ads.AdShowOutcome
import com.sodogku.libraries.ads.AdShowResult
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PaywallCoordinator
import com.sodogku.libraries.billing.PaywallRequest
import com.sodogku.libraries.billing.PaywallTrigger
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.core.AppState
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.sodogku.Session
import com.sodogku.libraries.sodogku.SessionStartReason
import com.sodogku.libraries.sodogku.SessionTracker
import com.sodogku.libraries.storage.Cache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Hand-rolled doubles for the ad-gate tests, per `docs/practices/testing.md`.
 *
 * The two that carry weight are [FakeAdNetwork], which records *whether it was
 * asked at all* (most of the gating assertions are about the network never
 * being touched, not about what it returned), and [FakeAdStateCache], which is
 * a real read-modify-write store so a "survives a force quit" test can hand the
 * same instance to a second gate.
 */
class FakeAdNetwork(
    var outcome: AdShowOutcome = AdShowOutcome(AdShowResult.Rewarded),
) : AdNetwork {

    val shown = mutableListOf<AdFormat>()
    val preloaded = mutableListOf<AdFormat>()
    var prepareCalls = 0
    var showCallsBeforePrepare = 0

    override suspend fun prepare() {
        prepareCalls++
    }

    override suspend fun show(format: AdFormat): AdShowOutcome {
        if (prepareCalls == 0) showCallsBeforePrepare++
        shown += format
        return outcome
    }

    override fun preload(format: AdFormat) {
        preloaded += format
    }
}

class FakeEntitlements(isProNow: Boolean = false) : Entitlements {
    private val state = MutableStateFlow(isProNow)
    override val isPro: StateFlow<Boolean> = state

    fun setPro(value: Boolean) {
        state.value = value
    }

    override suspend fun purchasePro(trigger: String?): PurchaseOutcome = PurchaseOutcome.Unavailable
    override suspend fun restore(): RestoreOutcome = RestoreOutcome.NothingToRestore
}

class FakePaywallCoordinator(
    /** What `requestOffer` answers. False models a capped or disabled trigger. */
    var acceptsOffers: Boolean = true,
) : PaywallCoordinator {
    val offers = mutableListOf<PaywallTrigger>()
    val standIns = mutableListOf<Pair<String, String>>()
    var offlineBlocks = 0

    override val requests: Flow<PaywallRequest> = emptyFlow()

    override fun requestOffer(trigger: PaywallTrigger): Boolean {
        if (!acceptsOffers) return false
        offers += trigger
        return true
    }

    override fun requestOfflineBlock(): Boolean {
        offlineBlocks++
        return true
    }

    override fun requestAdStandIn(placementId: String, reason: String): Boolean {
        standIns += placementId to reason
        return true
    }
}

/**
 * The two connectivity signals are separate fields on purpose: `isOffline` also
 * trips when *our* backend is unreachable, and the ad layer must not care about
 * that. Keeping them independent here is what lets a test say "wifi is fine,
 * our server is down" — the state a dev build with no server deployed is
 * permanently in.
 */
class FakeAppState(offline: Boolean = false, deviceOffline: Boolean = offline) : AppState {
    override val isOffline = MutableStateFlow(offline)
    override val isDeviceOffline = MutableStateFlow(deviceOffline)
    override val isBlockActive = MutableStateFlow(false)
}

class FakeProgressRepository(var unlocked: Int = 1) : ProgressRepository {
    override fun observe(levelId: Int): Flow<LevelRecord> = emptyFlow()
    override suspend fun record(levelId: Int): LevelRecord = LevelRecord.unplayed(levelId)
    override suspend fun all(): List<LevelRecord> = emptyList()
    override suspend fun unlockedThrough(): Int = unlocked
    override suspend fun onAttemptStarted(levelId: Int) = Unit
    override suspend fun onCompleted(levelId: Int, score: Int, paws: Int, timeMs: Long) = Unit
    override suspend fun onSkipped(levelId: Int) = Unit
    override suspend fun reset() = Unit
}

class FakeAdStateCache(initial: AdState = AdState()) : AdStateCache {
    private val state = MutableStateFlow(initial)

    override val updates: Flow<AdState> = state

    override suspend fun get(): AdState = state.value

    override suspend fun set(value: AdState) {
        state.value = value
    }

    override suspend fun clear() {
        state.value = AdState()
    }

    override suspend fun update(transform: (AdState) -> AdState): AdState {
        state.value = transform(state.value)
        return state.value
    }
}

class FakeSessionTracker(startId: Long = 1L) : SessionTracker {
    private var sessionId = startId

    override val current: Session
        get() = Session(
            id = sessionId,
            startedAtMs = 0L,
            reason = SessionStartReason.ColdBoot,
            uuid = "session-$sessionId",
        )

    fun roll() {
        sessionId++
    }

    override fun observe(): Flow<Session> = emptyFlow()
}

class TestConfigMap(override val map: Map<String, Any> = emptyMap()) : AppConfigMap()

@OptIn(ExperimentalTime::class)
class MutableClock(private var millis: Long = 1_700_000_000_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)

    fun advanceMinutes(minutes: Long) {
        millis += minutes * 60_000L
    }

    fun advanceSeconds(seconds: Long) {
        millis += seconds * 1_000L
    }
}
