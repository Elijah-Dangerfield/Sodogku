package com.sodogku.libraries.billing.impl

import com.sodogku.libraries.billing.StoreBilling
import com.sodogku.libraries.billing.StoreOwnership
import com.sodogku.libraries.billing.StorePurchaseOutcome
import com.sodogku.libraries.billing.StorePurchaseResult
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import com.sodogku.libraries.sodogku.AppEvent
import com.sodogku.libraries.sodogku.AppEventBus
import com.sodogku.libraries.sodogku.AppEvents
import com.sodogku.libraries.sodogku.Session
import com.sodogku.libraries.sodogku.SessionStartReason
import com.sodogku.libraries.sodogku.SessionTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Hand-rolled doubles, per `docs/practices/testing.md`. No mocking library. */
class FakeStoreBilling(
    var ownership: StoreOwnership = StoreOwnership.NotOwned,
    var purchaseOutcome: StorePurchaseOutcome = StorePurchaseOutcome(StorePurchaseResult.Purchased),
    var restoreOwnership: StoreOwnership? = null,
    var throwOnEverything: Boolean = false,
) : StoreBilling {

    var ownershipCalls = 0
    var restoreCalls = 0

    override suspend fun ownership(productId: String): StoreOwnership {
        ownershipCalls++
        if (throwOnEverything) error("store exploded")
        return ownership
    }

    override suspend fun purchase(productId: String): StorePurchaseOutcome {
        if (throwOnEverything) error("store exploded")
        return purchaseOutcome
    }

    override suspend fun restore(productId: String): StoreOwnership {
        restoreCalls++
        if (throwOnEverything) error("store exploded")
        return restoreOwnership ?: ownership
    }

    override suspend fun priceLabel(productId: String): String? = "$4.99"
}

class FakeAppCache(initial: AppData = AppData()) : AppCache {
    private val state = MutableStateFlow(initial)

    override val updates: Flow<AppData> = state

    override suspend fun get(): AppData = state.value

    override suspend fun set(value: AppData) {
        state.value = value
    }

    override suspend fun clear() {
        state.value = AppData()
    }

    override suspend fun update(transform: (AppData) -> AppData): AppData {
        state.value = transform(state.value)
        return state.value
    }
}

class FakeAppEventBus : AppEventBus {
    private val stream = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)

    override fun dispatch(event: AppEvent) {
        stream.tryEmit(event)
    }

    override fun eventStream(): Flow<AppEvent> = stream
    override fun liveEventStream(): Flow<AppEvent> = stream
}

fun fakeAppEvents(bus: AppEventBus): AppEvents = AppEvents(bus)

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
