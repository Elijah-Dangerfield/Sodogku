package com.sodogku.libraries.storage

interface Cache<T : Any> {
    val updates: kotlinx.coroutines.flow.Flow<T>

    suspend fun get(): T

    suspend fun set(value: T)

    suspend fun clear()

    /**
     * Read-modify-write.
     *
     * **Every implementation must override this with an atomic one.** The
     * default below is a convenience for a single-writer cache and nothing
     * more: it reads, then writes, and two overlapping callers each transform a
     * snapshot the other has already replaced, so the later write silently
     * reverts the earlier one. `AppData` has several writers live at once on a
     * cold start, which is exactly where that shows up — as a setting the
     * player flipped that is back off next launch.
     */
    suspend fun update(transform: (T) -> T): T {
        val newValue = transform(get())
        set(newValue)
        return newValue
    }
}

