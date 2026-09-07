package com.sodogku.libraries.config.impl

import com.sodogku.libraries.config.AppConfigRepository
import com.sodogku.libraries.config.EnsureAppConfigLoaded
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.ignoreValue
import com.sodogku.libraries.core.throwIfDebug
import kotlinx.coroutines.flow.first
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EnsureAppConfigLoadedImpl @Inject constructor(
    private val repository: AppConfigRepository
) : EnsureAppConfigLoaded {
    override suspend fun invoke(): Catching<Unit> =
        Catching {
            repository.configStream().first()
        }
            .throwIfDebug()
            .ignoreValue()
}
