package com.sodogku.libraries.config.impl.data

import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.impl.model.BasicMapAppConfig
import com.sodogku.libraries.config.impl.serialization.ConfigJsonConverter
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.flatMap
import com.sodogku.libraries.flowroutines.DispatcherProvider
import com.sodogku.libraries.networking.NetworkClient
import com.sodogku.libraries.networking.networkCall
import com.sodogku.libraries.networking.retry.RetryPolicy
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Fetches the app config tree from the app server.
 *
 * Returns a sparse JSON object whose keys are the dotted [com.sodogku.libraries.config.ConfiguredValue.path]s
 * that the server wants to override. Anything missing falls back to the
 * client-side default declared on the [com.sodogku.libraries.config.ConfiguredValue].
 *
 * The call carries no identity. There are no accounts, so the server targets on
 * the install-id and client-context axes that every request header already
 * carries.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RemoteConfigRemoteDataSource @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val networkClient: NetworkClient,
    private val converter: ConfigJsonConverter,
) : RemoteConfigDataSource {

    private val logger = KLog.withTag("RemoteConfigDataSource")

    override suspend fun getConfig(): Catching<AppConfigMap> = withContext(dispatcherProvider.io) {
        networkClient.networkCall(
            description = "appConfig.fetch",
            retry = RetryPolicy.idempotent(),
        ) { client ->
            client.get("/v1/app-config").body<String>()
        }
            .flatMap { raw -> converter.decodeToMap(raw).map { BasicMapAppConfig(it) } }
            .onSuccess { logger.d { "Fetched remote app config" } }
    }
}
