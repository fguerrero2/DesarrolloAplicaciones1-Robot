package ar.edu.fguerrero.da1.robot_guia_universitario.data

import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendConfig
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendMode
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** CP-REST-01..05: cliente Retrofit contra un servidor HTTP simulado (MockWebServer). */
class RestRobotDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: RestRobotDataSource

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        source = RestRobotDataSource.create(server.url("/").toString(), 2)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `health llama a GET health`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok","service":"unitree-g1-backend"}"""))
        assertEquals("ok", source.health().status)
        assertEquals("/health", server.takeRequest().path)
    }

    @Test fun `telemetria parsea el contrato del plan e ignora campos extra`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"robotId":"g1-01","connection":"CONNECTED","battery":78,"operationalState":"IDLE","missionId":null,
                "missionProgress":0,"position":{"x":1.2,"y":2.5,"yaw":0.4},"velocity":{"vx":0,"vy":0,"omega":0},
                "alerts":[],"updatedAt":"2026-11-16T12:00:00Z","campoNuevo":123}""",
            ),
        )
        val t = source.telemetry("g1-01")
        assertEquals(78, t.battery)
        assertEquals(2.5, t.position.y, 0.0)
        assertEquals("/api/v1/robots/g1-01/telemetry", server.takeRequest().path)
    }

    @Test fun `comando confirmado devuelve la respuesta`() = runTest {
        server.enqueue(MockResponse().setBody("""{"commandId":"cmd-1021","status":"CONFIRMED","message":"Command accepted","timestamp":"2026-11-16T12:00:02Z"}"""))
        assertEquals("CONFIRMED", source.pause("m1").status)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/missions/m1/pause", req.path)
    }

    @Test fun `HTTP 409 se traduce a comando rechazado`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"busy"}"""))
        try { source.stop("g1-01"); fail("Debía lanzar RemoteRejectedException") } catch (e: RemoteRejectedException) {
            assertEquals(true, e.message!!.contains("409"))
        }
    }

    @Test fun `el proveedor reutiliza el cliente si la URL no cambia`() {
        val sim = SimulatedRobotDataSource(latencyMs = 0)
        var created = 0
        val provider = RemoteDataSourceProvider(sim) { url, t -> created++; RestRobotDataSource.create(url, t) }
        assertSame(sim, provider.get(BackendConfig(mode = BackendMode.SIMULATED)))
        val rest = BackendConfig(BackendMode.REST, "http://10.0.2.2:8000/", 3)
        val a = provider.get(rest)
        assertSame(a, provider.get(rest))
        assertNotSame(a, provider.get(rest.copy(baseUrl = "http://192.168.1.100:8000/")))
        assertEquals(2, created)
    }
}
