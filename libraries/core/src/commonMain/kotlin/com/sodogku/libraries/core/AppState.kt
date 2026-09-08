package com.sodogku.libraries.core

import kotlinx.coroutines.flow.StateFlow

interface AppState {
    /**
     * "The user cannot reach us." True when the OS reports no path **or** our
     * own requests are not completing. This is the banner signal, and it is the
     * right one for anything that talks to our backend.
     */
    val isOffline: StateFlow<Boolean>

    val isBlockActive: StateFlow<Boolean>

    /**
     * "The device has no network at all." The OS signal on its own, without the
     * backend-reachability half of [isOffline].
     *
     * This exists because the two are not interchangeable and one caller
     * genuinely needs the narrower one: the ad layer's offline grace, which
     * ends in a screen that blocks play. AdMob is perfectly reachable while
     * *our* server is down, and gating on [isOffline] meant a dev build with no
     * server deployed spent its offline grace on full wifi and put the block
     * screen up. Observed on a device, 2026-09-07 — SPEC 6 already said this,
     * and there was no flow that expressed it.
     *
     * Defaults to [isOffline] so previews and test doubles that only model one
     * signal keep compiling; `AppStateImpl` is the only implementation that has
     * two to tell apart.
     */
    val isDeviceOffline: StateFlow<Boolean> get() = isOffline
}
