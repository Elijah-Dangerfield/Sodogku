package com.sodogku.libraries.networking

import com.sodogku.libraries.core.AuthGate
import com.sodogku.libraries.core.AuthRequirement
import com.sodogku.libraries.core.AuthVerdict
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Default [AuthGate] binding for an app with no accounts: everything is ready,
 * always. Sodogku's only backend surface is public remote config, so no call
 * and no route has an auth requirement to gate on.
 *
 * It lives in this api module — not `:impl` — for the same reason
 * [NoOpAuthTokenProvider] does: a future auth library can replace it with
 * `@ContributesBinding(AppScope::class, replaces = [AlwaysReadyAuthGate::class])`
 * without crossing the impl-module boundary.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AlwaysReadyAuthGate : AuthGate {
    override fun verdict(requirement: AuthRequirement): AuthVerdict = AuthVerdict.Ready
    override suspend fun awaitVerdict(requirement: AuthRequirement): AuthVerdict = AuthVerdict.Ready
}
