package com.sodogku.integration.setup

import com.sodogku.integration.helpers.IntegrationTest
import com.sodogku.libraries.config.getValueForPath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The harness's own smoke test: a real client fetches remote config from the
 * real in-process server over real Postgres. Proves the helpers work end to end
 * — real RemoteConfigRemoteDataSource → real HTTP client → real TCP → real
 * routes → real config source → real DB — before any journey tests lean on them.
 */
class HarnessSmokeTest : IntegrationTest() {

    @Test
    fun realClient_fetchesSeededConfig_fromRealServer() = integration {
        server.seedConfigValue("harness.smoke", "\"seeded\"")

        val config = client().remoteConfig.getConfig().getOrThrow()

        assertEquals("seeded", config.map.getValueForPath<String>("harness.smoke"))
    }
}
