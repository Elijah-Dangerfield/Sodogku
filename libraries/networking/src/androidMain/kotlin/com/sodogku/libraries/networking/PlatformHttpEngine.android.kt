package com.sodogku.libraries.networking

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual val platformHttpEngineFactory: HttpClientEngineFactory<*> = OkHttp
