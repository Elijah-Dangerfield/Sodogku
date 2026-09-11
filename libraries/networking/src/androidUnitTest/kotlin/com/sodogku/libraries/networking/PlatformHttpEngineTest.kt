package com.sodogku.libraries.networking

import io.ktor.client.engine.okhttp.OkHttp
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * One assertion, because the alternative to naming an engine is Ktor picking
 * one.
 *
 * With no explicit factory, Ktor resolves whatever engine happens to be on the
 * classpath, and the answer changes when a dependency is added by someone who
 * never thought about HTTP. The binding is therefore stated per platform and
 * pinned per platform. This is the Android half, which has to be OkHttp: it is
 * the engine the app's timeouts, connection pooling and proxy behaviour were
 * tuned against, and a swap would be silent everywhere except in production
 * latency.
 *
 * ### Not here
 *
 * The iOS half is the file of the same name in `iosTest`, and its reason for
 * existing is sharper. Nothing about the client's behaviour once an engine is
 * chosen is covered here.
 */
class PlatformHttpEngineTest {

    @Test
    fun `android binds the OkHttp engine`() {
        assertSame(OkHttp, platformHttpEngineFactory)
    }
}
