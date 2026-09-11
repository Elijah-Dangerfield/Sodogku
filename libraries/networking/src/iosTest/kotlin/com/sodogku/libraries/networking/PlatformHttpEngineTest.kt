package com.sodogku.libraries.networking

import io.ktor.client.engine.darwin.Darwin
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * One assertion standing between the app and every request failing on device.
 *
 * Kotlin/Native ships engines that cannot do TLS, and Ktor will resolve one of
 * them if nothing names a factory. The app then compiles, links, runs in a
 * simulator against whatever is reachable, and fails every `https` call on a
 * real phone. Naming Darwin explicitly is the fix, and this pins that the
 * naming is still there, because the way it disappears is a refactor that looks
 * like tidying up an unused import.
 *
 * ### Not here
 *
 * The Android half is the file of the same name in `androidUnitTest`. How the
 * Darwin engine reports a device with no connection is `OfflineErrorsTest`.
 */
class PlatformHttpEngineTest {

    @Test
    fun `ios binds the Darwin engine so no TLS-incapable native engine can be auto-resolved`() {
        assertSame(Darwin, platformHttpEngineFactory)
    }
}
