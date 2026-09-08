package com.sodogku.libraries.storage.impl.cache

import com.sodogku.libraries.storage.Cache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryCache<T : Any>(
    private val initial: () -> T,
) : Cache<T> {
    private val state = MutableStateFlow(initial())

    override val updates = state

    override suspend fun get(): T = state.value

    override suspend fun set(value: T) { state.value = value }

    /** Atomic, for the same reason the persistent cache overrides it. */
    override suspend fun update(transform: (T) -> T): T {
        state.update(transform)
        return state.value
    }

    override suspend fun clear() {
        state.value = initial()
    }
}
