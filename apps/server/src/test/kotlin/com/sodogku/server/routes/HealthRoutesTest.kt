package com.sodogku.server.routes

import com.sodogku.server.plugins.installSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The probe every deploy is gated on, booted with nothing behind it.
 *
 * `/_health` answers without a database, a config repository or any other
 * dependency, and the test proves that by installing only serialisation and the
 * one route. If it ever grew a dependency the route would start failing exactly
 * when something was wrong, which sounds reasonable and is the opposite of what
 * a liveness probe is for: the orchestrator would recycle a server that was
 * running fine beside a database that was not.
 *
 * ### Not here
 *
 * Readiness, in the sense of whether the database is reachable, is not this
 * endpoint and is not modelled. Serialisation of anything richer belongs to the
 * routes that return it.
 */
class HealthRoutesTest {

    @Test
    fun healthReturnsOk() = testApplication {
        application {
            installSerialization()
            routing { healthRoutes() }
        }
        val response = client.get("/_health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"ok\""))
    }
}
