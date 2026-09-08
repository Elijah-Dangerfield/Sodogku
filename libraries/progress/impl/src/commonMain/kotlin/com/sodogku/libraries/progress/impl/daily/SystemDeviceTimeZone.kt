package com.sodogku.libraries.progress.impl.daily

import com.sodogku.libraries.progress.daily.DeviceTimeZone
import kotlinx.datetime.TimeZone
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The device's zone, read on every call.
 *
 * A singleton that caches nothing: `currentSystemDefault()` is the one thing in
 * here that is allowed to change under us, and holding onto it is the bug this
 * class exists to not have.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SystemDeviceTimeZone : DeviceTimeZone {
    override fun current(): TimeZone = TimeZone.currentSystemDefault()
}
