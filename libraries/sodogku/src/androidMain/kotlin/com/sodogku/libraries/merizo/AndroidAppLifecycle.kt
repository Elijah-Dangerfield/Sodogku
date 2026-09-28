package com.sodogku.libraries.sodogku

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sodogku.libraries.sodogku.AppLifecycle
import com.sodogku.libraries.sodogku.AppLifecycleObserver
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@ContributesBinding(AppScope::class, boundType = AppLifecycle::class)
@SingleIn(AppScope::class)
@Inject
class AndroidAppLifecycle(
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.Companion.get().lifecycle,
) : AppLifecycle, DefaultLifecycleObserver {

    private val observerLock = Any()
    private val observers = LinkedHashSet<AppLifecycleObserver>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun addObserver(observer: AppLifecycleObserver) {
        val shouldRegister = synchronized(observerLock) {
            val wasEmpty = observers.isEmpty()
            observers.add(observer)
            wasEmpty
        }

        if (shouldRegister) {
            onMainThread { lifecycle.addObserver(this) }
        }
    }

    override fun removeObserver(observer: AppLifecycleObserver) {
        val shouldUnregister = synchronized(observerLock) {
            observers.remove(observer)
            observers.isEmpty()
        }

        if (shouldUnregister) {
            onMainThread { lifecycle.removeObserver(this) }
        }
    }

    /**
     * Registration goes through the main thread, because `LifecycleRegistry`
     * throws rather than synchronize: `addObserver` off the main thread is an
     * `IllegalStateException`, and an uncaught one inside a coroutine takes the
     * process with it.
     *
     * Who calls this is not something this class gets to choose. Callers reach
     * it through the object graph, so the thread is whichever one first touched
     * a dependency that happens to construct an [AppLifecycle] observer. That
     * made it a race rather than a bug you can see: on 2026-09-25 the Play
     * Games sign-in coroutine won it on a cold boot of 0.3.0 and crashed the
     * app at launch (SODOGKU-T), while the same binary started fine whenever
     * the main thread got there first.
     *
     * Posting rather than running inline when already on main keeps add and
     * remove in the order they were called, which an inline fast path would
     * invert: a remove running immediately on main while an add sits queued
     * behind it leaves the registry holding an observer nobody asked for.
     * Nothing is lost by the one-frame delay, because a registry replays its
     * current state to an observer added later.
     */
    private fun onMainThread(block: () -> Unit) {
        mainHandler.post(block)
    }

    override fun onStart(owner: LifecycleOwner) {
        notify { it.onEnterForeground() }
    }

    override fun onStop(owner: LifecycleOwner) {
        notify { it.onEnterBackground() }
    }

    private inline fun notify(invocation: (AppLifecycleObserver) -> Unit) {
        val snapshot = synchronized(observerLock) { observers.toList() }
        snapshot.forEach(invocation)
    }
}